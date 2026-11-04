package com.jh.launcher;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Gestos a ciegas con 1 dedo sobre el escenario del auto:
 * - Deslizar a la derecha / izquierda: siguiente / anterior
 * - Doble toque: play / pausa
 * - Arrastrar hacia arriba / abajo: subir / bajar volumen
 *
 * Solo se sigue al primer dedo: el multitáctil de las pantallas de radio baratas no es
 * confiable, y un segundo dedo (o un roce de la palma) no debe alterar el gesto.
 *
 * El volumen se comporta igual que el gesto ciego de JH Music, para que se sienta
 * idéntico en las dos apps: zona muerta de 14dp, el arrastre debe ser claramente
 * vertical (1,2 veces más que horizontal) y 240dp de recorrido equivalen a 0-100%.
 * Si el movimiento empieza horizontal, el gesto ya no puede volverse volumen.
 */
final class BlindGestureListener implements View.OnTouchListener {

    interface Callback {
        void onGestureNext();

        void onGesturePrev();

        void onGestureToggle();

        void onVolumeGestureStart();

        /** @param delta recorrido desde el inicio en fracción de la escala completa (+ = subir). */
        void onVolumeGestureMove(float delta);

        void onVolumeGestureEnd();
    }

    private static final long MAX_TAP_MS = 380;
    private static final long DOUBLE_TAP_MS = 420;
    private static final float VOLUME_AXIS_RATIO = 1.2f;

    private final Callback callback;
    private final float swipeThresholdPx;
    private final float deadzonePx;
    private final float volumeFullScalePx;

    private int pointerId = MotionEvent.INVALID_POINTER_ID;
    private float startX;
    private float startY;
    private float currentX;
    private float currentY;
    private long startTime;
    private boolean horizontalLocked;
    private boolean volumeMode;
    private long lastTapTime;

    BlindGestureListener(float density, Callback callback) {
        this.callback = callback;
        this.swipeThresholdPx = 40 * density;
        this.deadzonePx = 14 * density;
        this.volumeFullScalePx = 240 * density;
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerId = e.getPointerId(0);
                startTime = SystemClock.uptimeMillis();
                horizontalLocked = false;
                volumeMode = false;
                startX = currentX = e.getX(0);
                startY = currentY = e.getY(0);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (updatePosition(e)) trackVolume();
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                // Si se levanta el primer dedo y queda otro apoyado, el gesto termina aquí
                if (e.getPointerId(e.getActionIndex()) == pointerId) {
                    finish(e);
                    pointerId = MotionEvent.INVALID_POINTER_ID;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (pointerId != MotionEvent.INVALID_POINTER_ID) finish(e);
                pointerId = MotionEvent.INVALID_POINTER_ID;
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (volumeMode) callback.onVolumeGestureEnd();
                volumeMode = false;
                pointerId = MotionEvent.INVALID_POINTER_ID;
                return true;

            default:
                // ACTION_POINTER_DOWN: un segundo dedo se ignora
                return true;
        }
    }

    /** Actualiza la posición del primer dedo; false si ya no está en pantalla. */
    private boolean updatePosition(MotionEvent e) {
        int index = e.findPointerIndex(pointerId);
        if (index < 0) return false;
        currentX = e.getX(index);
        currentY = e.getY(index);
        return true;
    }

    private void finish(MotionEvent e) {
        // En un deslizamiento rápido la posición final llega recién al levantar el dedo
        // (los MOVE se suavizan): sin esto se perdería el último tramo del gesto
        if (updatePosition(e)) trackVolume();
        if (volumeMode) {
            callback.onVolumeGestureEnd();
            volumeMode = false;
        } else {
            evaluate();
        }
    }

    private void trackVolume() {
        if (!volumeMode) {
            if (horizontalLocked) return;
            float dx = Math.abs(currentX - startX);
            float dy = Math.abs(currentY - startY);
            if (dx > deadzonePx && dx > dy) {
                horizontalLocked = true;
                return;
            }
            if (dy <= deadzonePx || dy <= dx * VOLUME_AXIS_RATIO) return;
            volumeMode = true;
            callback.onVolumeGestureStart();
        }
        // Hacia arriba (Y menor) sube
        callback.onVolumeGestureMove((startY - currentY) / volumeFullScalePx);
    }

    private void evaluate() {
        long now = SystemClock.uptimeMillis();
        float deltaX = currentX - startX;

        if (Math.abs(deltaX) > swipeThresholdPx) {
            if (deltaX > 0) callback.onGestureNext();
            else callback.onGesturePrev();
        } else if (now - startTime < MAX_TAP_MS) {
            if (now - lastTapTime < DOUBLE_TAP_MS) {
                callback.onGestureToggle();
                lastTapTime = 0;
            } else {
                lastTapTime = now;
            }
        }
    }
}
