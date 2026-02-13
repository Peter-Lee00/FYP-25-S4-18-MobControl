package com.example.mobcontrol;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.LinearLayout;
import android.widget.ScrollView;


public class EditLayoutActivity extends AppCompatActivity {

    private RelativeLayout editContainer;
    private String layoutName;
    private LayoutData currentLayout;
    private Map<String, View> activeButtons = new HashMap<>();
    private View selectedButton = null;
    private RelativeLayout  fabMenu;
    private PopupWindow resizePopup;

    private boolean isDragging = false;
    private float dX, dY;
    private long pressStartTime = 0;
    private float initialTouchX = 0;
    private float initialTouchY = 0;
    private static final int DRAG_THRESHOLD = 20;

    // Available button templates
    private static final Map<String, ButtonTemplate> BUTTON_TEMPLATES = new HashMap<>();

    static class ButtonTemplate {
        String label;
        String action;
        String color;
        int defaultWidth;
        int defaultHeight;
        int drawableResId;

        // drawable
        ButtonTemplate(String label, String action, String color, int w, int h, int drawableResId) {
            this.label = label;
            this.action = action;
            this.color = color;
            this.defaultWidth = w;
            this.defaultHeight = h;
            this.drawableResId = drawableResId;
        }

        // without drawable
        ButtonTemplate(String label, String action, String color, int w, int h) {
            this(label, action, color, w, h, 0);
        }
    }

    // Actually its list of all available buttons (need to add design in future)
    static {
        // Face Buttons (ABXY)
        BUTTON_TEMPLATES.put("A Button", new ButtonTemplate(
                "A", "button_a", "#D32F2F", 80, 80, R.drawable.button_a));

        BUTTON_TEMPLATES.put("B Button", new ButtonTemplate(
                "B", "button_b", "#C62828", 80, 80, R.drawable.button_b));

        BUTTON_TEMPLATES.put("X Button", new ButtonTemplate(
                "X", "button_x", "#B71C1C", 80, 80, R.drawable.button_x));

        BUTTON_TEMPLATES.put("Y Button", new ButtonTemplate(
                "Y", "button_y", "#FDD835", 80, 80, R.drawable.button_y));

        // D-Pad
        BUTTON_TEMPLATES.put("D-Pad Up", new ButtonTemplate(
                "↑", "dpad_up", "#424242", 60, 60, R.drawable.dpad_up));

        BUTTON_TEMPLATES.put("D-Pad Down", new ButtonTemplate(
                "↓", "dpad_down", "#424242", 60, 60, R.drawable.dpad_down));

        BUTTON_TEMPLATES.put("D-Pad Left", new ButtonTemplate(
                "←", "dpad_left", "#424242", 60, 60, R.drawable.dpad_left));

        BUTTON_TEMPLATES.put("D-Pad Right", new ButtonTemplate(
                "→", "dpad_right", "#424242", 60, 60, R.drawable.dpad_right));


        BUTTON_TEMPLATES.put("D-Pad Combined", new ButtonTemplate(
                "DPAD", "dpad_combined", "#424242", 180, 180, R.drawable.dpad_combined_bg));


        BUTTON_TEMPLATES.put("Movement Joystick", new ButtonTemplate(
                "MOVE", "movement_joystick", "#424242", 200, 200, R.drawable.movement_joystick_bg));
        // Bumpers
        BUTTON_TEMPLATES.put("LB", new ButtonTemplate(
                "LB", "lb", "#D32F2F", 50, 50, R.drawable.button_lb));

        BUTTON_TEMPLATES.put("RB", new ButtonTemplate(
                "RB", "rb", "#D32F2F", 50, 50, R.drawable.button_rb));

        // Triggers
        BUTTON_TEMPLATES.put("LT", new ButtonTemplate(
                "LT", "trigger_left", "#D32F2F", 80, 300, R.drawable.trigger_lt));

        BUTTON_TEMPLATES.put("RT", new ButtonTemplate(
                "RT", "trigger_right", "#D32F2F", 80, 300, R.drawable.trigger_rt));

        // Home
        BUTTON_TEMPLATES.put("Home", new ButtonTemplate(
                "⊙", "home", "#FFFFFF", 100, 100, R.drawable.button_home));

        // Sticks
        BUTTON_TEMPLATES.put("Left Stick", new ButtonTemplate(
                "LS", "stick_left", "#8B1A1A", 90, 90, R.drawable.stick_left));

        BUTTON_TEMPLATES.put("Right Stick", new ButtonTemplate(
                "RS", "stick_right", "#8B1A1A", 90, 90, R.drawable.stick_right));

        // System
        BUTTON_TEMPLATES.put("Select", new ButtonTemplate(
                "≡", "select", "#424242", 35, 35, R.drawable.button_select));

        BUTTON_TEMPLATES.put("Start", new ButtonTemplate(
                "☰", "start", "#424242", 35, 35, R.drawable.button_start));

        BUTTON_TEMPLATES.put("Mouse Joystick", new ButtonTemplate(
                "🖱️", "mouse_joystick", "#2196F3", 250, 250, R.drawable.mouse_joystick_bg));

        // Mouse Click \
        BUTTON_TEMPLATES.put("Left Click", new ButtonTemplate(
                "L-CLICK", "mouse_left", "#FF5722", 80, 80, R.drawable.center_button_bg));

        BUTTON_TEMPLATES.put("Right Click", new ButtonTemplate(
                "R-CLICK", "mouse_right", "#FF5722", 80, 80, R.drawable.center_button_bg));

        // Combined Button Groups


        BUTTON_TEMPLATES.put("D-Pad Combined", new ButtonTemplate(
                "D-PAD", "dpad_combined", "#2A2A3A", 180, 180, R.drawable.dpad_combined));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_layout);

