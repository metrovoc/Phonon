package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finds videos by title with yt-dlp, and fetches their thumbnails, on the server so players never contact the site.
 * Only a player's newest search keeps running.
 */
public final class TitleSearch {
    public static final int MAX_QUERY_CHARS = 200;
    public static final int MAX_RESULTS = 8;
    public static final int MAX_THUMBNAIL_BYTES = 64 * 1024;
    private static final long SEARCH_TIMEOUT_SECONDS = 20;
    private static final long THUMBNAIL_TIMEOUT_SECONDS = 8;
    private static final String THUMBNAIL_FILTER =
        "scale=64:36:flags=area,split[a][b];[a]palettegen=max_colors=32[p];[b][p]paletteuse=dither=none";

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Phonon title search");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Integer> newest = new ConcurrentHashMap<>();

    private record Outcome(List<SearchResult> results, AudioFailure failure) {}

    private TitleSearch() {}

    /** Sends a player the results, then each thumbnail as it arrives. */
    public static void search(ServerPlayer player, int searchId, String text) {
        String query = text.strip();
        if (query.isEmpty() || query.length() > MAX_QUERY_CHARS) {
            return;
        }
        UUID uuid = player.getUUID();
        MinecraftServer server = player.level().getServer();
        newest.put(uuid, searchId);
        WORKERS.execute(() -> {
            if (!isNewest(uuid, searchId)) {
                return;
            }
            Outcome outcome = run(query);
            server.execute(() -> deliver(server, uuid, searchId, outcome));
            if (outcome.failure() != null) {
                return;
            }
            for (SearchResult result : outcome.results()) {
                if (!isNewest(uuid, searchId)) {
                    return;
                }
                byte[] png = thumbnail(result.videoId());
                if (png != null) {
                    server.execute(() -> {
                        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                        if (online != null) {
                            PlatformHelper.INSTANCE.sendSearchThumbnail(online, searchId, result.videoId(), png);
                        }
                    });
                }
            }
        });
    }

    private static boolean isNewest(UUID player, int searchId) {
        Integer current = newest.get(player);
        return current != null && current == searchId;
    }

    private static void deliver(MinecraftServer server, UUID uuid, int searchId, Outcome outcome) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }
        String failure = outcome.failure() == null ? "" : outcome.failure().translationKey();
        PlatformHelper.INSTANCE.sendSearchResults(player, searchId, outcome.results(), failure);
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
            Outcome outcome = run(query);
            server.execute(() -> {
                if (outcome.failure() != null) {
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

    private static Outcome run(String query) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("phonon_search_", ".txt");
            List<String> arguments = new ArrayList<>(List.of(
                AudioTools.command("yt-dlp"), "--flat-playlist", "--dump-json", "--no-warnings", "--encoding", "utf-8"));
            AudioTools.installed("deno").ifPresent(deno -> arguments.addAll(List.of("--js-runtimes", "deno:" + deno)));
            arguments.addAll(List.of("--", "ytsearch" + MAX_RESULTS + ":" + query));
            try {
                process = new ProcessBuilder(arguments)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            } catch (IOException e) {
                return new Outcome(List.of(), AudioFailure.TOOLS_MISSING);
            }
            if (!process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return new Outcome(List.of(), AudioFailure.TIMEOUT);
            }
            String log = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                Phonon.LOGGER.warn("Title search failed:\n{}", log);
                return new Outcome(List.of(), AudioFailure.classify(log));
            }
            return new Outcome(SearchResult.parseLines(log, MAX_RESULTS), null);
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

    /** A tiny PNG in a few colours, so it sits among the game's own pixels. Null when there isn't one. */
    private static byte[] thumbnail(String videoId) {
        Path png = null;
        Process process = null;
        try {
            png = Files.createTempFile("phonon_thumbnail_", ".png");
            process = new ProcessBuilder(
                AudioTools.command("ffmpeg"), "-v", "error", "-y",
                "-protocol_whitelist", "https,tls,tcp,crypto",
                "-i", "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg",
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
            Phonon.LOGGER.debug("No thumbnail for {}", videoId, e);
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(png);
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
