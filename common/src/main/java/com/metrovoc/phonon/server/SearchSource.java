package com.metrovoc.phonon.server;

import java.util.List;

/** Where a title search can look. */
public enum SearchSource {
    YOUTUBE("YouTube", "ytsearch", 8, true, 20, List.of("youtube.com", "youtu.be"), List.of("ytimg.com")),
    SOUNDCLOUD("SoundCloud", "scsearch", 8, true, 25, List.of("soundcloud.com"), List.of("sndcdn.com")),
    /** Its fast search only returns ids, so each result needs its own lookup. */
    BILIBILI("Bilibili", "bilisearch", 5, false, 40, List.of("bilibili.com", "b23.tv"), List.of("hdslb.com"));

    private final String label;
    private final String prefix;
    private final int results;
    private final boolean flat;
    private final int timeoutSeconds;
    private final List<String> linkHosts;
    private final List<String> pictureHosts;

    SearchSource(String label, String prefix, int results, boolean flat, int timeoutSeconds, List<String> linkHosts, List<String> pictureHosts) {
        this.label = label;
        this.prefix = prefix;
        this.results = results;
        this.flat = flat;
        this.timeoutSeconds = timeoutSeconds;
        this.linkHosts = linkHosts;
        this.pictureHosts = pictureHosts;
    }

    public String label() { return label; }
    public String query(String text) { return prefix + results + ":" + text; }
    public int results() { return results; }
    public boolean flat() { return flat; }
    public int timeoutSeconds() { return timeoutSeconds; }
    boolean isLink(String host) { return matches(linkHosts, host); }
    boolean isPicture(String host) { return matches(pictureHosts, host); }

    private static boolean matches(List<String> domains, String host) {
        return domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    public static SearchSource of(int ordinal) {
        SearchSource[] all = values();
        return all[Math.max(0, Math.min(ordinal, all.length - 1))];
    }
}
