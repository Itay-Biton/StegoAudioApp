package com.stegoaudioapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.stegoaudioapp.Utils.FileManager;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    private final ActivityResultLauncher<Intent> uploadLauncher = FileManager.registerAudioUploadLauncher(this, new FileManager.UploadCallback() {
        @Override
        public void onSuccess(Uri newAudioUri) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof UploadResultCallback) {
                ((UploadResultCallback) currentFragment).onUploadSuccess(newAudioUri);
            }
        }

        @Override
        public void onFailure(String errorMessage) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof UploadResultCallback) {
                ((UploadResultCallback) currentFragment).onUploadFailure(errorMessage);
            }
        }
    });
    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher = FileManager.registerIntentSenderLauncher(this, new FileManager.IntentSenderCallback() {
        @Override
        public void onSuccess() {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof ResultCallback) {
                ((ResultCallback) currentFragment).onSuccess();
            }
        }

        @Override
        public void onFailure() {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof ResultCallback) {
                ((ResultCallback) currentFragment).onFailure();
            }
        }
    });
    private final ActivityResultLauncher<IntentSenderRequest> renameLauncher = FileManager.registerIntentSenderLauncher(this, new FileManager.IntentSenderCallback() {
        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure() {

        }
    });

    public interface UploadResultCallback {
        void onUploadSuccess(Uri uri);
        void onUploadFailure(String error);
    }
    public interface ResultCallback {
        void onSuccess();
        void onFailure();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_encode) {
                    EncodeActivity encodeActivity = new EncodeActivity();
                    encodeActivity.setLaunchers(uploadLauncher, deleteLauncher, renameLauncher);
                    replaceFragment(encodeActivity);
                    return true;
                } else if (itemId == R.id.navigation_list) {
                    AudioLibraryActivity audioLibraryActivity = new AudioLibraryActivity();
                    audioLibraryActivity.setLaunchers(uploadLauncher, deleteLauncher, renameLauncher);
                    replaceFragment(audioLibraryActivity);
                    return true;
                } else if (itemId == R.id.navigation_decode) {
                    DecodeActivity decodeActivity = new DecodeActivity();
                    decodeActivity.setLaunchers(uploadLauncher, deleteLauncher, renameLauncher);
                    replaceFragment(decodeActivity);
                    return true;
                }
                return false;
            }
        });

        bottomNavigationView.setSelectedItemId(R.id.navigation_encode);
    }

    private void replaceFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}