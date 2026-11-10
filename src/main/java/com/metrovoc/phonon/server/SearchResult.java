package com.metrovoc.phonon.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** A track found by title. The picture is empty when there's nothing to show. */
public record SearchResult(String key, String title, String uploader, long durationMs, String url, String picture) {
    private static final int MAX_TEXT_CHARS = 256;
    private static final int MAX_URL_CHARS = 512;

    /** Reads yt-dlp's output, one JSON object per line. Entries with no length are live streams, which can't be added. */
    static List<SearchResult> parseLines(SearchSource source, String lines) {
        List<SearchResult> results = new ArrayList<>();
        for (String line : lines.split("\n")) {
            if (!line.startsWith("{") || results.size() >= source.results()) {
                continue;
            }
            try {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                JsonElement duration = json.get("duration");
                String url = text(json, "webpage_url");
                url = safeUrl(url.isEmpty() ? text(json, "url") : url, source, false);
                if (url == null || duration == null || duration.isJsonNull() || text(json, "title").isEmpty()) {
                    continue;
                }
                String channel = text(json, "channel");
                results.add(new SearchResult(shortened(text(json, "id")), shortened(text(json, "title")),
                    shortened(channel.isEmpty() ? text(json, "uploader") : channel),
                    (long) (duration.getAsDouble() * 1000), url, picture(json, source)));
            } catch (RuntimeException malformed) {
                // Not a result: skip the line
            }
        }
        return results;
    }

    private static String picture(JsonObject json, SearchSource source) {
        String id = text(json, "id");
        if (source == SearchSource.YOUTUBE) {
            return id.matches("[A-Za-z0-9_-]{6,20}") ? "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg" : "";
        }
        String address = text(json, "thumbnail");
        JsonElement others = json.get("thumbnails");
        if (address.isEmpty() && others != null && others.isJsonArray() && !others.getAsJsonArray().isEmpty()) {
            address = text(others.getAsJsonArray().get(others.getAsJsonArray().size() - 1).getAsJsonObject(), "url");
        }
        String picture = safeUrl(address, source, true);
        return picture == null || picture.contains("default_avatar") ? "" : picture;
    }

    /** Only https links to the source's own sites are ever fetched or added. Returns null for anything else. */
    private static String safeUrl(String url, SearchSource source, boolean picture) {
        if (url.length() > MAX_URL_CHARS) {
            return null;
        }
        String https = url.startsWith("//") ? "https:" + url : url.replaceFirst("^http://", "https://");
        try {
            URI uri = new URI(https);
            String host = uri.getHost();
            boolean allowed = "https".equals(uri.getScheme()) && host != null && (picture ? source.isPicture(host) : source.isLink(host));
            return allowed ? https : null;
        } catch (java.net.URISyntaxException invalid) {
            return null;
        }
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String shortened(String text) {
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }
}
