package com.metrovoc.phonon.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OggPageScanner {

    private static final int MAGIC = 0x5367674F; // "OggS" in little-endian

    public record SeekPoint(long timeMs, int fileOffset) {}

    public record OggScanResult(byte[] headerBytes, List<SeekPoint> seekTable, int sampleRate) {}

    public static OggScanResult scan(Path oggFile) {
        try {
            byte[] data = Files.readAllBytes(oggFile);
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            return scanBuffer(buf);
        } catch (Exception e) {
            return null;
        }
    }

    private static OggScanResult scanBuffer(ByteBuffer buf) {
        int sampleRate = 0;
        int headerEndOffset = 0;
        List<SeekPoint> seekTable = new ArrayList<>();

        while (buf.remaining() >= 27) {
            int pageStart = buf.position();

            if (buf.getInt() != MAGIC) {
                buf.position(pageStart + 1);
                continue;
            }

            buf.get(); // version
            buf.get(); // header type
            long granulePos = buf.getLong();
            buf.getInt(); // serial
            buf.getInt(); // sequence
            buf.getInt(); // checksum
            int pageSegments = buf.get() & 0xFF;

            if (buf.remaining() < pageSegments) return null;

            int bodySize = 0;
            for (int i = 0; i < pageSegments; i++) {
                bodySize += buf.get() & 0xFF;
            }

            if (buf.remaining() < bodySize) return null;

            if (sampleRate == 0) {
                sampleRate = extractSampleRate(buf, bodySize);
            }

            buf.position(buf.position() + bodySize);

            if (granulePos <= 0) {
                headerEndOffset = buf.position();
            } else {
                long timeMs = granulePos * 1000 / sampleRate;
                seekTable.add(new SeekPoint(timeMs, pageStart));
            }
        }

        if (sampleRate == 0 || headerEndOffset == 0) return null;

        byte[] headerBytes = Arrays.copyOf(buf.array(), headerEndOffset);
        return new OggScanResult(headerBytes, seekTable, sampleRate);
    }

    private static int extractSampleRate(ByteBuffer buf, int bodySize) {
        if (bodySize < 16) return 0;

        int pos = buf.position();
        int packetType = buf.get() & 0xFF;

        if (packetType != 0x01) {
            buf.position(pos);
            return 0;
        }

        byte[] vorbis = new byte[6];
        buf.get(vorbis);

        if (vorbis[0] != 'v' || vorbis[1] != 'o' || vorbis[2] != 'r' ||
            vorbis[3] != 'b' || vorbis[4] != 'i' || vorbis[5] != 's') {
            buf.position(pos);
            return 0;
        }

        buf.getInt(); // version
        buf.get();    // channels
        int sampleRate = buf.getInt();

        buf.position(pos);
        return sampleRate;
    }
}
