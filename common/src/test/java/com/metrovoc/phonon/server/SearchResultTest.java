package com.metrovoc.phonon.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchResultTest {
    private static final int LIMIT = 8;

    private static List<SearchResult> parse(String lines) {
        return SearchResult.parseLines(lines, LIMIT);
    }

    @Test
    void readsTitleUploaderAndLength() {
        List<SearchResult> results = parse(
            "{\"id\":\"abc_DEF-123\",\"title\":\"Song\",\"channel\":\"Band\",\"duration\":61.5}\n");
        assertEquals(List.of(new SearchResult("abc_DEF-123", "Song", "Band", 61500)), results);
        assertEquals("https://www.youtube.com/watch?v=abc_DEF-123", results.get(0).url());
    }

    @Test
    void fallsBackToUploaderWithoutChannel() {
        assertEquals("Someone", parse(
            "{\"id\":\"abcdefghijk\",\"title\":\"Song\",\"uploader\":\"Someone\",\"duration\":1}").get(0).uploader());
    }

    @Test
    void skipsLiveStreamsBadIdsAndNoise() {
        assertEquals(List.of(), parse(String.join("\n",
            "WARNING: something",
            "{\"id\":\"livestream1\",\"title\":\"Live\",\"duration\":null}",
            "{\"id\":\"../../etc/x\",\"title\":\"Bad\",\"duration\":5}",
            "{not json")));
    }

    @Test
    void stopsAtTheLimit() {
        String line = "{\"id\":\"abcdefghijk\",\"title\":\"Song\",\"duration\":1}\n";
        assertEquals(LIMIT, parse(line.repeat(20)).size());
    }
}
