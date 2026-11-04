package com.jh.launcher;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Rotación automática de los renders del Tucson 2013 (frente, perfil, trasera) en Negro Obsidiana
 * o Gris Acero. El cambio de ángulo desliza el auto de lado con un fundido.
 *
 * Las 6 fotos se decodifican una vez en el hilo de E/S y quedan en memoria. A cada una se le
 * funden los 4 bordes (máscara de transparencia) en ese mismo momento, así el auto se disuelve en
 * el fondo de la tarjeta sin que dibujar cueste nada extra: ARGB_8888, ~800 KB cada una (~4,8 MB).
 */
final class CarTurntable {

    static final int COLOR_BLACK = 0;
    static final int COLOR_GREY = 1;

    private static final long SPIN_INTERVAL_MS = 3600;
    private static final long SLIDE_OUT_MS = 200;
    private static final long SLIDE_IN_MS = 380;
    private static final float SLIDE_FRACTION = 0.10f;   // del ancho de la imagen

    // Fundido de bordes: porcentaje del alto/ancho donde la foto llega a opacidad total
    private static final float FADE_V = 0.24f;
    private static final float FADE_H = 0.09f;

    private static final String[][] ANGLES = {
            {"car/tucson2013_black_front.jpg", "car/tucson2013_black_side.jpg", "car/tucson2013_black_rear.jpg"},
            {"car/tucson_angle_front_right.jpg", "car/tucson_angle_side.jpg", "car/tucson_angle_rear.jpg"}
    };

    private final ImageView view;
    private final Handler mainHandler;
    private final Bitmap[][] cache = new Bitmap[2][3];

    private View[] dots;
    private int color = COLOR_BLACK;
    private int angle = 0;
    private boolean spinning;

    private final Runnable spinStep = new Runnable() {
        @Override
        public void run() {
            angle = (angle + 1) % ANGLES[color].length;
            show(1);
            mainHandler.postDelayed(this, SPIN_INTERVAL_MS);
        }
    };

    CarTurntable(ImageView view, Handler mainHandler) {
        this.view = view;
        this.mainHandler = mainHandler;
    }

    /** Puntos que indican el ángulo actual (uno por foto). */
    void setIndicator(View[] dots) {
        this.dots = dots;
        updateDots();
    }

    /** Decodifica todas las fotos en el hilo de E/S y muestra la primera al terminar. */
    void preload(final AssetManager assets, Handler ioHandler) {
        ioHandler.post(() -> {
            final Bitmap[][] decoded = new Bitmap[2][3];
            for (int c = 0; c < ANGLES.length; c++) {
                for (int a = 0; a < ANGLES[c].length; a++) {
                    try (InputStream in = assets.open(ANGLES[c][a])) {
                        Bitmap src = BitmapFactory.decodeStream(in);
                        if (src != null) {
                            decoded[c][a] = fadeEdges(src);
                            src.recycle();
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            mainHandler.post(() -> {
                for (int c = 0; c < decoded.length; c++) {
                    System.arraycopy(decoded[c], 0, cache[c], 0, decoded[c].length);
                }
                show(0);
            });
        });
    }

    /** Copia de la foto con los 4 bordes desvanecidos hasta transparente. */
    private static Bitmap fadeEdges(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(src, 0, 0, null);

        Paint mask = new Paint();
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        int clear = 0x00000000;
        int solid = 0xFF000000;

        mask.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{clear, solid, solid, clear},
                new float[]{0f, FADE_V, 1f - FADE_V, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mask);

        mask.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{clear, solid, solid, clear},
                new float[]{0f, FADE_H, 1f - FADE_H, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mask);
        return out;
    }

    void setColor(int newColor) {
        if (color == newColor) return;
        color = newColor;
        show(0);
    }

    int getColor() {
        return color;
    }

    void start() {
        if (spinning) return;
        spinning = true;
        mainHandler.postDelayed(spinStep, SPIN_INTERVAL_MS);
    }

    void stop() {
        spinning = false;
        mainHandler.removeCallbacks(spinStep);
    }

    /**
     * @param direction 1 = el auto sale hacia la izquierda y el nuevo entra desde la derecha;
     *                  0 = solo fundido (primera foto o cambio de color).
     */
    private void show(int direction) {
        updateDots();
        final Bitmap bmp = cache[color][angle];
        if (bmp == null) return;
        view.animate().cancel();
        if (direction == 0 && view.getDrawable() == null) {
            view.setImageBitmap(bmp);
            view.setAlpha(1f);
            view.setTranslationX(0f);
            return;
        }
        final float shift = view.getWidth() * SLIDE_FRACTION * direction;
        view.animate().alpha(0f).translationX(-shift).setDuration(SLIDE_OUT_MS)
                .withEndAction(() -> {
                    view.setImageBitmap(bmp);
                    view.setTranslationX(shift);
                    view.animate().alpha(1f).translationX(0f).setDuration(SLIDE_IN_MS)
                            .setInterpolator(new DecelerateInterpolator(1.6f));
                });
    }

    private void updateDots() {
        if (dots == null) return;
        for (int i = 0; i < dots.length; i++) {
            boolean on = i == angle;
            dots[i].setSelected(on);
            dots[i].animate().scaleX(on ? 3f : 1f).setDuration(250);
        }
    }
}