        // Receive Intent
        Intent intent = getIntent();
        String receivedLayoutName = intent.getStringExtra("LAYOUT_NAME");
        boolean isNew = intent.getBooleanExtra("IS_NEW", false);
        String controllerType = intent.getStringExtra("CONTROLLER_TYPE");

        android.util.Log.d("EditLayout", "receivedLayoutName: " + receivedLayoutName);
        android.util.Log.d("EditLayout", "isNew: " + isNew);
        android.util.Log.d("EditLayout", "controllerType: " + controllerType);

        // Reset
        editContainer = findViewById(R.id.editContainer);
        fabMenu = findViewById(R.id.fabMenu);

        activeButtons = new HashMap<>();

        if (isNew) {
            // Create new layout
            currentLayout = new LayoutData();
            currentLayout.name = receivedLayoutName;
            currentLayout.controllerType = controllerType != null ? controllerType : "Normal Game Controller";
            currentLayout.gyroEnabled = "Racing".equals(controllerType);
            currentLayout.buttons = new ArrayList<>();

            android.util.Log.d("EditLayout", "✓ New layout created: " + currentLayout.name);
        } else {
            // Load Layout
            currentLayout = LayoutConfig.loadLayoutData(this, receivedLayoutName);

            if (currentLayout == null) {
                Toast.makeText(this, "Layout not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            android.util.Log.d("EditLayout", "✓ Loaded layout: " + currentLayout.name);

            // Show Buttons
            for (LayoutData.ButtonData btnData : currentLayout.buttons) {
                View button = createButton(btnData);
                editContainer.addView(button);
                activeButtons.put(btnData.id, button);
            }
        }

        // Menu button
        fabMenu.setOnClickListener(v -> showMenuDialog());
    }

    private void loadButtons() {
        if (currentLayout == null || currentLayout.buttons == null) return;

        for (LayoutData.ButtonData btnData : currentLayout.buttons) {
            View button = createButton(btnData);
            editContainer.addView(button);
            activeButtons.put(btnData.id, button);
        }
    }

    private View createButton(LayoutData.ButtonData data) {
        Button btn = new Button(this);
        btn.setText(data.label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setTag(data);

        if (data.drawableResId != 0) {
            btn.setBackgroundResource(data.drawableResId);
        } else {
            // fallback
            btn.setBackgroundColor(Color.parseColor(data.color));
        }

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                data.width, data.height);
        params.leftMargin = data.x;
        params.topMargin = data.y;
        btn.setLayoutParams(params);

        makeButtonEditable(btn, data.id);

        return btn;
    }

    private void makeButtonEditable(View button, String buttonId) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pressStartTime = System.currentTimeMillis();
                    selectButton(v);

                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) v.getLayoutParams();
                    dX = params.leftMargin - event.getRawX();
                    dY = params.topMargin - event.getRawY();

                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = Math.abs(event.getRawX() - initialTouchX);
                    float deltaY = Math.abs(event.getRawY() - initialTouchY);

