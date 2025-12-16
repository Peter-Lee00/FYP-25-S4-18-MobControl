package com.example.mobcontrol;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button scanQRButton;
    private EditText manualCodeInput;
    private Button connectButton;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int DEFAULT_PORT = 7777;

    private ExecutorService executorService;
    private Handler mainHandler;
    private Gson gson;
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanQRButton = findViewById(R.id.scanQRButton);
        manualCodeInput = findViewById(R.id.manualCodeInput);
        connectButton = findViewById(R.id.connectButton);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();

        scanQRButton.setOnClickListener(v -> requestCameraPermission());

        connectButton.setOnClickListener(v -> {
            String code = manualCodeInput.getText().toString().trim();

            if (code.length() != 4 || !code.matches("\\d+")) {
                Toast.makeText(this, "Please enter a valid 4-digit code", Toast.LENGTH_SHORT).show();
                return;
            }

            searchForDesktop(code);
        });
    }

    private void searchForDesktop(String code) {
        showProgressDialog("Searching for Desktop...");

        executorService.execute(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(5000);

                Map<String, String> discoveryMsg = new HashMap<>();
                discoveryMsg.put("action", "discover");
                discoveryMsg.put("code", code);
                String message = gson.toJson(discoveryMsg);

                byte[] sendData = message.getBytes();

                String[] ipsToTry = {
                        "10.0.2.2",           // Emulator → Host PC
                        "192.168.1.255",      // Local broadcast
                        "255.255.255.255"     // Global broadcast
                };

                boolean found = false;
                String desktopIP = null;

                for (String ip : ipsToTry) {
                    try {
                        InetAddress address = InetAddress.getByName(ip);
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendData,
                                sendData.length,
                                address,
                                DEFAULT_PORT
                        );

                        socket.send(sendPacket);

                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                        try {
                            socket.receive(receivePacket);

                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                            Map<String, Object> jsonResponse = gson.fromJson(response, Map.class);

                            if ("discovered".equals(jsonResponse.get("action"))) {
                                desktopIP = receivePacket.getAddress().getHostAddress();
                                found = true;
                                break;
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                socket.close();

                if (found && desktopIP != null) {
                    String finalIP = desktopIP;
                    mainHandler.post(() -> {
                        dismissProgressDialog();
                        connectToDesktop(finalIP, DEFAULT_PORT, code);
                    });
                } else {
                    throw new Exception("Desktop not found");
                }

            } catch (Exception e) {
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    Toast.makeText(this,
                            "Desktop not found. Check:\n" +
                                    "1. Desktop app is running\n" +
                                    "2. Correct 4-digit code\n" +
                                    "3. Firewall allows UDP 7777",
                            Toast.LENGTH_LONG).show();
                });
                e.printStackTrace();
            }
        });
    }

    private void showProgressDialog(String message) {
        mainHandler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setCancelable(false);
            progressDialog = builder.create();
            progressDialog.show();
        });
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );
        } else {
            startQRScanner();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQRScanner();
            } else {
                Toast.makeText(this, "Camera permission required for QR scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startQRScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan QR code from Desktop");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            // QR format: "192.168.1.100:7777:1234"
            String qrContent = result.getContents();
            Toast.makeText(this, "QR: " + qrContent, Toast.LENGTH_LONG).show();

            String[] parts = qrContent.split(":");
            if (parts.length == 3) {
                String ip = parts[0];
                int port = DEFAULT_PORT;
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                String code = parts[2];

                Toast.makeText(this,
                        "Parsed:\nIP: " + ip + "\nPort: " + port + "\nCode: " + code,
                        Toast.LENGTH_LONG).show();

                // Verify connection before proceeding
                verifyAndConnect(ip, port, code);
            } else {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void verifyAndConnect(String ip, int port, String code) {
        showProgressDialog("Verifying connection...");

        executorService.execute(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(3000); // 3 second timeout

                // Send a discovery message to verify the server is reachable
                Map<String, String> discoveryMsg = new HashMap<>();
                discoveryMsg.put("action", "discover");
                discoveryMsg.put("code", code);
                String message = gson.toJson(discoveryMsg);

                byte[] sendData = message.getBytes();
                InetAddress address = InetAddress.getByName(ip);
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData,
                        sendData.length,
                        address,
                        port
                );

                socket.send(sendPacket);

                // Wait for response
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                Map<String, Object> jsonResponse = gson.fromJson(response, Map.class);

                socket.close();

                if ("discovered".equals(jsonResponse.get("action"))) {
                    // Server is reachable, proceed to connect
                    mainHandler.post(() -> {
                        dismissProgressDialog();
                        connectToDesktop(ip, port, code);
                    });
                } else {
                    throw new Exception("Invalid response from server");
                }

            } catch (Exception e) {
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("timed out")) {
                        Toast.makeText(this,
                                "Cannot reach desktop at " + ip + "\n\n" +
                                        "Check:\n" +
                                        "1. Desktop app is running\n" +
                                        "2. Same WiFi network\n" +
                                        "3. Firewall allows UDP " + port,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Connection verification failed: " + errorMsg,
                                Toast.LENGTH_LONG).show();
                    }
                });
                e.printStackTrace();
            }
        });
    }

    private void connectToDesktop(String ip, int port, String code) {
        Toast.makeText(this, "Connecting to " + ip + ":" + port, Toast.LENGTH_SHORT).show();

        String deviceName = android.os.Build.MODEL;

        Intent intent = new Intent(this, ControllerActivity.class);
        intent.putExtra("IP", ip);
        intent.putExtra("PORT", port);
        intent.putExtra("CODE", code);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}