package com.metrovoc.phonon.audio;

import com.metrovoc.phonon.Phonon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a lightweight seek index without loading the complete OGG file.
 */
public final class OggPageScanner {
    private static final int FIXED_HEADER_SIZE = 27;
    private static final int MAX_SEGMENTS = 255;
    private static final int IDENTIFICATION_PACKET_SIZE = 16;
    private static final int VORBIS_HEADER_PACKETS = 3;
    private static final int MAGIC = 0x5367674F; // "OggS" in little-endian

    private OggPageScanner() {}

    /**
     * {@code timeMs} is the approximate start time of the page, not its end
     * granule. This makes the value suitable for decoder restart and PCM skip.
     */
    public record SeekPoint(long timeMs, long fileOffset) {}

    public record OggScanResult(
        byte[] headerBytes,
        List<SeekPoint> seekTable,
        int sampleRate,
        long durationMs
    ) {}

    public static OggScanResult scan(Path oggFile) {
        try (FileChannel channel = FileChannel.open(oggFile, StandardOpenOption.READ)) {
            return scanChannel(channel);
        } catch (Exception e) {
            Phonon.LOGGER.warn("Failed to scan OGG file {}: {}", oggFile, e.getMessage());
            return null;
        }
    }

    private static OggScanResult scanChannel(FileChannel channel) throws IOException {
        long fileSize = channel.size();
        long pageOffset = 0;
        long headerEndOffset = 0;
        long previousGranuleTimeMs = 0;
        long durationMs = 0;
        int sampleRate = 0;
        int streamSerial = 0;
        int headerPacketCount = 0;
        boolean firstPage = true;
        boolean headerComplete = false;

        List<SeekPoint> seekTable = new ArrayList<>();
        ByteBuffer fixedHeader = ByteBuffer.allocate(FIXED_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer segmentTable = ByteBuffer.allocate(MAX_SEGMENTS);

        while (pageOffset + FIXED_HEADER_SIZE <= fileSize) {
            fixedHeader.clear();
            if (!readFully(channel, fixedHeader, pageOffset)) {
                return null;
            }
            fixedHeader.flip();

            if (fixedHeader.getInt() != MAGIC || fixedHeader.get() != 0) {
                return null;
            }

            int headerType = fixedHeader.get() & 0xFF;
            long granulePosition = fixedHeader.getLong();
            int pageSerial = fixedHeader.getInt();
            fixedHeader.getInt(); // page sequence number
            fixedHeader.getInt(); // checksum
            fixedHeader.position(26);
            int pageSegments = fixedHeader.get() & 0xFF;

            if (firstPage) {
                if ((headerType & 0x02) == 0) {
                    return null;
                }
                streamSerial = pageSerial;
                firstPage = false;
            } else if (pageSerial != streamSerial) {
                // Multiplexed or chained streams need separate decoder state.
                return null;
            }

            segmentTable.clear();
            segmentTable.limit(pageSegments);
            if (!readFully(channel, segmentTable, pageOffset + FIXED_HEADER_SIZE)) {
                return null;
            }
            segmentTable.flip();

            int bodySize = 0;
            while (segmentTable.hasRemaining()) {
                int segmentSize = segmentTable.get() & 0xFF;
                bodySize += segmentSize;
                if (!headerComplete && segmentSize < 255) {
                    headerPacketCount++;
                }
            }

            long bodyOffset = pageOffset + FIXED_HEADER_SIZE + pageSegments;
            long pageEndOffset = bodyOffset + bodySize;
            if (pageEndOffset > fileSize) {
                return null;
            }

            if (sampleRate == 0 && pageOffset == 0 && bodySize >= IDENTIFICATION_PACKET_SIZE) {
                int probeSize = Math.min(bodySize, 64);
                ByteBuffer bodyPrefix = ByteBuffer.allocate(probeSize).order(ByteOrder.LITTLE_ENDIAN);
                if (!readFully(channel, bodyPrefix, bodyOffset)) {
                    return null;
                }
                bodyPrefix.flip();
                sampleRate = extractSampleRate(bodyPrefix);
            }

            boolean completedHeaderOnPage = !headerComplete
                && headerPacketCount >= VORBIS_HEADER_PACKETS;
            if (completedHeaderOnPage) {
                headerEndOffset = pageEndOffset;
                headerComplete = true;
                if (headerEndOffset > AudioLimits.MAX_HEADER_BYTES) {
                    return null;
                }
            }

            if (granulePosition >= 0) {
                if (sampleRate <= 0) {
                    return null;
                }
                if (headerComplete && !completedHeaderOnPage && (headerType & 0x01) == 0) {
                    seekTable.add(new SeekPoint(previousGranuleTimeMs, pageOffset));
                }
                durationMs = granuleToTimeMs(granulePosition, sampleRate);
                previousGranuleTimeMs = durationMs;
            }

            pageOffset = pageEndOffset;
        }

        if (sampleRate <= 0 || !headerComplete || headerEndOffset <= 0
            || headerEndOffset > AudioLimits.MAX_HEADER_BYTES || pageOffset != fileSize) {
            return null;
        }

        ByteBuffer header = ByteBuffer.allocate(Math.toIntExact(headerEndOffset));
        if (!readFully(channel, header, 0)) {
            return null;
        }

        return new OggScanResult(header.array(), List.copyOf(seekTable), sampleRate, durationMs);
    }

    private static long granuleToTimeMs(long granulePosition, int sampleRate) {
        if (granulePosition > Long.MAX_VALUE / 1000L) {
            return Long.MAX_VALUE;
        }
        return granulePosition * 1000L / sampleRate;
    }

    private static boolean readFully(FileChannel channel, ByteBuffer target, long offset) throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, offset + target.position());
            if (read < 0) {
                return false;
            }
            if (read == 0) {
                Thread.onSpinWait();
            }
        }
        return true;
    }

    private static int extractSampleRate(ByteBuffer body) {
        int start = body.position();
        if (body.remaining() < IDENTIFICATION_PACKET_SIZE || (body.get() & 0xFF) != 0x01) {
            return 0;
        }

        if (body.get() != 'v' || body.get() != 'o' || body.get() != 'r'
            || body.get() != 'b' || body.get() != 'i' || body.get() != 's') {
            body.position(start);
            return 0;
        }

        body.getInt(); // Vorbis version
        body.get();    // channels
        int sampleRate = body.getInt();
        body.position(start);
        return sampleRate;
    }
}