                    if (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        int newX = (int) (event.getRawX() + dX);
                        int newY = (int) (event.getRawY() + dY);

                        newX = Math.max(0, Math.min(newX, editContainer.getWidth() - v.getWidth()));
                        newY = Math.max(0, Math.min(newY, editContainer.getHeight() - v.getHeight()));

                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) v.getLayoutParams();
                        layoutParams.leftMargin = newX;
                        layoutParams.topMargin = newY;
                        v.setLayoutParams(layoutParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long pressDuration = System.currentTimeMillis() - pressStartTime;

                    if (pressDuration > 800 && !isDragging) {
                        showDeleteButtonDialog(buttonId);
                    } else if (!isDragging) {
                        showResizePopup(v);
                    }
                    return true;
            }
            return false;
        });
    }

    private void selectButton(View button) {
        if (selectedButton != null) {
            selectedButton.setAlpha(0.9f);
        }
        selectedButton = button;
        button.setAlpha(1.0f);
        button.bringToFront();
    }

    private void showResizePopup(View button) {
        if (resizePopup != null && resizePopup.isShowing()) {
            resizePopup.dismiss();
        }

        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_resize_button, null);

        // 340dp 팝업
        resizePopup = new PopupWindow(popupView,
                (int) (340 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        TextView titleText = popupView.findViewById(R.id.popupTitle);
        SeekBar sizeSeekBar = popupView.findViewById(R.id.sizeSeekBar);
        TextView currentMappingText = popupView.findViewById(R.id.currentMappingText);
        Button showKeyListButton = popupView.findViewById(R.id.showKeyListButton);
        Button closeButton = popupView.findViewById(R.id.closeButton);

        LayoutData.ButtonData data = (LayoutData.ButtonData) button.getTag();
        titleText.setText(data != null ? data.label : "Button");

        // 현재 매핑 표시
        String currentMapping = (data != null && data.mappedKey != null && !data.mappedKey.isEmpty())
                ? data.mappedKey
                : "None";
        currentMappingText.setText(currentMapping);

        // 크기 설정 (50-400px)
        ViewGroup.LayoutParams params = button.getLayoutParams();
        int currentSize = (params.width + params.height) / 2;
        currentSize = Math.max(50, Math.min(400, currentSize));
        int progress = (int) ((currentSize - 50) / 3.5);
        sizeSeekBar.setProgress(progress);

        // SeekBar 리스너
        sizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = 50 + (int)(progress * 3.5);
                params.width = newSize;
                params.height = newSize;
                button.setLayoutParams(params);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // "List ▼" 버튼 → 전체화면 키 선택
        showKeyListButton.setOnClickListener(v -> {
            showKeySelectionDialog(currentMapping, selectedKey -> {
                if (data != null) {
                    data.mappedKey = selectedKey;
                    currentMappingText.setText(selectedKey);
                    Toast.makeText(this, "✓ " + selectedKey, Toast.LENGTH_SHORT).show();
                }
            });
        });

        closeButton.setOnClickListener(v -> resizePopup.dismiss());
        resizePopup.showAtLocation(editContainer, Gravity.CENTER, 0, 0);
    }

    private void showKeySelectionDialog(String currentKey, KeySelectionListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF2a2a2a);
        mainLayout.setPadding(20, 20, 20, 20);

        // 헤더
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.setMargins(0, 0, 0, 20);
        header.setLayoutParams(headerParams);

        TextView title = new TextView(this);
        title.setText("Select Key Mapping");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        title.setLayoutParams(titleParams);

        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setTextSize(20);
        closeBtn.setBackgroundColor(0xFFF44336);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(60, 60));

        header.addView(title);
        header.addView(closeBtn);
        mainLayout.addView(header);

        // 탭 (키보드용)
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams tabScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabScrollParams.setMargins(0, 0, 0, 16);
        tabScroll.setLayoutParams(tabScrollParams);

        LinearLayout tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);

        String[] keyCategories = {"Letters", "Numbers", "Special", "Arrow", "F-Keys", "Mouse"};
        Button[] tabButtons = new Button[keyCategories.length];

        for (int i = 0; i < keyCategories.length; i++) {
            Button tab = new Button(this);
            tab.setText(keyCategories[i]);
            tab.setTextColor(0xFFFFFFFF);
            tab.setTextSize(14);
            tab.setBackgroundColor(i == 0 ? 0xFF2196F3 : 0xFF424242);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tabParams.setMargins(0, 0, 8, 0);
            tab.setLayoutParams(tabParams);
            tab.setPadding(32, 16, 32, 16);
            tabButtons[i] = tab;
            tabContainer.addView(tab);
        }

        tabScroll.addView(tabContainer);
        mainLayout.addView(tabScroll);

        // 그리드
        ScrollView gridScroll = new ScrollView(this);
        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(gridContainer);
        mainLayout.addView(gridScroll);

        builder.setView(mainLayout);
        AlertDialog dialog = builder.create();

        // 키 분류
        Map<String, List<String>> keysByCategory = new HashMap<>();

        List<String> letters = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            letters.add(String.valueOf(c));
        }
        keysByCategory.put("Letters", letters);

        List<String> numbers = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            numbers.add(String.valueOf(i));
        }
        keysByCategory.put("Numbers", numbers);

        List<String> special = new ArrayList<>();
        special.add("Space");
        special.add("Enter");
        special.add("Shift");
        special.add("Ctrl");
        special.add("Alt");
        special.add("Tab");
        special.add("Esc");
        special.add("Backspace");
        keysByCategory.put("Special", special);

        List<String> arrow = new ArrayList<>();
        arrow.add("Up");
        arrow.add("Down");
        arrow.add("Left");
        arrow.add("Right");
        keysByCategory.put("Arrow", arrow);

        List<String> fkeys = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            fkeys.add("F" + i);
        }
        keysByCategory.put("F-Keys", fkeys);

        List<String> mouse = new ArrayList<>();
        mouse.add("Mouse1");
        mouse.add("Mouse2");
        mouse.add("Mouse3");
        keysByCategory.put("Mouse", mouse);

        // 그리드 업데이트
        Runnable[] updateGrid = new Runnable[1];
        updateGrid[0] = () -> {
            gridContainer.removeAllViews();

            String selectedCategory = "Letters";
            for (int i = 0; i < tabButtons.length; i++) {
                if (tabButtons[i].getBackground() instanceof android.graphics.drawable.ColorDrawable) {
                    android.graphics.drawable.ColorDrawable cd = (android.graphics.drawable.ColorDrawable) tabButtons[i].getBackground();
                    if (cd.getColor() == 0xFF2196F3) {
                        selectedCategory = keyCategories[i];
                        break;
                    }
                }
            }

            List<String> keys = keysByCategory.get(selectedCategory);
            if (keys == null) return;

            int columns = 5;
            LinearLayout currentRow = null;

            for (int i = 0; i < keys.size(); i++) {
                if (i % columns == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, 0, 0, 8);
                    currentRow.setLayoutParams(rowParams);
                    gridContainer.addView(currentRow);
                }

                String key = keys.get(i);
                Button keyBtn = new Button(this);
                keyBtn.setText(key);
                keyBtn.setTextColor(0xFFFFFFFF);
                keyBtn.setTextSize(16);

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        0, 120, 1);
                btnParams.setMargins(0, 0, 8, 0);
                keyBtn.setLayoutParams(btnParams);

                if (key.equals(currentKey)) {
                    keyBtn.setBackgroundColor(0xFF4CAF50);
                } else {
                    keyBtn.setBackgroundColor(0xFF424242);
                }

                keyBtn.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onKeySelected(key);
                    }
                    dialog.dismiss();
                });

                currentRow.addView(keyBtn);
            }

            if (currentRow != null && keys.size() % columns != 0) {
                int remaining = columns - (keys.size() % columns);
                for (int i = 0; i < remaining; i++) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                            0, 120, 1);
                    spacerParams.setMargins(0, 0, 8, 0);
                    spacer.setLayoutParams(spacerParams);
                    currentRow.addView(spacer);
                }
            }
        };

        for (int i = 0; i < tabButtons.length; i++) {
            int index = i;
            tabButtons[i].setOnClickListener(v -> {
                for (Button tab : tabButtons) {
                    tab.setBackgroundColor(0xFF424242);
                }
                tabButtons[index].setBackgroundColor(0xFF2196F3);
                updateGrid[0].run();
            });
        }

        updateGrid[0].run();
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // 리스너 인터페이스
    private interface KeySelectionListener {
        void onKeySelected(String key);
    }

    // 모든 키 목록
    private List<String> getAllKeys() {
        List<String> keys = new ArrayList<>();

        keys.add("═══ Letters ═══");
        for (char c = 'A'; c <= 'Z'; c++) {
            keys.add(String.valueOf(c));
        }

        keys.add("═══ Numbers ═══");
        for (int i = 0; i <= 9; i++) {
            keys.add(String.valueOf(i));
        }

        keys.add("═══ Special Keys ═══");
        keys.add("Space");
        keys.add("Enter");
        keys.add("Shift");
        keys.add("Ctrl");
        keys.add("Alt");
        keys.add("Tab");
        keys.add("Esc");
        keys.add("Backspace");

        keys.add("═══ Arrow Keys ═══");
        keys.add("Up");
        keys.add("Down");
        keys.add("Left");
        keys.add("Right");

        keys.add("═══ Function Keys ═══");
        for (int i = 1; i <= 12; i++) {
            keys.add("F" + i);
        }

        keys.add("═══ Mouse Buttons ═══");
        keys.add("Mouse1");
        keys.add("Mouse2");
        keys.add("Mouse3");

        return keys;
    }


    private void showDeleteButtonDialog(String buttonId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Button?")
                .setMessage("Remove this button from layout?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    View button = activeButtons.get(buttonId);
                    if (button != null) {
                        editContainer.removeView(button);
                        activeButtons.remove(buttonId);
                        selectedButton = null;
                        Toast.makeText(this, "Button removed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMenuDialog() {
        String gyroStatus = currentLayout.gyroEnabled ? "✓ Gyro ON" : "Gyro OFF";
        String[] options = {
                "💾 Save",
                "✨ Save As",
                "➕ Add Button",
                gyroStatus,  // gyro
                "🗑️ Clear All",
                "❌ Cancel"
        };

        new AlertDialog.Builder(this)
                .setTitle("Edit Menu")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: saveLayout(); break;
                        case 1: showSaveAsDialog(); break;
                        case 2: showAddButtonDialog(); break;
                        case 3: toggleGyro(); break;  // gyro
                        case 4: showClearAllDialog(); break;
                        case 5: finish(); break;
                    }
                })
                .show();
    }

    private void toggleGyro() {
        currentLayout.gyroEnabled = !currentLayout.gyroEnabled;
        String status = currentLayout.gyroEnabled ? "ON" : "OFF";
        Toast.makeText(this, "Gyro: " + status, Toast.LENGTH_SHORT).show();
    }

    private void showAddButtonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF1a1a1a);  // 더 어두운 배경
        mainLayout.setPadding(0, 0, 0, 0);

        // 헤더
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF2a2a2a);
        header.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        header.setLayoutParams(headerParams);

        TextView title = new TextView(this);
        title.setText("Add Button");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        title.setLayoutParams(titleParams);

        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setTextSize(24);
        closeBtn.setBackgroundColor(0xFFF44336);
        closeBtn.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams closeBtnParams = new LinearLayout.LayoutParams(60, 60);
        closeBtn.setLayoutParams(closeBtnParams);

        header.addView(title);
        header.addView(closeBtn);
        mainLayout.addView(header);

        // 탭 바
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(0xFF2a2a2a);
        tabScroll.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams tabScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabScroll.setLayoutParams(tabScrollParams);

        LinearLayout tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);

        String[] categories = {"FACE", "D-PAD", "TRIGGERS", "SYSTEM"};
        Button[] tabButtons = new Button[categories.length];

        for (int i = 0; i < categories.length; i++) {
            Button tab = new Button(this);
            tab.setText(categories[i]);
            tab.setTextColor(0xFFFFFFFF);
            tab.setTextSize(13);
            tab.setTypeface(null, android.graphics.Typeface.BOLD);
            tab.setAllCaps(true);

            // 선택된 탭 스타일
            if (i == 0) {
                tab.setBackgroundColor(0xFFE60012);  // Nintendo Red
            } else {
                tab.setBackgroundColor(0xFF424242);
            }

            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tabParams.setMargins(0, 0, 12, 0);
            tab.setLayoutParams(tabParams);
            tab.setPadding(40, 18, 40, 18);

            tabButtons[i] = tab;
            tabContainer.addView(tab);
        }

        tabScroll.addView(tabContainer);
        mainLayout.addView(tabScroll);

        // 그리드 컨테이너
        ScrollView gridScroll = new ScrollView(this);
        gridScroll.setBackgroundColor(0xFF1a1a1a);
        gridScroll.setPadding(20, 20, 20, 20);
        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(gridContainer);
        mainLayout.addView(gridScroll);

        builder.setView(mainLayout);
        AlertDialog dialog = builder.create();

        // 버튼 분류
        Map<String, List<ButtonTemplate>> buttonsByCategory = new HashMap<>();
        buttonsByCategory.put("FACE", new ArrayList<>());
        buttonsByCategory.put("D-PAD", new ArrayList<>());
        buttonsByCategory.put("TRIGGERS", new ArrayList<>());
        buttonsByCategory.put("SYSTEM", new ArrayList<>());

        for (Map.Entry<String, ButtonTemplate> entry : BUTTON_TEMPLATES.entrySet()) {
            String name = entry.getKey();
            ButtonTemplate template = entry.getValue();

            if (name.contains("A Button") || name.contains("B Button") ||
                    name.contains("X Button") || name.contains("Y Button")) {
                buttonsByCategory.get("FACE").add(template);
            } else if (name.contains("D-Pad")) {
                buttonsByCategory.get("D-PAD").add(template);
            } else if (name.equals("LT") || name.equals("RT") ||
                    name.equals("LB") || name.equals("RB")) {
                buttonsByCategory.get("TRIGGERS").add(template);
            } else {
                buttonsByCategory.get("SYSTEM").add(template);
            }
        }

        // 그리드 업데이트 함수
        Runnable[] updateGrid = new Runnable[1];
        updateGrid[0] = () -> {
            gridContainer.removeAllViews();

            String selectedCategory = "FACE";
            for (int i = 0; i < tabButtons.length; i++) {
                if (tabButtons[i].getCurrentTextColor() == 0xFFFFFFFF &&
                        ((android.graphics.drawable.ColorDrawable)tabButtons[i].getBackground()).getColor() == 0xFFE60012) {
                    selectedCategory = categories[i];
                    break;
                }
            }

            List<ButtonTemplate> buttons = buttonsByCategory.get(selectedCategory);
            if (buttons == null || buttons.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("No buttons available");
                emptyText.setTextColor(0xFF888888);
                emptyText.setTextSize(16);
                emptyText.setGravity(Gravity.CENTER);
                emptyText.setPadding(20, 80, 20, 20);
                gridContainer.addView(emptyText);
                return;
            }

            // 🎮 Nintendo 스타일 그리드 (비율 맞춤)
            LinearLayout currentRow = null;
            int columns = selectedCategory.equals("FACE") ? 2 : 3;  // Face는 2열, 나머지 3열

            for (int i = 0; i < buttons.size(); i++) {
                if (i % columns == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, 0, 0, 16);
                    currentRow.setLayoutParams(rowParams);
                    gridContainer.addView(currentRow);
                }

                ButtonTemplate template = buttons.get(i);

                // 카드 컨테이너
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(0xFF2a2a2a);
                card.setPadding(16, 16, 16, 16);
                card.setGravity(Gravity.CENTER);

                // 비율에 따른 크기 계산
                int cardWidth = selectedCategory.equals("FACE") ? 280 : 200;
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(8, 8, 8, 8);
                card.setLayoutParams(cardParams);

                // 버튼 프리뷰 (실제 비율 반영)
                View preview;
                int previewWidth = template.defaultWidth * 2;  // 2배 크기로 표시
                int previewHeight = template.defaultHeight * 2;

                if (template.drawableResId != 0) {
                    ImageView imagePreview = new ImageView(this);
                    imagePreview.setImageResource(template.drawableResId);
                    imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imagePreview.setBackgroundColor(0xFF1a1a1a);
                    imagePreview.setPadding(12, 12, 12, 12);
                    preview = imagePreview;
                } else {
                    // 텍스트 프리뷰 (원형 버튼)
                    TextView textPreview = new TextView(this);
                    textPreview.setText(template.label);
                    textPreview.setTextColor(0xFFFFFFFF);
                    textPreview.setTextSize(24);
                    textPreview.setTypeface(null, android.graphics.Typeface.BOLD);
                    textPreview.setGravity(Gravity.CENTER);
                    textPreview.setBackgroundColor(Color.parseColor(template.color));
                    preview = textPreview;
                }

                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                        previewWidth, previewHeight);
                previewParams.setMargins(0, 0, 0, 12);
                preview.setLayoutParams(previewParams);

                // 버튼 이름
                TextView nameText = new TextView(this);
                nameText.setText(getButtonDisplayName(template));
                nameText.setTextColor(0xFFCCCCCC);
                nameText.setTextSize(13);
                nameText.setGravity(Gravity.CENTER);
                nameText.setMaxLines(1);
                nameText.setTypeface(null, android.graphics.Typeface.BOLD);

                card.addView(preview);
                card.addView(nameText);

                // 클릭 이벤트
                String templateName = getTemplateName(template);
                card.setOnClickListener(v -> {
                    addButton(templateName);
                    dialog.dismiss();
                });

                currentRow.addView(card);
            }
        };

        // 탭 클릭 이벤트
        for (int i = 0; i < tabButtons.length; i++) {
            int finalI = i;
            tabButtons[i].setOnClickListener(v -> {
                // 모든 탭 비활성화
                for (Button tab : tabButtons) {
                    tab.setBackgroundColor(0xFF424242);
                }
                // 선택된 탭 활성화
                tabButtons[finalI].setBackgroundColor(0xFFE60012);
                updateGrid[0].run();
            });
        }

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        // 첫 카테고리 표시
        updateGrid[0].run();
        dialog.show();
    }

    // 헬퍼 메서드들
    private String getButtonDisplayName(ButtonTemplate template) {
        if (template.label.equals("A")) return "A Button";
        if (template.label.equals("B")) return "B Button";
        if (template.label.equals("X")) return "X Button";
        if (template.label.equals("Y")) return "Y Button";
        if (template.label.equals("↑")) return "D-Pad Up";
        if (template.label.equals("↓")) return "D-Pad Down";
        if (template.label.equals("←")) return "D-Pad Left";
        if (template.label.equals("→")) return "D-Pad Right";
        return template.label;
    }

    private String getTemplateName(ButtonTemplate template) {
        for (Map.Entry<String, ButtonTemplate> entry : BUTTON_TEMPLATES.entrySet()) {
            if (entry.getValue() == template) {
                return entry.getKey();
            }
        }
        return template.label;
    }


    private void addButton(String templateName) {
        ButtonTemplate template = BUTTON_TEMPLATES.get(templateName);
        if (template == null) return;

        String buttonId = template.action + "_" + System.currentTimeMillis();

        LayoutData.ButtonData data = new LayoutData.ButtonData(
                buttonId,
                template.label,
                template.action,
                editContainer.getWidth() / 2 - template.defaultWidth / 2,
                editContainer.getHeight() / 2 - template.defaultHeight / 2,
                template.defaultWidth * 2,
                template.defaultHeight * 2,
                template.color,
                template.drawableResId
        );

        View button = createButton(data);
        editContainer.addView(button);
        activeButtons.put(buttonId, button);

        Toast.makeText(this, "Added: " + template.label, Toast.LENGTH_SHORT).show();
    }

    private void showClearAllDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Buttons?")
                .setMessage("This will remove all buttons from the layout.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    editContainer.removeAllViews();
                    activeButtons.clear();
                    selectedButton = null;
                    Toast.makeText(this, "All buttons cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSaveAsDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Layout Name");
        input.setPadding(50, 30, 50, 30);
        input.setText(currentLayout.name);

        new AlertDialog.Builder(this)
                .setTitle("Save Layout As")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        currentLayout.name = newName;
                        saveLayout();
                        Toast.makeText(this, "Saved as: " + newName, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveLayout() {
        // Collect all button data
        currentLayout.buttons.clear();

        for (Map.Entry<String, View> entry : activeButtons.entrySet()) {
            View button = entry.getValue();
            LayoutData.ButtonData data = (LayoutData.ButtonData) button.getTag();

            if (data != null) {
                // Update position and size
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) button.getLayoutParams();
                data.x = params.leftMargin;
                data.y = params.topMargin;
                data.width = params.width;
                data.height = params.height;

                currentLayout.buttons.add(data);
            }
        }

        LayoutConfig.saveLayoutData(this, currentLayout);
        Toast.makeText(this, "Layout saved!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, UniversalControllerActivity.class);
        intent.putExtra("LAYOUT_NAME", currentLayout.name);
        intent.putExtra("IP", getIntent().getStringExtra("IP"));
        intent.putExtra("PORT", getIntent().getIntExtra("PORT", 7777));
        intent.putExtra("CODE", getIntent().getStringExtra("CODE"));
        intent.putExtra("DEVICE_NAME", getIntent().getStringExtra("DEVICE_NAME"));
        startActivity(intent);
        finish();
    }
}