package com.example.mobcontrol;

import android.content.Intent;
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
    private Button editLayoutButton;
    private Button backToMenuButton;
    private Button quitButton;
    private ImageButton backButton;

    // Connection info for Edit Layout
    private String layoutName;
    private String serverIP;
    private int serverPort;
    private String pairingCode;
    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        // Get connection info from intent
        layoutName = getIntent().getStringExtra("LAYOUT_NAME");
        serverIP = getIntent().getStringExtra("IP");
        serverPort = getIntent().getIntExtra("PORT", 7777);
        pairingCode = getIntent().getStringExtra("CODE");
        deviceName = getIntent().getStringExtra("DEVICE_NAME");

        preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);

        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        soundSwitch = findViewById(R.id.soundSwitch);
        editLayoutButton = findViewById(R.id.editLayoutButton);
        backToMenuButton = findViewById(R.id.backToMenuButton);
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

        // ✨ NEW: Edit Layout button
        editLayoutButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditLayoutActivity.class);
            intent.putExtra("LAYOUT_NAME", layoutName);
            intent.putExtra("IS_NEW", false);
            intent.putExtra("IP", serverIP);
            intent.putExtra("PORT", serverPort);
            intent.putExtra("CODE", pairingCode);
            intent.putExtra("DEVICE_NAME", deviceName);
            startActivity(intent);
        });

        // ✨ NEW: Back to Menu button
        backToMenuButton.setOnClickListener(v -> {
            // Return to layout selection
            Intent intent = new Intent(this, LayoutSelectionActivity.class);
            intent.putExtra("IP", serverIP);
            intent.putExtra("PORT", serverPort);
            intent.putExtra("CODE", pairingCode);
            intent.putExtra("DEVICE_NAME", deviceName);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // Quit button
        quitButton.setOnClickListener(v -> {
            finishAffinity();
        });

        // Back button (returns to controller)
        backButton.setOnClickListener(v -> {
            backButton.setAlpha(0.6f);
            backButton.postDelayed(() -> backButton.setAlpha(1.0f), 100);
            finish();
        });
    }
}