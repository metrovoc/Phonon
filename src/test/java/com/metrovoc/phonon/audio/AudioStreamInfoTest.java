package com.metrovoc.phonon.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioStreamInfoTest {
    @Test
    void findsTheLatestSeekPointNotAfterTheTarget() {
        AudioStreamInfo info = new AudioStreamInfo(
            new byte[42],
            List.of(
                new OggPageScanner.SeekPoint(0, 42),
                new OggPageScanner.SeekPoint(1_000, 100),
                new OggPageScanner.SeekPoint(2_000, 200)
            ),
            48_000,
            3_000
        );

        assertEquals(new OggPageScanner.SeekPoint(0, 42), info.findSeekPoint(999));
        assertEquals(new OggPageScanner.SeekPoint(1_000, 100), info.findSeekPoint(1_500));
        assertEquals(new OggPageScanner.SeekPoint(2_000, 200), info.findSeekPoint(Long.MAX_VALUE));
    }

    @Test
    void fallsBackToTheFirstAudioByteWithoutAnIndex() {
        AudioStreamInfo info = new AudioStreamInfo(new byte[17], List.of(), 44_100, 0);
        assertEquals(new OggPageScanner.SeekPoint(0, 17), info.findSeekPoint(5_000));
    }
}
