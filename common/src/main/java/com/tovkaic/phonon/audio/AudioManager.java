package com.tovkaic.phonon.audio;

import com.tovkaic.phonon.Constants;
import com.tovkaic.phonon.network.AudioPacketSender;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side audio resource manager.
 * Single source of truth for all audio resources.
 */
public class AudioManager {
    private static AudioManager instance;
    private final Map<UUID, AudioResource> resources = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    private AudioManager() {}

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public void addResource(AudioResource resource) {
        resources.put(resource.id(), resource);
        nameIndex.put(resource.name().toLowerCase(), resource.id());
    }

    public void removeResource(UUID id) {
        AudioResource resource = resources.remove(id);
        if (resource != null) {
            nameIndex.remove(resource.name().toLowerCase());
        }
    }

    public void loadResources(List<AudioResource> resourceList) {
        resources.clear();
        nameIndex.clear();
        for (AudioResource resource : resourceList) {
            resources.put(resource.id(), resource);
            nameIndex.put(resource.name().toLowerCase(), resource.id());
        }
    }

    private AudioPacketSender packetSender;
    private final Path serverCacheDir = java.nio.file.Paths.get("phonon_server_cache");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void setPacketSender(AudioPacketSender sender) {
        this.packetSender = sender;
    }

    public void handleChunkRequest(Object player, UUID resourceId, int chunkIndex) {
        getResource(resourceId).ifPresent(resource -> {
            try {
                Path file = resolveFile(resource);
                if (!Files.exists(file)) {
                    downloadFile(resource, file);
                }
                
                if (Files.exists(file)) {
                    byte[] data = Files.readAllBytes(file);
                    int totalChunks = (int) Math.ceil((double) data.length / Constants.CHUNK_SIZE);
                    
                    if (chunkIndex >= 0 && chunkIndex < totalChunks) {
                        int start = chunkIndex * Constants.CHUNK_SIZE;
                        int end = Math.min(start + Constants.CHUNK_SIZE, data.length);
                        byte[] chunk = Arrays.copyOfRange(data, start, end);
                        
                        if (packetSender != null) {
                            packetSender.sendChunk(player, resourceId, chunk, chunkIndex, totalChunks);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private Path resolveFile(AudioResource resource) {
        try {
            Files.createDirectories(serverCacheDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return serverCacheDir.resolve(resource.id() + ".ogg");
    }

    private void downloadFile(AudioResource resource, Path target) throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(resource.url()))
            .GET()
            .build();
            
        java.net.http.HttpResponse<java.io.InputStream> response = httpClient.send(
            request,
            java.net.http.HttpResponse.BodyHandlers.ofInputStream()
        );
        
        if (response.statusCode() == 200) {
            Files.copy(response.body(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Optional<AudioResource> getResource(UUID id) {
        return Optional.ofNullable(resources.get(id));
    }

    public Optional<AudioResource> getResourceByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase());
        return id != null ? getResource(id) : Optional.empty();
    }

    public List<AudioResource> getAllResources() {
        return new ArrayList<>(resources.values());
    }

    public List<AudioResource> searchResources(String query) {
        String lowerQuery = query.toLowerCase();
        return resources.values().stream()
            .filter(r -> r.name().toLowerCase().contains(lowerQuery))
            .toList();
    }
}
