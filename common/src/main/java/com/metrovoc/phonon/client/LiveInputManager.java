package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.AudioInputDevice;
import com.metrovoc.phonon.server.FFmpegHelper;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Manages live audio input capture and encoding.
 * Captures from system audio input (e.g., VB-Cable for Bluetooth line-in),
 * encodes via FFmpeg to OGG Vorbis, and sends chunks to broadcast callback.
 */
public class LiveInputManager {
    private static final LiveInputManager INSTANCE = new LiveInputManager();

    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 2;
    private static final int BUFFER_SIZE = 4096;
    private static final int CHUNK_SIZE = 8192;

    private final AtomicBoolean broadcasting = new AtomicBoolean(false);
    private volatile Thread captureThread;
    private volatile Thread encoderThread;
    private volatile Process ffmpegProcess;
    private volatile TargetDataLine targetDataLine;
    private volatile UUID currentResourceId;

    private BiConsumer<UUID, byte[]> chunkCallback;
    private BiConsumer<UUID, byte[]> headerCallback;

    public static LiveInputManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get all available audio input devices.
     */
    public static List<AudioInputDevice> getAvailableDevices() {
        List<AudioInputDevice> devices = new ArrayList<>();
        AudioFormat format = createCaptureFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    devices.add(new AudioInputDevice(
                        mixerInfo.getName(),
                        mixerInfo.getDescription(),
                        mixerInfo
                    ));
                }
            } catch (Exception e) {
                // Skip unavailable mixers
            }
        }

        return devices;
    }

    /**
     * Find device by name.
     */
    public static AudioInputDevice findDevice(String name) {
        if (name == null || name.isEmpty()) return null;

        for (AudioInputDevice device : getAvailableDevices()) {
            if (device.name().equals(name)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Set callback for header data (OGG header pages).
     */
    public void setHeaderCallback(BiConsumer<UUID, byte[]> callback) {
        this.headerCallback = callback;
    }

    /**
     * Set callback for chunk data.
     */
    public void setChunkCallback(BiConsumer<UUID, byte[]> callback) {
        this.chunkCallback = callback;
    }

    /**
     * Start broadcasting from the specified device.
     */
    public boolean startBroadcast(String deviceName, UUID resourceId) {
        if (broadcasting.get()) {
            Phonon.LOGGER.warn("Already broadcasting, stop first");
            return false;
        }

        if (!FFmpegHelper.isAvailable()) {
            Phonon.LOGGER.error("FFmpeg not available, cannot start live input");
            return false;
        }

        AudioInputDevice device = findDevice(deviceName);
        if (device == null) {
            Phonon.LOGGER.error("Audio input device not found: {}", deviceName);
            return false;
        }

        try {
            AudioFormat format = createCaptureFormat();
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);

            Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
            targetDataLine = (TargetDataLine) mixer.getLine(lineInfo);
            targetDataLine.open(format, BUFFER_SIZE * 4);
            targetDataLine.start();

            currentResourceId = resourceId;
            broadcasting.set(true);

            startFFmpegProcess();
            startCaptureThread();
            startEncoderThread();

            Phonon.LOGGER.info("Started live broadcast from device: {}", deviceName);
            return true;

        } catch (LineUnavailableException e) {
            Phonon.LOGGER.error("Failed to open audio input device: {}", e.getMessage());
            cleanup();
            return false;
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to start FFmpeg: {}", e.getMessage());
            cleanup();
            return false;
        }
    }

    /**
     * Stop broadcasting.
     */
    public void stopBroadcast() {
        if (!broadcasting.compareAndSet(true, false)) {
            return;
        }

        Phonon.LOGGER.info("Stopping live broadcast");
        cleanup();
    }

    public boolean isBroadcasting() {
        return broadcasting.get();
    }

    public UUID getCurrentResourceId() {
        return currentResourceId;
    }

    private static AudioFormat createCaptureFormat() {
        return new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            CHANNELS * (SAMPLE_SIZE_BITS / 8),
            SAMPLE_RATE,
            true  // big-endian for FFmpeg s16be
        );
    }

    private void startFFmpegProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-f", "s16be",
            "-ar", String.valueOf(SAMPLE_RATE),
            "-ac", String.valueOf(CHANNELS),
            "-i", "pipe:0",
            "-c:a", "libvorbis",
            "-q:a", "3",
            "-f", "ogg",
            "-fflags", "nobuffer",
            "pipe:1"
        );
        pb.redirectErrorStream(false);

        ffmpegProcess = pb.start();

        // Drain stderr to prevent blocking
        Thread stderrDrain = new Thread(() -> {
            try (InputStream stderr = ffmpegProcess.getErrorStream()) {
                byte[] buf = new byte[1024];
                while (stderr.read(buf) >= 0 && broadcasting.get()) {
                    // discard
                }
            } catch (IOException ignored) {}
        }, "Phonon-FFmpeg-Stderr");
        stderrDrain.setDaemon(true);
        stderrDrain.start();
    }

    private void startCaptureThread() {
        captureThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            OutputStream ffmpegIn = ffmpegProcess.getOutputStream();

            try {
                while (broadcasting.get() && targetDataLine.isOpen()) {
                    int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        ffmpegIn.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                if (broadcasting.get()) {
                    Phonon.LOGGER.error("Capture thread error: {}", e.getMessage());
                }
            } finally {
                try {
                    ffmpegIn.close();
                } catch (IOException ignored) {}
            }
        }, "Phonon-LiveInput-Capture");

        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void startEncoderThread() {
        encoderThread = new Thread(() -> {
            InputStream ffmpegOut = ffmpegProcess.getInputStream();
            byte[] buffer = new byte[CHUNK_SIZE];
            boolean headerSent = false;

            try {
                while (broadcasting.get()) {
                    int bytesRead = ffmpegOut.read(buffer);
                    if (bytesRead <= 0) {
                        if (!ffmpegProcess.isAlive()) break;
                        continue;
                    }

                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);

                    if (!headerSent) {
                        // First chunk contains OGG header
                        if (headerCallback != null) {
                            byte[] header = extractOggHeader(data);
                            if (header != null) {
                                headerCallback.accept(currentResourceId, header);
                                headerSent = true;
                            }
                        }
                    }

                    if (chunkCallback != null) {
                        chunkCallback.accept(currentResourceId, data);
                    }
                }
            } catch (IOException e) {
                if (broadcasting.get()) {
                    Phonon.LOGGER.error("Encoder thread error: {}", e.getMessage());
                }
            }
        }, "Phonon-LiveInput-Encoder");

        encoderThread.setDaemon(true);
        encoderThread.start();
    }

    /**
     * Extract OGG header pages (ID + Comment + Setup headers).
     * Returns at least the first page if available.
     */
    private byte[] extractOggHeader(byte[] data) {
        // OGG pages start with "OggS" magic
        if (data.length < 27) return null;
        if (data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') {
            return null;
        }

        // For simplicity, return the entire first chunk as header
        // The decoder will handle parsing
        return data;
    }

    private void cleanup() {
        if (targetDataLine != null) {
            try {
                targetDataLine.stop();
                targetDataLine.close();
            } catch (Exception ignored) {}
            targetDataLine = null;
        }

        if (ffmpegProcess != null) {
            try {
                ffmpegProcess.destroyForcibly();
                ffmpegProcess.waitFor();
            } catch (Exception ignored) {}
            ffmpegProcess = null;
        }

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        if (encoderThread != null) {
            encoderThread.interrupt();
            encoderThread = null;
        }

        currentResourceId = null;
    }
}
