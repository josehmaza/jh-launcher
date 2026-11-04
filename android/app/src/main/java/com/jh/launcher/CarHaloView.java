package com.jh.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Resplandor cian elíptico bajo el auto que lo asienta en la tarjeta. Es estático: el degradado
 * se arma una sola vez al medir la vista, así que no hay nada que repintar después.
 */
public class CarHaloView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CarHaloView(Context context) {
        super(context);
    }

    public CarHaloView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        // Círculo de radio w/2 aplastado verticalmente hasta el alto de la vista: una elipse
        RadialGradient gradient = new RadialGradient(w / 2f, h / 2f, w / 2f,
                new int[]{0x6600d2ff, 0x2400d2ff, 0x0000d2ff},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        Matrix squash = new Matrix();
        squash.setScale(1f, h / (float) w, w / 2f, h / 2f);
        gradient.setLocalMatrix(squash);
        paint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
