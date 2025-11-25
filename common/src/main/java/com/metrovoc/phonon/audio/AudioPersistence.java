package com.metrovoc.phonon.audio;

import com.google.gson.*;
import com.metrovoc.phonon.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            array.add(obj);
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(array));
    }

    public static List<AudioResource> load(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(file);
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<AudioResource> resources = new ArrayList<>();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                UUID id = UUID.fromString(obj.get("id").getAsString());
                String name = obj.get("name").getAsString();
                String url = obj.get("url").getAsString();
                long duration = obj.get("duration").getAsLong();
                resources.add(new AudioResource(id, name, url, duration));
            }

            return resources;
        } catch (Exception e) {
            System.err.println(Constants.MOD_NAME + " failed to load audio resources: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
