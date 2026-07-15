package com.metrovoc.phonon.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OggPageScannerTest {
    private static final int SERIAL = 0x12345678;

    @TempDir
    Path temporaryDirectory;

    @Test
    void scansHeadersDurationAndOnlySafeSeekPages() throws Exception {
        byte[] identification = identificationPacket(48_000);
        byte[] firstPage = page(0x02, 0, 0, new int[]{identification.length}, identification);
        byte[] secondPage = page(0, 0, 1, new int[]{7, 8}, new byte[15]);
        byte[] firstAudioPage = page(0, 48_000, 2, new int[]{4}, new byte[4]);
        byte[] unfinishedPage = page(0, -1, 3, new int[]{255}, new byte[255]);
        byte[] continuedPage = page(0x01, 96_000, 4, new int[]{4}, new byte[4]);
        byte[] finalPage = page(0x04, 144_000, 5, new int[]{4}, new byte[4]);

        byte[] fileBytes = concatenate(
            firstPage,
            secondPage,
            firstAudioPage,
            unfinishedPage,
            continuedPage,
            finalPage
        );
        Path file = temporaryDirectory.resolve("audio.ogg");
        Files.write(file, fileBytes);

        OggPageScanner.OggScanResult result = OggPageScanner.scan(file);

        assertEquals(48_000, result.sampleRate());
        assertEquals(3_000, result.durationMs());
        assertArrayEquals(concatenate(firstPage, secondPage), result.headerBytes());
        assertEquals(2, result.seekTable().size());
        assertEquals(new OggPageScanner.SeekPoint(0, firstPage.length + secondPage.length),
            result.seekTable().get(0));
        assertEquals(new OggPageScanner.SeekPoint(
            2_000,
            firstPage.length + secondPage.length + firstAudioPage.length
                + unfinishedPage.length + continuedPage.length
        ), result.seekTable().get(1));
    }

    @Test
    void rejectsAFileWithoutAllVorbisHeaders() throws Exception {
        byte[] identification = identificationPacket(44_100);
        Path file = temporaryDirectory.resolve("incomplete.ogg");
        Files.write(file, page(0x02, 0, 0, new int[]{identification.length}, identification));

        assertNull(OggPageScanner.scan(file));
    }

    private static byte[] identificationPacket(int sampleRate) {
        ByteBuffer packet = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        packet.put((byte) 0x01);
        packet.put("vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        packet.putInt(0);
        packet.put((byte) 2);
        packet.putInt(sampleRate);
        packet.putInt(0);
        packet.putInt(0);
        packet.putInt(0);
        packet.put((byte) 0xB8);
        packet.put((byte) 0x01);
        return packet.array();
    }

    private static byte[] page(
        int headerType,
        long granule,
        int sequence,
        int[] segmentSizes,
        byte[] body
    ) {
        int expectedBodySize = java.util.Arrays.stream(segmentSizes).sum();
        assertEquals(expectedBodySize, body.length);

        ByteBuffer page = ByteBuffer.allocate(27 + segmentSizes.length + body.length)
            .order(ByteOrder.LITTLE_ENDIAN);
        page.put((byte) 'O').put((byte) 'g').put((byte) 'g').put((byte) 'S');
        page.put((byte) 0);
        page.put((byte) headerType);
        page.putLong(granule);
        page.putInt(SERIAL);
        page.putInt(sequence);
        page.putInt(0);
        page.put((byte) segmentSizes.length);
        for (int segmentSize : segmentSizes) {
            page.put((byte) segmentSize);
        }
        page.put(body);
        return page.array();
    }

    private static byte[] concatenate(byte[]... parts) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.write(part);
        }
        return output.toByteArray();
    }
}
