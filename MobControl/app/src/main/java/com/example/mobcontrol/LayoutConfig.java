package com.example.mobcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutConfig {

    private static final String PREFS_NAME = "LayoutConfigs";
    private static final String KEY_LAYOUTS = "layouts";

    // Button configuration - PUBLIC
    public static class ButtonConfig {
        public float x;
        public float y;
        public float width;
        public float height;

        public ButtonConfig() {}

        public ButtonConfig(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static void saveButtonConfig(Context context, String controllerId, String buttonId, ButtonConfig config) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        Map<String, Map<String, ButtonConfig>> allLayouts = loadAllLayouts(context);
        Map<String, ButtonConfig> controllerLayout = allLayouts.get(controllerId);
        if (controllerLayout == null) {
            controllerLayout = new HashMap<>();
            allLayouts.put(controllerId, controllerLayout);
        }

        controllerLayout.put(buttonId, config);
        String json = gson.toJson(allLayouts);
        prefs.edit().putString(KEY_LAYOUTS, json).apply();
    }

    public static ButtonConfig loadButtonConfig(Context context, String controllerId, String buttonId) {
        Map<String, Map<String, ButtonConfig>> allLayouts = loadAllLayouts(context);
        Map<String, ButtonConfig> controllerLayout = allLayouts.get(controllerId);

        if (controllerLayout != null) {
            return controllerLayout.get(buttonId);
        }
        return null;
    }

    public static void saveControllerLayout(Context context, String controllerId, Map<String, ButtonConfig> layout) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        Map<String, Map<String, ButtonConfig>> allLayouts = loadAllLayouts(context);
        allLayouts.put(controllerId, layout);

        String json = gson.toJson(allLayouts);
        prefs.edit().putString(KEY_LAYOUTS, json).apply();
    }

    public static Map<String, ButtonConfig> loadControllerLayout(Context context, String controllerId) {
        Map<String, Map<String, ButtonConfig>> allLayouts = loadAllLayouts(context);
        return allLayouts.get(controllerId);
    }

    public static void resetControllerLayout(Context context, String controllerId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        Map<String, Map<String, ButtonConfig>> allLayouts = loadAllLayouts(context);
        allLayouts.remove(controllerId);

        String json = gson.toJson(allLayouts);
        prefs.edit().putString(KEY_LAYOUTS, json).apply();
    }

    // NEW: Get all saved layouts for a controller type
    public static List<String> getLayoutNamesForController(Context context, String baseControllerId) {
        Map<String, Map<String, ButtonConfig>> allLayouts = loadAllLayouts(context);
        List<String> layoutNames = new ArrayList<>();

        // Add default layout
        layoutNames.add("Default");

        // Find all custom layouts
        for (String layoutId : allLayouts.keySet()) {
            if (layoutId.startsWith(baseControllerId + "_")) {
                String customName = layoutId.substring(baseControllerId.length() + 1);
                layoutNames.add(customName);
            }
        }

        return layoutNames;
    }

    private static Map<String, Map<String, ButtonConfig>> loadAllLayouts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_LAYOUTS, "{}");

        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Map<String, ButtonConfig>>>(){}.getType();
        Map<String, Map<String, ButtonConfig>> layouts = gson.fromJson(json, type);

        return layouts != null ? layouts : new HashMap<>();
    }

    public static void applyButtonLayout(Context context, android.view.View button, String controllerId, String buttonId) {
        ButtonConfig config = loadButtonConfig(context, controllerId, buttonId);
        if (config != null && button != null) {
            android.view.ViewGroup.LayoutParams params = button.getLayoutParams();

            if (params instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams relParams =
                        (android.widget.RelativeLayout.LayoutParams) params;
                relParams.leftMargin = (int) config.x;
                relParams.topMargin = (int) config.y;
                relParams.width = (int) config.width;
                relParams.height = (int) config.height;
                button.setLayoutParams(relParams);
            }
        }
    }
}