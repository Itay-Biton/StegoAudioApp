package com.stegoaudioapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.stegoaudioapp.Utils.AudioPlayer;
import com.stegoaudioapp.Utils.AudioProcessor;
import com.stegoaudioapp.Utils.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EncodeActivity extends Fragment implements MainActivity.UploadResultCallback, MainActivity.ResultCallback {
    @Override
    public void onSuccess() {
        if (!locked && currentFile != null)
            refreshFileName();
    }

    @Override
    public void onFailure() {

    }

    @Override
    public void onUploadSuccess(Uri uri) {
        currentFile = FileManager.getFileFromUri(requireContext(), uri);
        refreshFileName();
        currentFile = AudioProcessor.encodeStringToFile(messageEditText.getText().toString(), currentFile, requireContext());
        refreshFileName();
    }

    @Override
    public void onUploadFailure(String error) {

    }

    private enum InputMode {RECORD, UPLOAD, CHOOSE};

    private EditText messageEditText, fileNameEditText;
    private AppCompatButton lockButton, recordButton, uploadButton, chooseButton, renameButton, playButton, shareButton;
    private AppCompatImageButton audioButton;
    private TextView durationTextView;
    private ListView listView;
    private File currentFile;
    private List<File> audioFiles;

    private boolean locked = true;
    InputMode inputMode = InputMode.RECORD;

    private ActivityResultLauncher<Intent> uploadLauncher;
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher, renameLauncher;

    public void setLaunchers(ActivityResultLauncher<Intent> uploadLauncher, ActivityResultLauncher<IntentSenderRequest> deleteLauncher, ActivityResultLauncher<IntentSenderRequest> renameLauncher) {
        this.uploadLauncher = uploadLauncher;
        this.deleteLauncher = deleteLauncher;
        this.renameLauncher = renameLauncher;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_encode, container, false);

        findViews(view);
        toggleLock();

        return view;
    }

    private void findViews(View view) {
        messageEditText = view.findViewById(R.id.messageEditText);
        fileNameEditText = view.findViewById(R.id.fileNameEditText);
        lockButton = view.findViewById(R.id.lockButton);
        recordButton = view.findViewById(R.id.recordButton);
        uploadButton = view.findViewById(R.id.uploadButton);
        chooseButton = view.findViewById(R.id.chooseButton);
        renameButton = view.findViewById(R.id.renameButton);
        playButton = view.findViewById(R.id.playButton);
        shareButton = view.findViewById(R.id.shareButton);
        audioButton = view.findViewById(R.id.audioButton);
        durationTextView = view.findViewById(R.id.durationTextView);
        listView = view.findViewById(R.id.list_audio_files);

        listView.setVisibility(View.GONE);

        listView.setOnItemClickListener((AdapterView<?> parent, View itemView, int position, long id) -> {
            currentFile = audioFiles.get(position);
            refreshFileName();
            encodeAudioFile(currentFile, messageEditText.getText().toString(), requireContext());
        });
        lockButton.setOnClickListener(v -> {toggleLock();});
        recordButton.setOnClickListener(v -> {
            inputMode = InputMode.RECORD;
            toggleInputMode();
        });
        uploadButton.setOnClickListener(v -> {
            inputMode = InputMode.UPLOAD;
            toggleInputMode();
        });
        chooseButton.setOnClickListener(v -> {
            inputMode = InputMode.CHOOSE;
            toggleInputMode();
        });
        renameButton.setOnClickListener(v -> {
            FileManager.renameFile(requireContext(), currentFile.getName(), fileNameEditText.getText().toString(), renameLauncher);
        });
        playButton.setOnClickListener(v -> {
            AudioPlayer.playFile(requireContext(),currentFile);
        });
        shareButton.setOnClickListener(v -> {});
        audioButton.setOnClickListener(v -> {
            switch (inputMode) {
                case CHOOSE:
                    refreshFileName();
                    break;
                case RECORD:
                    AudioPlayer audioPlayer = new AudioPlayer();
                    audioPlayer.startRecording(requireContext(), AudioProcessor.calculateMinimumDuration(messageEditText.getText().toString()), new AudioPlayer.RecordingCallback() {
                        @Override
                        public void onRecordingFinished(Uri fileUri) {
                            Toast.makeText(requireContext(), "done", Toast.LENGTH_LONG).show();
                            currentFile = FileManager.getFileFromUri(requireContext(), fileUri);
                            refreshFileName();
                            encodeAudioFile(currentFile, messageEditText.getText().toString(), requireContext());
                        }

                        @Override
                        public void onRecordingFailed(String errorMessage) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                case UPLOAD:
                    FileManager.uploadAudioFile(requireContext(), uploadLauncher);
                    break;
            }
        });
    }

    private void refreshFileName() {
        fileNameEditText.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
        fileNameEditText.setText(currentFile.getName());
        renameButton.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);

        playButton.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
        shareButton.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
    }

    private void toggleLock() {
        locked = !locked;
        messageEditText.setEnabled(!locked);
        fileNameEditText.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
        lockButton.setText(locked ? "Unlock" : "Lock");
        recordButton.setVisibility(locked ? View.VISIBLE : View.GONE);
        uploadButton.setVisibility(locked ? View.VISIBLE : View.GONE);
        chooseButton.setVisibility(locked ? View.VISIBLE : View.GONE);
        renameButton.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
        playButton.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
        shareButton.setVisibility(locked && (currentFile != null) ? View.VISIBLE : View.GONE);
        durationTextView.setVisibility(locked ? View.VISIBLE : View.GONE);
        if (locked) {
            durationTextView.setText(String.format("Minimum recording time is %s", AudioProcessor.calculateMinimumDuration(messageEditText.getText().toString())));
            toggleInputMode();
        }
        else {
            audioButton.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
        }
    }

    private void toggleInputMode() {
        switch (inputMode) {
            case CHOOSE:
                chooseButton.setBackgroundTintList(getResources().getColorStateList(R.color.white, requireContext().getTheme()));
                chooseButton.setTextColor(Color.BLACK);
                recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.gray, requireContext().getTheme()));
                recordButton.setTextColor(Color.WHITE);
                uploadButton.setBackgroundTintList(getResources().getColorStateList(R.color.gray, requireContext().getTheme()));
                uploadButton.setTextColor(Color.WHITE);
                audioButton.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);

                audioFiles = FileManager.getAudioFiles(requireContext());
                List<String> audioFileNames = new ArrayList<>();
                for (File file : audioFiles)
                    audioFileNames.add(file.getName());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, audioFileNames);
                listView.setAdapter(adapter);
                break;
            case RECORD:
                recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.white, requireContext().getTheme()));
                recordButton.setTextColor(Color.BLACK);
                chooseButton.setBackgroundTintList(getResources().getColorStateList(R.color.gray, requireContext().getTheme()));
                chooseButton.setTextColor(Color.WHITE);
                uploadButton.setBackgroundTintList(getResources().getColorStateList(R.color.gray, requireContext().getTheme()));
                uploadButton.setTextColor(Color.WHITE);
                audioButton.setVisibility(View.VISIBLE);
                audioButton.setImageResource(R.drawable.mic);
                listView.setVisibility(View.GONE);
                break;
            case UPLOAD:
                uploadButton.setBackgroundTintList(getResources().getColorStateList(R.color.white, requireContext().getTheme()));
                uploadButton.setTextColor(Color.BLACK);
                chooseButton.setBackgroundTintList(getResources().getColorStateList(R.color.gray, requireContext().getTheme()));
                chooseButton.setTextColor(Color.WHITE);
                recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.gray, requireContext().getTheme()));
                recordButton.setTextColor(Color.WHITE);
                audioButton.setVisibility(View.VISIBLE);
                audioButton.setImageResource(R.drawable.upload);
                listView.setVisibility(View.GONE);
                break;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void encodeAudioFile(File audioFile, String message, Context context) {
        CompletableFuture.supplyAsync(() -> AudioProcessor.encodeStringToFile(message, audioFile, context), executor)
                .thenAccept(encodedFile -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (encodedFile != null) {
                                if (inputMode.equals(InputMode.RECORD))
                                    FileManager.deleteFile(context, currentFile.getName(), deleteLauncher);
                                currentFile = encodedFile;
                                refreshFileName();
                                Toast.makeText(context, "Encoding Complete", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, "Encoding Failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .exceptionally(ex -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(context, "Encoding Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                    return null;
                });
    }
}