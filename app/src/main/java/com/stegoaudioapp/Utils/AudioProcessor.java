package com.stegoaudioapp.Utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class AudioProcessor {

    private static int SAMPLE_RATE = 44100;//AudioPlayer.RECORDER_SAMPLE_RATE; // 44100
    private static int MARKER_FREQ = 20_000;
    private static int DATA_FREQ = 19_000;

    private static int CYCLE_COUNT = 50;
    private static int MARKER_SAMPLE_COUNT = calculateSampleCount(MARKER_FREQ);
    private static int DATA_BIT_SAMPLE_COUNT = calculateSampleCount(DATA_FREQ);
    private static int MARGIN_SAMPLE_COUNT = MARKER_SAMPLE_COUNT*5;

    private static int FREQ_THRESHOLD = 100;
    private static int AMPLITUDE_THRESHOLD = 240;


    public static File encodeStringToFile(String message, File file, Context context) {
        byte[] messageBits = Convertor.stringToBits(message);
        short[] audioSamples = extractAudioSamples(file);
        int index = MARGIN_SAMPLE_COUNT;
        addMarker(audioSamples, index);
        index += MARKER_SAMPLE_COUNT;
        Log.d("pttt", "Start marker "+index);
        for (byte messageBit : messageBits) {
            if (messageBit == 1) {
                addDataBit(audioSamples, index);
            }
            index += DATA_BIT_SAMPLE_COUNT;
        }
        Log.d("pttt", "End marker "+index);
        addMarker(audioSamples, index);

        byte[] encodedBytes = shortsToBytes(audioSamples);
        Uri encodedFileUri = FileManager.createFile(context, "encoded_"+file.getName(), encodedBytes);
        return FileManager.getFileFromUri(context, encodedFileUri);
    }

    public static String decodeFileToMessage(File file) {
        short[] audioSamples = extractAudioSamples(file);
        int startIndex = (int) (findMarker(audioSamples, 0) + MARKER_SAMPLE_COUNT*1.2);
        Log.d("pttt", "startIndex "+startIndex);
        int endIndex = (int) (findMarker(audioSamples, startIndex+MARKER_SAMPLE_COUNT) + MARKER_SAMPLE_COUNT*0.3);
        Log.d("pttt", "End marker "+endIndex);
        if (endIndex < MARKER_SAMPLE_COUNT)
            return "Markers not found";

        int numOfBits = (endIndex-startIndex) / DATA_BIT_SAMPLE_COUNT;
        if (numOfBits%8 != 0) {
            int bitCount = numOfBits+(8-numOfBits%8);
            if (bitCount*DATA_BIT_SAMPLE_COUNT + startIndex < audioSamples.length)
                numOfBits = bitCount;
        }
        byte[] decodedBits = new byte[numOfBits];
        short[] bitWindow = new short[DATA_BIT_SAMPLE_COUNT];
        int bitIndex = 0;
        for (int i = startIndex; i+DATA_BIT_SAMPLE_COUNT < endIndex; i += DATA_BIT_SAMPLE_COUNT) {
            System.arraycopy(audioSamples, i, bitWindow, 0, DATA_BIT_SAMPLE_COUNT);
            boolean b = isFrequencyPresent(bitWindow, DATA_FREQ);
            if (b) {
                decodedBits[bitIndex] = 1;
            } else {
                decodedBits[bitIndex] = 0;
            }
            bitIndex++;
        }

        return Convertor.bitsToString(decodedBits);
    }
    private static short[] extractAudioSamples(File file) {
        short[] audioShorts = new short[(int) file.length() / 2];
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            for (int i = 0; i < audioShorts.length; i++) {
                audioShorts[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
            }
        } catch (IOException e) {
            Log.e("pttt", "Error reading audio file: " + e.getMessage());
            return new short[0];
        }
        return audioShorts;
    }

    private static void addMarker(short[] audioSamples, int startIndex) {
        short[] markerWaveSamples = generateSineWaveSamples(MARKER_FREQ, 25000, MARKER_SAMPLE_COUNT);
        for (int i = 0; i < MARKER_SAMPLE_COUNT; i++) {
            if (startIndex + i < audioSamples.length) {
                audioSamples[startIndex + i] = (short) ((audioSamples[startIndex + i] * markerWaveSamples[i]) / 32767);
            }
        }
    }
    private static int findMarker(short[] audioSamples, int startIndex) {
        for (int i = startIndex; i < audioSamples.length - MARKER_SAMPLE_COUNT; i++) {
            short[] window = new short[MARKER_SAMPLE_COUNT];
            System.arraycopy(audioSamples, i, window, 0, MARKER_SAMPLE_COUNT);
            if (isFrequencyPresent(window, MARKER_FREQ)) {
                return i;
            }
        }
        return -1; // Marker not found
    }

    private static void addDataBit(short[] audioSamples, int startIndex) {
        short[] carrierWave = generateSineWaveSamples(DATA_FREQ, 25000, DATA_BIT_SAMPLE_COUNT);
        for (int i = 0; i < DATA_BIT_SAMPLE_COUNT; i++) {
            if (startIndex + i < audioSamples.length) {
                audioSamples[startIndex + i] = (short) ((audioSamples[startIndex + i] * carrierWave[i]) / 32767);
            }
        }
    }

    private static short[] generateSineWaveSamples(int freq, int amplitude, int sampleCount) {
        short[] sineWaveSamples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            double time = (double) i / SAMPLE_RATE;
            double value = amplitude * Math.sin(2 * Math.PI * freq * time);
            sineWaveSamples[i] = (short) value;
        }
        return sineWaveSamples;
    }

    private static boolean isFrequencyPresent(short[] window, int targetFreq) {
        if (window == null || window.length == 0) {
            return false;
        }

        double omega = 2.0 * Math.PI * targetFreq / SAMPLE_RATE;
        double cosine = Math.cos(omega);

        double coeff = 2.0 * cosine;
        double q1 = 0.0;
        double q2 = 0.0;
        double q0;

        for (short sample : window) {
            double normalizedSample = sample / 32768.0; // Normalize short to -1.0 to 1.0
            q0 = coeff * q1 - q2 + normalizedSample;
            q2 = q1;
            q1 = q0;
        }

        double magnitudeSquared = q1 * q1 + q2 * q2 - q1 * q2 * coeff;

        // Check for presence of nearby frequencies
        double[] magnitudes = new double[3]; // Check targetFreq, targetFreq - FREQ_THRESHOLD, targetFreq + FREQ_THRESHOLD
        magnitudes[1] = magnitudeSquared;

        omega = 2.0 * Math.PI * (targetFreq - FREQ_THRESHOLD) / SAMPLE_RATE;
        cosine = Math.cos(omega);
        coeff = 2.0 * cosine;
        q1 = 0.0;
        q2 = 0.0;
        for(short sample : window){
            double normalizedSample = sample / 32768.0;
            q0 = coeff * q1 - q2 + normalizedSample;
            q2 = q1;
            q1 = q0;
        }
        magnitudes[0] = q1*q1 + q2*q2 - q1*q2*coeff;

        omega = 2.0 * Math.PI * (targetFreq + FREQ_THRESHOLD) / SAMPLE_RATE;
        cosine = Math.cos(omega);
        coeff = 2.0 * cosine;
        q1 = 0.0;
        q2 = 0.0;
        for(short sample : window){
            double normalizedSample = sample / 32768.0;
            q0 = coeff * q1 - q2 + normalizedSample;
            q2 = q1;
            q1 = q0;
        }
        magnitudes[2] = q1*q1 + q2*q2 - q1*q2*coeff;

        for (double magnitude : magnitudes) {
            if (magnitude > AMPLITUDE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private static int calculateSampleCount(int freq) {
        Log.d("pttt", "freq--"+Math.round(SAMPLE_RATE * (CYCLE_COUNT / (float) freq)));
        return Math.round(SAMPLE_RATE * (CYCLE_COUNT / (float) freq));
    }

    private static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    public static double calculateMinimumDuration(String message) {
        byte[] messageBits = Convertor.stringToBits(message);
        int messageSampleCount = messageBits.length * DATA_BIT_SAMPLE_COUNT;
        int totalSampleCount = MARKER_SAMPLE_COUNT * 2 + messageSampleCount;
        double time = (double) Math.round(10 * ((double) totalSampleCount / SAMPLE_RATE)) / 10;
        return time + 1; // will add 2 half-second margins
    }
}
