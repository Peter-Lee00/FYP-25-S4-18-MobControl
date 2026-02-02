package com.example.mobcontrol;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.HashMap;
import java.util.Map;

public class EditLayoutActivity extends AppCompatActivity {

    private RelativeLayout editContainer;
    private String controllerId;
    private Map<String, DraggableButton> editableButtons = new HashMap<>();
    private DraggableButton selectedButton = null;
    private FloatingActionButton fabMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_layout);

        controllerId = getIntent().getStringExtra("CONTROLLER_ID");

        editContainer = findViewById(R.id.editContainer);
        fabMenu = findViewById(R.id.fabMenu);
        TextView instructionText = findViewById(R.id.instructionText);

        instructionText.setText("Drag • Tap to resize • Long press to delete");

        // Create editable buttons from current layout
        createEditableButtons();

        // Floating Menu Button
        fabMenu.setOnClickListener(v -> showMenuDialog());
    }

    private void showMenuDialog() {
        String[] options = {"💾 Save", "✨ Save as New", "🔄 Reset to Default", "🗑️ Delete Layout", "❌ Cancel"};

        new AlertDialog.Builder(this)
                .setTitle("Edit Menu")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Save
                            saveLayout(null);
                            Toast.makeText(this, "Layout saved!", Toast.LENGTH_SHORT).show();
                            finish();
                            break;
                        case 1: // Save as New
                            showSaveAsDialog();
                            break;
                        case 2: // Reset
                            showResetDialog();
                            break;
                        case 3: // Delete Layout
                            showDeleteLayoutDialog();
                            break;
                        case 4: // Cancel
                            showCancelDialog();
                            break;
                    }
                })
                .show();
    }

    private void showCancelDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Discard Changes?")
                .setMessage("Are you sure you want to discard your changes?")
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .setNegativeButton("Stay", null)
                .show();
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Layout?")
                .setMessage("This will restore the default layout.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    LayoutConfig.resetControllerLayout(this, controllerId);
                    Toast.makeText(this, "Layout reset!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteLayoutDialog() {
        java.util.List<String> layoutNames = LayoutConfig.getLayoutNamesForController(this, controllerId);

        // Remove "Default" from list
        layoutNames.remove("Default");

        if (layoutNames.isEmpty()) {
            Toast.makeText(this, "No custom layouts to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] layoutArray = layoutNames.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Delete Layout")
                .setItems(layoutArray, (dialog, which) -> {
                    String selectedLayout = layoutArray[which];
                    String layoutId = controllerId + "_" + selectedLayout;

                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Delete")
                            .setMessage("Delete '" + selectedLayout + "'?")
                            .setPositiveButton("Delete", (d, w) -> {
                                LayoutConfig.resetControllerLayout(this, layoutId);
                                Toast.makeText(this, "Deleted: " + selectedLayout, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createEditableButtons() {
        String[] buttonIds = getIntent().getStringArrayExtra("BUTTON_IDS");
        String[] buttonLabels = getIntent().getStringArrayExtra("BUTTON_LABELS");
        float[] buttonX = getIntent().getFloatArrayExtra("BUTTON_X");
        float[] buttonY = getIntent().getFloatArrayExtra("BUTTON_Y");
        float[] buttonWidth = getIntent().getFloatArrayExtra("BUTTON_WIDTH");
        float[] buttonHeight = getIntent().getFloatArrayExtra("BUTTON_HEIGHT");

        if (buttonIds == null) return;

        for (int i = 0; i < buttonIds.length; i++) {
            createEditableButton(
                    buttonIds[i],
                    buttonLabels[i],
                    buttonX[i],
                    buttonY[i],
                    buttonWidth[i],
                    buttonHeight[i]
            );
        }
    }

    private void createEditableButton(String id, String label, float x, float y, float width, float height) {
        DraggableButton button = new DraggableButton(this, id, label);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                (int) width,
                (int) height
        );
        params.leftMargin = (int) x;
        params.topMargin = (int) y;

        button.setLayoutParams(params);
        editContainer.addView(button);
        editableButtons.put(id, button);
    }

    private void showSaveAsDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Layout Name");
        input.setPadding(50, 30, 50, 30);

        new AlertDialog.Builder(this)
                .setTitle("Save as New Layout")
                .setMessage("Enter a name for this layout:")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String layoutName = input.getText().toString().trim();
                    if (!layoutName.isEmpty()) {
                        saveLayout(layoutName);
                        Toast.makeText(this, "Saved as: " + layoutName, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveLayout(String customName) {
        Map<String, LayoutConfig.ButtonConfig> newLayout = new HashMap<>();

        for (Map.Entry<String, DraggableButton> entry : editableButtons.entrySet()) {
            String id = entry.getKey();
            DraggableButton button = entry.getValue();

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) button.getLayoutParams();

            LayoutConfig.ButtonConfig config = new LayoutConfig.ButtonConfig(
                    params.leftMargin,
                    params.topMargin,
                    params.width,
                    params.height
            );

            newLayout.put(id, config);
        }

        String saveId = customName != null ? controllerId + "_" + customName : controllerId;
        LayoutConfig.saveControllerLayout(this, saveId, newLayout);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("CONTROLLER_ID", controllerId);
        setResult(RESULT_OK, resultIntent);
    }

    private void deleteButton(DraggableButton button) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Button?")
                .setMessage("Remove this button from layout?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    editContainer.removeView(button);
                    editableButtons.remove(button.buttonId);
                    selectedButton = null;
                    Toast.makeText(this, "Button removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Custom Draggable & Resizable Button
    private class DraggableButton extends FrameLayout {
        private Button mainButton;
        private View[] resizeHandles = new View[4]; // 4 corners
        private String buttonId;

        private float dX, dY;
        private boolean isDragging = false;
        private boolean isResizing = false;
        private int resizeCorner = -1;
        private float startX, startY, startWidth, startHeight;
        private long pressStartTime = 0;

        public DraggableButton(AppCompatActivity context, String id, String label) {
            super(context);
            this.buttonId = id;

            // Main button
            mainButton = new Button(context);
            mainButton.setText(label);
            mainButton.setTextSize(12);
            mainButton.setBackgroundColor(Color.parseColor("#2196F3"));
            mainButton.setTextColor(Color.WHITE);
            mainButton.setAlpha(0.8f);

            FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            mainButton.setLayoutParams(buttonParams);
            addView(mainButton);

            // Resize handles (invisible by default)
            int handleSize = 50;
            int[] corners = {
                    Gravity.TOP | Gravity.START,
                    Gravity.TOP | Gravity.END,
                    Gravity.BOTTOM | Gravity.START,
                    Gravity.BOTTOM | Gravity.END
            };

            for (int i = 0; i < 4; i++) {
                resizeHandles[i] = new View(context);
                resizeHandles[i].setBackgroundColor(Color.parseColor("#FFEB3B"));
                resizeHandles[i].setVisibility(GONE);

                FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(handleSize, handleSize);
                handleParams.gravity = corners[i];
                resizeHandles[i].setLayoutParams(handleParams);
                addView(resizeHandles[i]);

                final int corner = i;
                resizeHandles[i].setOnTouchListener((v, event) -> handleResize(event, corner));
            }

            // Drag and select
            mainButton.setOnTouchListener(this::handleTouch);
        }

        private boolean handleTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pressStartTime = System.currentTimeMillis();
                    if (selectedButton != this) {
                        selectButton();
                    }
                    dX = getX() - event.getRawX();
                    dY = getY() - event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    isDragging = true;
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    newX = Math.max(0, Math.min(newX, editContainer.getWidth() - getWidth()));
                    newY = Math.max(0, Math.min(newY, editContainer.getHeight() - getHeight()));

                    setX(newX);
                    setY(newY);

                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
                    params.leftMargin = (int) newX;
                    params.topMargin = (int) newY;
                    setLayoutParams(params);
                    return true;

                case MotionEvent.ACTION_UP:
                    long pressDuration = System.currentTimeMillis() - pressStartTime;

                    if (pressDuration > 800 && !isDragging) {
                        // Long press - delete
                        deleteButton(this);
                    } else if (!isDragging) {
                        // Single tap - toggle resize handles
                        if (selectedButton == this && resizeHandles[0].getVisibility() == VISIBLE) {
                            hideResizeHandles();
                            selectedButton = null;
                        } else {
                            selectButton();
                        }
                    }
                    return true;
            }
            return false;
        }

        private boolean handleResize(MotionEvent event, int corner) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isResizing = true;
                    resizeCorner = corner;
                    startX = getX();
                    startY = getY();
                    startWidth = getWidth();
                    startHeight = getHeight();
                    dX = event.getRawX();
                    dY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isResizing) {
                        float deltaX = event.getRawX() - dX;
                        float deltaY = event.getRawY() - dY;

                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();

                        switch (resizeCorner) {
                            case 0: // Top-Left
                                params.width = Math.max(80, (int)(startWidth - deltaX));
                                params.height = Math.max(60, (int)(startHeight - deltaY));
                                params.leftMargin = (int)(startX + deltaX);
                                params.topMargin = (int)(startY + deltaY);
                                break;
                            case 1: // Top-Right
                                params.width = Math.max(80, (int)(startWidth + deltaX));
                                params.height = Math.max(60, (int)(startHeight - deltaY));
                                params.topMargin = (int)(startY + deltaY);
                                break;
                            case 2: // Bottom-Left
                                params.width = Math.max(80, (int)(startWidth - deltaX));
                                params.height = Math.max(60, (int)(startHeight + deltaY));
                                params.leftMargin = (int)(startX + deltaX);
                                break;
                            case 3: // Bottom-Right
                                params.width = Math.max(80, (int)(startWidth + deltaX));
                                params.height = Math.max(60, (int)(startHeight + deltaY));
                                break;
                        }

                        setLayoutParams(params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    isResizing = false;
                    return true;
            }
            return false;
        }

        private void selectButton() {
            // Deselect previous
            if (selectedButton != null && selectedButton != this) {
                selectedButton.hideResizeHandles();
            }

            selectedButton = this;
            showResizeHandles();
            bringToFront();
            mainButton.setAlpha(1.0f);
        }

        private void showResizeHandles() {
            for (View handle : resizeHandles) {
                handle.setVisibility(VISIBLE);
            }
        }

        private void hideResizeHandles() {
            for (View handle : resizeHandles) {
                handle.setVisibility(GONE);
            }
            mainButton.setAlpha(0.8f);
        }
    }
}