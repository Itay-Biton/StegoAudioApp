package com.stegoaudioapp.Utils;

import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class utilProccessor {
    private static final int SAMPLE_RATE = 44100;
    private static final int START_FREQ = 18000; // 18kHz start marker
    private static final int DATA_FREQ = 1000;  // 19kHz for '1'
    private static final int END_FREQ = 18000;   // 18kHz end marker

    public static void encodeMessage(File audioFile, String message) {
        String binaryMessage = textToBinary(message);

        try {
            String outputPath = audioFile.getParent() + "/encoded_" + audioFile.getName();

            // Generate FFmpeg command
            String command = "ffmpeg -i " + audioFile.getAbsolutePath() +
                    " -filter_complex \"[0:a]asplit=2[a][b];" +
                    " [a]sine=frequency=" + START_FREQ + ":duration=0.5[start]; " +
                    generateBinaryTone(binaryMessage) +
                    " [b]sine=frequency=" + END_FREQ + ":duration=0.5[end]; " +
                    " [start][a][end]amix=inputs=3:duration=longest\" " + outputPath;

            System.out.println("Executing command: " + command); // Debugging line

            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            Log.d("AudioProcessor", "Encoding complete. Saved at: " + outputPath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String decodeMessage(File audioFile) {
        try {
            byte[] audioData = Files.readAllBytes(Paths.get(audioFile.getAbsolutePath()));
            double[] samples = convertToDoubleArray(audioData);
            Log.d("AudioProcessor", Arrays.toString(samples));

            DoubleFFT_1D fft = new DoubleFFT_1D(samples.length);
            fft.realForward(samples);

            List<Integer> detectedBits = extractBits(samples);
            Log.d("AudioProcessor", detectedBits.toString());
            return binaryToText(detectedBits);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Decoding failed";
    }

    private static String textToBinary(String text) {
        StringBuilder binary = new StringBuilder();
        for (char c : text.toCharArray()) {
            binary.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }
        return binary.toString();
    }

    private static String generateBinaryTone(String binaryMessage) {
        StringBuilder toneFilters = new StringBuilder();
        double durationPerBit = 0.2; // Adjust as needed
        int bitIndex = 0;
        double amplitude = 1.0; // Adjust between 0.0 (silent) and 1.0 (full volume)

        for (char bit : binaryMessage.toCharArray()) {
            int freq = (bit == '1') ? DATA_FREQ : 0; // 1s and 0s represented by different frequencies
            toneFilters.append(String.format(
                    "sine=frequency=%d:duration=%.2f:amplitude=%.2f[bit%d]; ",
                    freq, durationPerBit, amplitude, bitIndex
            ));
            bitIndex++;
        }

        // Merge all tones sequentially
        for (int i = 0; i < bitIndex; i++) {
            if (i == 0) {
                toneFilters.append(String.format("[bit%d]", i));
            } else {
                toneFilters.append(String.format("[bit%d]", i));
            }
        }
        toneFilters.append("concat=n=" + bitIndex + ":v=0:a=1[encoded]; ");

        return toneFilters.toString();
    }

    private static double[] convertToDoubleArray(byte[] audioData) {
        ByteBuffer buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN);
        double[] samples = new double[audioData.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort() / 32768.0;
        }
        return samples;
    }

    private static List<Integer> extractBits(double[] samples) {
        List<Integer> bits = new ArrayList<>();
        for (double sample : samples) {
            if (Math.abs(sample - START_FREQ) < 100) {
                bits.clear(); // Start marker detected, reset
                Log.d("AudioProcessor", "Start Signal");
            } else if (Math.abs(sample - END_FREQ) < 100) {
                Log.d("AudioProcessor", "Stop Signal");
                break; // Stop marker detected
            } else if (Math.abs(sample - DATA_FREQ) < 100) {
                Log.d("AudioProcessor", "BIT");
                bits.add(1);
            } else {
                bits.add(0);
            }
        }
        return bits;
    }

    private static String binaryToText(List<Integer> binaryList) {
        StringBuilder binaryString = new StringBuilder();
        for (int bit : binaryList) {
            binaryString.append(bit);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i+8 < binaryString.length(); i += 8) {
            int charCode = Integer.parseInt(binaryString.substring(i, i + 8), 2);
            text.append((char) charCode);
        }
        return text.toString();
    }
}

