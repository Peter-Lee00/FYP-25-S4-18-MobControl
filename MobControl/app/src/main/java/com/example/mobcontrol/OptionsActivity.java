package com.example.mobcontrol;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class OptionsActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private Switch vibrationSwitch;
    private Switch darkModeSwitch;
    private Switch soundSwitch;
    private Button quitButton;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);

        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        soundSwitch = findViewById(R.id.soundSwitch);
        quitButton = findViewById(R.id.quitButton);
        backButton = findViewById(R.id.backButton);

        // Load saved settings
        vibrationSwitch.setChecked(preferences.getBoolean("vibration_enabled", true));
        darkModeSwitch.setChecked(preferences.getBoolean("dark_mode", false));
        soundSwitch.setChecked(preferences.getBoolean("sound_enabled", true));

        // Vibration toggle
        vibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("vibration_enabled", isChecked).apply();
        });

        // Dark mode toggle
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("dark_mode", isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Sound toggle
        soundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("sound_enabled", isChecked).apply();
        });

        // Quit button
        quitButton.setOnClickListener(v -> {
            finishAffinity();
        });

        // Back button
        backButton.setOnClickListener(v -> {
            backButton.setAlpha(0.6f);
            backButton.postDelayed(() -> backButton.setAlpha(1.0f), 100);
            finish();
        });
    }
}