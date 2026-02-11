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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

public class UniversalControllerActivity extends AppCompatActivity {

    private RelativeLayout controllerContainer;
    private TextView statusText;

    private String serverIP;
    private int serverPort;
    private String pairingCode;
    private String deviceName;
    private String layoutName;

    private DatagramSocket udpSocket;
    private boolean isConnected = false;
    private ExecutorService executorService;
    private Handler mainHandler;
    private Gson gson;

    private Map<String, View> activeButtons = new HashMap<>();

    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private boolean gyroEnabled = false;

    private LayoutData currentLayout;

    // PWM for Racing
    private Handler pwmHandler = new Handler(Looper.getMainLooper());
    private Runnable pwmRunnable;
    private boolean isPWMActive = false;
    private boolean isLeftPressed = false;
    private boolean isRightPressed = false;
    private float currentRoll = 0.0f;
    private float currentPitch = 0.0f;
    private static final float GYRO_DEADZONE = 8.0f;

    private static final float MAX_ANGLE = 30.0f;
    private static final int PWM_CYCLE_MS = 100;

    // key status for flight
    private boolean isWPressed = false;
    private boolean isSPressed = false;
    private boolean isAPressed = false;
    private boolean isDPressed = false;
    private static final float KEY_ACTIVATE_THRESHOLD = 15.0f;
    private static final float KEY_DEACTIVATE_THRESHOLD = 10.0f;

    // Calibration
    private float rollOffset = 0.0f;
    private float pitchOffset = 0.0f;

    // For gyro bar
    private View leftGyroBar;
    private View rightGyroBar;
    private TextView gyroAngleText;
    private TextView gyroStatusText;
    private RelativeLayout gyroBarContainer;

    // For Mouse
    private float lastTouchX, lastTouchY;
    private boolean isMouseDragging = false;
    // Smooth mouse movement
    private float velocityX = 0;
    private float velocityY = 0;
    private static final float ACCELERATION = 0.3f;
    private static final float MAX_SPEED = 2.0f;
    private static final float MOUSE_DEADZONE = 0.1f;

    private Handler mouseHandler = new Handler();
    private static final int MOUSE_INTERVAL_MS = 16;  // 60fps
    private boolean isMouseActive = false;

    private LinearLayout centerContainer;
    private RelativeLayout  calibrateButton;

    // Vibration
    private android.os.Vibrator vibrator;
    private android.content.SharedPreferences preferences;

    private Handler heartbeatHandler = new Handler();
    private static final int HEARTBEAT_INTERVAL_MS = 3000; // 3seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_universal_controller);
        // reset gyro bar
        gyroBarContainer = findViewById(R.id.gyroBarContainer);
        leftGyroBar = findViewById(R.id.leftGyroBar);
        rightGyroBar = findViewById(R.id.rightGyroBar);
        gyroAngleText = findViewById(R.id.gyroAngleText);
        gyroStatusText = findViewById(R.id.gyroStatusText);

        serverIP = getIntent().getStringExtra("IP");
        serverPort = getIntent().getIntExtra("PORT", 7777);
        pairingCode = getIntent().getStringExtra("CODE");
        deviceName = getIntent().getStringExtra("DEVICE_NAME");
        layoutName = getIntent().getStringExtra("LAYOUT_NAME");

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();

        controllerContainer = findViewById(R.id.controllerContainer);
        statusText = findViewById(R.id.statusText);
        centerContainer = findViewById(R.id.centerContainer);
        calibrateButton = findViewById(R.id.calibrateButton);


        connectToServer();

