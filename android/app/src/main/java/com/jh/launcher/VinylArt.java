package com.jh.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Dibuja una sola vez el disco de vinilo protagonista (Propuesta A) y su brazo/aguja
 * en Bitmaps estáticos. En la Mali-400 dibujar un Bitmap ya rasterizado no cuesta casi nada,
 * y la aguja solo rota 350 ms al cambiar entre Play y Pausa (cero animación continua).
 */
final class VinylArt {

    private VinylArt() {
    }

    static Bitmap create(int sizePx) {
        return create(sizePx, false);
    }

    static Bitmap create(int sizePx, boolean lightMode) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = sizePx / 120f;
        c.scale(s, s);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);

        // Base del disco con degradado radial realista
        fill.setShader(new RadialGradient(60f, 60f, 57f,
                new int[]{0xFF1E293B, 0xFF0D111A, 0xFF05070B},
                new float[]{0f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(60, 60, 57, fill);
        fill.setShader(null);

        int rimColor = lightMode ? Color.argb(115, 37, 99, 235) : Color.argb(145, 0, 210, 255);
        int grooveAccent = lightMode ? Color.argb(95, 59, 130, 246) : Color.argb(110, 0, 210, 255);
        ring(c, stroke, 57f, 1.5f, rimColor, null);

        // Surcos concéntricos
        ring(c, stroke, 49f, 1.0f, Color.argb(42, 255, 255, 255), null);
        ring(c, stroke, 42f, 1.2f, Color.argb(38, 255, 255, 255), new float[]{16, 5, 10, 4});
        ring(c, stroke, 35f, 1.2f, grooveAccent, new float[]{22, 7, 9, 5});
        ring(c, stroke, 28f, 1.0f, Color.argb(36, 255, 255, 255), null);

        // Haces de luz reflectiva diagonal
        fill.setColor(Color.argb(38, 255, 255, 255));
        RectF disc = new RectF(3, 3, 117, 117);
        Path beam = new Path();
        beam.moveTo(60, 60);
        beam.lineTo(17, 17);
        beam.arcTo(disc, 225f, 34f);
        beam.close();
        beam.moveTo(60, 60);
        beam.lineTo(103, 103);
        beam.arcTo(disc, 45f, 34f);
        beam.close();
        c.drawPath(beam, fill);

        // Etiqueta central y agujero
        fill.setColor(lightMode ? Color.rgb(0x25, 0x63, 0xEB) : Color.rgb(0x00, 0x66, 0xFF));
        c.drawCircle(60, 60, 19f, fill);
        ring(c, stroke, 19f, 2f, lightMode ? Color.rgb(0x60, 0xA5, 0xFA) : Color.rgb(0x00, 0xD2, 0xFF), null);
        ring(c, stroke, 13f, 1f, Color.argb(85, 255, 255, 255), null);
        fill.setColor(Color.rgb(0x0B, 0x0F, 0x17));
        c.drawCircle(60, 60, 4.5f, fill);
        ring(c, stroke, 4.5f, 1.2f, Color.WHITE, null);

        return bmp;
    }

    /**
     * Brazo/aguja minimalista de tocadiscos (espacio base 30x64, pivote de giro en (20, 10)).
     */
    static Bitmap createTonearm(int wPx, int hPx, boolean lightMode) {
        Bitmap bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(wPx / 30f, hPx / 64f);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Base del pivote (20, 10)
        p.setStyle(Paint.Style.FILL);
        p.setColor(lightMode ? 0xFF64748B : 0xFF94A3B8);
        c.drawCircle(20f, 10f, 5f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(lightMode ? 0xFF334155 : 0xFF1E293B);
        c.drawCircle(20f, 10f, 5f, p);

        // Tubo del brazo
        p.setStrokeWidth(2.6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(lightMode ? 0xFF475569 : 0xFFCBD5E1);
        Path arm = new Path();
        arm.moveTo(20f, 10f);
        arm.lineTo(17f, 44f);
        arm.lineTo(10f, 53f);
        c.drawPath(arm, p);

        // Cápsula / aguja en la punta
        c.save();
        c.rotate(28f, 9.5f, 54.5f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(lightMode ? 0xFF2563EB : 0xFF00D2FF);
        c.drawRoundRect(new RectF(6f, 50f, 13f, 59f), 2f, 2f, p);
        c.restore();

        return bmp;
    }

    private static void ring(Canvas c, Paint p, float r, float width, int color, float[] dash) {
        p.setStrokeWidth(width);
        p.setColor(color);
        p.setPathEffect(dash != null ? new DashPathEffect(dash, 0) : null);
        c.drawCircle(60, 60, r, p);
    }
}
