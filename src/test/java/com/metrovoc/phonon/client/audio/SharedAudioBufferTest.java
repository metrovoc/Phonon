package com.metrovoc.phonon.client.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedAudioBufferTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsAcrossPacketBoundariesWithoutReassembly() {
        SharedAudioBuffer buffer = new SharedAudioBuffer();
        buffer.append(new byte[]{0, 1, 2});
        buffer.append(new byte[]{3, 4});
        buffer.append(new byte[]{5, 6, 7, 8});

        byte[] destination = new byte[6];
        assertEquals(6, buffer.read(2, destination, 0, destination.length));
        assertArrayEquals(new byte[]{2, 3, 4, 5, 6, 7}, destination);
        assertThrows(IndexOutOfBoundsException.class,
            () -> buffer.read(0, new byte[4], 2, Integer.MAX_VALUE));
    }

    @Test
    void wakesAWaitingReaderWhenAChunkArrives() throws Exception {
        SharedAudioBuffer buffer = new SharedAudioBuffer();
        CountDownLatch ready = new CountDownLatch(1);
        CompletableFuture<Boolean> waiter = CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                return buffer.awaitDataAfter(0, 2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });

        assertTrue(ready.await(1, TimeUnit.SECONDS));
        buffer.append(new byte[]{1});
        assertTrue(waiter.get(1, TimeUnit.SECONDS));
    }

    @Test
    void writesACompletedSnapshotSegmentBySegment() throws Exception {
        SharedAudioBuffer buffer = new SharedAudioBuffer();
        buffer.append(new byte[]{1, 2});
        buffer.append(new byte[]{3, 4, 5});
        Path output = temporaryDirectory.resolve("cache.ogg");

        assertThrows(IllegalStateException.class, () -> buffer.writeTo(output));
        assertTrue(buffer.markComplete());
        assertFalse(buffer.markComplete());
        buffer.writeTo(output);

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, Files.readAllBytes(output));
    }
}
