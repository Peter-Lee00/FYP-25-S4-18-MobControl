package com.example.mobcontrol;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
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

public class FlightControllerActivity extends AppCompatActivity implements SensorEventListener {

    private TextView statusText;
    private TextView pitchText, rollText;

    // Control buttons
    private Button machineGunButton, rocketButton, turboButton, pauseButton;

    // Virtual joystick for mouse control
    private JoystickView joystickView;

    // Navigation buttons
    private ImageButton calibrateButton;
    private ImageButton menuButton, homeButton, optionsButton;

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

    // Sensor related (using GAME_ROTATION_VECTOR like Racing Controller)
    private SensorManager sensorManager;
    private Sensor rotationSensor;

    // Flight control values (landscape mode based, like Racing Controller)
    private float currentRoll = 0.0f;   // Left/right tilt → A/D
    private float currentPitch = 0.0f;  // Forward/backward tilt → W/S

    // Calibration offsets
    private float rollOffset = 0.0f;
    private float pitchOffset = 0.0f;

    // Settings
    private static final float KEY_ACTIVATE_THRESHOLD = 15.0f;    // Activate key at 15°
    private static final float KEY_DEACTIVATE_THRESHOLD = 10.0f;  // Deactivate key at 10°

    // Key states for WASD
    private boolean isWPressed = false;
    private boolean isSPressed = false;
    private boolean isAPressed = false;
    private boolean isDPressed = false;