        // Menu button
        RelativeLayout  menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> showMenu());

        // Load layout
        currentLayout = loadLayout(layoutName);

        // Vibration reset
        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);

        // Gyro Reset
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);

        if (currentLayout != null && currentLayout.gyroEnabled && gyroSensor != null) {
            gyroEnabled = true;
            gyroBarContainer.setVisibility(View.VISIBLE);
            gyroStatusText.setText("GYRO ON");
            gyroStatusText.setTextColor(Color.parseColor("#4CAF50"));

            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);

            if ("Racing".equals(currentLayout.controllerType)) {
                startPWMControl();
                centerContainer.setVisibility(View.VISIBLE);
            } else if ("Flight Simulator".equals(currentLayout.controllerType)) {
                calibrateButton.setVisibility(View.VISIBLE);
                calibrateButton.setOnClickListener(v -> calibrateGyro());
                centerContainer.setVisibility(View.VISIBLE);
            }
        } else {
            centerContainer.setVisibility(View.VISIBLE);
            calibrateButton.setVisibility(View.GONE);
        }

        setupEmptySpaceMouseControl();

        if (isConnected) {
            startHeartbeat();
        }
    }

    // Start Heartbeat to capture connection status
    private void startHeartbeat() {
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    // Heartbeat Runnable
    private Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected) {
                sendHeartbeat();
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    // send Heartbeat message
    private void sendHeartbeat() {
        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "heartbeat");
                message.put("deviceName", deviceName);

                String json = gson.toJson(message);
                byte[] buffer = json.getBytes();

                InetAddress serverAddress = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

            } catch (Exception e) {
                // Heartbeat 실패하면 연결 끊김
                isConnected = false;
            }
        });
    }

    // for flight sim
    private void addCalibrateButton() {
        Button calibrateBtn = new Button(this);
        calibrateBtn.setText("CALIBRATE");
        calibrateBtn.setTextColor(Color.WHITE);
        calibrateBtn.setTextSize(12);
        calibrateBtn.setBackgroundColor(Color.parseColor("#FF9800"));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                (int)(120 * getResources().getDisplayMetrics().density),
                (int)(50 * getResources().getDisplayMetrics().density)
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        params.setMargins(0, 100, 20, 0);
        calibrateBtn.setLayoutParams(params);

        calibrateBtn.setOnClickListener(v -> calibrateGyro());

        controllerContainer.addView(calibrateBtn);
    }

    private void calibrateGyro() {
        // save as offset for current degree
        rollOffset = currentRoll + rollOffset;
        pitchOffset = currentPitch + pitchOffset;


        currentRoll = 0;
        currentPitch = 0;

        Toast.makeText(this, "Calibrated!", Toast.LENGTH_SHORT).show();

        vibrateShort();

        // vibration feedabck
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(50);
        }
    }

    // mouse input
    private void setupEmptySpaceMouseControl() {
        controllerContainer.setOnTouchListener((v, event) -> {
            View touchedView = findViewAt(event.getRawX(), event.getRawY());
            if (touchedView != null && touchedView != v) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    isMouseActive = true;
                    isMouseDragging = true;
                    velocityX = 0;
                    velocityY = 0;
                    mouseHandler.post(smoothMouseRunnable);  // 60fps
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isMouseDragging) {
                        float deltaX = event.getX() - lastTouchX;
                        float deltaY = event.getY() - lastTouchY;

                        float joyX = deltaX / 100f;  // Sensitivity
                        float joyY = deltaY / 100f;

                        // Deadzone
                        if (Math.abs(joyX) < MOUSE_DEADZONE) joyX = 0;
                        if (Math.abs(joyY) < MOUSE_DEADZONE) joyY = 0;


                        float targetVelocityX = joyX * MAX_SPEED;
                        float targetVelocityY = joyY * MAX_SPEED;


                        velocityX += (targetVelocityX - velocityX) * ACCELERATION;
                        velocityY += (targetVelocityY - velocityY) * ACCELERATION;

                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isMouseDragging = false;
                    isMouseActive = false;
                    velocityX = 0;
                    velocityY = 0;
                    mouseHandler.removeCallbacks(smoothMouseRunnable);
                    return true;
            }
            return false;
        });
    }

    // for smooth mouse input
    private Runnable smoothMouseRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMouseActive) {

                int mouseX = Math.round(velocityX);
                int mouseY = Math.round(velocityY);

                if (Math.abs(mouseX) > 0 || Math.abs(mouseY) > 0) {
                    sendMouseMove(mouseX, mouseY);
                }

                mouseHandler.postDelayed(this, MOUSE_INTERVAL_MS);
            }
        }
    };
    private View findViewAt(float x, float y) {
        for (int i = 0; i < controllerContainer.getChildCount(); i++) {
            View child = controllerContainer.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;

            int[] location = new int[2];
            child.getLocationOnScreen(location);

            float left = location[0];
            float top = location[1];
            float right = left + child.getWidth();
            float bottom = top + child.getHeight();

            if (x >= left && x <= right && y >= top && y <= bottom) {
                return child;
            }
        }
        return null;
    }

    // Vibration
    private void vibrateShort() {
        // Check Settings
        if (!preferences.getBoolean("vibration_enabled", true)) {
            return;
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        25, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(25);
            }
        }
    }


    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!gyroEnabled || !isConnected) return;

            if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

                float[] remappedMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_Y,
                        SensorManager.AXIS_MINUS_X,
                        remappedMatrix);

                float[] orientation = new float[3];
                SensorManager.getOrientation(remappedMatrix, orientation);

                // ✅ Offset 적용 (이미 있음)
                currentRoll = (float) Math.toDegrees(orientation[2]) - rollOffset;
                currentPitch = (float) Math.toDegrees(orientation[1]) - pitchOffset;

                // 각도 정규화
                if (currentRoll > 180) currentRoll -= 360;
                else if (currentRoll < -180) currentRoll += 360;

                updateGyroUI();

                String type = currentLayout.controllerType;

                if ("Racing".equals(type)) {
                    // PWM
                } else if ("Flight Simulator".equals(type)) {
                    processFlightControls();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // Update Status
    private void updateStatus(String message, int color) {
        runOnUiThread(() -> {
            statusText.setText(message);
            statusText.setTextColor(color);
        });
    }



    // GyroUI
    private void updateGyroUI() {
        runOnUiThread(() -> {
            String type = currentLayout.controllerType;

            if ("Racing".equals(type)) {
                // Racing: Roll only
                gyroAngleText.setText(String.format("%.1f°", Math.abs(currentRoll)));

                android.view.ViewGroup.LayoutParams leftParams = leftGyroBar.getLayoutParams();
                android.view.ViewGroup.LayoutParams rightParams = rightGyroBar.getLayoutParams();

                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int maxBarWidth = screenWidth / 2 - 1;

                if (currentRoll < -GYRO_DEADZONE) {
                    float intensity = Math.min(Math.abs(currentRoll) / MAX_ANGLE, 1.0f);
                    leftParams.width = (int)(maxBarWidth * intensity);
                    rightParams.width = 0;
                } else if (currentRoll > GYRO_DEADZONE) {
                    float intensity = Math.min(Math.abs(currentRoll) / MAX_ANGLE, 1.0f);
                    rightParams.width = (int)(maxBarWidth * intensity);
                    leftParams.width = 0;
                } else {
                    leftParams.width = 0;
                    rightParams.width = 0;
                }

                leftGyroBar.setLayoutParams(leftParams);
                rightGyroBar.setLayoutParams(rightParams);

            } else if ("Flight Simulator".equals(type)) {
                // Flight: Pitch + Roll
                gyroAngleText.setText(String.format("P:%.1f° R:%.1f°", currentPitch, currentRoll));

                android.view.ViewGroup.LayoutParams leftParams = leftGyroBar.getLayoutParams();
                android.view.ViewGroup.LayoutParams rightParams = rightGyroBar.getLayoutParams();

                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int maxBarWidth = screenWidth / 2 - 1;


                if (currentRoll < -GYRO_DEADZONE) {
                    float intensity = Math.min(Math.abs(currentRoll) / KEY_ACTIVATE_THRESHOLD, 1.0f);
                    leftParams.width = (int)(maxBarWidth * intensity);
                    rightParams.width = 0;
                } else if (currentRoll > GYRO_DEADZONE) {
                    float intensity = Math.min(Math.abs(currentRoll) / KEY_ACTIVATE_THRESHOLD, 1.0f);
                    rightParams.width = (int)(maxBarWidth * intensity);
                    leftParams.width = 0;
                } else {
                    leftParams.width = 0;
                    rightParams.width = 0;
                }

                leftGyroBar.setLayoutParams(leftParams);
                rightGyroBar.setLayoutParams(rightParams);

                // change color for pitch
                if (Math.abs(currentPitch) > KEY_ACTIVATE_THRESHOLD) {
                    leftGyroBar.setBackgroundColor(Color.parseColor("#FF5722"));  // 주황색
                    rightGyroBar.setBackgroundColor(Color.parseColor("#FF5722"));
                } else {
                    leftGyroBar.setBackgroundColor(Color.parseColor("#4CAF50"));  // 초록색
                    rightGyroBar.setBackgroundColor(Color.parseColor("#4CAF50"));
                }
            }
        });
    }

    private void startPWMControl() {
        isPWMActive = true;
        pwmRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPWMActive && isConnected) {
                    processPWMSteering();
                }
                pwmHandler.postDelayed(this, PWM_CYCLE_MS);
            }
        };
        pwmHandler.post(pwmRunnable);
    }

    private void processPWMSteering() {
        float rollIntensity = calculateSteeringIntensity(currentRoll);

        if (Math.abs(currentRoll) > GYRO_DEADZONE) {
            if (currentRoll < 0) {
                pulsateSteeringKey("a", rollIntensity);
                if (isRightPressed) {
                    sendKeyPress("d", false);
                    isRightPressed = false;
                }
            } else {
                pulsateSteeringKey("d", rollIntensity);
                if (isLeftPressed) {
                    sendKeyPress("a", false);
                    isLeftPressed = false;
                }
            }
        } else {
            if (isLeftPressed) {
                sendKeyPress("a", false);
                isLeftPressed = false;
            }
            if (isRightPressed) {
                sendKeyPress("d", false);
                isRightPressed = false;
            }
        }
    }

    private float calculateSteeringIntensity(float angle) {
        float absAngle = Math.abs(angle) - GYRO_DEADZONE;
        if (absAngle < 0) return 0;
        return Math.max(0.0f, Math.min(1.0f, absAngle / (MAX_ANGLE - GYRO_DEADZONE)));
    }

    private void pulsateSteeringKey(String key, float intensity) {
        boolean shouldBePressed;

        if (intensity < 0.15f) {
            shouldBePressed = false;
        } else if (intensity < 0.35f) {
            long cyclePosition = System.currentTimeMillis() % PWM_CYCLE_MS;
            shouldBePressed = cyclePosition < (PWM_CYCLE_MS * 0.5f);
        } else if (intensity < 0.65f) {
            long cyclePosition = System.currentTimeMillis() % PWM_CYCLE_MS;
            shouldBePressed = cyclePosition < (PWM_CYCLE_MS * 0.8f);
        } else if (intensity < 0.85f) {
            long cyclePosition = System.currentTimeMillis() % PWM_CYCLE_MS;
            shouldBePressed = cyclePosition < (PWM_CYCLE_MS * 0.95f);
        } else {
            shouldBePressed = true;
        }

        boolean currentlyPressed = key.equals("a") ? isLeftPressed : isRightPressed;

        if (shouldBePressed != currentlyPressed) {
            sendKeyPress(key, shouldBePressed);
            if (key.equals("a")) {
                isLeftPressed = shouldBePressed;
            } else {
                isRightPressed = shouldBePressed;
            }
        }
    }

    // ========== Flight Controller WASD ==========
    private void processFlightControls() {
        // Pitch: W/S
        if (currentPitch < -KEY_ACTIVATE_THRESHOLD && !isWPressed) {
            sendKeyPress("w", true);
            isWPressed = true;
        } else if (currentPitch > -KEY_DEACTIVATE_THRESHOLD && isWPressed) {
            sendKeyPress("w", false);
            isWPressed = false;
        }

        if (currentPitch > KEY_ACTIVATE_THRESHOLD && !isSPressed) {
            sendKeyPress("s", true);
            isSPressed = true;
        } else if (currentPitch < KEY_DEACTIVATE_THRESHOLD && isSPressed) {
            sendKeyPress("s", false);
            isSPressed = false;
        }

        // Roll: A/D
        if (currentRoll < -KEY_ACTIVATE_THRESHOLD && !isAPressed) {
            sendKeyPress("a", true);
            isAPressed = true;
        } else if (currentRoll > -KEY_DEACTIVATE_THRESHOLD && isAPressed) {
            sendKeyPress("a", false);
            isAPressed = false;
        }

        if (currentRoll > KEY_ACTIVATE_THRESHOLD && !isDPressed) {
            sendKeyPress("d", true);
            isDPressed = true;
        } else if (currentRoll < KEY_DEACTIVATE_THRESHOLD && isDPressed) {
            sendKeyPress("d", false);
            isDPressed = false;
        }
    }

    private void sendKeyPress(String key, boolean pressed) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                // Multiplayer 설정 가져오기
                SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
                boolean multiplayerEnabled = prefs.getBoolean("multiplayer_enabled", false);
                int playerNumber = prefs.getInt("player_number", 1);

                Map<String, Object> message = new HashMap<>();
                message.put("action", "key");
                message.put("key", key);
                message.put("pressed", pressed);

                // Multiplayer 활성화 시 player 번호 추가
                if (multiplayerEnabled) {
                    message.put("player", playerNumber);
                }

                String json = gson.toJson(message);
                byte[] buffer = json.getBytes();

                InetAddress serverAddress = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

                android.util.Log.d("Universal", "Sent: " + key + " = " + pressed + (multiplayerEnabled ? " (P" + playerNumber + ")" : ""));

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendGyroData(float x, float y, float z) {
        executorService.execute(() -> {
            try {
                Map<String, Object> command = new HashMap<>();
                command.put("type", "gyro");
                command.put("x", x);
                command.put("y", y);
                command.put("z", z);

                String json = gson.toJson(command);
                byte[] buffer = json.getBytes();

                InetAddress serverAddress = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private LayoutData loadLayout(String layoutName) {  // ✅ void → LayoutData
        LayoutData layout = LayoutConfig.loadLayoutData(this, layoutName);

        if (layout == null) {
            Toast.makeText(this, "Layout not found", Toast.LENGTH_SHORT).show();
            finish();
            return null;
        }

        for (LayoutData.ButtonData btnData : layout.buttons) {
            View button = createButton(btnData);
            controllerContainer.addView(button);
            activeButtons.put(btnData.id, button);
        }

        return layout;  // ✅ 추가
    }


    private View createButton(LayoutData.ButtonData data) {
        // Mouse Joystick
        if ("mouse_joystick".equals(data.action)) {
            return createMouseJoystick(data);
        }

        // Mouse Click
        if ("mouse_left".equals(data.action) || "mouse_right".equals(data.action)) {
            return createMouseClickButton(data);
        }

        // Combined buttons - 시각적으로만 표시 (기능 없음)
        if ("abxy_combined".equals(data.action) || "dpad_combined".equals(data.action)) {
            return createCombinedButton(data);
        }

        // normal buttons
        Button btn = new Button(this);
        btn.setText(data.label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setTag(data);

        if (data.drawableResId != 0) {
            btn.setBackgroundResource(data.drawableResId);
        } else {
            btn.setBackgroundColor(Color.parseColor(data.color));
        }


        // gyroBar가 실제로 표시될 때만 보정
        int gyroBarHeight = gyroEnabled ? (int) (35 * getResources().getDisplayMetrics().density) : 0;

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                data.width, data.height);

        params.leftMargin = data.x;
        params.topMargin = data.y - gyroBarHeight;

        if (params.topMargin < 0) params.topMargin = 0;

        btn.setLayoutParams(params);

        setupButtonAction(btn, data.action);

        return btn;
    }

    // Mouse joystick
    private View createMouseJoystick(LayoutData.ButtonData data) {
        JoystickView joystick = new JoystickView(this);

        // gyroBar가 실제로 표시될 때만 보정
        int gyroBarHeight = gyroEnabled ? (int) (35 * getResources().getDisplayMetrics().density) : 0;

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                data.width, data.height);
        params.leftMargin = data.x;
        params.topMargin = data.y - gyroBarHeight;
        joystick.setLayoutParams(params);
        joystick.setTag(data);

        return joystick;
    }

    // Mouse click button
    private View createMouseClickButton(LayoutData.ButtonData data) {
        Button btn = new Button(this);
        btn.setText(data.label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setTag(data);

        if (data.drawableResId != 0) {
            btn.setBackgroundResource(data.drawableResId);
        } else {
            btn.setBackgroundColor(Color.parseColor(data.color));
        }

        // gyroBar가 실제로 표시될 때만 보정
        int gyroBarHeight = gyroEnabled ? (int) (35 * getResources().getDisplayMetrics().density) : 0;

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                data.width, data.height);
        params.leftMargin = data.x;
        params.topMargin = data.y - gyroBarHeight;
        btn.setLayoutParams(params);

        // 마우스 클릭 액션
        String button = data.action.equals("mouse_left") ? "left" : "right";
        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                sendMouseClick(button, true);
                vibrateShort();
                btn.setAlpha(0.7f);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                sendMouseClick(button, false);
                btn.setAlpha(1.0f);
            }
            return true;
        });

        return btn;
    }

    // Combined button - 시각적으로만 표시
    private View createCombinedButton(LayoutData.ButtonData data) {
        View view = new View(this);
        view.setTag(data);

        if (data.drawableResId != 0) {
            view.setBackgroundResource(data.drawableResId);
        } else {
            view.setBackgroundColor(Color.parseColor(data.color));
        }

        // gyroBar가 실제로 표시될 때만 보정
        int gyroBarHeight = gyroEnabled ? (int) (35 * getResources().getDisplayMetrics().density) : 0;

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                data.width, data.height);
        params.leftMargin = data.x;
        params.topMargin = data.y - gyroBarHeight;
        view.setLayoutParams(params);

        // 터치 비활성화 (시각용)
        view.setEnabled(false);

        return view;
    }

    // ========== JoystickView 클래스 ==========
    private class JoystickView extends View {
        private Paint basePaint, handlePaint, crosshairPaint;
        private float centerX, centerY;
        private float handleX, handleY;
        private float baseRadius = 100f;
        private float handleRadius = 35f;
        private boolean isTouching = false;

        private Handler sendHandler = new Handler();
        private static final int SEND_INTERVAL_MS = 16; // 60fps

        // ✅ Smooth mouse
        private float velocityX = 0;
        private float velocityY = 0;
        private static final float JOYSTICK_ACCELERATION = 0.3f;
        private static final float JOYSTICK_MAX_SPEED = 5.0f;
        private static final float JOYSTICK_DEADZONE = 0.1f;

        private Runnable joystickMouseRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTouching) {
                    float relativeX = (handleX - centerX) / (baseRadius - handleRadius);
                    float relativeY = (handleY - centerY) / (baseRadius - handleRadius);

                    // Deadzone
                    if (Math.abs(relativeX) < JOYSTICK_DEADZONE) relativeX = 0;  // ✅
                    if (Math.abs(relativeY) < JOYSTICK_DEADZONE) relativeY = 0;

                    // ✅ 목표 속도
                    float targetVelocityX = relativeX * JOYSTICK_MAX_SPEED;
                    float targetVelocityY = relativeY * JOYSTICK_MAX_SPEED;

                    // ✅ 부드러운 가속
                    velocityX += (targetVelocityX - velocityX) * JOYSTICK_ACCELERATION;
                    velocityY += (targetVelocityY - velocityY) * JOYSTICK_ACCELERATION;

                    int mouseX = Math.round(velocityX);
                    int mouseY = Math.round(velocityY);

                    sendMouseMove(mouseX, mouseY);
                    sendHandler.postDelayed(this, SEND_INTERVAL_MS);
                }
            }
        };

        public JoystickView(Context context) {
            super(context);

            basePaint = new Paint();
            basePaint.setColor(Color.parseColor("#80424242"));
            basePaint.setStyle(Paint.Style.FILL);
            basePaint.setAntiAlias(true);

            handlePaint = new Paint();
            handlePaint.setColor(Color.parseColor("#2196F3"));
            handlePaint.setStyle(Paint.Style.FILL);
            handlePaint.setAntiAlias(true);

            crosshairPaint = new Paint();
            crosshairPaint.setColor(Color.parseColor("#90CAF9"));
            crosshairPaint.setStrokeWidth(2f);
            crosshairPaint.setAntiAlias(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            baseRadius = Math.min(w, h) / 2f * 0.8f;
            handleRadius = baseRadius * 0.35f;
            centerX = w / 2f;
            centerY = h / 2f;
            handleX = centerX;
            handleY = centerY;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // 외부 원
            canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

            // 십자선
            canvas.drawLine(centerX - 15, centerY, centerX + 15, centerY, crosshairPaint);
            canvas.drawLine(centerX, centerY - 15, centerX, centerY + 15, crosshairPaint);

            // 핸들
            canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isTouching = true;
                    velocityX = 0;  // ✅ 초기화
                    velocityY = 0;
                    updateHandle(event.getX(), event.getY());
                    sendHandler.post(joystickMouseRunnable);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    updateHandle(event.getX(), event.getY());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isTouching = false;
                    velocityX = 0;
                    velocityY = 0;
                    sendHandler.removeCallbacks(joystickMouseRunnable);
                    resetHandle();
                    sendMouseMove(0, 0);
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

        private Runnable sendMouseRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTouching) {
                    float relativeX = (handleX - centerX) / (baseRadius - handleRadius);
                    float relativeY = (handleY - centerY) / (baseRadius - handleRadius);

                    // 감도 300 (Flight Controller와 동일)
                    int mouseX = Math.round(relativeX * 300);
                    int mouseY = Math.round(relativeY * 300);

                    sendMouseMove(mouseX, mouseY);
                    sendHandler.postDelayed(this, SEND_INTERVAL_MS);
                }
            }
        };
    }

    // send mouse inputs
    private void sendMouseMove(int x, int y) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "mouse_move");
                message.put("x", x);
                message.put("y", y);

                String json = gson.toJson(message);
                byte[] buffer = json.getBytes();

                InetAddress serverAddress = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMouseClick(String button, boolean pressed) {
        if (!isConnected) return;

        executorService.execute(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("action", "mouse_button");
                message.put("button", button);
                message.put("pressed", pressed);

                String json = gson.toJson(message);
                byte[] buffer = json.getBytes();

                InetAddress serverAddress = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    private void setupButtonAction(Button btn, String action) {
        LayoutData.ButtonData data = (LayoutData.ButtonData) btn.getTag();

        // Movement Joystick
        if ("movement_joystick".equals(action)) {
            setupJoystickControl(btn);
            return;
        }

        // D-Pad Combined
        if ("dpad_combined".equals(action)) {
            setupDPadCombined(btn);
            return;
        }

        // Load mappedKey
        String keyToSend = getKeyForButton(data, action);

        if (keyToSend == null || keyToSend.isEmpty()) {
            android.util.Log.w("Universal", "No key mapping for: " + action);
            return;
        }

        // Hold-type buttons
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendKeyPress(keyToSend, true);
                    vibrateShort();
                    btn.setAlpha(0.7f);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sendKeyPress(keyToSend, false);
                    btn.setAlpha(1.0f);
                    return true;
            }
            return false;
        });
    }

    // Movement Joystick
    private void setupMovementJoystick(Button btn) {
        MovementJoystickView joystick = new MovementJoystickView(this);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) btn.getLayoutParams();
        joystick.setLayoutParams(params);
        joystick.setTag(btn.getTag());

        controllerContainer.removeView(btn);
        controllerContainer.addView(joystick);
    }

    // ✅ D-Pad Combined 설정
    private void setupDPadCombined(Button btn) {
        // 현재 눌린 키들
        final boolean[] keysPressed = {false, false, false, false}; // W, A, S, D

        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                    event.getAction() == MotionEvent.ACTION_MOVE) {

                float x = event.getX();
                float y = event.getY();
                float centerX = v.getWidth() / 2f;
                float centerY = v.getHeight() / 2f;

                float dx = x - centerX;
                float dy = y - centerY;

                // 모든 키 릴리즈
                if (keysPressed[0]) { sendKeyPress("w", false); keysPressed[0] = false; }
                if (keysPressed[1]) { sendKeyPress("a", false); keysPressed[1] = false; }
                if (keysPressed[2]) { sendKeyPress("s", false); keysPressed[2] = false; }
                if (keysPressed[3]) { sendKeyPress("d", false); keysPressed[3] = false; }

                // 거리 체크
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < v.getWidth() * 0.15f) {
                    // 중앙 데드존
                    return true;
                }

                // 각도 계산
                double angle = Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;

                // 8방향
                if (angle >= 337.5 || angle < 22.5) {
                    // Right
                    sendKeyPress("d", true);
                    keysPressed[3] = true;
                } else if (angle >= 22.5 && angle < 67.5) {
                    // Down-Right
                    sendKeyPress("s", true);
                    sendKeyPress("d", true);
                    keysPressed[2] = true;
                    keysPressed[3] = true;
                } else if (angle >= 67.5 && angle < 112.5) {
                    // Down
                    sendKeyPress("s", true);
                    keysPressed[2] = true;
                } else if (angle >= 112.5 && angle < 157.5) {
                    // Down-Left
                    sendKeyPress("s", true);
                    sendKeyPress("a", true);
                    keysPressed[2] = true;
                    keysPressed[1] = true;
                } else if (angle >= 157.5 && angle < 202.5) {
                    // Left
                    sendKeyPress("a", true);
                    keysPressed[1] = true;
                } else if (angle >= 202.5 && angle < 247.5) {
                    // Up-Left
                    sendKeyPress("w", true);
                    sendKeyPress("a", true);
                    keysPressed[0] = true;
                    keysPressed[1] = true;
                } else if (angle >= 247.5 && angle < 292.5) {
                    // Up
                    sendKeyPress("w", true);
                    keysPressed[0] = true;
                } else if (angle >= 292.5 && angle < 337.5) {
                    // Up-Right
                    sendKeyPress("w", true);
                    sendKeyPress("d", true);
                    keysPressed[0] = true;
                    keysPressed[3] = true;
                }

                btn.setAlpha(0.7f);

            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                // 모든 키 릴리즈
                if (keysPressed[0]) { sendKeyPress("w", false); keysPressed[0] = false; }
                if (keysPressed[1]) { sendKeyPress("a", false); keysPressed[1] = false; }
                if (keysPressed[2]) { sendKeyPress("s", false); keysPressed[2] = false; }
                if (keysPressed[3]) { sendKeyPress("d", false); keysPressed[3] = false; }

                btn.setAlpha(1.0f);
            }
            return true;
        });
    }

    // mappedKey First, if none default key settings
    private String getKeyForButton(LayoutData.ButtonData data, String action) {
        // if there is no mappedKey
        if (data != null && data.mappedKey != null && !data.mappedKey.isEmpty()) {
            return normalizeKeyName(data.mappedKey);
        }

        //  load from action
        return getDefaultKeyForAction(action);
    }

    // ========== 조이스틱 컨트롤 ==========
    private void setupJoystickControl(Button btn) {
        JoystickView joystick = new JoystickView(this);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) btn.getLayoutParams();
        joystick.setLayoutParams(params);
        joystick.setTag(btn.getTag());

        // 기존 버튼 제거하고 조이스틱 추가
        controllerContainer.removeView(btn);
        controllerContainer.addView(joystick);
    }

    // WASD JOYSTICK
    private class MovementJoystickView extends View {
        private float centerX, centerY;
        private float handleX, handleY;
        private float baseRadius = 100f;
        private float handleRadius = 40f;
        private boolean isTouching = false;

        private Paint basePaint, handlePaint;


        private boolean isWPressed = false;
        private boolean isAPressed = false;
        private boolean isSPressed = false;
        private boolean isDPressed = false;

        public MovementJoystickView(Context context) {
            super(context);

            basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            basePaint.setColor(Color.parseColor("#2a2a2a"));
            basePaint.setStyle(Paint.Style.FILL);

            handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            handlePaint.setColor(Color.parseColor("#616161"));
            handlePaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            centerX = w / 2f;
            centerY = h / 2f;
            handleX = centerX;
            handleY = centerY;
            baseRadius = Math.min(w, h) / 2.5f;
            handleRadius = baseRadius / 2.5f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
            canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    isTouching = true;
                    updateHandle(event.getX(), event.getY());
                    updateKeys();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isTouching = false;
                    handleX = centerX;
                    handleY = centerY;
                    releaseAllKeys();
                    invalidate();
                    break;
            }
            return true;
        }

        private void updateHandle(float x, float y) {
            float dx = x - centerX;
            float dy = y - centerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance > baseRadius) {
                float angle = (float) Math.atan2(dy, dx);
                handleX = centerX + baseRadius * (float) Math.cos(angle);
                handleY = centerY + baseRadius * (float) Math.sin(angle);
            } else {
                handleX = x;
                handleY = y;
            }

            invalidate();
        }

        private void updateKeys() {
            float dx = handleX - centerX;
            float dy = handleY - centerY;
            float threshold = baseRadius * 0.3f;

            // W/S
            if (dy < -threshold && !isWPressed) {
                sendKeyPress("w", true);
                isWPressed = true;
            } else if (dy >= -threshold && isWPressed) {
                sendKeyPress("w", false);
                isWPressed = false;
            }

            if (dy > threshold && !isSPressed) {
                sendKeyPress("s", true);
                isSPressed = true;
            } else if (dy <= threshold && isSPressed) {
                sendKeyPress("s", false);
                isSPressed = false;
            }

            // A/D
            if (dx < -threshold && !isAPressed) {
                sendKeyPress("a", true);
                isAPressed = true;
            } else if (dx >= -threshold && isAPressed) {
                sendKeyPress("a", false);
                isAPressed = false;
            }

            if (dx > threshold && !isDPressed) {
                sendKeyPress("d", true);
                isDPressed = true;
            } else if (dx <= threshold && isDPressed) {
                sendKeyPress("d", false);
                isDPressed = false;
            }
        }

        private void releaseAllKeys() {
            if (isWPressed) { sendKeyPress("w", false); isWPressed = false; }
            if (isAPressed) { sendKeyPress("a", false); isAPressed = false; }
            if (isSPressed) { sendKeyPress("s", false); isSPressed = false; }
            if (isDPressed) { sendKeyPress("d", false); isDPressed = false; }
        }
    }

    // key name
    private String normalizeKeyName(String key) {
        if (key == null) return null;

        key = key.toLowerCase().trim();

        // Special
        switch (key) {
            case "space": case "spacebar": return "space";
            case "enter": case "return": return "enter";
            case "shift": return "shift";
            case "ctrl": case "control": return "ctrl";
            case "alt": return "alt";
            case "tab": return "tab";
            case "esc": case "escape": return "esc";
            case "backspace": return "backspace";

            // Arrows
            case "up": case "arrow up": case "↑": return "up";
            case "down": case "arrow down": case "↓": return "down";
            case "left": case "arrow left": case "←": return "left";
            case "right": case "arrow right": case "→": return "right";

            // F-keys
            case "f1": return "f1";
            case "f2": return "f2";
            case "f3": return "f3";
            case "f4": return "f4";
            case "f5": return "f5";
            case "f6": return "f6";
            case "f7": return "f7";
            case "f8": return "f8";
            case "f9": return "f9";
            case "f10": return "f10";
            case "f11": return "f11";
            case "f12": return "f12";

            // numbers
            case "0": case "1": case "2": case "3": case "4":
            case "5": case "6": case "7": case "8": case "9":
                return key;

            // Alphabet
            default:
                if (key.length() == 1 && key.matches("[a-z]")) {
                    return key;
                }
                return key;
        }
    }
    // action keys
    private String getDefaultKeyForAction(String action) {
        switch (action) {
            // Triggers
            case "trigger_lt": return "w";
            case "trigger_rt": return "s";
            case "trigger_lb": return "q";
            case "trigger_rb": return "e";

            // Face buttons
            case "button_a": return "space";
            case "button_b": return "b";
            case "button_x": return "x";
            case "button_y": return "y";

            // D-Pad
            case "dpad_up": return "up";
            case "dpad_down": return "down";
            case "dpad_left": return "left";
            case "dpad_right": return "right";

            // Sticks
            case "stick_left": return null;
            case "stick_right": return null;

            default:

                if (action.startsWith("key_")) {
                    return action.substring(4);
                }
                return null;
        }
    }

    private void connectToServer() {
        executorService.execute(() -> {
            try {
                udpSocket = new DatagramSocket();
                InetAddress serverAddress = InetAddress.getByName(serverIP);

                Map<String, String> pairingMsg = new HashMap<>();
                pairingMsg.put("type", "pairing");
                pairingMsg.put("code", pairingCode);
                pairingMsg.put("device_name", deviceName);

                String json = gson.toJson(pairingMsg);
                byte[] buffer = json.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

                mainHandler.post(() -> {
                    isConnected = true;
                    statusText.setText("Connected - " + layoutName);
                    statusText.setTextColor(Color.parseColor("#4CAF50"));
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    statusText.setText("Connection Failed");
                    statusText.setTextColor(Color.parseColor("#F44336"));
                });
            }
        });
        // connectToServer() if it's successful
        updateStatus("Connected", Color.parseColor("#4CAF50"));

        // if it's failed to connect
        updateStatus("Disconnected", Color.parseColor("#F44336"));
    }

    private void sendCommand(String action, String state) {
        if (!isConnected || udpSocket == null) return;

        executorService.execute(() -> {
            try {
                Map<String, String> command = new HashMap<>();
                command.put("type", "command");
                command.put("action", action);
                command.put("state", state);

                String json = gson.toJson(command);
                byte[] buffer = json.getBytes();

                InetAddress serverAddress = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                udpSocket.send(packet);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showMenu() {
        vibrateShort();
        // Open OptionsActivity instead of showing dialog
        Intent intent = new Intent(this, OptionsActivity.class);
        intent.putExtra("LAYOUT_NAME", layoutName);
        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivity(intent);
    }

    private void openEditLayout() {
        Intent intent = new Intent(this, EditLayoutActivity.class);
        intent.putExtra("LAYOUT_NAME", layoutName);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gyroEnabled && sensorManager != null) {
            sensorManager.unregisterListener(gyroListener);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gyroEnabled && gyroSensor != null) {
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        android.util.Log.d("Disconnect", "Disconnected");

        // Send disconnect signal to Desktop
        sendDisconnectMessage();
        heartbeatHandler.removeCallbacks(heartbeatRunnable);


        isPWMActive = false;
        if (pwmHandler != null && pwmRunnable != null) {
            pwmHandler.removeCallbacks(pwmRunnable);
        }

        if (isConnected) {
            sendKeyPress("w", false);
            sendKeyPress("a", false);
            sendKeyPress("s", false);
            sendKeyPress("d", false);
        }

        // Send Message and quit
        try {
            Thread.sleep(300);
        } catch (Exception e) {}

        // ExecutorService finish
        if (executorService != null) {
            executorService.shutdown();
        }

        // Socket close
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        android.util.Log.d("Disconnect", "=== onDestroy() FINISHED ===");
    }

    // Send Disconnect Message
    private void sendDisconnectMessage() {
        android.util.Log.d("Disconnect", "=== sendDisconnectMessage() START ===");

        if (udpSocket == null || udpSocket.isClosed()) {
            android.util.Log.e("Disconnect", "Socket is null or closed!");
            return;
        }

        if (serverIP == null || serverIP.isEmpty()) {
            android.util.Log.e("Disconnect", "Server IP is null!");
            return;
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(() -> {
                try {
                    Map<String, Object> message = new HashMap<>();
                    message.put("action", "disconnect");
                    message.put("deviceName", deviceName);

                    String json = gson.toJson(message);
                    android.util.Log.d("Disconnect", "Message: " + json);

                    byte[] buffer = json.getBytes();

                    InetAddress serverAddress = InetAddress.getByName(serverIP);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);

                    udpSocket.send(packet);

                    android.util.Log.d("Disconnect", "✓✓✓ DISCONNECT SENT to " + serverIP + ":" + serverPort);

                } catch (Exception e) {
                    android.util.Log.e("Disconnect", "❌ ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            // hold message
            try {
                Thread.sleep(200);
            } catch (Exception e) {}
        }
    }
}