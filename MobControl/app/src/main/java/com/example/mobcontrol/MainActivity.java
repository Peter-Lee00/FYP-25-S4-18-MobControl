package com.example.mobcontrol;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
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

    // ==========================================
    // NEW UDP DISCOVERY SYSTEM
    // ==========================================

    private void searchForDesktop(String code) {
        showProgressDialog("Searching for Desktop...");

        executorService.execute(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(2000); // 2 seconds per attempt

                // Discovery message
                Map<String, String> msg = new HashMap<>();
                msg.put("action", "discover");
                msg.put("code", code);
                String json = gson.toJson(msg);
                byte[] sendData = json.getBytes();

                android.util.Log.d("UDP", "=== NEW UDP DISCOVERY ===");
                android.util.Log.d("UDP", "Code: " + code);
                android.util.Log.d("UDP", "Message: " + json);

                // Calculate local network broadcast
                String localBroadcast = calculateBroadcastAddress();
                android.util.Log.d("UDP", "Local broadcast: " + localBroadcast);

                // Try both local and global broadcast
                String[] broadcasts = {
                        localBroadcast,
                        "255.255.255.255"
                };

                String foundIP = null;

                for (String broadcast : broadcasts) {
                    if (broadcast == null) continue;

                    try {
                        android.util.Log.d("UDP", "Trying: " + broadcast + ":" + DEFAULT_PORT);

                        InetAddress addr = InetAddress.getByName(broadcast);
                        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, addr, DEFAULT_PORT);

                        socket.send(packet);
                        android.util.Log.d("UDP", "✓ Sent to " + broadcast);

                        // Wait for response
                        byte[] recvData = new byte[1024];
                        DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);

                        socket.receive(recvPacket);

                        String response = new String(recvPacket.getData(), 0, recvPacket.getLength());
                        android.util.Log.d("UDP", "✓ Response: " + response);

                        Map<String, Object> respJson = gson.fromJson(response, Map.class);

                        // Accept both "discovered" and "found"
                        if ("discovered".equals(respJson.get("action")) ||
                                "found".equals(respJson.get("status"))) {
                            foundIP = recvPacket.getAddress().getHostAddress();
                            android.util.Log.d("UDP", "✓✓✓ FOUND: " + foundIP);
                            break;
                        }

                    } catch (Exception e) {
                        android.util.Log.w("UDP", broadcast + " - " + e.getMessage());
                    }
                }

                final String desktopIP = foundIP;

                mainHandler.post(() -> {
                    dismissProgressDialog();

                    if (desktopIP != null) {
                        Toast.makeText(this, "✓ Connected to " + desktopIP, Toast.LENGTH_SHORT).show();
                        connectToDesktop(desktopIP, DEFAULT_PORT, code);
                    } else {
                        showError();
                    }
                });

            } catch (Exception e) {
                android.util.Log.e("UDP", "ERROR: " + e.getMessage());
                e.printStackTrace();

                mainHandler.post(() -> {
                    dismissProgressDialog();
                    showError();
                });

            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        });
    }

    private String calculateBroadcastAddress() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                int ipInt = wifi.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    String ip = Formatter.formatIpAddress(ipInt);
                    android.util.Log.d("UDP", "My IP: " + ip);

                    // Calculate .255 broadcast
                    String[] parts = ip.split("\\.");
                    if (parts.length == 4) {
                        return parts[0] + "." + parts[1] + "." + parts[2] + ".255";
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("UDP", "Broadcast calc error: " + e.getMessage());
        }
        return null;
    }

    private void showError() {
        Toast.makeText(this,
                "Desktop not found\n\n" +
                        "Check:\n" +
                        "• Desktop app running?\n" +
                        "• Same WiFi network?\n" +
                        "• Correct code?\n" +
                        "• Firewall off?",
                Toast.LENGTH_LONG).show();
    }

    // ==========================================
    // QR CODE SCANNER
    // ==========================================

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
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
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
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
            // QR format: "IP:PORT:CODE"
            String qr = result.getContents();
            android.util.Log.d("QR", "Scanned: " + qr);

            String[] parts = qr.split(":");

            if (parts.length == 3) {
                try {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    String code = parts[2];

                    android.util.Log.d("QR", "IP: " + ip + ", Port: " + port + ", Code: " + code);
                    Toast.makeText(this, "QR: " + ip + ":" + port, Toast.LENGTH_SHORT).show();

                    // Direct connect
                    connectToDesktop(ip, port, code);

                } catch (Exception e) {
                    Toast.makeText(this, "Invalid QR format", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // ==========================================
    // CONNECT TO DESKTOP
    // ==========================================

    private void connectToDesktop(String ip, int port, String code) {
        android.util.Log.d("UDP", "Connecting to: " + ip + ":" + port);
        Toast.makeText(this, "Connected! Choose layout", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LayoutSelectionActivity.class);
        intent.putExtra("IP", ip);
        intent.putExtra("PORT", port);
        intent.putExtra("CODE", code);
        intent.putExtra("DEVICE_NAME", android.os.Build.MODEL);
        startActivity(intent);
        finish();
    }

    // ==========================================
    // HELPERS
    // ==========================================

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}