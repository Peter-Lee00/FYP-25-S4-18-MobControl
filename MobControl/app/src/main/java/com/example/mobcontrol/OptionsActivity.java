// OptionsActivity.java에 Ping Test 추가

package com.example.mobcontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class OptionsActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private Switch vibrationSwitch;
    private Switch darkModeSwitch;
    private Switch soundSwitch;
    private Switch multiplayerSwitch;
    private Button editLayoutButton;
    private Button backToMenuButton;
    private Button quitButton;
    private ImageButton backButton;

    // ✅ Ping Test
    private Button pingTestButton;
    private TextView pingDisplay;

    // Connection info
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

        // ✅ Connection 정보 저장 (Ping Test용)
        preferences.edit()
                .putString("serverIP", serverIP)
                .putInt("serverPort", serverPort)
                .apply();

        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        soundSwitch = findViewById(R.id.soundSwitch);
        multiplayerSwitch = findViewById(R.id.multiplayerSwitch);
        editLayoutButton = findViewById(R.id.editLayoutButton);
        backToMenuButton = findViewById(R.id.backToMenuButton);
        quitButton = findViewById(R.id.quitButton);
        backButton = findViewById(R.id.backButton);

        // ✅ Ping Test
        pingTestButton = findViewById(R.id.pingTestButton);
        pingDisplay = findViewById(R.id.pingDisplay);

        // Load saved settings
        vibrationSwitch.setChecked(preferences.getBoolean("vibration_enabled", true));
        darkModeSwitch.setChecked(preferences.getBoolean("dark_mode", false));
        soundSwitch.setChecked(preferences.getBoolean("sound_enabled", true));
        multiplayerSwitch.setChecked(preferences.getBoolean("multiplayer_enabled", false));

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

        // Multiplayer toggle
        multiplayerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showPlayerSelectionDialog();
            } else {
                preferences.edit()
                        .putBoolean("multiplayer_enabled", false)
                        .putInt("player_number", 1)
                        .apply();
                Toast.makeText(this, "Multiplayer disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // ✅ Ping Test 버튼
        pingTestButton.setOnClickListener(v -> testPing());

        // Edit Layout button
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

        // Back to Menu button
        backToMenuButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, LayoutSelectionActivity.class);
            intent.putExtra("IP", serverIP);
            intent.putExtra("PORT", serverPort);
            intent.putExtra("CODE", pairingCode);
            intent.putExtra("DEVICE_NAME", deviceName);
            startActivity(intent);
            finish();
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

    // ✅ Ping Test 메서드
    private void testPing() {
        pingTestButton.setEnabled(false);
        pingTestButton.setText("...");
        pingDisplay.setText("...");
        pingDisplay.setTextColor(Color.parseColor("#9E9E9E"));

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                Map<String, Object> pingMsg = new HashMap<>();
                pingMsg.put("action", "ping");
                pingMsg.put("timestamp", startTime);

                String json = new Gson().toJson(pingMsg);
                byte[] sendData = json.getBytes();

                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(3000);

                if (serverIP == null || serverIP.isEmpty()) {
                    runOnUiThread(() -> {
                        pingDisplay.setText("ERR");
                        pingDisplay.setTextColor(Color.parseColor("#F44336"));
                        pingTestButton.setEnabled(true);
                        pingTestButton.setText("TEST");
                    });
                    return;
                }

                InetAddress address = InetAddress.getByName(serverIP);
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length, address, serverPort);

                socket.send(sendPacket);

                byte[] recvData = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
                socket.receive(recvPacket);

                long endTime = System.currentTimeMillis();
                long ping = endTime - startTime;

                socket.close();

                runOnUiThread(() -> {
                    pingDisplay.setText(ping + " ms");

                    if (ping < 100) {
                        pingDisplay.setTextColor(Color.parseColor("#4CAF50")); // 초록
                    } else if (ping < 200) {
                        pingDisplay.setTextColor(Color.parseColor("#FFC107")); // 노랑
                    } else {
                        pingDisplay.setTextColor(Color.parseColor("#F44336")); // 빨강
                    }

                    pingTestButton.setEnabled(true);
                    pingTestButton.setText("TEST");
                });

            } catch (java.net.SocketTimeoutException e) {
                runOnUiThread(() -> {
                    pingDisplay.setText("TIMEOUT");
                    pingDisplay.setTextColor(Color.parseColor("#F44336"));
                    pingTestButton.setEnabled(true);
                    pingTestButton.setText("TEST");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pingDisplay.setText("ERROR");
                    pingDisplay.setTextColor(Color.parseColor("#F44336"));
                    pingTestButton.setEnabled(true);
                    pingTestButton.setText("TEST");
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void showPlayerSelectionDialog() {
        String[] players = {"Player 1", "Player 2", "Player 3", "Player 4"};
        int currentPlayer = preferences.getInt("player_number", 1);

        new AlertDialog.Builder(this)
                .setTitle("Select Player Number")
                .setSingleChoiceItems(players, currentPlayer - 1, (dialog, which) -> {
                    int playerNumber = which + 1;
                    preferences.edit()
                            .putBoolean("multiplayer_enabled", true)
                            .putInt("player_number", playerNumber)
                            .apply();

                    Toast.makeText(this, "Player " + playerNumber + " selected", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    multiplayerSwitch.setChecked(false);
                    dialog.dismiss();
                })
                .setOnCancelListener(dialog -> {
                    multiplayerSwitch.setChecked(false);
                })
                .show();
    }
}