package com.metrovoc.phonon.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** A video found by title. */
public record SearchResult(String videoId, String title, String uploader, long durationMs) {
    private static final int MAX_TEXT_CHARS = 256;
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,20}");

    public String url() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    /** Reads yt-dlp's output, one JSON object per line. Entries with no length are live streams, which can't be added. */
    static List<SearchResult> parseLines(String lines, int limit) {
        List<SearchResult> results = new ArrayList<>();
        for (String line : lines.split("\n")) {
            if (!line.startsWith("{") || results.size() >= limit) {
                continue;
            }
            try {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                String id = text(json, "id");
                JsonElement duration = json.get("duration");
                if (VIDEO_ID.matcher(id).matches() && duration != null && !duration.isJsonNull()) {
                    String channel = text(json, "channel");
                    results.add(new SearchResult(id, shortened(text(json, "title")),
                        shortened(channel.isEmpty() ? text(json, "uploader") : channel),
                        (long) (duration.getAsDouble() * 1000)));
                }
            } catch (RuntimeException malformed) {
                // Not a result: skip the line
            }
        }
        return results;
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String shortened(String text) {
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }
}
