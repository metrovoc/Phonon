package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.server.SearchResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The player's current title search: its results, thumbnails, and which results they've added. */
public final class ClientSearch {
    private static final ClientSearch INSTANCE = new ClientSearch();

    private final Map<String, GuiPort.Picture> pictures = new HashMap<>();
    private final Set<String> added = new HashSet<>();
    private int id;
    private String query = "";
    private boolean searching;
    private List<SearchResult> results = List.of();
    private String failureKey = "";

    private ClientSearch() {}

    public static ClientSearch getInstance() {
        return INSTANCE;
    }

    /** Forgets the last search and returns the new one's id. */
    int start(String text) {
        clear();
        query = text;
        searching = true;
        return id;
    }

    public void setResults(int searchId, List<SearchResult> found, String failure) {
        if (searchId == id && searching) {
            searching = false;
            results = List.copyOf(found);
            failureKey = failure;
        }
    }

    public void setThumbnail(int searchId, String videoId, byte[] png) {
        if (searchId == id && !pictures.containsKey(videoId) && results.stream().anyMatch(each -> each.videoId().equals(videoId))) {
            GuiPort.Picture picture = GuiPort.Picture.read(png);
            if (picture != null) {
                pictures.put(videoId, picture);
            }
        }
    }

    /** Ends the search, and any answer still on its way counts for nothing. */
    public void clear() {
        id++;
        pictures.values().forEach(GuiPort.Picture::release);
        pictures.clear();
        added.clear();
        query = "";
        searching = false;
        results = List.of();
        failureKey = "";
    }

    boolean active() {
        return !query.isEmpty();
    }

    String query() {
        return query;
    }

    boolean searching() {
        return searching;
    }

    String failureKey() {
        return failureKey;
    }

    List<SearchResult> results() {
        return results;
    }

    @Nullable
    GuiPort.Picture picture(String videoId) {
        return pictures.get(videoId);
    }

    void markAdded(String videoId) {
        added.add(videoId);
    }

    boolean wasAdded(String videoId) {
        return added.contains(videoId);
    }
}
