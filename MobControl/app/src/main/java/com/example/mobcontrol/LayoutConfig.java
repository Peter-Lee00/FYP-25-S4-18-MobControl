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
    private static Gson gson = new Gson();

    // Save layout
    public static void saveLayoutData(Context context, LayoutData layout) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        Map<String, LayoutData> allLayouts = loadAllLayouts(context);
        allLayouts.put(layout.name, layout);

        String json = gson.toJson(allLayouts);
        prefs.edit().putString(KEY_LAYOUTS, json).apply();
    }

    // Load specific layout
    public static LayoutData loadLayoutData(Context context, String layoutName) {
        Map<String, LayoutData> allLayouts = loadAllLayouts(context);

        LayoutData layout = allLayouts.get(layoutName);

        // If not found, return default preset
        if (layout == null) {
            switch (layoutName) {
                case "Racing":
                    return LayoutData.createRacingPreset();
                case "Flight":
                    return LayoutData.createFlightPreset();
                case "Game":
                    return LayoutData.createGamePreset();
                default:
                    return LayoutData.createRacingPreset();
            }
        }

        return layout;
    }

    // Load all layouts
    private static Map<String, LayoutData> loadAllLayouts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_LAYOUTS, "{}");

        Type type = new TypeToken<Map<String, LayoutData>>(){}.getType();
        Map<String, LayoutData> layouts = gson.fromJson(json, type);

        return layouts != null ? layouts : new HashMap<>();
    }

    // Get all layout names
    public static List<String> getAllLayoutNames(Context context) {
        List<String> names = new ArrayList<>();

        // Add default presets
        names.add("Racing");
        names.add("Flight");
        names.add("Game");

        // Add custom layouts
        Map<String, LayoutData> allLayouts = loadAllLayouts(context);
        for (String name : allLayouts.keySet()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }

        return names;
    }

    // Delete layout
    public static void deleteLayout(Context context, String layoutName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        Map<String, LayoutData> allLayouts = loadAllLayouts(context);
        allLayouts.remove(layoutName);

        String json = gson.toJson(allLayouts);
        prefs.edit().putString(KEY_LAYOUTS, json).apply();
    }

    // Duplicate layout
    public static void duplicateLayout(Context context, String originalName, String newName) {
        LayoutData original = loadLayoutData(context, originalName);
        if (original != null) {
            LayoutData duplicate = gson.fromJson(gson.toJson(original), LayoutData.class);
            duplicate.name = newName;
            saveLayoutData(context, duplicate);
        }
    }

    // Initialize default layouts (first run)
    public static void initializeDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean initialized = prefs.getBoolean("initialized", false);

        if (!initialized) {
            saveLayoutData(context, LayoutData.createRacingPreset());
            saveLayoutData(context, LayoutData.createFlightPreset());
            saveLayoutData(context, LayoutData.createGamePreset());

            prefs.edit().putBoolean("initialized", true).apply();
        }
    }
}