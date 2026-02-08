package com.example.mobcontrol;

import android.app.AlertDialog;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * 간단한 키 선택 다이얼로그
 * 사용법: KeySelectionDialog.show(context, currentKey, listener);
 */
public class KeySelectionDialog {

    /**
     * 키 선택 시 콜백 인터페이스
     */
    public interface OnKeySelectedListener {
        void onKeySelected(String key);
    }

    /**
     * 키 선택 다이얼로그 표시
     * @param context Context (보통 Activity)
     * @param currentKey 현재 선택된 키 (하이라이트용)
     * @param listener 키 선택 시 호출될 리스너
     */
    public static void show(Context context, String currentKey, OnKeySelectedListener listener) {
        List<String> allKeys = getAllKeys();
        String[] keyArray = allKeys.toArray(new String[0]);

        // 현재 선택된 키의 인덱스 찾기
        int currentIndex = allKeys.indexOf(currentKey);
        if (currentIndex < 0) currentIndex = 0;

        // AlertDialog 표시
        new AlertDialog.Builder(context)
                .setTitle("Select Key Mapping")
                .setSingleChoiceItems(keyArray, currentIndex, (dialog, which) -> {
                    String selectedKey = allKeys.get(which);

                    // 섹션 헤더는 선택 불가
                    if (selectedKey.startsWith("═══")) {
                        return;
                    }

                    if (listener != null) {
                        listener.onKeySelected(selectedKey);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * 모든 사용 가능한 키 목록 생성
     */
    private static List<String> getAllKeys() {
        List<String> keys = new ArrayList<>();

        // Letters
        keys.add("═══ Letters ═══");
        for (char c = 'A'; c <= 'Z'; c++) {
            keys.add(String.valueOf(c));
        }

        // Numbers
        keys.add("═══ Numbers ═══");
        for (int i = 0; i <= 9; i++) {
            keys.add(String.valueOf(i));
        }

        // Special Keys
        keys.add("═══ Special Keys ═══");
        keys.add("Space");
        keys.add("Enter");
        keys.add("Shift");
        keys.add("Ctrl");
        keys.add("Alt");
        keys.add("Tab");
        keys.add("Esc");
        keys.add("Backspace");

        // Arrow Keys
        keys.add("═══ Arrow Keys ═══");
        keys.add("Up");
        keys.add("Down");
        keys.add("Left");
        keys.add("Right");

        // Function Keys
        keys.add("═══ Function Keys ═══");
        for (int i = 1; i <= 12; i++) {
            keys.add("F" + i);
        }

        // Mouse Buttons
        keys.add("═══ Mouse Buttons ═══");
        keys.add("Mouse1");
        keys.add("Mouse2");
        keys.add("Mouse3");

        return keys;
    }
}