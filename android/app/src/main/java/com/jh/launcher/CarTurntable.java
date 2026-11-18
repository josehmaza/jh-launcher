package com.jh.launcher;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
 * Las 6 fotos ya vienen recortadas sin fondo (PNG con sombra de contacto de showroom) y se
 * decodifican una sola vez en el hilo de E/S, sirviendo tanto para el Tema Claro (Showroom) como
 * para el Tema Oscuro sin duplicar memoria.
 */
final class CarTurntable {

    static final int COLOR_BLACK = 0;
    static final int COLOR_GREY = 1;

    private static final long SPIN_INTERVAL_MS = 3600;
    private static final long SLIDE_OUT_MS = 200;
    private static final long SLIDE_IN_MS = 380;
    private static final float SLIDE_FRACTION = 0.10f;   // del ancho de la imagen

    private static final String[][] ANGLES = {
            {"car/tucson2013_black_front.png", "car/tucson2013_black_side.png", "car/tucson2013_black_rear.png"},
            {"car/tucson_angle_front_right.png", "car/tucson_angle_side.png", "car/tucson_angle_rear.png"}
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
                        decoded[c][a] = BitmapFactory.decodeStream(in);
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
