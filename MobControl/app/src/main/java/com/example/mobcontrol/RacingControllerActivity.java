package com.example.mobcontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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

public class RacingControllerActivity extends AppCompatActivity implements SensorEventListener {

    private TextView statusText;
    private TextView gyroStatusText;
    private TextView gyroAngleText;
    private TextView leftArrow;
    private TextView rightArrow;
    private View leftGyroBar;
    private View rightGyroBar;
    private Button brakeButton;
    private Button acceleratorButton;
    private Button ltButton;
    private Button rtButton;
    private ImageButton menuButton;
    private ImageButton homeButton;
    private ImageButton optionsButton;

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

    // Gyroscope
    private SensorManager sensorManager;
    private Sensor gyroscope;
    private boolean gyroAvailable = false;

    // Gyro settings
    private static final float DEADZONE = 0.5f;  // 초민감 데드존 (0.5도)
    private static final float MAX_ANGLE = 45.0f;  // 최대 인식 각도
    private static final float LANDSCAPE_OFFSET = -90.0f;  // 가로 모드 기준점
    private float currentRoll = 0.0f;  // 현재 좌우 기울기 (보정된 값)
    private String lastSteeringState = "center";  // "left", "center", "right"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load racing controller layout
        setContentView(R.layout.activity_racing_controller);

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
        gyroStatusText = findViewById(R.id.gyroStatusText);
        gyroAngleText = findViewById(R.id.gyroAngleText);
        leftArrow = findViewById(R.id.leftArrow);
        rightArrow = findViewById(R.id.rightArrow);
        leftGyroBar = findViewById(R.id.leftGyroBar);
        rightGyroBar = findViewById(R.id.rightGyroBar);
        brakeButton = findViewById(R.id.brakeButton);
        acceleratorButton = findViewById(R.id.acceleratorButton);
        ltButton = findViewById(R.id.ltButton);
        rtButton = findViewById(R.id.rtButton);
        menuButton = findViewById(R.id.menuButton);
        homeButton = findViewById(R.id.homeButton);
        optionsButton = findViewById(R.id.optionsButton);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();

        statusText.setText("Connecting to " + serverIP + "...");

        // Setup buttons based on visibility
        if (brakeButton.getVisibility() == View.VISIBLE) {
            // Racing layout: BRAKE/GAS buttons use up/down mappings
            setupButton(brakeButton, "down");  // ✅ Brake = down → S
            setupButton(acceleratorButton, "up");  // ✅ Gas = up → W
        }

        if (ltButton.getVisibility() == View.VISIBLE) {
            // Legacy layout (if exists)
            setupButton(ltButton, "up");  // LT = up → W
            setupButton(rtButton, "down");  // RT = down → S
        }

        // Setup center buttons
        setupImageButton(menuButton, this::openLayoutsMenu);
        setupImageButton(homeButton, this::goToHome);
        setupImageButton(optionsButton, this::openOptions);

        // Initialize Gyroscope
        initializeGyroscope();