    // Rotation matrix and orientation
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];

    // Sending interval
    private static final int SEND_INTERVAL_MS = 50; // 20 Hz
    private long lastSendTime = 0;

    private boolean isCalibrating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_controller);

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
        pitchText = findViewById(R.id.pitchText);
        rollText = findViewById(R.id.rollText);

        machineGunButton = findViewById(R.id.machineGunButton);
        rocketButton = findViewById(R.id.rocketButton);
        turboButton = findViewById(R.id.turboButton);
        pauseButton = findViewById(R.id.pauseButton);

        calibrateButton = findViewById(R.id.calibrateButton);

        menuButton = findViewById(R.id.menuButton);
        homeButton = findViewById(R.id.homeButton);
        optionsButton = findViewById(R.id.optionsButton);

        // Create and add joystick
        RelativeLayout joystickContainer = findViewById(R.id.joystickContainer);
        joystickView = new JoystickView(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(400, 400);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        joystickView.setLayoutParams(params);
        joystickContainer.addView(joystickView);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();

        // Initialize sensors (GAME_ROTATION_VECTOR like Racing Controller)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);

        if (rotationSensor == null) {
            Toast.makeText(this, "Rotation sensor not available!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();
        connectToServer();

        statusText.setText("Connecting to " + serverIP + "...");
    }

    private void setupUI() {
        // Machine Gun (Left mouse)
        machineGunButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    machineGunButton.setAlpha(0.6f);
                    sendMouseButton("left", true);
                    vibrateShort();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    machineGunButton.setAlpha(1.0f);
                    sendMouseButton("left", false);
                    return true;
            }
            return false;
        });

        // Rockets (Right mouse)
        rocketButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    rocketButton.setAlpha(0.6f);
                    sendMouseButton("right", true);
                    vibrateShort();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    rocketButton.setAlpha(1.0f);
                    sendMouseButton("right", false);
                    return true;
            }
            return false;
        });

        // Turbo (Shift)
        turboButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    turboButton.setAlpha(0.6f);
                    sendKeyPress("shift", true);
                    vibrateLong();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    turboButton.setAlpha(1.0f);
                    sendKeyPress("shift", false);
                    return true;
            }
            return false;
        });

        // Pause (P key)
        pauseButton.setOnClickListener(v -> {
            pauseButton.setAlpha(0.6f);
            pauseButton.postDelayed(() -> pauseButton.setAlpha(1.0f), 100);
            sendKeyPress("p", true);
            new Handler().postDelayed(() -> sendKeyPress("p", false), 100);
            vibrateShort();
            Toast.makeText(this, "Pause", Toast.LENGTH_SHORT).show();
        });

        // Calibrate
        calibrateButton.setOnClickListener(v -> calibrateSensors());

        // App navigation
        setupAppFunctionButton(menuButton, this::openLayoutsMenu);
        setupAppFunctionButton(homeButton, this::goToHome);
        setupAppFunctionButton(optionsButton, this::openOptions);
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

    private void calibrateSensors() {
        isCalibrating = true;
        Toast.makeText(this, "Calibrating...", Toast.LENGTH_SHORT).show();

        calibrateButton.setEnabled(false);
        calibrateButton.setAlpha(0.5f);

        new Handler().postDelayed(() -> {
            rollOffset = currentRoll;
            pitchOffset = currentPitch;
            isCalibrating = false;

            calibrateButton.setEnabled(true);
            calibrateButton.setAlpha(1.0f);

            Toast.makeText(this, "✓ Calibrated!", Toast.LENGTH_SHORT).show();
            vibrateLong();
        }, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // Get rotation matrix from rotation vector
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Get orientation angles (azimuth, pitch, roll)
            SensorManager.getOrientation(rotationMatrix, orientation);

            // Convert to degrees
            // orientation[1] = pitch (forward/back)
            // orientation[2] = roll (left/right)
            float rawPitch = (float) Math.toDegrees(orientation[1]);
            float rawRoll = (float) Math.toDegrees(orientation[2]);

            // For landscape mode: add 90 degrees to roll to make horizontal = 0
            rawRoll += 90.0f;

            // Normalize roll to -180 ~ +180
            if (rawRoll > 180) {
                rawRoll -= 360;
            } else if (rawRoll < -180) {
                rawRoll += 360;
            }

            // Apply calibration offsets
            currentRoll = rawRoll - rollOffset;
            currentPitch = rawPitch - pitchOffset;

            // Normalize after offset
            if (currentRoll > 180) {
                currentRoll -= 360;
            } else if (currentRoll < -180) {
                currentRoll += 360;
            }

            // Update WASD key states based on tilt
            updateKeyStates();

            // Update UI
            updateSensorDisplay();

            // Send data at fixed interval
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSendTime >= SEND_INTERVAL_MS) {
                sendGyroData();
                lastSendTime = currentTime;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateKeyStates() {
        // W key - forward tilt (use hysteresis)
        if (!isWPressed) {
            // Not pressed - check if we should activate
            if (currentPitch < -KEY_ACTIVATE_THRESHOLD) {
                isWPressed = true;
                sendKeyPress("w", true);
            }
        } else {
            // Already pressed - check if we should deactivate (use lower threshold)
            if (currentPitch > -KEY_DEACTIVATE_THRESHOLD) {
                isWPressed = false;
                sendKeyPress("w", false);
            }
        }

        // S key - backward tilt
        if (!isSPressed) {
            if (currentPitch > KEY_ACTIVATE_THRESHOLD) {
                isSPressed = true;
                sendKeyPress("s", true);
            }
        } else {
            if (currentPitch < KEY_DEACTIVATE_THRESHOLD) {
                isSPressed = false;
                sendKeyPress("s", false);
            }
        }

        // A key - left tilt
        if (!isAPressed) {
            if (currentRoll < -KEY_ACTIVATE_THRESHOLD) {
                isAPressed = true;
                sendKeyPress("a", true);
            }
        } else {
            if (currentRoll > -KEY_DEACTIVATE_THRESHOLD) {
                isAPressed = false;
                sendKeyPress("a", false);
            }
        }

        // D key - right tilt
        if (!isDPressed) {
            if (currentRoll > KEY_ACTIVATE_THRESHOLD) {
                isDPressed = true;
                sendKeyPress("d", true);
            }
        } else {
            if (currentRoll < KEY_DEACTIVATE_THRESHOLD) {
                isDPressed = false;
                sendKeyPress("d", false);
            }
        }
    }

    private void updateSensorDisplay() {
        mainHandler.post(() -> {
            pitchText.setText(String.format("Pitch: %.1f°", currentPitch));
            rollText.setText(String.format("Roll: %.1f°", currentRoll));

            // Change color if tilted significantly
            if (Math.abs(currentPitch) > 30 || Math.abs(currentRoll) > 30) {
                pitchText.setTextColor(Color.parseColor("#FF5722"));
                rollText.setTextColor(Color.parseColor("#FF5722"));
            } else {
                pitchText.setTextColor(Color.WHITE);
                rollText.setTextColor(Color.WHITE);
            }
        });
    }

    private void sendGyroData() {
        if (!isConnected || isCalibrating) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "gyro_data");
                message.put("pitch", currentPitch);
                message.put("roll", currentRoll);

                sendMessage(gson.toJson(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendKeyPress(String key, boolean pressed) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "key");
                message.put("key", key);
                message.put("pressed", pressed);

                sendMessage(gson.toJson(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMouseButton(String button, boolean pressed) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "mouse_button");
                message.put("button", button);
                message.put("pressed", pressed);

                sendMessage(gson.toJson(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void connectToServer() {
        executorService.execute(() -> {
            try {
                udpSocket = new DatagramSocket();
                isConnected = true;

                mainHandler.post(() -> {
                    statusText.setText("✈️ Space Fighter Ready");
                    statusText.setTextColor(Color.parseColor("#4CAF50"));
                    Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
                    vibrateLong();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("Connection failed");
                    statusText.setTextColor(Color.parseColor("#F44336"));
                });
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

    private void openLayoutsMenu() {
        Intent intent = new Intent(FlightControllerActivity.this, LayoutSelectionActivity.class);
        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            finish();
        }
    }

    private void goToHome() {
        Intent intent = new Intent(FlightControllerActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void openOptions() {
        Intent intent = new Intent(FlightControllerActivity.this, OptionsActivity.class);
        startActivity(intent);
    }

    private void vibrateShort() {
        if (!preferences.getBoolean("vibration_enabled", true)) return;

        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(30);
            }
        }
    }

    private void vibrateLong() {
        if (!preferences.getBoolean("vibration_enabled", true)) return;

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

        // Release all keys
        if (isWPressed) sendKeyPress("w", false);
        if (isSPressed) sendKeyPress("s", false);
        if (isAPressed) sendKeyPress("a", false);
        if (isDPressed) sendKeyPress("d", false);

        sensorManager.unregisterListener(this);
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // Custom Joystick View for mouse control
    private class JoystickView extends View {
        private Paint basePaint, handlePaint, arrowPaint;
        private float centerX, centerY;
        private float handleX, handleY;
        private float baseRadius = 150f;
        private float handleRadius = 50f;
        private boolean isTouching = false;

        private Handler sendHandler = new Handler();
        private Runnable sendRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTouching) {
                    sendMouseMovement();
                    sendHandler.postDelayed(this, SEND_INTERVAL_MS);
                }
            }
        };

        public JoystickView(Context context) {
            super(context);

            basePaint = new Paint();
            basePaint.setColor(Color.parseColor("#424242"));
            basePaint.setStyle(Paint.Style.FILL);
            basePaint.setAlpha(180);

            handlePaint = new Paint();
            handlePaint.setColor(Color.parseColor("#2196F3"));
            handlePaint.setStyle(Paint.Style.FILL);
            handlePaint.setAntiAlias(true);

            arrowPaint = new Paint();
            arrowPaint.setColor(Color.parseColor("#90CAF9"));
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(3f);
            arrowPaint.setAntiAlias(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            centerX = w / 2f;
            centerY = h / 2f;
            handleX = centerX;
            handleY = centerY;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            // Draw base circle
            canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

            // Draw crosshair
            canvas.drawLine(centerX - 20, centerY, centerX + 20, centerY, arrowPaint);
            canvas.drawLine(centerX, centerY - 20, centerX, centerY + 20, arrowPaint);

            // Draw handle
            canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    isTouching = true;
                    updateHandle(event.getX(), event.getY());

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        sendHandler.post(sendRunnable);
                        vibrateShort();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isTouching = false;
                    sendHandler.removeCallbacks(sendRunnable);
                    resetHandle();
                    sendMouseMovement(); // Send center position
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateHandle(float x, float y) {
            float dx = x - centerX;
            float dy = y - centerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance < baseRadius - handleRadius) {
                handleX = x;
                handleY = y;
            } else {
                float angle = (float) Math.atan2(dy, dx);
                handleX = centerX + (float) Math.cos(angle) * (baseRadius - handleRadius);
                handleY = centerY + (float) Math.sin(angle) * (baseRadius - handleRadius);
            }

            invalidate();
        }

        private void resetHandle() {
            handleX = centerX;
            handleY = centerY;
            invalidate();
        }

        private void sendMouseMovement() {
            if (!isConnected) return;

            // Calculate relative position (-1.0 to 1.0)
            float relativeX = (handleX - centerX) / (baseRadius - handleRadius);
            float relativeY = (handleY - centerY) / (baseRadius - handleRadius);

            // Convert to mouse movement with reduced sensitivity (300 instead of 500)
            int mouseX = Math.round(relativeX * 300);
            int mouseY = Math.round(relativeY * 300);

            executorService.execute(() -> {
                try {
                    Map<String, Object> message = new HashMap<>();
                    message.put("action", "mouse_move");
                    message.put("x", mouseX);
                    message.put("y", mouseY);

                    sendMessage(gson.toJson(message));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
}