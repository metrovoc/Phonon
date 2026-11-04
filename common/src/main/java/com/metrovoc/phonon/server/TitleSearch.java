package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finds tracks by title with yt-dlp, and fetches their pictures, on the server so players never contact the site.
 * Only a player's newest search keeps running.
 */
public final class TitleSearch {
    public static final int MAX_QUERY_CHARS = 200;
    public static final int MAX_RESULTS = 8;
    public static final int MAX_THUMBNAIL_BYTES = 64 * 1024;
    private static final long THUMBNAIL_TIMEOUT_SECONDS = 8;
    private static final int MAX_PICTURE_BYTES = 8 * 1024 * 1024;
    private static final HttpClient PICTURE_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    /** Fills 64 by 36 whatever the picture's shape, in few enough colours to sit among the game's own pixels. */
    private static final String THUMBNAIL_FILTER =
        "scale=64:36:force_original_aspect_ratio=increase:flags=area,crop=64:36,split[a][b];[a]palettegen=max_colors=32[p];[b][p]paletteuse=dither=none";

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Phonon title search");
        thread.setDaemon(true);
        return thread;
    });
    /** Separate, so slow pictures never make someone else's search wait. */
    private static final ExecutorService PICTURES = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Phonon search pictures");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Integer> newest = new ConcurrentHashMap<>();

    private record Outcome(List<SearchResult> results, AudioFailure failure) {}

    private TitleSearch() {}

    /** A request from a player's screen, for a title or link. Updating yt-dlp first is for operators. */
    public static void request(ServerPlayer player, int searchId, SearchSource source, String text, boolean updateFirst) {
        if (!SpeakerControl.canAddTracks(player)) {
            PlatformHelper.INSTANCE.sendSearchResults(player, searchId, List.of(), null);
            return;
        }
        if (!updateFirst || !PlayerPermissions.isOperator(player)) {
            run(player, searchId, source, text);
            return;
        }
        ToolsFlow.update(player.createCommandSourceStack(), success -> {
            if (success) {
                run(player, searchId, source, text);
            } else {
                PlatformHelper.INSTANCE.sendSearchResults(player, searchId, List.of(), AudioFailure.UNKNOWN);
            }
        });
    }

    private static void run(ServerPlayer player, int searchId, SearchSource source, String text) {
        if (AddRequest.parse(text) != null) {
            /* A retried link: the screen already shows it as searching, so clear that when it starts. */
            PlatformHelper.INSTANCE.sendSearchResults(player, searchId, List.of(), null);
            AddFlow.addFromText(player.createCommandSourceStack(), text);
        } else {
            search(player, searchId, source, text);
        }
    }

    /** Sends a player the results, then each picture as it arrives. */
    private static void search(ServerPlayer player, int searchId, SearchSource source, String text) {
        String query = text.strip();
        if (query.isEmpty() || query.length() > MAX_QUERY_CHARS) {
            PlatformHelper.INSTANCE.sendSearchResults(player, searchId, List.of(), null);
            return;
        }
        UUID uuid = player.getUUID();
        MinecraftServer server = player.level().getServer();
        newest.put(uuid, searchId);
        WORKERS.execute(() -> {
            if (!isNewest(uuid, searchId)) {
                return;
            }
            Outcome outcome = run(source, query);
            server.execute(() -> deliver(server, uuid, searchId, source, outcome));
            if (outcome.failure() == null) {
                PICTURES.execute(() -> sendPictures(server, uuid, searchId, outcome.results()));
            }
        });
    }

    private static void sendPictures(MinecraftServer server, UUID uuid, int searchId, List<SearchResult> results) {
        for (SearchResult result : results) {
            if (!isNewest(uuid, searchId)) {
                return;
            }
            byte[] png = result.picture().isEmpty() ? null : thumbnailOf(result.picture());
            if (png != null) {
                server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                    if (online != null) {
                        PlatformHelper.INSTANCE.sendSearchThumbnail(online, searchId, result.key(), png);
                    }
                });
            }
        }
    }

    private static boolean isNewest(UUID player, int searchId) {
        Integer current = newest.get(player);
        return current != null && current == searchId;
    }

    private static void deliver(MinecraftServer server, UUID uuid, int searchId, SearchSource source, Outcome outcome) {
        if (outcome.failure() == null) {
            ToolsFlow.worked(source.label());
        } else {
            ToolsFlow.failed(source.label(), outcome.failure());
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }
        PlatformHelper.INSTANCE.sendSearchResults(player, searchId, outcome.results(), outcome.failure());
        if (outcome.failure() == AudioFailure.TOOLS_MISSING) {
            AddFlow.offerInstall(player.createCommandSourceStack());
        }
    }

    public static void playerLeft(UUID player) {
        newest.remove(player);
    }

    /** {@code /phonon add} with words instead of a link: adds the first thing they find. */
    static void addFirstResult(CommandSourceStack source, String text) {
        String query = text.strip();
        MinecraftServer server = source.getServer();
        source.sendSystemMessage(Component.translatable("phonon.add.searching", query).withStyle(ChatFormatting.YELLOW));
        WORKERS.execute(() -> {
            Outcome outcome = run(SearchSource.YOUTUBE, query);
            server.execute(() -> {
                if (outcome.failure() != null) {
                    ToolsFlow.failed(SearchSource.YOUTUBE.label(), outcome.failure());
                    source.sendFailure(Component.translatable(outcome.failure().translationKey()));
                    if (outcome.failure() == AudioFailure.TOOLS_MISSING) {
                        AddFlow.offerInstall(source);
                    }
                } else if (outcome.results().isEmpty()) {
                    source.sendFailure(Component.translatable("phonon.add.no_results", query));
                } else {
                    SearchResult first = outcome.results().get(0);
                    AddFlow.start(source, new AddRequest(first.title(), first.url()));
                }
            });
        });
    }

    private static Outcome run(SearchSource source, String query) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("phonon_search_", ".txt");
            List<String> arguments = new ArrayList<>(List.of(
                AudioTools.command("yt-dlp"), "--dump-json", "--no-warnings", "--encoding", "utf-8", "--skip-download", "--ignore-errors"));
            if (source.flat()) {
                arguments.add("--flat-playlist");
            } else {
                arguments.add("--no-playlist");
            }
            AudioTools.installed("deno").ifPresent(deno -> arguments.addAll(List.of("--js-runtimes", "deno:" + deno)));
            arguments.addAll(AudioTools.networkArguments());
            arguments.addAll(List.of("--", source.query(query)));
            try {
                process = new ProcessBuilder(arguments)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            } catch (IOException e) {
                return new Outcome(List.of(), AudioFailure.TOOLS_MISSING);
            }
            if (!process.waitFor(source.timeoutSeconds(), TimeUnit.SECONDS)) {
                return new Outcome(List.of(), AudioFailure.TIMEOUT);
            }
            String log = Files.readString(output, StandardCharsets.UTF_8);
            List<SearchResult> results = SearchResult.parseLines(source, log);
            if (process.exitValue() != 0 && results.isEmpty()) {
                Phonon.LOGGER.warn("Title search on {} failed:\n{}", source.label(), log);
                return new Outcome(List.of(), AudioFailure.classify(log));
            }
            return new Outcome(results, null);
        } catch (IOException | InterruptedException e) {
            Phonon.LOGGER.warn("Title search failed", e);
            return new Outcome(List.of(), AudioFailure.UNKNOWN);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(output);
        }
    }

    /** SoundCloud's originals can be huge, so try its smaller size first. Not every picture has one. */
    private static byte[] thumbnailOf(String url) {
        String smaller = url.replace("-original.", "-t300x300.");
        byte[] png = thumbnail(smaller);
        return png != null || smaller.equals(url) ? png : thumbnail(url);
    }

    /** A tiny PNG, or null when there isn't one. The address has already been limited to the source's own sites. */
    private static byte[] thumbnail(String url) {
        Path picture = null;
        Path png = null;
        Process process = null;
        try {
            picture = Files.createTempFile("phonon_picture_", ".img");
            if (!download(url, picture)) {
                return null;
            }
            png = Files.createTempFile("phonon_thumbnail_", ".png");
            process = new ProcessBuilder(
                AudioTools.command("ffmpeg"), "-v", "error", "-y",
                "-protocol_whitelist", "file",
                "-i", picture.toString(),
                "-filter_complex", THUMBNAIL_FILTER,
                "-frames:v", "1", "-c:v", "png", "-f", "image2", png.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(THUMBNAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(png);
            return bytes.length > 0 && bytes.length <= MAX_THUMBNAIL_BYTES ? bytes : null;
        } catch (IOException | InterruptedException e) {
            Phonon.LOGGER.debug("No thumbnail at {}", url, e);
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(picture);
            deleteQuietly(png);
        }
    }

    /** Fetches at most a few megabytes, following no redirects, so only the checked address is ever contacted. */
    private static boolean download(String url, Path target) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = PICTURE_CLIENT.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(THUMBNAIL_TIMEOUT_SECONDS)).build(),
            HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                return false;
            }
            byte[] bytes = body.readNBytes(MAX_PICTURE_BYTES + 1);
            if (bytes.length > MAX_PICTURE_BYTES) {
                return false;
            }
            Files.write(target, bytes);
            return true;
        }
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                Phonon.LOGGER.debug("Could not delete {}", file, e);
            }
        }
    }
}
