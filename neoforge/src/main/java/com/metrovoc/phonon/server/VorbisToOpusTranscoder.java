package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.OpusEncoder;
import com.metrovoc.phonon.audio.Resampler;
import io.github.jaredmdobson.concentus.OpusException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Transcodes OGG Vorbis files to Opus packets.
 * Uses VorbisSPI (pure Java) for decoding - works on dedicated servers without LWJGL.
 */
public class VorbisToOpusTranscoder implements AutoCloseable {

    private final Path sourceFile;
    private final int targetSampleRate;
    private final int targetChannels;

    private AudioInputStream pcmStream;
    private int sourceSampleRate;
    private int sourceChannels;
    private long totalSamples;

    private OpusEncoder encoder;
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
     * VorbisSPI registers via Java SPI, so AudioSystem automatically handles OGG.
     */
    public boolean init() throws IOException {
        if (initialized) return true;

        try {
            AudioInputStream sourceStream = AudioSystem.getAudioInputStream(sourceFile.toFile());
            AudioFormat sourceFormat = sourceStream.getFormat();

            sourceSampleRate = (int) sourceFormat.getSampleRate();
            sourceChannels = sourceFormat.getChannels();

            // Estimate total samples from frame length (may be -1 if unknown)
            long frameLength = sourceStream.getFrameLength();
            totalSamples = frameLength > 0 ? frameLength : 0;

            // Convert to 16-bit signed PCM (little endian)
            AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceFormat.getChannels(),
                sourceFormat.getChannels() * 2,
                sourceFormat.getSampleRate(),
                false
            );
            pcmStream = AudioSystem.getAudioInputStream(decodedFormat, sourceStream);

            encoder = new OpusEncoder(targetChannels);
            encodeBuffer = new short[OpusEncoder.FRAME_SIZE * targetChannels];

            initialized = true;
            return true;

        } catch (UnsupportedAudioFileException e) {
            Phonon.LOGGER.error("Unsupported audio format: {}", sourceFile, e);
            return false;
        } catch (OpusException e) {
            Phonon.LOGGER.error("Failed to create Opus encoder", e);
            close();
            return false;
        }
    }

    /**
     * Transcode entire file to Opus packets.
     */
    public List<byte[]> transcodeAll() throws IOException, OpusException {
        if (!initialized && !init()) {
            throw new IOException("Failed to initialize transcoder");
        }

        // Read all PCM data
        byte[] pcmBytes = readAllBytes(pcmStream);

        // Convert bytes to shorts (little endian)
        short[] pcmSamples = bytesToShorts(pcmBytes);

        // Mix to mono if needed
        short[] monoSamples;
        if (sourceChannels > 1 && targetChannels == 1) {
            monoSamples = mixToMono(pcmSamples, sourceChannels);
        } else {
            monoSamples = pcmSamples;
        }

        // Resample to 48kHz if needed
        short[] resampledSamples;
        if (sourceSampleRate != targetSampleRate) {
            resampledSamples = Resampler.resample(monoSamples, sourceSampleRate, targetSampleRate);
        } else {
            resampledSamples = monoSamples;
        }

        // Update totalSamples based on actual data
        if (totalSamples == 0) {
            totalSamples = pcmSamples.length / sourceChannels;
        }

        // Encode to Opus frames
        List<byte[]> packets = new ArrayList<>();
        int offset = 0;

        while (offset < resampledSamples.length) {
            int remaining = resampledSamples.length - offset;
            int toCopy = Math.min(remaining, OpusEncoder.FRAME_SIZE);

            System.arraycopy(resampledSamples, offset, encodeBuffer, 0, toCopy);

            // Zero-pad last frame if needed
            for (int i = toCopy; i < OpusEncoder.FRAME_SIZE; i++) {
                encodeBuffer[i] = 0;
            }

            byte[] packet = encoder.encodeFrame(encodeBuffer);
            if (packet.length > 0) {
                packets.add(packet);
            }

            offset += OpusEncoder.FRAME_SIZE;
        }

        return packets;
    }

    private byte[] readAllBytes(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private short[] bytesToShorts(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        return shorts;
    }

    private short[] mixToMono(short[] interleaved, int channels) {
        int sampleCount = interleaved.length / channels;
        short[] mono = new short[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int sum = 0;
            for (int ch = 0; ch < channels; ch++) {
                sum += interleaved[i * channels + ch];
            }
            mono[i] = (short) (sum / channels);
        }

        return mono;
    }

    public long getDurationMs() {
        if (!initialized || totalSamples == 0) return 0;
        return totalSamples * 1000 / sourceSampleRate;
    }

    public int getSourceSampleRate() {
        return sourceSampleRate;
    }

    public int getSourceChannels() {
        return sourceChannels;
    }

    public int getTargetSampleRate() {
        return targetSampleRate;
    }

    @Override
    public void close() {
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }

        if (pcmStream != null) {
            try {
                pcmStream.close();
            } catch (IOException ignored) {}
            pcmStream = null;
        }

        initialized = false;
    }

    /**
     * Transcode a file directly.
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
