package com.example.mobcontrol;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class LayoutSelectionActivity extends AppCompatActivity {

    private ListView layoutListView;
    private String serverIP;
    private int serverPort;
    private String pairingCode;
    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_selection);

        serverIP = getIntent().getStringExtra("IP");
        serverPort = getIntent().getIntExtra("PORT", 7777);
        pairingCode = getIntent().getStringExtra("CODE");
        deviceName = getIntent().getStringExtra("DEVICE_NAME");

        layoutListView = findViewById(R.id.layoutListView);
        Button createNewButton = findViewById(R.id.createNewLayoutButton);

        // Initialize defaults
        LayoutConfig.initializeDefaults(this);

        loadLayoutList();

        createNewButton.setOnClickListener(v -> createNewLayout());
    }

    private void loadLayoutList() {
        List<String> layoutNames = LayoutConfig.getAllLayoutNames(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                layoutNames
        );

        layoutListView.setAdapter(adapter);

        layoutListView.setOnItemClickListener((parent, view, position, id) -> {
            String layoutName = layoutNames.get(position);
            showLayoutOptions(layoutName);
        });
    }

    private void showLayoutOptions(String layoutName) {
        String[] options = {"▶️ Use Layout", "✏️ Edit Layout", "📋 Duplicate", "🗑️ Delete"};

        new AlertDialog.Builder(this)
                .setTitle(layoutName)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: useLayout(layoutName); break;
                        case 1: editLayout(layoutName); break;
                        case 2: duplicateLayout(layoutName); break;
                        case 3: deleteLayout(layoutName); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void useLayout(String layoutName) {
        Intent intent = new Intent(this, UniversalControllerActivity.class);
        intent.putExtra("LAYOUT_NAME", layoutName);
        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivity(intent);
    }

    private void editLayout(String layoutName) {
        Intent intent = new Intent(this, EditLayoutActivity.class);
        intent.putExtra("LAYOUT_NAME", layoutName);
        intent.putExtra("IS_NEW", false);
        intent.putExtra("IP", serverIP);
        intent.putExtra("PORT", serverPort);
        intent.putExtra("CODE", pairingCode);
        intent.putExtra("DEVICE_NAME", deviceName);
        startActivity(intent);
    }

    private void duplicateLayout(String layoutName) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("New Layout Name");
        input.setText(layoutName + " Copy");
        input.setPadding(50, 30, 50, 30);

        new AlertDialog.Builder(this)
                .setTitle("Duplicate Layout")
                .setView(input)
                .setPositiveButton("Duplicate", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        LayoutConfig.duplicateLayout(this, layoutName, newName);
                        Toast.makeText(this, "Duplicated as: " + newName, Toast.LENGTH_SHORT).show();
                        loadLayoutList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteLayout(String layoutName) {
        // Prevent deleting default presets
        if (layoutName.equals("Racing") || layoutName.equals("Flight") || layoutName.equals("Game")) {
            Toast.makeText(this, "Cannot delete default presets", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Layout?")
                .setMessage("Delete '" + layoutName + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    LayoutConfig.deleteLayout(this, layoutName);
                    Toast.makeText(this, "Deleted: " + layoutName, Toast.LENGTH_SHORT).show();
                    loadLayoutList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewLayout() {
        // Choose type
        String[] types = {"Racing", "Flight Simulator", "Normal Game Controller"};

        new AlertDialog.Builder(this)
                .setTitle("Select Controller Type")
                .setItems(types, (dialog, which) -> {
                    String selectedType = types[which];
                    showNameInputDialog(selectedType);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNameInputDialog(String controllerType) {
        EditText input = new EditText(this);
        input.setHint("Layout Name");
        input.setPadding(50, 30, 50, 30);

        new AlertDialog.Builder(this)
                .setTitle("Create " + controllerType)
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        Intent intent = new Intent(this, EditLayoutActivity.class);
                        intent.putExtra("LAYOUT_NAME", name);
                        intent.putExtra("CONTROLLER_TYPE", controllerType);  // ✅ 타입 전달
                        intent.putExtra("IS_NEW", true);
                        intent.putExtra("IP", serverIP);
                        intent.putExtra("PORT", serverPort);
                        intent.putExtra("CODE", pairingCode);
                        intent.putExtra("DEVICE_NAME", deviceName);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    @Override
    protected void onResume() {
        super.onResume();
        loadLayoutList();
    }
}