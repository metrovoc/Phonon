package com.metrovoc.phonon.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddRequestTest {
    private static final String URL = "https://example.com/watch?v=abc";

    @Test
    void linkAloneHasNoName() {
        AddRequest request = AddRequest.parse(URL);
        assertEquals(URL, request.url());
        assertFalse(request.hasName());
    }

    @Test
    void nameNeedsNoQuotes() {
        AddRequest request = AddRequest.parse("My Favourite Song " + URL);
        assertEquals("My Favourite Song", request.name());
        assertEquals(URL, request.url());
    }

    @Test
    void chineseNameWorks() {
        assertEquals("晴天", AddRequest.parse("晴天 " + URL).name());
    }

    @Test
    void oldQuotedFormStillWorks() {
        assertEquals("My Song", AddRequest.parse("\"My Song\" " + URL).name());
    }

    @Test
    void rejectsAnythingButWebLinks() {
        assertNull(AddRequest.parse("my song"));
        assertNull(AddRequest.parse("--exec evil"));
        assertNull(AddRequest.parse("name file:///etc/passwd"));
    }
}
