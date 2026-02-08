package com.example.mobcontrol;

import java.util.ArrayList;
import java.util.List;

public class LayoutData {
    public String name;
    public String controllerType;
    public boolean gyroEnabled;


    public List<ButtonData> buttons;

    public LayoutData() {
        buttons = new ArrayList<>();
        controllerType = "Normal Game Controller";  // default
        gyroEnabled = false;
    }

    // LayoutData.java의 ButtonData 클래스 수정

    public static class ButtonData {
        public String id;
        public String label;
        public String action;
        public int x;
        public int y;
        public int width;
        public int height;
        public String color;
        public int drawableResId;      // ✅ 추가!
        public String mappedKey;       // 키보드 매핑용

        public ButtonData() {}

        public ButtonData(String id, String label, String action, int x, int y,
                          int width, int height, String color) {
            this.id = id;
            this.label = label;
            this.action = action;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.drawableResId = 0;     // 기본값
        }

        public ButtonData(String id, String label, String action, int x, int y,
                          int width, int height, String color, int drawableResId) {
            this(id, label, action, x, y, width, height, color);
            this.drawableResId = drawableResId;
        }
    }

    // Default presets
    public static LayoutData createRacingPreset() {
        LayoutData layout = new LayoutData();
        layout.name = "Racing";

        // Left turn - mapped to A
        layout.buttons.add(new ButtonData(
                "left_turn", "←", "left", 30, 500, 80, 80, "#2196F3"));

        // Right turn - mapped to D
        layout.buttons.add(new ButtonData(
                "right_turn", "→", "right", 30, 600, 80, 80, "#FF5722"));

        // Turbo - mapped to Shift
        layout.buttons.add(new ButtonData(
                "turbo", "TURBO", "turbo", 20, 380, 100, 60, "#4CAF50"));

        // Gas - mapped to W
        layout.buttons.add(new ButtonData(
                "gas", "GAS", "gas", 800, 350, 200, 400, "#388E3C"));

        // Pause - mapped to P
        layout.buttons.add(new ButtonData(
                "pause", "PAUSE", "pause", 450, 350, 80, 60, "#9E9E9E"));

        return layout;
    }

    public static LayoutData createFlightPreset() {
        LayoutData layout = new LayoutData();
        layout.name = "Flight";

        // Machine Gun - mapped to Mouse1
        layout.buttons.add(new ButtonData(
                "machine_gun", "GUN", "machine_gun", 50, 400, 150, 150, "#FF5722"));

        // Rocket - mapped to Mouse2
        layout.buttons.add(new ButtonData(
                "rocket", "ROCKET", "rocket", 50, 580, 150, 150, "#FF9800"));

        // Turbo - mapped to Shift
        layout.buttons.add(new ButtonData(
                "turbo", "TURBO", "turbo", 800, 400, 150, 150, "#4CAF50"));

        // Pause - mapped to P
        layout.buttons.add(new ButtonData(
                "pause", "PAUSE", "pause", 450, 50, 100, 80, "#9E9E9E"));

        return layout;
    }

    public static LayoutData createGamePreset() {
        LayoutData layout = new LayoutData();
        layout.name = "Game";

        // WASD
        layout.buttons.add(new ButtonData(
                "key_w", "W", "key_w", 100, 400, 60, 60, "#757575"));
        layout.buttons.add(new ButtonData(
                "key_a", "A", "key_a", 40, 470, 60, 60, "#757575"));
        layout.buttons.add(new ButtonData(
                "key_s", "S", "key_s", 100, 470, 60, 60, "#757575"));
        layout.buttons.add(new ButtonData(
                "key_d", "D", "key_d", 160, 470, 60, 60, "#757575"));

        // Action buttons
        layout.buttons.add(new ButtonData(
                "key_space", "SPACE", "key_space", 800, 500, 150, 60, "#2196F3"));
        layout.buttons.add(new ButtonData(
                "key_shift", "SHIFT", "key_shift", 800, 580, 150, 60, "#FF5722"));

        return layout;
    }
}