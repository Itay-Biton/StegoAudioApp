package com.stegoaudioapp.Utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioPlayer {

    private static MediaPlayer mediaPlayer;

    public static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private String fileName;
    private Context context;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface RecordingCallback {
        void onRecordingFinished(Uri fileUri);
        void onRecordingFailed(String errorMessage);
    }

    public static void playFile(Context context, File file) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = MediaPlayer.create(context, Uri.fromFile(file));
        mediaPlayer.start();
    }

    public void startRecording(Context context, double durationSeconds, RecordingCallback callback) {
        this.context = context;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback.onRecordingFailed("Record audio permission not granted");
            return;
        }
        if (isRecording) {
            callback.onRecordingFailed("Already recording");
            return;
        }

        fileName = "recorded_audio_" + System.currentTimeMillis() + ".pcm";
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, BUFFER_SIZE);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            callback.onRecordingFailed("AudioRecord initialization failed");
            return;
        }

        audioRecord.startRecording();
        isRecording = true;

        executor.execute(() -> recordAudio(durationSeconds, callback));
    }

    public void stopRecording() {
        if (isRecording) {
            isRecording = false;
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    private void recordAudio(double durationSeconds, RecordingCallback callback) {
        byte[] data = new byte[BUFFER_SIZE];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            int read;
            long startTime = System.currentTimeMillis();
            long endTime = (long) (startTime + (durationSeconds * 1000));

            while (isRecording && System.currentTimeMillis() < endTime) {
                read = audioRecord.read(data, 0, BUFFER_SIZE);
                if (read > 0) {
                    byteArrayOutputStream.write(data, 0, read);
                }
            }
            isRecording = false;

            byte[] audioData = byteArrayOutputStream.toByteArray();
            long totalAudioLen = audioData.length;
            long totalDataLen = totalAudioLen + 36;
            byte[] header = generateWavHeader(totalAudioLen, totalDataLen, 1, RECORDER_SAMPLE_RATE, BITS_PER_SAMPLE);

            ByteArrayOutputStream wavOutputStream = new ByteArrayOutputStream();
            wavOutputStream.write(header, 0, 44);
            wavOutputStream.write(audioData, 0, audioData.length);

            Uri fileUri = FileManager.createFile(context, fileName.replace(".pcm", ".wav"), wavOutputStream.toByteArray());

            mainHandler.post(() -> {
                if (fileUri != null) {
                    callback.onRecordingFinished(fileUri);
                } else {
                    callback.onRecordingFailed("Recording Failed");
                }
            });

        } catch (Exception e) {
            Log.e("AudioRecorder", "Error writing audio data", e);
            mainHandler.post(() -> callback.onRecordingFailed("Error writing audio data"));
        } finally {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                Log.e("AudioRecorder", "Error closing output stream", e);
            }
        }
    }

    public static byte[] generateWavHeader(long totalAudioLen, long totalDataLen, int channels, int sampleRate, int bitsPerSample) {
        long byteRate = bitsPerSample * sampleRate * channels / 8;

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; // WAVE header
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 16 for PCM
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // PCM - 1
        header[21] = 0;
        header[22] = (byte) channels; // Channels: 1 mono, 2 stereo
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff); // Sample rate
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff); // Byte rate
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8); // Block align
        header[33] = 0;
        header[34] = (byte) bitsPerSample; // Bits per sample
        header[35] = 0;
        header[36] = 'd'; // 'data' chunk
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        return header;
    }
}
