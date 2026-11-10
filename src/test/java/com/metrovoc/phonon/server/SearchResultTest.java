package com.metrovoc.phonon.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchResultTest {
    private static final String YT = "\"url\":\"https://www.youtube.com/watch?v=abc_DEF-123\"";

    @Test
    void readsYouTube() {
        assertEquals(List.of(new SearchResult("abc_DEF-123", "Song", "Band", 61500, "https://www.youtube.com/watch?v=abc_DEF-123",
                "https://i.ytimg.com/vi/abc_DEF-123/mqdefault.jpg")),
            SearchResult.parseLines(SearchSource.YOUTUBE, "{\"id\":\"abc_DEF-123\",\"title\":\"Song\",\"channel\":\"Band\",\"duration\":61.5," + YT + "}\n"));
    }

    @Test
    void readsSoundCloudArtworkAndSkipsDefaultAvatars() {
        String art = "{\"id\":\"1\",\"title\":\"A\",\"uploader\":\"B\",\"duration\":1,\"webpage_url\":\"https://soundcloud.com/b/a\",\"thumbnail\":\"https://i1.sndcdn.com/artworks-x.jpg\"}";
        String avatar = "{\"id\":\"2\",\"title\":\"C\",\"uploader\":\"D\",\"duration\":1,\"webpage_url\":\"https://soundcloud.com/d/c\",\"thumbnail\":\"https://a1.sndcdn.com/images/default_avatar_large.png\"}";
        List<SearchResult> results = SearchResult.parseLines(SearchSource.SOUNDCLOUD, art + "\n" + avatar);
        assertEquals("https://i1.sndcdn.com/artworks-x.jpg", results.get(0).picture());
        assertEquals("", results.get(1).picture());
    }

    @Test
    void findsArtworkInTheThumbnailsList() {
        assertEquals("https://i1.sndcdn.com/art-original.png", SearchResult.parseLines(SearchSource.SOUNDCLOUD,
            "{\"id\":\"1\",\"title\":\"A\",\"duration\":1,\"webpage_url\":\"https://soundcloud.com/a\",\"thumbnails\":[{\"url\":\"https://i1.sndcdn.com/small.png\"},{\"url\":\"https://i1.sndcdn.com/art-original.png\"}]}").get(0).picture());
    }

    @Test
    void upgradesBilibiliLinksToHttps() {
        SearchResult result = SearchResult.parseLines(SearchSource.BILIBILI,
            "{\"id\":\"BV1\",\"title\":\"A\",\"uploader\":\"B\",\"duration\":5,\"webpage_url\":\"http://www.bilibili.com/video/BV1\",\"thumbnail\":\"http://i0.hdslb.com/a.jpg\"}").get(0);
        assertEquals("https://www.bilibili.com/video/BV1", result.url());
        assertEquals("https://i0.hdslb.com/a.jpg", result.picture());
    }

    @Test
    void refusesOtherHostsInLinksAndPictures() {
        assertEquals(List.of(), SearchResult.parseLines(SearchSource.YOUTUBE,
            "{\"id\":\"abc_DEF-123\",\"title\":\"A\",\"duration\":1,\"url\":\"https://evil.example/x\"}"));
        assertEquals(List.of(), SearchResult.parseLines(SearchSource.YOUTUBE,
            "{\"id\":\"abc_DEF-123\",\"title\":\"A\",\"duration\":1,\"url\":\"https://youtube.com.evil.example/x\"}"));
        assertEquals("", SearchResult.parseLines(SearchSource.SOUNDCLOUD,
            "{\"id\":\"1\",\"title\":\"A\",\"duration\":1,\"webpage_url\":\"https://soundcloud.com/a\",\"thumbnail\":\"https://evil.example/a.jpg\"}").get(0).picture());
    }

    @Test
    void skipsLiveStreamsAndNoise() {
        assertEquals(List.of(), SearchResult.parseLines(SearchSource.YOUTUBE, String.join("\n",
            "WARNING: something", "{\"id\":\"livestream1\",\"title\":\"Live\",\"duration\":null," + YT + "}", "{not json")));
    }

    @Test
    void stopsAtTheSourcesLimit() {
        String line = "{\"id\":\"abcdefghijk\",\"title\":\"Song\",\"duration\":1," + YT + "}\n";
        assertEquals(SearchSource.YOUTUBE.results(), SearchResult.parseLines(SearchSource.YOUTUBE, line.repeat(20)).size());
    }
}
