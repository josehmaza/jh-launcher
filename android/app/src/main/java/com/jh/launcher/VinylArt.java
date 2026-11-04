package com.jh.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Dibuja una sola vez el disco de vinilo de la maqueta (SVG de 100x100 con surcos
 * punteados) en un Bitmap. Girar un Bitmap ya rasterizado es casi gratis para la GPU,
 * a diferencia de volver a dibujar los trazos punteados en cada cuadro.
 */
final class VinylArt {

    private VinylArt() {
    }

    static Bitmap create(int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = sizePx / 100f;
        c.scale(s, s);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);

        // Base del disco
        fill.setColor(Color.rgb(0x11, 0x16, 0x22));
        c.drawCircle(50, 50, 48, fill);
        ring(c, stroke, 48, 1.5f, Color.argb(128, 0, 210, 255), null);

        // Surcos concéntricos
        ring(c, stroke, 41, 1.3f, Color.argb(89, 255, 255, 255), new float[]{10, 4, 14, 5});
        ring(c, stroke, 35, 1.2f, Color.argb(115, 0, 210, 255), new float[]{16, 6, 8, 4});
        ring(c, stroke, 29, 1.3f, Color.argb(77, 255, 255, 255), new float[]{8, 3, 18, 6});
        ring(c, stroke, 23, 1.2f, Color.argb(102, 0, 210, 255), new float[]{12, 5, 6, 3});

        // Haces de luz reflectiva (hacen evidente el giro)
        fill.setColor(Color.argb(71, 255, 255, 255));
        RectF disc = new RectF(2, 2, 98, 98);
        Path beam = new Path();
        beam.moveTo(50, 50);
        beam.lineTo(14, 14);
        beam.arcTo(disc, 225f, 35.34f);
        beam.close();
        beam.moveTo(50, 50);
        beam.lineTo(86, 86);
        beam.arcTo(disc, 45f, 35.34f);
        beam.close();
        c.drawPath(beam, fill);

        // Etiqueta central, agujero y marca reflectiva
        fill.setColor(Color.rgb(0x00, 0x66, 0xff));
        c.drawCircle(50, 50, 16, fill);
        ring(c, stroke, 16, 2f, Color.rgb(0x00, 0xd2, 0xff), null);
        fill.setColor(Color.rgb(0x08, 0x0b, 0x12));
        c.drawCircle(50, 50, 4.5f, fill);
        ring(c, stroke, 4.5f, 1f, Color.WHITE, null);
        fill.setColor(Color.WHITE);
        c.drawCircle(50, 39, 2.5f, fill);

        return bmp;
    }

    private static void ring(Canvas c, Paint p, float r, float width, int color, float[] dash) {
        p.setStrokeWidth(width);
        p.setColor(color);
        p.setPathEffect(dash != null ? new DashPathEffect(dash, 0) : null);
        c.drawCircle(50, 50, r, p);
    }
}
