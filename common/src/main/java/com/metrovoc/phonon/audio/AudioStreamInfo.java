package com.metrovoc.phonon.audio;

import java.util.Collections;
import java.util.List;

public record AudioStreamInfo(
    byte[] headerBytes,
    List<OggPageScanner.SeekPoint> seekTable,
    int sampleRate
) {
    public int findOffsetForTime(long timeMs) {
        if (seekTable.isEmpty() || timeMs < seekTable.getFirst().timeMs()) {
            return headerBytes.length;
        }

        int idx = Collections.binarySearch(
            seekTable,
            new OggPageScanner.SeekPoint(timeMs, 0),
            (a, b) -> Long.compare(a.timeMs(), b.timeMs())
        );

        if (idx >= 0) {
            return seekTable.get(idx).fileOffset();
        }

        int insertionPoint = -idx - 1;
        return seekTable.get(insertionPoint - 1).fileOffset();
    }
}
