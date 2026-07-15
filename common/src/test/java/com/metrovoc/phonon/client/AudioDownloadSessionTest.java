package com.metrovoc.phonon.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioDownloadSessionTest {
    @Test
    void onlySharesWhenTheRequestedPositionIsAvailable() {
        AudioDownloadSession session = new AudioDownloadSession(1, UUID.randomUUID(), 10_000);

        assertFalse(session.canShare(9_999));
        assertTrue(session.canShare(10_000));
        assertTrue(session.canShare(12_000));
        assertFalse(session.canShare(12_001));

        assertTrue(session.receiveHeader(new byte[]{1}, 48_000, 9_800, false));
        assertFalse(session.canShare(9_799));
        assertTrue(session.canShare(9_800));
        assertTrue(session.canShare(10_000));
    }
}
