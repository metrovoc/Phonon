package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/** The server's track list: adding, removing, saving, and importing the world's music folder. */
public final class AudioLibrary {
    private static final String DATA_FILE = "phonon_audio.json";
    private static final String MUSIC_FOLDER = "phonon_music";
    private static final String FILE_URL_PREFIX = "file:";
    private static final Set<String> AUDIO_EXTENSIONS =
        Set.of("ogg", "oga", "mp3", "wav", "flac", "m4a", "aac", "opus", "wma");

    private static final Set<String> importing = new HashSet<>();

    private AudioLibrary() {}

    public static Path dataFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DATA_FILE);
    }

    public static Path musicFolder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(MUSIC_FOLDER).toAbsolutePath().normalize();
    }

    /** The preferred name, numbered when taken and cut to fit. */
    public static String uniqueName(String preferred) {
        return uniqueName(preferred, name -> AudioManager.getInstance().getResourceByName(name).isPresent());
    }

    static String uniqueName(String preferred, java.util.function.Predicate<String> taken) {
        String base = preferred.isBlank() ? "Untitled" : preferred.strip();
        base = base.substring(0, Math.min(base.length(), AudioLimits.MAX_RESOURCE_NAME_CHARS - 8));
        String name = base;
        for (int number = 2; taken.test(name); number++) {
            name = base + " (" + number + ")";
        }
        return name;
    }

    /** Server thread only. Registers stored audio, saves the list and tells every player. */
    public static AudioResource register(MinecraftServer server, UUID id, String name, String url) {
        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        AudioResource resource = new AudioResource(
            id, name, url, storage.getDurationMs(id), storage.getAudioSize(id));
        if (!AudioManager.getInstance().addResource(resource)) {
            storage.deleteAudio(id);
            return null;
        }
        changed(server);
        return resource;
    }

    public static void remove(MinecraftServer server, AudioResource resource) {
        AudioManager.getInstance().removeResource(resource.id());
        ServerAudioStorage.getInstance().deleteAudio(resource.id());
        changed(server);
    }

    public static boolean isFromMusicFolder(AudioResource resource) {
        return resource.url().startsWith(FILE_URL_PREFIX);
    }

    private static void changed(MinecraftServer server) {
        try {
            AudioPersistence.save(dataFile(server), AudioManager.getInstance().getAllResources());
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to save audio resources", e);
        }
        PlatformHelper.INSTANCE.broadcastAudioResources(server);
    }

    /**
     * Server thread only. Imports audio files new to the world's music folder, leaving the files in place.
     * Each imported file is reported with null, each failure with its cause.
     */
    public static int importMusicFolder(MinecraftServer server, BiConsumer<String, AudioFailure> report) {
        Path folder = musicFolder(server);
        List<Path> candidates;
        try {
            Files.createDirectories(folder);
            try (Stream<Path> files = Files.list(folder)) {
                candidates = files.filter(AudioLibrary::isAudioFile).sorted().toList();
            }
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to read music folder {}", folder, e);
            return 0;
        }

        Set<String> known = new HashSet<>();
        for (AudioResource resource : AudioManager.getInstance().getAllResources()) {
            known.add(resource.url());
        }

        int started = 0;
        for (Path file : candidates) {
            String url = FILE_URL_PREFIX + file.getFileName();
            if (known.contains(url) || !importing.add(url)) {
                continue;
            }
            started++;
            UUID id = UUID.randomUUID();
            ServerAudioStorage.getInstance().importAndStore(id, file).thenAccept(result -> server.execute(() -> {
                importing.remove(url);
                String fileName = file.getFileName().toString();
                if (!result.success()) {
                    Phonon.LOGGER.error("Could not import {}: {}", file, result.failure());
                    report.accept(fileName, result.failure());
                } else if (register(server, id, uniqueName(result.title()), url) != null) {
                    report.accept(fileName, null);
                } else {
                    report.accept(fileName, AudioFailure.UNKNOWN);
                }
            }));
        }
        return started;
    }

    private static boolean isAudioFile(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && Files.isRegularFile(path)
            && AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
