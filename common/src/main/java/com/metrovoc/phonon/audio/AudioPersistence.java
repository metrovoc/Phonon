package com.metrovoc.phonon.audio;

import com.google.gson.*;
import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.Phonon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simple JSON-based persistence for audio resources.
 * No database, no ORM, just flat files.
 */
public class AudioPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save(Path file, List<AudioResource> resources) throws IOException {
        JsonArray array = new JsonArray();
        for (AudioResource resource : resources) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", resource.id().toString());
            obj.addProperty("name", resource.name());
            obj.addProperty("url", resource.url());
            obj.addProperty("duration", resource.durationMs());
            obj.addProperty("size", resource.sizeBytes());
            array.add(obj);
        }

        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(array));
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static List<AudioResource> load(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(file);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                Phonon.LOGGER.error("Audio resource file {} does not contain a JSON array", file);
                return new ArrayList<>();
            }
            JsonArray array = root.getAsJsonArray();
            List<AudioResource> resources = new ArrayList<>();

            for (JsonElement element : array) {
                if (resources.size() >= AudioLimits.MAX_RESOURCE_COUNT) {
                    Phonon.LOGGER.warn("Ignoring audio resources beyond the {} entry limit",
                        AudioLimits.MAX_RESOURCE_COUNT);
                    break;
                }
                try {
                    JsonObject obj = element.getAsJsonObject();
                    UUID id = UUID.fromString(obj.get("id").getAsString());
                    String name = obj.get("name").getAsString();
                    String url = obj.get("url").getAsString();
                    long duration = obj.get("duration").getAsLong();
                    // Backward compatibility: size field may not exist in old saves.
                    long size = obj.has("size") ? obj.get("size").getAsLong() : 0L;
                    if (!name.isBlank()
                        && name.length() <= AudioLimits.MAX_RESOURCE_NAME_CHARS
                        && url.length() <= AudioLimits.MAX_RESOURCE_URL_CHARS) {
                        resources.add(new AudioResource(id, name, url, duration, size));
                    }
                } catch (RuntimeException invalidEntry) {
                    Phonon.LOGGER.warn("Skipping malformed audio resource entry in {}", file);
                }
            }

            return resources;
        } catch (Exception e) {
            Phonon.LOGGER.error("{} failed to load audio resources from {}: {}",
                Constants.MOD_NAME, file, e.getMessage());
            return new ArrayList<>();
        }
    }
}
