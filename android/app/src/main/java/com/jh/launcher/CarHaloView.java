package com.jh.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Escenario y plataforma elíptica bajo el Tucson.
 * - En Modo Claro (Showroom): cielo azul suave radial + anillo elíptico azul cobalto sutil.
 * - En Modo Oscuro (Obsidian): resplandor cian elíptico + anillo neón cian bajo las llantas.
 * Es 100 % estático: los shaders se calculan solo al medir o al alternar Día/Noche.
 */
public class CarHaloView extends View {

    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();
    private boolean lightMode;

    public CarHaloView(Context context) {
        super(context);
        init();
    }

    public CarHaloView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
    }

    public void setLightMode(boolean light) {
        if (this.lightMode == light) return;
        this.lightMode = light;
        rebuildShaders(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShaders(w, h);
    }

    private void rebuildShaders(int w, int h) {
        if (w <= 0 || h <= 0) return;

        // 1. Degradado superior/central de cabina (cielo suave en Light / aura cian tenue en Dark)
        float skyCy = h * 0.44f;
        float skyR = w * 0.52f;
        int[] skyColors = lightMode
                ? new int[]{0xFFD4E6FF, 0xFFEAF3FF, 0x00FFFFFF}
                : new int[]{0x2200D2FF, 0x0A00D2FF, 0x0000D2FF};
        RadialGradient skyGrad = new RadialGradient(w / 2f, skyCy, skyR,
                skyColors, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        Matrix skyMat = new Matrix();
        skyMat.setScale(1f, 0.72f, w / 2f, skyCy);
        skyGrad.setLocalMatrix(skyMat);
        skyPaint.setShader(skyGrad);

        // 2. Plataforma elíptica en el piso justo bajo las llantas del Tucson
        float floorCy = h * 0.64f;
        float floorRx = w * 0.42f;
        float floorRy = 26f * getResources().getDisplayMetrics().density;
        int[] haloColors = lightMode
                ? new int[]{0x292563EB, 0x102563EB, 0x002563EB}
                : new int[]{0x5800D2FF, 0x2200D2FF, 0x0000D2FF};
        RadialGradient floorGrad = new RadialGradient(w / 2f, floorCy, floorRx,
                haloColors, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        Matrix floorMat = new Matrix();
        floorMat.setScale(1f, floorRy / Math.max(1f, floorRx), w / 2f, floorCy);
        floorGrad.setLocalMatrix(floorMat);
        haloPaint.setShader(floorGrad);

        ringRect.set(w / 2f - floorRx * 0.94f, floorCy - floorRy * 0.85f,
                w / 2f + floorRx * 0.94f, floorCy + floorRy * 0.85f);
        ringPaint.setColor(lightMode ? 0x382563EB : 0x7800D2FF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, skyPaint);
        canvas.drawRect(0, 0, w, h, haloPaint);
        canvas.drawOval(ringRect, ringPaint);
    }
}
