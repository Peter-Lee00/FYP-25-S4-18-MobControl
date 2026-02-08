package com.example.mobcontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public class TriggerButtonView extends View {
    private Paint buttonPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint shadowPaint;
    private String label;
    
    public TriggerButtonView(Context context, String label) {
        super(context);
        this.label = label;
        
        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6);
        borderPaint.setColor(Color.rgb(66, 66, 66));
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(80, 0, 0, 0));
        
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float width = getWidth();
        float height = getHeight();
        float cornerRadius = Math.min(width, height) / 2f;
        
        RectF rect = new RectF(10, 10, width - 10, height - 10);
        RectF shadowRect = new RectF(13, 13, width - 7, height - 7);
        
        // 그림자
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint);
        
        // 그라데이션 버튼
        LinearGradient gradient = new LinearGradient(
            0, 0, 0, height,
            new int[]{Color.rgb(117, 117, 117), Color.rgb(90, 90, 90)},
            new float[]{0f, 1f},
            Shader.TileMode.CLAMP
        );
        buttonPaint.setShader(gradient);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, buttonPaint);
        
        // 테두리
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint);
        
        // 텍스트
        textPaint.setTextSize(height * 0.5f);
        float textY = height / 2f + (textPaint.descent() + textPaint.ascent()) / 2;
        canvas.drawText(label, width / 2f, height / 2f - textY, textPaint);
    }
}
