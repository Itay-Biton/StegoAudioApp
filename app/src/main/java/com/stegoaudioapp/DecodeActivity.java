package com.stegoaudioapp;

import android.app.Activity;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DecodeActivity extends Fragment implements MainActivity.UploadResultCallback, MainActivity.ResultCallback {
    @Override
    public void onSuccess() {
        if (currentFile != null)
            refreshUI();
    }

    @Override
    public void onFailure() {

    }

    @Override
    public void onUploadSuccess(Uri uri) {
        currentFile = FileManager.getFileFromUri(requireContext(), uri);
        refreshUI();
        messageEditText.setText(AudioProcessor.decodeFileToMessage(currentFile));
    }

    @Override
    public void onUploadFailure(String error) {

    }

    private enum InputMode {RECORD, UPLOAD, CHOOSE};

    private EditText messageEditText, fileNameEditText;
    private AppCompatButton recordButton, uploadButton, chooseButton, renameButton, playButton, shareButton;
    private AppCompatImageButton audioButton;
    private ListView listView;

    InputMode inputMode = InputMode.RECORD;
    private File currentFile;
    private List<File> audioFiles;

    private ActivityResultLauncher<Intent> uploadLauncher;
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher, renameLauncher;

    public void setLaunchers(ActivityResultLauncher<Intent> uploadLauncher, ActivityResultLauncher<IntentSenderRequest> deleteLauncher, ActivityResultLauncher<IntentSenderRequest> renameLauncher) {
        this.uploadLauncher = uploadLauncher;
        this.deleteLauncher = deleteLauncher;
        this.renameLauncher = renameLauncher;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_decode, container, false);

        findViews(view);
        refreshUI();
        return view;
    }

    private void findViews(View view) {
        messageEditText = view.findViewById(R.id.messageEditText);
        fileNameEditText = view.findViewById(R.id.fileNameEditText);
        recordButton = view.findViewById(R.id.recordButton);
        uploadButton = view.findViewById(R.id.uploadButton);
        chooseButton = view.findViewById(R.id.chooseButton);
        renameButton = view.findViewById(R.id.renameButton);
        playButton = view.findViewById(R.id.playButton);
        shareButton = view.findViewById(R.id.shareButton);
        audioButton = view.findViewById(R.id.audioButton);
        listView = view.findViewById(R.id.list_audio_files);

        messageEditText.setEnabled(false);
        listView.setVisibility(View.GONE);

        listView.setOnItemClickListener((AdapterView<?> parent, View itemView, int position, long id) -> {
            currentFile = audioFiles.get(position);
            refreshUI();
            decodeAudioFile(currentFile);
        });
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
        shareButton.setOnClickListener(v -> {
            // Implement share logic here
        });
        audioButton.setOnClickListener(v -> {
            switch (inputMode) {
                case CHOOSE:
                    refreshUI();
                    break;
                case RECORD:
                    AudioPlayer audioPlayer = new AudioPlayer();
                    audioPlayer.startRecording(requireContext(), 5, new AudioPlayer.RecordingCallback() {
                        @Override
                        public void onRecordingFinished(Uri fileUri) {
                            Toast.makeText(requireContext(), "done", Toast.LENGTH_LONG).show();
                            currentFile = FileManager.getFileFromUri(requireContext(), fileUri);
                            refreshUI();
                            decodeAudioFile(currentFile);
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

    private void refreshUI() {
        if (currentFile != null)
            fileNameEditText.setText(currentFile.getName());

        toggleInputMode();
        fileNameEditText.setVisibility(currentFile != null ? View.VISIBLE : View.GONE);
        renameButton.setVisibility(currentFile != null ? View.VISIBLE : View.GONE);
        playButton.setVisibility(currentFile != null ? View.VISIBLE : View.GONE);
        shareButton.setVisibility(currentFile != null ? View.VISIBLE : View.GONE);
        messageEditText.setVisibility(currentFile != null ? View.VISIBLE : View.GONE);
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
                ArrayAdapter<File> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, audioFiles);
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
                listView.setVisibility(View.GONE);
                break;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void decodeAudioFile(File audioFile) {
        CompletableFuture.supplyAsync(() -> AudioProcessor.decodeFileToMessage(audioFile), executor)
                .thenAccept(decodedMessage -> {
                    Activity activity = getActivity(); // Get the Activity
                    if (activity != null) { // Check if Activity is attached
                        activity.runOnUiThread(() -> {
                            if (decodedMessage != null) {
                                messageEditText.setText(decodedMessage);
                            } else {
                                messageEditText.setText("Decoding failed.");
                            }
                        });
                    }
                });
    }
}