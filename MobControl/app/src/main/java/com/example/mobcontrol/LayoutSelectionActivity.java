package com.example.mobcontrol;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
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

        // Perform pairing first
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

            Toast.makeText(this, "🏎️ Racing Mode!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(LayoutSelectionActivity.this, RacingControllerActivity.class);
            intent.putExtra("IP", serverIP);
            intent.putExtra("PORT", serverPort);
            intent.putExtra("CODE", pairingCode);
            intent.putExtra("DEVICE_NAME", deviceName);
            startActivity(intent);

            setResult(RESULT_OK);
            finish();
        });

        flightLayout.setOnClickListener(v -> {
            if (!isPaired) {
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "✈️ Flight Controller!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(LayoutSelectionActivity.this, FlightControllerActivity.class);
            intent.putExtra("IP", serverIP);
            intent.putExtra("PORT", serverPort);
            intent.putExtra("CODE", pairingCode);
            intent.putExtra("DEVICE_NAME", deviceName);
            startActivity(intent);

            setResult(RESULT_OK);
            finish();
        });

        gameControllerLayout.setOnClickListener(v -> {
            if (!isPaired) {
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "🎮 Standard Controller", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(LayoutSelectionActivity.this, ControllerActivity.class);
            intent.putExtra("IP", serverIP);
            intent.putExtra("PORT", serverPort);
            intent.putExtra("CODE", pairingCode);
            intent.putExtra("DEVICE_NAME", deviceName);
            startActivity(intent);

            setResult(RESULT_OK);
            finish();
        });

        newLayoutButton.setOnClickListener(v -> {
            Toast.makeText(this, "Custom layout coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void performPairing() {
        executorService.execute(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();

                // Send pairing request
                Map<String, String> pairMessage = new HashMap<>();
                pairMessage.put("action", "pair");
                pairMessage.put("code", pairingCode);
                pairMessage.put("device", deviceName);
                String jsonMessage = gson.toJson(pairMessage);

                // Log for debugging
                android.util.Log.d("LayoutSelection", "Sending pairing: " + jsonMessage);

                byte[] sendData = jsonMessage.getBytes();
                InetAddress address = InetAddress.getByName(serverIP);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, serverPort);
                socket.send(sendPacket);

                // Wait for ACK
                socket.setSoTimeout(5000);
                byte[] recvData = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
                socket.receive(recvPacket);

                String response = new String(recvPacket.getData(), 0, recvPacket.getLength());
                android.util.Log.d("LayoutSelection", "Received response: " + response);

                Map<String, Object> jsonResponse = gson.fromJson(response, Map.class);

                // Desktop sends both "status" and "action" for compatibility
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