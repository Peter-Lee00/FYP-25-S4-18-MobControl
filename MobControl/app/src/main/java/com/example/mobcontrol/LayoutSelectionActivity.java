package com.example.mobcontrol;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LayoutSelectionActivity extends AppCompatActivity {

    private String serverIP;
    private int serverPort;
    private String pairingCode;
    private String deviceName;

    private ExecutorService executorService;
    private Handler mainHandler;
    private Gson gson;
    private boolean isPaired = false;

    private static final int REQUEST_EDIT_LAYOUT = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_selection);

        serverIP = getIntent().getStringExtra("IP");
        serverPort = getIntent().getIntExtra("PORT", 7777);
        pairingCode = getIntent().getStringExtra("CODE");
        deviceName = getIntent().getStringExtra("DEVICE_NAME");

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();

        ImageButton backButton = findViewById(R.id.backButton);
        LinearLayout racing1Layout = findViewById(R.id.racing1Layout);
        LinearLayout flightLayout = findViewById(R.id.racing2Layout);
        LinearLayout gameControllerLayout = findViewById(R.id.gameControllerLayout);
        Button newLayoutButton = findViewById(R.id.newLayoutButton);

        ImageView racing1Preview = findViewById(R.id.racing1Preview);
        ImageView flightPreview = findViewById(R.id.racing2Preview);
        ImageView gameControllerPreview = findViewById(R.id.gameControllerPreview);

        racing1Preview.setBackgroundColor(0xFF4A148C);
        flightPreview.setBackgroundColor(0xFF4A148C);
        gameControllerPreview.setBackgroundColor(0xFF4A148C);

        performPairing();

        backButton.setOnClickListener(v -> {
            backButton.setAlpha(0.6f);
            backButton.postDelayed(() -> backButton.setAlpha(1.0f), 100);
            finish();
        });

        racing1Layout.setOnClickListener(v -> {
            if (!isPaired) {
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
                return;
            }
            showLayoutOptions("racing_controller");
        });

        flightLayout.setOnClickListener(v -> {
            if (!isPaired) {
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
                return;
            }
            showLayoutOptions("flight_controller");
        });

        gameControllerLayout.setOnClickListener(v -> {
            if (!isPaired) {
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
                return;
            }
            showLayoutOptions("game_controller");
        });

        newLayoutButton.setOnClickListener(v -> {
            Toast.makeText(this, "Select a controller type first", Toast.LENGTH_SHORT).show();
        });
    }

    private void showLayoutOptions(String controllerId) {
        List<String> layoutNames = LayoutConfig.getLayoutNamesForController(this, controllerId);
        layoutNames.add(0, "➕ Edit Layout");

        String[] options = layoutNames.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Choose Layout")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Edit Layout - Open EditLayoutActivity
                        openEditLayout(controllerId);
                    } else {
                        // Select existing layout
                        String selectedLayout = options[which];
                        if ("Default".equals(selectedLayout)) {
                            LayoutConfig.resetControllerLayout(this, controllerId);
                        } else {
                            String layoutId = controllerId + "_" + selectedLayout;
                            Map<String, LayoutConfig.ButtonConfig> customLayout =
                                    LayoutConfig.loadControllerLayout(this, layoutId);
                            if (customLayout != null) {
                                LayoutConfig.saveControllerLayout(this, controllerId, customLayout);
                            }
                        }
                        launchController(controllerId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditLayout(String controllerId) {
        Intent intent = new Intent(this, EditLayoutActivity.class);
        intent.putExtra("CONTROLLER_ID", controllerId);
        intent.putExtra("FROM_LAYOUT_SELECTION", true);

        // Get default button positions for this controller
        String[] buttonIds;
        String[] buttonLabels;
        float[] buttonX;
        float[] buttonY;
        float[] buttonWidth;
        float[] buttonHeight;

        switch (controllerId) {
            case "racing_controller":
                buttonIds = new String[]{"brakeButton", "acceleratorButton", "leftClickButton", "rightClickButton"};
                buttonLabels = new String[]{"BRAKE", "GAS", "LEFT", "RIGHT"};
                // Default positions for racing (you need to set these)
                buttonX = new float[]{50, 250, 450, 650};
                buttonY = new float[]{500, 500, 500, 500};
                buttonWidth = new float[]{150, 150, 150, 150};
                buttonHeight = new float[]{150, 150, 150, 150};
                break;

            case "flight_controller":
                buttonIds = new String[]{"machineGunButton", "rocketButton", "turboButton", "pauseButton"};
                buttonLabels = new String[]{"MACHINE GUN", "ROCKET", "TURBO", "PAUSE"};
                // Default positions for flight (you need to set these)
                buttonX = new float[]{50, 250, 450, 650};
                buttonY = new float[]{500, 500, 500, 500};
                buttonWidth = new float[]{150, 150, 150, 150};
                buttonHeight = new float[]{150, 150, 150, 150};
                break;

            case "game_controller":
            default:
                buttonIds = new String[]{"actionAButton", "actionBButton", "actionXButton", "actionYButton"};
                buttonLabels = new String[]{"A", "B", "X", "Y"};
                buttonX = new float[]{50, 250, 450, 650};
                buttonY = new float[]{500, 500, 500, 500};
                buttonWidth = new float[]{150, 150, 150, 150};
                buttonHeight = new float[]{150, 150, 150, 150};
                break;
        }

        // Check if custom layout exists, use it instead of default
        Map<String, LayoutConfig.ButtonConfig> savedLayout =
                LayoutConfig.loadControllerLayout(this, controllerId);

        if (savedLayout != null) {
            // Use saved positions
            for (int i = 0; i < buttonIds.length; i++) {
                LayoutConfig.ButtonConfig config = savedLayout.get(buttonIds[i]);
                if (config != null) {
                    buttonX[i] = config.x;
                    buttonY[i] = config.y;
                    buttonWidth[i] = config.width;
                    buttonHeight[i] = config.height;
                }
            }
        }

        intent.putExtra("BUTTON_IDS", buttonIds);
        intent.putExtra("BUTTON_LABELS", buttonLabels);
        intent.putExtra("BUTTON_X", buttonX);
        intent.putExtra("BUTTON_Y", buttonY);
        intent.putExtra("BUTTON_WIDTH", buttonWidth);
        intent.putExtra("BUTTON_HEIGHT", buttonHeight);

        // Store controller info for after edit
        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);

        startActivityForResult(intent, REQUEST_EDIT_LAYOUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDIT_LAYOUT && resultCode == RESULT_OK) {
            // Edit completed, now launch controller
            if (data != null) {
                String controllerId = data.getStringExtra("CONTROLLER_ID");
                if (controllerId != null) {
                    launchController(controllerId);
                }
            }
        }
    }

    private void launchController(String controllerId) {
        Intent intent;
        String displayName;

        switch (controllerId) {
            case "racing_controller":
                intent = new Intent(this, RacingControllerActivity.class);
                displayName = "🏎️ Racing Mode!";
                break;
            case "flight_controller":
                intent = new Intent(this, FlightControllerActivity.class);
                displayName = "✈️ Flight Controller!";
                break;
            case "game_controller":
                intent = new Intent(this, ControllerActivity.class);
                displayName = "🎮 Standard Controller";
                break;
            default:
                return;
        }

        Toast.makeText(this, displayName, Toast.LENGTH_SHORT).show();

        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivity(intent);

        setResult(RESULT_OK);
        finish();
    }

    private void performPairing() {
        executorService.execute(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();

                Map<String, String> pairMessage = new HashMap<>();
                pairMessage.put("action", "pair");
                pairMessage.put("code", pairingCode);
                pairMessage.put("device", deviceName);
                String jsonMessage = gson.toJson(pairMessage);

                android.util.Log.d("LayoutSelection", "Sending pairing: " + jsonMessage);

                byte[] sendData = jsonMessage.getBytes();
                InetAddress address = InetAddress.getByName(serverIP);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, serverPort);
                socket.send(sendPacket);

                socket.setSoTimeout(5000);
                byte[] recvData = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
                socket.receive(recvPacket);

                String response = new String(recvPacket.getData(), 0, recvPacket.getLength());
                android.util.Log.d("LayoutSelection", "Received response: " + response);

                Map<String, Object> jsonResponse = gson.fromJson(response, Map.class);
                String status = (String) jsonResponse.get("status");
                String action = (String) jsonResponse.get("action");

                if ("connected".equals(status) || "pair_success".equals(action)) {
                    isPaired = true;
                    android.util.Log.d("LayoutSelection", "Pairing successful!");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "✓ Connected! Choose layout", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    android.util.Log.e("LayoutSelection", "Pairing failed - unexpected response");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Pairing failed", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                socket.close();

            } catch (Exception e) {
                android.util.Log.e("LayoutSelection", "Pairing error: " + e.getMessage());
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}