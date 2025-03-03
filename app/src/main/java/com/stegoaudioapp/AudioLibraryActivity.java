package com.stegoaudioapp;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;

import com.stegoaudioapp.Utils.AudioPlayer;
import com.stegoaudioapp.Utils.FileManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudioLibraryActivity extends Fragment {

    private ListView listView;
    private AppCompatButton addNewBTN;
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
        View view = inflater.inflate(R.layout.activity_audio_library, container, false);
        findViews(view);
        refreshList();
        return view;
    }

    private void findViews(View view) {
        listView = view.findViewById(R.id.list_audio_files);
        addNewBTN = view.findViewById(R.id.addNewBTN);

        addNewBTN.setOnClickListener(v -> addNewMedia());

        listView.setOnItemClickListener((AdapterView<?> parent, View itemView, int position, long id) -> {
            AudioPlayer.playFile(requireContext(), audioFiles.get(position));
        });

        listView.setOnItemLongClickListener((AdapterView<?> parent, View itemView, int position, long id) -> {
            deleteAudio(audioFiles.get(position));
            return true;
        });
    }

    private void refreshList() {
        audioFiles = FileManager.getAudioFiles(requireContext());
        List<String> audioFileNames = new ArrayList<>();

        for (File file : audioFiles)
            audioFileNames.add(file.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, audioFileNames);
        listView.setAdapter(adapter);
    }

    private void addNewMedia() {
        FileManager.uploadAudioFile(requireContext(), uploadLauncher);
    }

    private void deleteAudio(File file) {
        FileManager.deleteFile(requireContext(), file.getName(), deleteLauncher);
        refreshList();
    }
}