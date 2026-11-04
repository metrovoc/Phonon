package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.server.AudioFailure;
import com.metrovoc.phonon.server.SearchResult;
import com.metrovoc.phonon.server.SearchSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The player's current title search: its results, pictures, what's been added, and how it failed if it did. */
public final class ClientSearch {
    private static final ClientSearch INSTANCE = new ClientSearch();

    private final Map<String, GuiPort.Picture> pictures = new HashMap<>();
    private final Set<String> added = new HashSet<>();
    private final Map<String, AudioFailure> failedAdds = new HashMap<>();
    private int id;
    private String query = "";
    private SearchSource source = SearchSource.YOUTUBE;
    private boolean searching;
    private boolean updating;
    private boolean link;
    private List<SearchResult> results = List.of();
    @Nullable private AudioFailure failure;

    private ClientSearch() {}

    public static ClientSearch getInstance() {
        return INSTANCE;
    }

    /** Forgets the last search and returns the new one's id. The chosen source stays. */
    int start(String text, boolean isLink, boolean updateFirst) {
        clear();
        query = text;
        searching = !updateFirst;
        updating = updateFirst;
        link = isLink;
        return id;
    }

    void chooseSource(SearchSource chosen) {
        source = chosen;
    }

    SearchSource source() {
        return source;
    }

    public void setResults(int searchId, List<SearchResult> found, String failureName) {
        if (searchId != id || !(searching || updating)) {
            return;
        }
        searching = false;
        updating = false;
        results = List.copyOf(found);
        failure = parse(failureName);
        if (link && failure == null) {
            /* A retried link has started: its progress shows in the library. */
            clear();
        }
    }

    /** A link couldn't be downloaded. A result says so on its own row; a pasted link shows why in place of the library. */
    public void linkFailed(String url, String failureName) {
        if (results.stream().anyMatch(each -> each.url().equals(url))) {
            added.removeIf(key -> results.stream().anyMatch(each -> each.key().equals(key) && each.url().equals(url)));
            failedAdds.put(url, parse(failureName));
            return;
        }
        if (!GuiPort.speakerScreenOpen()) {
            return;
        }
        clear();
        query = url;
        link = true;
        failure = parse(failureName);
    }

    @Nullable
    private static AudioFailure parse(String name) {
        if (name.isEmpty()) {
            return null;
        }
        try {
            return AudioFailure.valueOf(name);
        } catch (IllegalArgumentException newerServer) {
            return AudioFailure.UNKNOWN;
        }
    }

    public void setThumbnail(int searchId, String key, byte[] png) {
        if (searchId == id && !pictures.containsKey(key) && results.stream().anyMatch(each -> each.key().equals(key))) {
            GuiPort.Picture picture = GuiPort.Picture.read(png);
            if (picture != null) {
                pictures.put(key, picture);
            }
        }
    }

    /** Ends the search, and any answer still on its way counts for nothing. */
    public void clear() {
        id++;
        pictures.values().forEach(GuiPort.Picture::release);
        pictures.clear();
        added.clear();
        failedAdds.clear();
        query = "";
        searching = false;
        updating = false;
        link = false;
        results = List.of();
        failure = null;
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

    boolean updating() {
        return updating;
    }

    boolean isLink() {
        return link;
    }

    @Nullable
    AudioFailure failure() {
        return failure;
    }

    List<SearchResult> results() {
        return results;
    }

    @Nullable
    GuiPort.Picture picture(String key) {
        return pictures.get(key);
    }

    void markAdded(SearchResult result) {
        added.add(result.key());
        failedAdds.remove(result.url());
    }

    @Nullable
    AudioFailure failedAdd(String url) {
        return failedAdds.get(url);
    }

    boolean wasAdded(String key) {
        return added.contains(key);
    }
}
