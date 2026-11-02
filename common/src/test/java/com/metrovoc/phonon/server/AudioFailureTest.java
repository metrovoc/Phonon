package com.metrovoc.phonon.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioFailureTest {
    @Test
    void readsCauseFromRealYtDlpMessages() {
        assertEquals(AudioFailure.NOT_FOUND, AudioFailure.classify(
            "ERROR: [generic] t1: Unable to download webpage: HTTP Error 404: File not found"));
        assertEquals(AudioFailure.TOO_LARGE, AudioFailure.classify(
            "[download] File is larger than max-filesize (10 bytes > 5 bytes). Aborting."));
        assertEquals(AudioFailure.BOT_CHECK, AudioFailure.classify(
            "ERROR: [youtube] abc: Sign in to confirm you're not a bot."));
        assertEquals(AudioFailure.RESTRICTED, AudioFailure.classify("ERROR: [youtube] abc: Private video."));
        assertEquals(AudioFailure.REGION, AudioFailure.classify(
            "ERROR: The uploader has not made this video available in your country; not available in your country"));
        assertEquals(AudioFailure.TOOLS_MISSING, AudioFailure.classify(
            "ERROR: Postprocessing: ffprobe and ffmpeg not found. Please install or provide the path"));
        assertEquals(AudioFailure.UNKNOWN, AudioFailure.classify("ERROR: something new"));
    }

    @Test
    void mapsHttpStatus() {
        assertEquals(AudioFailure.NOT_FOUND, AudioFailure.fromHttpStatus(404));
        assertEquals(AudioFailure.RESTRICTED, AudioFailure.fromHttpStatus(403));
        assertEquals(AudioFailure.UNKNOWN, AudioFailure.fromHttpStatus(500));
    }
}
