package com.example.mobcontrol;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
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

public class ControllerActivity extends AppCompatActivity {

    private TextView statusText;

    // D-Pad buttons
    private ImageButton upButton, downButton, leftButton, rightButton;

    //Joystick
    private View joystick;
    private View joystickHandle;

    // Right Joystick (for mouse)
    private View rightJoystick;
    private View rightJoystickHandle;

    // Action buttons (ABXY)
    private Button actionAButton, actionBButton, actionXButton, actionYButton;

    // Shoulder and trigger buttons
    private Button lbButton, rbButton, ltButton, rtButton;

    // Center buttons
    private ImageButton menuButton, homeButton, optionsButton;

    // Settings button
    private ImageButton settingsButton;

    private String serverIP;
    private int serverPort;
    private String pairingCode;
    private String deviceName;

    private DatagramSocket udpSocket;
    private boolean isConnected = false;
    private Vibrator vibrator;
    private SharedPreferences preferences;

    private ExecutorService executorService;
    private Handler mainHandler;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_controller);

        serverIP = getIntent().getStringExtra("IP");
        serverPort = getIntent().getIntExtra("PORT", 7777);
        pairingCode = getIntent().getStringExtra("CODE");
        deviceName = getIntent().getStringExtra("DEVICE_NAME");

        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = android.os.Build.MODEL;
        }

        preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);

        // Initialize views
        statusText = findViewById(R.id.statusText);

        // D-Pad
        upButton = findViewById(R.id.upButton);
        downButton = findViewById(R.id.downButton);
        leftButton = findViewById(R.id.leftButton);
        rightButton = findViewById(R.id.rightButton);

        //Joystick
        joystick = findViewById(R.id.joystick);
        joystickHandle = joystick.findViewById(R.id.joystick_handle);
        joystick.setOnTouchListener((v, event) -> {
            float centerX = v.getWidth() / 2f;
            float centerY = v.getHeight() / 2f;

            float deltaX = event.getX() - centerX;
            float deltaY = event.getY() - centerY;

            float radius = v.getWidth() / 2f; // limit handle movement

            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (distance > radius) {
                deltaX = deltaX / distance * radius;
                deltaY = deltaY / distance * radius;
            }

            // Move the handle
            joystickHandle.setX(centerX + deltaX - joystickHandle.getWidth() / 2f);
            joystickHandle.setY(centerY + deltaY - joystickHandle.getHeight() / 2f);

            // Normalize values for sending
            float x = deltaX / radius;
            float y = -deltaY / radius;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    sendJoystickInput(x, y); // send values to desktop
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    joystickHandle.setX(centerX - joystickHandle.getWidth() / 2f);
                    joystickHandle.setY(centerY - joystickHandle.getHeight() / 2f);
                    sendJoystickInput(0f, 0f); // reset
                    break;
            }

            return true;
        });

        // Right Joystick for mouse
        rightJoystick = findViewById(R.id.right_joystick);
        rightJoystickHandle = rightJoystick.findViewById(R.id.right_joystick_handle);
        rightJoystick.setOnTouchListener((v, event) -> {
            float centerX = v.getWidth() / 2f;
            float centerY = v.getHeight() / 2f;

            float deltaX = event.getX() - centerX;
            float deltaY = event.getY() - centerY;

            float radius = v.getWidth() / 2f;

            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (distance > radius) {
                deltaX = deltaX / distance * radius;
                deltaY = deltaY / distance * radius;
            }

            rightJoystickHandle.setX(centerX + deltaX - rightJoystickHandle.getWidth() / 2f);
            rightJoystickHandle.setY(centerY + deltaY - rightJoystickHandle.getHeight() / 2f);

            float x = deltaX / radius;
            float y = -deltaY / radius;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    sendMouseInput(x, y);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    rightJoystickHandle.setX(centerX - rightJoystickHandle.getWidth() / 2f);
                    rightJoystickHandle.setY(centerY - rightJoystickHandle.getHeight() / 2f);
                    sendMouseInput(0f, 0f);
                    break;
            }

            return true;
        });

        // Action buttons
        actionAButton = findViewById(R.id.actionAButton);
        actionBButton = findViewById(R.id.actionBButton);
        actionXButton = findViewById(R.id.actionXButton);
        actionYButton = findViewById(R.id.actionYButton);

        // Shoulder and trigger buttons
        lbButton = findViewById(R.id.lbButton);
        rbButton = findViewById(R.id.rbButton);
        ltButton = findViewById(R.id.ltButton);
        rtButton = findViewById(R.id.rtButton);

        // Center buttons
        menuButton = findViewById(R.id.menuButton);
        homeButton = findViewById(R.id.homeButton);
        optionsButton = findViewById(R.id.optionsButton);

        // Settings button
        settingsButton = findViewById(R.id.settingsButton);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();

        statusText.setText("Connecting to " + serverIP + "...");

        // Setup D-Pad buttons
        setupImageButton(upButton, "up");
        setupImageButton(downButton, "down");
        setupImageButton(leftButton, "left");
        setupImageButton(rightButton, "right");

        // Setup Action buttons
        setupButton(actionAButton, "a");
        setupButton(actionBButton, "b");
        setupButton(actionXButton, "x");
        setupButton(actionYButton, "y");

        // Setup Shoulder buttons
        setupButton(lbButton, "lb");
        setupButton(rbButton, "rb");
        setupButton(ltButton, "lt");
        setupButton(rtButton, "rt");

        // Setup Center buttons - APP FUNCTIONS
        setupAppFunctionButton(menuButton, this::openLayoutsMenu);
        setupAppFunctionButton(homeButton, this::goToHome);
        setupAppFunctionButton(optionsButton, this::openOptions);

        // Setup Settings button
        setupImageButton(settingsButton, "settings");

        connectToServer();
    }

    private void sendJoystickInput(float x, float y) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "joystick");
                message.put("x", x);
                message.put("y", y);
                String jsonMessage = gson.toJson(message);
                sendMessage(jsonMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMouseInput(float x, float y) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "mouse");
                message.put("x", x);
                message.put("y", y);
                String jsonMessage = gson.toJson(message);
                sendMessage(jsonMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupButton(Button button, String action) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    button.setAlpha(0.6f);
                    button.setScaleX(0.95f);
                    button.setScaleY(0.95f);
                    sendInput(action + "_down"); // Send key down event
                    vibrateShort();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    button.setAlpha(1.0f);
                    button.setScaleX(1.0f);
                    button.setScaleY(1.0f);
                    sendInput(action + "_up"); // Send key up event
                    return true;
            }
            return false;
        });
    }

    private void setupImageButton(ImageButton button, String action) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    button.setAlpha(0.6f);
                    button.setScaleX(0.9f);
                    button.setScaleY(0.9f);
                    sendInput(action + "_down"); // Send key down event
                    vibrateShort();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    button.setAlpha(1.0f);
                    button.setScaleX(1.0f);
                    button.setScaleY(1.0f);
                    sendInput(action + "_up"); // Send key up event
                    return true;
            }
            return false;
        });
    }

    private void setupAppFunctionButton(ImageButton button, Runnable function) {
        button.setOnClickListener(v -> {
            button.setAlpha(0.6f);
            button.setScaleX(0.9f);
            button.setScaleY(0.9f);
            vibrateShort();

            new Handler().postDelayed(() -> {
                button.setAlpha(1.0f);
                button.setScaleX(1.0f);
                button.setScaleY(1.0f);
            }, 100);

            function.run();
        });
    }

    private void openLayoutsMenu() {
        Intent intent = new Intent(ControllerActivity.this, LayoutSelectionActivity.class);
        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivityForResult(intent, 100);  // Result를 받도록 변경
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // 새 레이아웃이 선택되었으므로 현재 Activity 종료
            finish();
        }
    }

    private void goToHome() {
        Intent intent = new Intent(ControllerActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void openOptions() {
        Intent intent = new Intent(ControllerActivity.this, OptionsActivity.class);
        startActivity(intent);
    }

    private void connectToServer() {
        executorService.execute(() -> {
            try {
                udpSocket = new DatagramSocket();

                isConnected = true;

                mainHandler.post(() -> {
                    statusText.setText("Connected to " + serverIP);
                    statusText.setTextColor(Color.parseColor("#4CAF50"));
                    Toast.makeText(this, "Controller connected!", Toast.LENGTH_SHORT).show();
                    vibrateLong();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("Connection failed: " + e.getMessage());
                    statusText.setTextColor(Color.parseColor("#F44336"));
                    Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            }
        });
    }

    private void sendInput(String action) {
        if (!isConnected) {
            mainHandler.post(() ->
                    Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        executorService.execute(() -> {
            try {
                Map<String, String> message = new HashMap<>();
                message.put("action", action);
                String jsonMessage = gson.toJson(message);
                sendMessage(jsonMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMessage(String message) {
        try {
            byte[] data = message.getBytes();
            InetAddress address = InetAddress.getByName(serverIP);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, serverPort);
            udpSocket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibrateShort() {
        if (!preferences.getBoolean("vibration_enabled", true)) {
            return;
        }

        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(30);
            }
        }
    }

    private void vibrateLong() {
        if (!preferences.getBoolean("vibration_enabled", true)) {
            return;
        }

        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
