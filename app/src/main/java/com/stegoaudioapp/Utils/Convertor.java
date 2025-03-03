package com.stegoaudioapp.Utils;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Convertor {
    public static byte[] stringToBits(String input) {
        if (input == null || input.isEmpty())
            return new byte[0]; // Return an empty byte array for null or empty input

        byte[] byteArray = input.getBytes(StandardCharsets.UTF_8); // Get byte array from string using UTF-8

        int totalBits = byteArray.length * 8;
        byte[] bits = new byte[totalBits];

        for (int i = 0; i < byteArray.length; i++) {
            byte currentByte = byteArray[i];
            for (int j = 0; j < 8; j++) {
                int bit = (currentByte >> (7 - j)) & 1;
                bits[i * 8 + j] = (byte) bit;
            }
        }

        return bits;
    }
    public static String bitsToString(byte[] bits) {
        if (bits == null || bits.length == 0)
            return "";

        if (bits.length % 8 != 0)
            throw new IllegalArgumentException("Bit array length must be a multiple of 8.");

        int byteCount = bits.length / 8;
        byte[] byteArray = new byte[byteCount];

        for (int i = 0; i < byteCount; i++) {
            byte currentByte = 0;
            for (int j = 0; j < 8; j++)
                currentByte = (byte) ((currentByte << 1) | bits[i * 8 + j]);
            byteArray[i] = currentByte;
        }

        return new String(byteArray, StandardCharsets.UTF_8);
    }
}
