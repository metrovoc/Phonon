package com.metrovoc.phonon.client.audio;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Reference-counted compressed OGG bytes shared by independent decoders.
 * STB Vorbis keeps the input memory alive but does not mutate it.
 */
final class SharedOggFile {
    private static final Map<Path, SharedOggFile> LOADED_FILES = new HashMap<>();

    private final Path path;
    private final ByteBuffer data;
    private int references;

    private SharedOggFile(Path path, ByteBuffer data) {
        this.path = path;
        this.data = data;
        this.references = 1;
    }

    static synchronized SharedOggFile acquire(Path source) throws IOException {
        Path path = source.toAbsolutePath().normalize();
        SharedOggFile existing = LOADED_FILES.get(path);
        if (existing != null) {
            existing.references++;
            return existing;
        }

        ByteBuffer data = readFile(path);
        SharedOggFile loaded = new SharedOggFile(path, data);
        LOADED_FILES.put(path, loaded);
        return loaded;
    }

    private static ByteBuffer readFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize <= 0 || fileSize > Integer.MAX_VALUE) {
                throw new IOException("OGG file has an unsupported size: " + fileSize);
            }

            ByteBuffer buffer = MemoryUtil.memAlloc(Math.toIntExact(fileSize));
            try {
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        throw new IOException("Unexpected end of OGG file");
                    }
                    if (read == 0) {
                        Thread.onSpinWait();
                    }
                }
                buffer.flip();
                return buffer;
            } catch (IOException | RuntimeException | Error failure) {
                MemoryUtil.memFree(buffer);
                throw failure;
            }
        }
    }

    ByteBuffer decoderView() {
        ByteBuffer view = data.duplicate();
        view.position(0);
        return view;
    }

    void release() {
        boolean free;
        synchronized (SharedOggFile.class) {
            if (references <= 0) {
                return;
            }
            references--;
            free = references == 0 && LOADED_FILES.remove(path, this);
        }
        if (free) {
            MemoryUtil.memFree(data);
        }
    }
}
