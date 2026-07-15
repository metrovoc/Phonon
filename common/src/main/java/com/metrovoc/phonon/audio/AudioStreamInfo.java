package com.metrovoc.phonon.audio;

import java.util.List;

public record AudioStreamInfo(
    byte[] headerBytes,
    List<OggPageScanner.SeekPoint> seekTable,
    int sampleRate,
    long durationMs
) {
    public AudioStreamInfo {
        seekTable = List.copyOf(seekTable);
    }

    public OggPageScanner.SeekPoint findSeekPoint(long timeMs) {
        if (seekTable.isEmpty()) {
            return new OggPageScanner.SeekPoint(0, headerBytes.length);
        }

        int low = 0;
        int high = seekTable.size() - 1;
        int match = -1;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            OggPageScanner.SeekPoint point = seekTable.get(middle);
            if (point.timeMs() <= timeMs) {
                match = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return match >= 0
            ? seekTable.get(match)
            : new OggPageScanner.SeekPoint(0, headerBytes.length);
    }
}
