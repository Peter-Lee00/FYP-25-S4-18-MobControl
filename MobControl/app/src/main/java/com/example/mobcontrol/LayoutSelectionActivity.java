package com.example.mobcontrol;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LayoutSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_selection);

        ImageButton backButton = findViewById(R.id.backButton);
        LinearLayout racing1Layout = findViewById(R.id.racing1Layout);
        LinearLayout racing2Layout = findViewById(R.id.racing2Layout);
        LinearLayout gameControllerLayout = findViewById(R.id.gameControllerLayout);
        Button newLayoutButton = findViewById(R.id.newLayoutButton);

        // Preview images
        ImageView racing1Preview = findViewById(R.id.racing1Preview);
        ImageView racing2Preview = findViewById(R.id.racing2Preview);
        ImageView gameControllerPreview = findViewById(R.id.gameControllerPreview);

        // Set preview images (you need to add these drawable resources)
        // racing1Preview.setImageResource(R.drawable.racing1_preview);
        // racing2Preview.setImageResource(R.drawable.racing2_preview);
        // gameControllerPreview.setImageResource(R.drawable.game_controller_preview);

        // For now, just set a placeholder color
        racing1Preview.setBackgroundColor(0xFF4A148C);
        racing2Preview.setBackgroundColor(0xFF4A148C);
        gameControllerPreview.setBackgroundColor(0xFF4A148C);

        backButton.setOnClickListener(v -> {
            // Add visual feedback
            backButton.setAlpha(0.6f);
            backButton.postDelayed(() -> backButton.setAlpha(1.0f), 100);
            finish();
        });

        racing1Layout.setOnClickListener(v -> {
            Toast.makeText(this, "Racing 1 layout selected", Toast.LENGTH_SHORT).show();
            // TODO: Implement layout switching
            finish();
        });

        racing2Layout.setOnClickListener(v -> {
            Toast.makeText(this, "Racing 2 layout selected", Toast.LENGTH_SHORT).show();
            // TODO: Implement layout switching
            finish();
        });

        gameControllerLayout.setOnClickListener(v -> {
            Toast.makeText(this, "Game Controller layout selected", Toast.LENGTH_SHORT).show();
            // TODO: Implement layout switching
            finish();
        });

        newLayoutButton.setOnClickListener(v -> {
            Toast.makeText(this, "Custom layout creator coming soon", Toast.LENGTH_SHORT).show();
            // TODO: Implement custom layout creator
        });
    }
}