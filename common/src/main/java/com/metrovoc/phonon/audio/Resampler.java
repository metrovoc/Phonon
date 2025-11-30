package com.metrovoc.phonon.audio;

/**
 * Simple audio resampler using linear interpolation.
 * Good enough for MVP; can be replaced with better algorithm later.
 */
public final class Resampler {

    private Resampler() {}

    /**
     * Resample audio from source rate to target rate.
     *
     * @param input      input samples
     * @param inputRate  input sample rate
     * @param targetRate target sample rate
     * @return resampled samples
     */
    public static short[] resample(short[] input, int inputRate, int targetRate) {
        if (inputRate == targetRate) {
            return input.clone();
        }

        double ratio = (double) inputRate / targetRate;
        int outputLen = (int) Math.ceil(input.length / ratio);
        short[] output = new short[outputLen];

        for (int i = 0; i < outputLen; i++) {
            double srcPos = i * ratio;
            int srcIndex = (int) srcPos;
            double frac = srcPos - srcIndex;

            if (srcIndex >= input.length - 1) {
                output[i] = input[input.length - 1];
            } else {
                // Linear interpolation
                double sample = input[srcIndex] * (1 - frac) + input[srcIndex + 1] * frac;
                output[i] = clampToShort(sample);
            }
        }

        return output;
    }

    /**
     * Resample and write to output buffer.
     *
     * @param input       input samples
     * @param inputOffset offset into input
     * @param inputLen    number of input samples
     * @param inputRate   input sample rate
     * @param output      output buffer
     * @param outputOffset offset into output
     * @param targetRate  target sample rate
     * @return number of samples written to output
     */
    public static int resample(short[] input, int inputOffset, int inputLen,
                               int inputRate, short[] output, int outputOffset, int targetRate) {
        if (inputRate == targetRate) {
            System.arraycopy(input, inputOffset, output, outputOffset, inputLen);
            return inputLen;
        }

        double ratio = (double) inputRate / targetRate;
        int outputLen = (int) Math.ceil(inputLen / ratio);

        for (int i = 0; i < outputLen; i++) {
            double srcPos = i * ratio;
            int srcIndex = inputOffset + (int) srcPos;
            double frac = srcPos - (int) srcPos;

            if (srcIndex >= inputOffset + inputLen - 1) {
                output[outputOffset + i] = input[inputOffset + inputLen - 1];
            } else {
                double sample = input[srcIndex] * (1 - frac) + input[srcIndex + 1] * frac;
                output[outputOffset + i] = clampToShort(sample);
            }
        }

        return outputLen;
    }

    /**
     * Calculate output size for given input.
     */
    public static int calculateOutputSize(int inputLen, int inputRate, int targetRate) {
        if (inputRate == targetRate) {
            return inputLen;
        }
        double ratio = (double) inputRate / targetRate;
        return (int) Math.ceil(inputLen / ratio);
    }

    /**
     * Convert stereo to mono by averaging channels.
     *
     * @param stereo interleaved stereo samples (L,R,L,R,...)
     * @return mono samples
     */
    public static short[] stereoToMono(short[] stereo) {
        int monoLen = stereo.length / 2;
        short[] mono = new short[monoLen];

        for (int i = 0; i < monoLen; i++) {
            int left = stereo[i * 2];
            int right = stereo[i * 2 + 1];
            mono[i] = (short) ((left + right) / 2);
        }

        return mono;
    }

    /**
     * Convert mono to stereo by duplicating samples.
     *
     * @param mono mono samples
     * @return interleaved stereo samples
     */
    public static short[] monoToStereo(short[] mono) {
        short[] stereo = new short[mono.length * 2];

        for (int i = 0; i < mono.length; i++) {
            stereo[i * 2] = mono[i];
            stereo[i * 2 + 1] = mono[i];
        }

        return stereo;
    }

    /**
     * Convert float samples to short samples.
     */
    public static short[] floatToShort(float[] input) {
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = clampToShort(input[i] * 32767.0);
        }
        return output;
    }

    /**
     * Convert short samples to float samples.
     */
    public static float[] shortToFloat(short[] input) {
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i] / 32768.0f;
        }
        return output;
    }

    private static short clampToShort(double value) {
        int val = (int) Math.round(value);
        return (short) Math.max(-32768, Math.min(32767, val));
    }
}