        // Connect to server
        connectToServer();
    }

    private void initializeGyroscope() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (sensorManager != null) {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);

            if (gyroscope != null) {
                gyroAvailable = true;
                gyroStatusText.setText("GYRO ON");
                gyroStatusText.setTextColor(Color.parseColor("#4CAF50"));
                Toast.makeText(this, "Gyroscope enabled! Tilt to steer.", Toast.LENGTH_LONG).show();
            } else {
                gyroAvailable = false;
                gyroStatusText.setText("GYRO OFF");
                gyroStatusText.setTextColor(Color.parseColor("#F44336"));
                Toast.makeText(this, "Gyroscope not available on this device", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gyroAvailable && gyroscope != null) {
            // SENSOR_DELAY_FASTEST for maximum responsiveness
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gyroAvailable && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // Convert rotation vector to orientation
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Remap coordinate system for landscape mode
            float[] remappedMatrix = new float[9];
            SensorManager.remapCoordinateSystem(rotationMatrix,
                    SensorManager.AXIS_Y,        // X축을 Y축으로
                    SensorManager.AXIS_MINUS_X,  // Y축을 -X축으로
                    remappedMatrix);

            float[] orientation = new float[3];
            SensorManager.getOrientation(remappedMatrix, orientation);

            // orientation[2] = Roll (좌우 기울기)
            // orientation[1] = Pitch (위아래 기울기) - 무시!
            currentRoll = (float) Math.toDegrees(orientation[2]);

            // -180도 ~ +180도 범위로 정규화
            if (currentRoll > 180) {
                currentRoll -= 360;
            } else if (currentRoll < -180) {
                currentRoll += 360;
            }

            // Debug log
            if (Math.abs(currentRoll) > 0.5f) {
                float pitch = (float) Math.toDegrees(orientation[1]);
                android.util.Log.d("RacingSensor",
                        String.format("Roll: %.1f° | Pitch: %.1f° (ignored)", currentRoll, pitch));
            }

            // Update UI (Roll만 사용)
            updateGyroUI();

            // Process steering input (Roll만 사용)
            processSteeringInput();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    private void updateGyroUI() {
        runOnUiThread(() -> {
            // Update angle display - 절댓값으로 표시
            gyroAngleText.setText(String.format("%.1f°", Math.abs(currentRoll)));

            // Update gyro bars based on tilt
            android.view.ViewGroup.LayoutParams leftParams = leftGyroBar.getLayoutParams();
            android.view.ViewGroup.LayoutParams rightParams = rightGyroBar.getLayoutParams();

            // Get screen width
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxBarWidth = screenWidth / 2 - 1; // Half screen minus divider

            if (currentRoll < -DEADZONE) {
                // Left tilt - 왼쪽 바 증가
                float intensity = Math.min(Math.abs(currentRoll) / MAX_ANGLE, 1.0f);
                leftParams.width = (int)(maxBarWidth * intensity);
                rightParams.width = 0;

            } else if (currentRoll > DEADZONE) {
                // Right tilt - 오른쪽 바 증가
                float intensity = Math.min(Math.abs(currentRoll) / MAX_ANGLE, 1.0f);
                rightParams.width = (int)(maxBarWidth * intensity);
                leftParams.width = 0;

            } else {
                // Center - no bars
                leftParams.width = 0;
                rightParams.width = 0;
            }

            leftGyroBar.setLayoutParams(leftParams);
            rightGyroBar.setLayoutParams(rightParams);

            // Legacy arrow update (if visible)
            if (leftArrow.getVisibility() == View.VISIBLE) {
                if (currentRoll < -DEADZONE) {
                    float intensity = Math.min(Math.abs(currentRoll) / MAX_ANGLE, 1.0f);
                    int alpha = (int) (255 * intensity);
                    leftArrow.setTextColor(Color.argb(alpha, 76, 175, 80));
                    rightArrow.setTextColor(Color.parseColor("#333333"));
                } else if (currentRoll > DEADZONE) {
                    float intensity = Math.min(Math.abs(currentRoll) / MAX_ANGLE, 1.0f);
                    int alpha = (int) (255 * intensity);
                    rightArrow.setTextColor(Color.argb(alpha, 76, 175, 80));
                    leftArrow.setTextColor(Color.parseColor("#333333"));
                } else {
                    leftArrow.setTextColor(Color.parseColor("#333333"));
                    rightArrow.setTextColor(Color.parseColor("#333333"));
                }
            }
        });
    }

    private void processSteeringInput() {
        String newState;

        if (currentRoll < -DEADZONE) {
            // Tilted left - use "left" mapping (A key)
            newState = "left";
            if (!lastSteeringState.equals("left")) {
                android.util.Log.d("RacingGyro", "LEFT DOWN - Roll: " + currentRoll);
                sendInput("left_down");  // ✅ buttonMappings["left"] → A
                if (lastSteeringState.equals("right")) {
                    sendInput("right_up");
                }
            }

        } else if (currentRoll > DEADZONE) {
            // Tilted right - use "right" mapping (D key)
            newState = "right";
            if (!lastSteeringState.equals("right")) {
                android.util.Log.d("RacingGyro", "RIGHT DOWN - Roll: " + currentRoll);
                sendInput("right_down");  // ✅ buttonMappings["right"] → D
                if (lastSteeringState.equals("left")) {
                    sendInput("left_up");
                }
            }

        } else {
            // Center - release both
            newState = "center";
            if (!lastSteeringState.equals("center")) {
                android.util.Log.d("RacingGyro", "CENTER - Roll: " + currentRoll);
                if (lastSteeringState.equals("left")) {
                    sendInput("left_up");
                } else if (lastSteeringState.equals("right")) {
                    sendInput("right_up");
                }
            }
        }

        lastSteeringState = newState;
    }

    private void setupButton(Button button, String action) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    android.util.Log.d("RacingButton", action.toUpperCase() + " DOWN");
                    button.setAlpha(0.7f);
                    button.setScaleX(0.95f);
                    button.setScaleY(0.95f);
                    sendInput(action + "_down");
                    vibrateShort();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    android.util.Log.d("RacingButton", action.toUpperCase() + " UP");
                    button.setAlpha(1.0f);
                    button.setScaleX(1.0f);
                    button.setScaleY(1.0f);
                    sendInput(action + "_up");
                    return true;
            }
            return false;
        });
    }

    private void setupImageButton(ImageButton button, Runnable function) {
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
        Intent intent = new Intent(RacingControllerActivity.this, LayoutSelectionActivity.class);
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
            // 새 레이아웃이 선택되었으므로 현재 Activity 종료
            finish();
        }
    }

    private void openOptions() {
        Intent intent = new Intent(RacingControllerActivity.this, OptionsActivity.class);
        startActivity(intent);
    }

    private void goToHome() {
        Intent intent = new Intent(RacingControllerActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void connectToServer() {
        executorService.execute(() -> {
            try {
                udpSocket = new DatagramSocket();

                // 이미 ControllerActivity가 pairing을 완료했으므로
                // 바로 연결된 상태로 시작
                isConnected = true;

                mainHandler.post(() -> {
                    statusText.setText("Connected - Racing Mode");
                    statusText.setTextColor(Color.parseColor("#4CAF50"));
                    Toast.makeText(this, "Racing controller ready!", Toast.LENGTH_SHORT).show();
                    vibrateLong();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("Connection failed");
                    statusText.setTextColor(Color.parseColor("#F44336"));
                    Toast.makeText(this, "Failed to connect: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                e.printStackTrace();
            }
        });
    }

    private void sendInput(String action) {
        if (!isConnected) {
            return;
        }

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "input");  // ✅ "input" action
                message.put("input", action);    // ✅ 실제 키 입력
                message.put("device", deviceName);
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

        // Release all keys before closing
        if (isConnected) {
            sendInput("a_up");
            sendInput("d_up");
            sendInput("w_up");
            sendInput("s_up");
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}