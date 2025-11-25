package com.metrovoc.phonon.webui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.config.PhononServerConfig;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Embedded HTTP server for WebUI.
 * Provides REST API for track management and serves static frontend.
 */
public class PhononWebServer extends NanoHTTPD {

    private static PhononWebServer instance;

    private final Map<UUID, DownloadProgress> activeDownloads = new ConcurrentHashMap<>();
    private Consumer<TrackRequest> onAddTrack;
    private Consumer<UUID> onDeleteTrack;

    private PhononWebServer(int port) {
        super("0.0.0.0", port);
    }

    public static synchronized void launch() {
        if (instance != null) {
            Phonon.LOGGER.warn("WebUI server already running");
            return;
        }

        if (!PhononServerConfig.isWebUiEnabled()) {
            Phonon.LOGGER.info("WebUI disabled in config");
            return;
        }

        int port = PhononServerConfig.getWebUiPort();
        int attempts = 0;
        int maxAttempts = 10;

        while (attempts < maxAttempts) {
            try {
                instance = new PhononWebServer(port);
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Phonon.LOGGER.info("WebUI started at http://0.0.0.0:{}", port);
                return;
            } catch (IOException e) {
                Phonon.LOGGER.warn("Port {} in use, trying {}", port, port + 1);
                port++;
                attempts++;
            }
        }

        Phonon.LOGGER.error("Failed to start WebUI after {} attempts", maxAttempts);
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            instance.stop();
            instance = null;
            Phonon.LOGGER.info("WebUI stopped");
        }
    }

    public static PhononWebServer getInstance() {
        return instance;
    }

    public void setOnAddTrack(Consumer<TrackRequest> handler) {
        this.onAddTrack = handler;
    }

    public void setOnDeleteTrack(Consumer<UUID> handler) {
        this.onDeleteTrack = handler;
    }

    public void updateDownloadProgress(UUID id, int percent, String status) {
        activeDownloads.put(id, new DownloadProgress(id, percent, status));
    }

    public void removeDownloadProgress(UUID id) {
        activeDownloads.remove(id);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        // Token auth check for API routes
        if (uri.startsWith("/api/")) {
            String token = PhononServerConfig.getWebUiToken();
            if (token != null && !token.isEmpty()) {
                String authHeader = session.getHeaders().get("authorization");
                if (authHeader == null || !authHeader.equals("Bearer " + token)) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED,
                        MIME_JSON, "{\"error\":\"Unauthorized\"}");
                }
            }
        }

        // API routes
        if (uri.startsWith("/api/")) {
            return handleApi(uri.substring(4), method, session);
        }

        // Static files
        return serveStatic(uri);
    }

    private Response handleApi(String path, Method method, IHTTPSession session) {
        try {
            // GET /api/tracks
            if (path.equals("/tracks") && method == Method.GET) {
                return jsonResponse(tracksToJson(AudioManager.getInstance().getAllResources()));
            }

            // POST /api/tracks
            if (path.equals("/tracks") && method == Method.POST) {
                Map<String, String> body = new HashMap<>();
                session.parseBody(body);
                String postData = body.get("postData");
                if (postData == null) {
                    return errorResponse(Response.Status.BAD_REQUEST, "Missing body");
                }

                TrackRequest req = parseTrackRequest(postData);
                if (req.name == null || req.name.isBlank()) {
                    return errorResponse(Response.Status.BAD_REQUEST, "Missing name");
                }
                if (req.url == null || req.url.isBlank()) {
                    return errorResponse(Response.Status.BAD_REQUEST, "Missing url");
                }

                if (AudioManager.getInstance().getResourceByName(req.name).isPresent()) {
                    return errorResponse(Response.Status.CONFLICT, "Track already exists");
                }

                if (onAddTrack != null) {
                    onAddTrack.accept(req);
                }

                return jsonResponse("{\"status\":\"downloading\",\"name\":\"" + escapeJson(req.name) + "\"}");
            }

            // DELETE /api/tracks/{id}
            if (path.startsWith("/tracks/") && method == Method.DELETE) {
                String idStr = path.substring(8);
                UUID id;
                try {
                    id = UUID.fromString(idStr);
                } catch (IllegalArgumentException e) {
                    return errorResponse(Response.Status.BAD_REQUEST, "Invalid UUID");
                }

                Optional<AudioResource> resource = AudioManager.getInstance().getResource(id);
                if (resource.isEmpty()) {
                    return errorResponse(Response.Status.NOT_FOUND, "Track not found");
                }

                AudioManager.getInstance().removeResource(id);
                if (onDeleteTrack != null) {
                    onDeleteTrack.accept(id);
                }
                return jsonResponse("{\"status\":\"deleted\"}");
            }

            // GET /api/downloads (active download progress)
            if (path.equals("/downloads") && method == Method.GET) {
                return jsonResponse(downloadsToJson());
            }

            return errorResponse(Response.Status.NOT_FOUND, "Unknown endpoint");

        } catch (Exception e) {
            Phonon.LOGGER.error("API error", e);
            return errorResponse(Response.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    private Response serveStatic(String uri) {
        if (uri.equals("/") || uri.isEmpty()) {
            uri = "/index.html";
        }

        String resourcePath = "/webui" + uri;
        InputStream stream = getClass().getResourceAsStream(resourcePath);

        if (stream == null) {
            // SPA fallback: serve index.html for unmatched routes
            stream = getClass().getResourceAsStream("/webui/index.html");
            if (stream == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND,
                    MIME_HTML, "Not Found");
            }
        }

        String mimeType = getMimeType(uri);
        return newChunkedResponse(Response.Status.OK, mimeType, stream);
    }

    private String getMimeType(String uri) {
        if (uri.endsWith(".html")) return MIME_HTML;
        if (uri.endsWith(".css")) return "text/css";
        if (uri.endsWith(".js")) return "application/javascript";
        if (uri.endsWith(".json")) return MIME_JSON;
        if (uri.endsWith(".svg")) return "image/svg+xml";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static final String MIME_JSON = "application/json";

    private Response jsonResponse(String json) {
        Response resp = newFixedLengthResponse(Response.Status.OK, MIME_JSON, json);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    private Response errorResponse(Response.Status status, String message) {
        return newFixedLengthResponse(status, MIME_JSON,
            "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    private String tracksToJson(List<AudioResource> tracks) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (AudioResource t : tracks) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"id\":\"").append(t.id()).append("\",");
            sb.append("\"name\":\"").append(escapeJson(t.name())).append("\",");
            sb.append("\"url\":\"").append(escapeJson(t.url())).append("\",");
            sb.append("\"durationMs\":").append(t.durationMs());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String downloadsToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (DownloadProgress p : activeDownloads.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"id\":\"").append(p.id).append("\",");
            sb.append("\"percent\":").append(p.percent).append(",");
            sb.append("\"status\":\"").append(escapeJson(p.status)).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private TrackRequest parseTrackRequest(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        String name = obj.has("name") ? obj.get("name").getAsString() : null;
        String url = obj.has("url") ? obj.get("url").getAsString() : null;
        return new TrackRequest(name, url);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record TrackRequest(String name, String url) {}
    private record DownloadProgress(UUID id, int percent, String status) {}
}
