package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.OpusEncoder;
import com.metrovoc.phonon.audio.Resampler;
import io.github.jaredmdobson.concentus.OpusException;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Transcodes OGG Vorbis files to Opus packets.
 * Used on server to convert existing .ogg files for Opus streaming.
 */
public class VorbisToOpusTranscoder implements AutoCloseable {

    private final Path sourceFile;
    private final int targetSampleRate;
    private final int targetChannels;

    private long vorbisHandle;
    private ByteBuffer fileBuffer;
    private int sourceSampleRate;
    private int sourceChannels;
    private int totalSamples;

    private OpusEncoder encoder;
    private short[] resampleBuffer;
    private short[] encodeBuffer;

    private boolean initialized = false;

    public VorbisToOpusTranscoder(Path sourceFile) {
        this(sourceFile, OpusEncoder.SAMPLE_RATE, 1);
    }

    public VorbisToOpusTranscoder(Path sourceFile, int targetSampleRate, int targetChannels) {
        this.sourceFile = sourceFile;
        this.targetSampleRate = targetSampleRate;
        this.targetChannels = targetChannels;
    }

    /**
     * Initialize transcoder by opening the Vorbis file.
     *
     * @return true if successful
     */
    public boolean init() throws IOException {
        if (initialized) return true;

        byte[] fileData = Files.readAllBytes(sourceFile);
        fileBuffer = MemoryUtil.memAlloc(fileData.length);
        fileBuffer.put(fileData);
        fileBuffer.flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            vorbisHandle = STBVorbis.stb_vorbis_open_memory(fileBuffer, error, null);

            if (vorbisHandle == 0) {
                Phonon.LOGGER.error("Failed to open Vorbis file: error {}", error.get(0));
                MemoryUtil.memFree(fileBuffer);
                fileBuffer = null;
                return false;
            }

            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(vorbisHandle, info);

            sourceSampleRate = info.sample_rate();
            sourceChannels = info.channels();
            totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(vorbisHandle);

            encoder = new OpusEncoder(targetChannels);

            // Buffer for one Opus frame worth of source samples
            int sourceSamplesPerFrame = (int) Math.ceil(
                (double) OpusEncoder.FRAME_SIZE * sourceSampleRate / targetSampleRate
            ) + 1;
            resampleBuffer = new short[sourceSamplesPerFrame * sourceChannels];
            encodeBuffer = new short[OpusEncoder.FRAME_SIZE * targetChannels];

            initialized = true;
            return true;

        } catch (OpusException e) {
            Phonon.LOGGER.error("Failed to create Opus encoder", e);
            if (vorbisHandle != 0) {
                STBVorbis.stb_vorbis_close(vorbisHandle);
                vorbisHandle = 0;
            }
            if (fileBuffer != null) {
                MemoryUtil.memFree(fileBuffer);
                fileBuffer = null;
            }
            return false;
        }
    }

    /**
     * Transcode entire file to Opus packets.
     *
     * @return list of Opus packets
     */
    public List<byte[]> transcodeAll() throws IOException, OpusException {
        if (!initialized && !init()) {
            throw new IOException("Failed to initialize transcoder");
        }

        List<byte[]> packets = new ArrayList<>();

        // Calculate samples per source frame to produce one Opus frame after resampling
        int sourceSamplesNeeded = (int) Math.ceil(
            (double) OpusEncoder.FRAME_SIZE * sourceSampleRate / targetSampleRate
        );

        short[] readBuffer = new short[sourceSamplesNeeded * sourceChannels];
        ShortBuffer nativeBuffer = MemoryUtil.memAllocShort(sourceSamplesNeeded * sourceChannels);

        try {
            while (true) {
                int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                    vorbisHandle, sourceChannels, nativeBuffer
                );

                if (samplesRead <= 0) break;

                nativeBuffer.position(0);
                nativeBuffer.get(readBuffer, 0, samplesRead * sourceChannels);
                nativeBuffer.position(0);

                // Convert to mono if needed
                short[] monoSamples;
                if (sourceChannels == 1) {
                    monoSamples = new short[samplesRead];
                    System.arraycopy(readBuffer, 0, monoSamples, 0, samplesRead);
                } else {
                    monoSamples = new short[samplesRead];
                    for (int i = 0; i < samplesRead; i++) {
                        int left = readBuffer[i * sourceChannels];
                        int right = readBuffer[i * sourceChannels + 1];
                        monoSamples[i] = (short) ((left + right) / 2);
                    }
                }

                // Resample if needed
                short[] resampledSamples;
                if (sourceSampleRate != targetSampleRate) {
                    resampledSamples = Resampler.resample(monoSamples, sourceSampleRate, targetSampleRate);
                } else {
                    resampledSamples = monoSamples;
                }

                // Encode in frame-sized chunks
                int offset = 0;
                while (offset + OpusEncoder.FRAME_SIZE <= resampledSamples.length) {
                    System.arraycopy(resampledSamples, offset, encodeBuffer, 0, OpusEncoder.FRAME_SIZE);
                    byte[] packet = encoder.encodeFrame(encodeBuffer);
                    if (packet.length > 0) {
                        packets.add(packet);
                    }
                    offset += OpusEncoder.FRAME_SIZE;
                }

                // Handle remaining samples (pad with zeros for last frame)
                if (offset < resampledSamples.length) {
                    int remaining = resampledSamples.length - offset;
                    System.arraycopy(resampledSamples, offset, encodeBuffer, 0, remaining);
                    // Zero-pad
                    for (int i = remaining; i < OpusEncoder.FRAME_SIZE; i++) {
                        encodeBuffer[i] = 0;
                    }
                    byte[] packet = encoder.encodeFrame(encodeBuffer);
                    if (packet.length > 0) {
                        packets.add(packet);
                    }
                }
            }
        } finally {
            MemoryUtil.memFree(nativeBuffer);
        }

        return packets;
    }

    /**
     * Get duration in milliseconds.
     */
    public long getDurationMs() {
        if (!initialized) return 0;
        return (long) totalSamples * 1000 / sourceSampleRate;
    }

    /**
     * Get source sample rate.
     */
    public int getSourceSampleRate() {
        return sourceSampleRate;
    }

    /**
     * Get source channel count.
     */
    public int getSourceChannels() {
        return sourceChannels;
    }

    /**
     * Get target sample rate (always 48000 for Opus).
     */
    public int getTargetSampleRate() {
        return targetSampleRate;
    }

    @Override
    public void close() {
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }

        if (vorbisHandle != 0) {
            STBVorbis.stb_vorbis_close(vorbisHandle);
            vorbisHandle = 0;
        }

        if (fileBuffer != null) {
            MemoryUtil.memFree(fileBuffer);
            fileBuffer = null;
        }

        initialized = false;
    }

    /**
     * Transcode a file directly.
     *
     * @return transcoded packets, or null on failure
     */
    public static TranscodeResult transcode(Path sourceFile) {
        try (VorbisToOpusTranscoder transcoder = new VorbisToOpusTranscoder(sourceFile)) {
            if (!transcoder.init()) {
                return null;
            }

            List<byte[]> packets = transcoder.transcodeAll();
            return new TranscodeResult(
                packets,
                transcoder.getDurationMs(),
                transcoder.getSourceSampleRate(),
                transcoder.getSourceChannels()
            );

        } catch (Exception e) {
            Phonon.LOGGER.error("Transcoding failed for {}", sourceFile, e);
            return null;
        }
    }

    public record TranscodeResult(
        List<byte[]> packets,
        long durationMs,
        int sourceSampleRate,
        int sourceChannels
    ) {}
}
