package com.stegoaudioapp.Utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    private static final String APP_DIRECTORY = "StegoAudio";

    // 1. Get all .wav files (using MediaStore)
    public static List<File> getAudioFiles(Context context) {
        List<File> audioFiles = new ArrayList<>();
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[]{
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME
        };

        String selection = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE '%.wav'";

        try (android.database.Cursor cursor = context.getContentResolver().query(collection, projection, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                while (!cursor.isAfterLast()) {
                    String data = cursor.getString(dataColumn);
                    File file = new File(data);
                    if (file.exists()) {
                        audioFiles.add(file);
                    }
                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            Log.e("FileManager", "Error getting audio files: " + e.getMessage());
        }
        return audioFiles;
    }

    // 2. Delete a file (using MediaStore)
    public static void deleteFile(Context context, String fileName, ActivityResultLauncher<IntentSenderRequest> launcher) {
        Uri fileUri = getAudioFileUri(context, fileName);
        if (fileUri != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.getContentResolver().delete(fileUri, null, null);
                } else {
                    IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(
                            MediaStore.createDeleteRequest(context.getContentResolver(), java.util.Arrays.asList(fileUri)).getIntentSender()
                    ).build();
                    launcher.launch(intentSenderRequest);
                }
            } catch (Exception e) {
                Log.e("FileManager", "Error deleting file: " + e.getMessage());
            }
        }
    }
    public static ActivityResultLauncher<IntentSenderRequest> registerIntentSenderLauncher(final Context context, final IntentSenderCallback callback) {
        return ((androidx.activity.ComponentActivity) context).registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure();
                    }
                }
            }
        });
    }
    public interface IntentSenderCallback {
        void onSuccess();
        void onFailure();
    }

    // 3. Get a file by name (using MediaStore)
    public static Uri getAudioFileUri(Context context, String fileName) {
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[]{MediaStore.Audio.Media._ID};
        String selection = MediaStore.Audio.Media.DISPLAY_NAME + " = ?";
        String[] selectionArgs = new String[]{fileName};

        try (android.database.Cursor cursor = context.getContentResolver().query(collection, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                long id = cursor.getLong(idColumn);
                return Uri.withAppendedPath(collection, String.valueOf(id));
            }
        } catch (Exception e) {
            Log.e("FileManager", "Error getting file URI: " + e.getMessage());
        }
        return null;
    }

    // 4. Create a new file and save (using MediaStore)
    public static Uri createFile(Context context, String fileName, byte[] audioData) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + APP_DIRECTORY);
        }

        Uri audioCollection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            audioCollection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        try {
            Uri audioUri = context.getContentResolver().insert(audioCollection, values);
            if (audioUri != null) {
                try (OutputStream os = context.getContentResolver().openOutputStream(audioUri)) {
                    os.write(audioData);
                    return audioUri;
                }
            }
        } catch (IOException e) {
            Log.e("FileManager", "Error creating file: " + e.getMessage());
        }
        return null;
    }

    // 5. Rename a file (using MediaStore)
    public static void renameFile(Context context, String oldFileName, String newFileName, ActivityResultLauncher<IntentSenderRequest> launcher) {
        Uri fileUri = getAudioFileUri(context, oldFileName);
        if (fileUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.getContentResolver().update(fileUri, values, null, null);
                } else {
                    IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(
                            MediaStore.createWriteRequest(context.getContentResolver(), java.util.Arrays.asList(fileUri)).getIntentSender()
                    ).build();
                    launcher.launch(intentSenderRequest);
                }
            } catch (Exception e) {
                Log.e("FileManager", "Error renaming file: " + e.getMessage());
            }
        }
    }

    // 6. save file from uri
    public static Uri saveFileFromUri(Context context, Uri fileUri, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, context.getContentResolver().getType(fileUri)); // Get MIME type from original URI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + APP_DIRECTORY);
        }

        Uri audioCollection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            audioCollection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        try {
            Uri savedFileUri = context.getContentResolver().insert(audioCollection, values);
            if (savedFileUri != null) {
                try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                     OutputStream outputStream = context.getContentResolver().openOutputStream(savedFileUri)) {

                    if (inputStream != null && outputStream != null) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        return savedFileUri;
                    }

                } catch (IOException e) {
                    Log.e("FileManager", "Error saving file from URI: " + e.getMessage());
                    context.getContentResolver().delete(savedFileUri, null, null); // Clean up on error
                    return null;
                }
            }
        } catch (Exception e) {
            Log.e("FileManager", "Error inserting file into MediaStore: " + e.getMessage());
        }

        return null;
    }

    // 7. Upload a file from device
    public static void uploadAudioFile(Context context, ActivityResultLauncher<Intent> launcher) {
        if (launcher == null) {
            Log.e("FileManager", "ActivityResultLauncher must be initialized");
            return;
        }
        launcher.launch(new Intent(Intent.ACTION_GET_CONTENT).setType("audio/*"));
    }
    public static ActivityResultLauncher<Intent> registerAudioUploadLauncher(final Context context, final UploadCallback callback) {
        return ((androidx.activity.ComponentActivity) context).registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null) {
                        Uri newAudioUri = saveFileFromUri(context, fileUri, getFileNameFromPath(fileUri));
                        if (newAudioUri != null) {
                            if (callback != null) {
                                callback.onSuccess(newAudioUri);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("Failed to save audio file.");
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure("No file selected.");
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure("File selection canceled.");
                    }
                }
            }
        });
    }
    public interface UploadCallback {
        void onSuccess(Uri newAudioUri);
        void onFailure(String errorMessage);
    }

    public static String getFileName(Context context, Uri uri) {
        String fileName = null;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

    public static String getFileNameFromPath(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                return new File(path).getName();
            }
        }
        return null;
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            return getFileName(context, uri);
        } else if ("file".equals(uri.getScheme())) {
            return getFileNameFromPath(uri);
        } else {
            return null;
        }
    }

    public static File getFileFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        File file = null;
        String fileName = getFileName(context, uri);
        if (fileName != null) {
            file = new File(context.getCacheDir(), fileName); // Use cache dir for temporary file
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(file)) {
                if (inputStream != null) {
                    byte[] buffer = new byte[4 * 1024]; // 4KB buffer
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                Log.e("FileUtils", "Error getting file from URI: " + e.getMessage());
                return null; // Return null on error
            }

        }
        return file;
    }

    public static void shareFile(Context context, File file) {
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file);

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sendIntent.setType(context.getContentResolver().getType(uri));
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent shareIntent = Intent.createChooser(sendIntent, null);
        context.startActivity(shareIntent);
    }

    public static void printWavHeader(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[44];
            if (fis.read(header) != 44) {
                System.out.println("Invalid WAV file: Header too short");
                return;
            }

            // Extract header fields
            String chunkID = new String(header, 0, 4);
            int fileSize = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            String format = new String(header, 8, 4);
            String subChunk1ID = new String(header, 12, 4);
            int subChunk1Size = ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int audioFormat = ByteBuffer.wrap(header, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            int numChannels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            int sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int byteRate = ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int blockAlign = ByteBuffer.wrap(header, 32, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            int bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            String subChunk2ID = new String(header, 36, 4);
            int subChunk2Size = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            // Print header info
            System.out.println("Chunk ID: " + chunkID);
            System.out.println("File Size: " + fileSize);
            System.out.println("Format: " + format);
            System.out.println("Subchunk1 ID: " + subChunk1ID);
            System.out.println("Subchunk1 Size: " + subChunk1Size);
            System.out.println("Audio Format: " + audioFormat);
            System.out.println("Number of Channels: " + numChannels);
            System.out.println("Sample Rate: " + sampleRate);
            System.out.println("Byte Rate: " + byteRate);
            System.out.println("Block Align: " + blockAlign);
            System.out.println("Bits per Sample: " + bitsPerSample);
            System.out.println("Subchunk2 ID: " + subChunk2ID);
            System.out.println("Subchunk2 Size: " + subChunk2Size);
        } catch (IOException e) {
            System.err.println("Error reading WAV file: " + e.getMessage());
        }
    }
}
