package com.jh.launcher;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Tokens y generadores de fondos/estados para los dos temas del launcher:
 * - Light Mode (Opción 1 · Showroom Claro): blanco porcelana, gris hielo y Azul Cobalto OEM (#2563EB).
 * - Dark Mode (Obsidian Modern): negro obsidiana, pizarra profundo y Cian (#00D2FF).
 *
 * Cero blur dinámico: usa GradientDrawable opacos con bordes de 1dp para rendimiento máximo en Mali-400.
 */
final class ThemePalette {

    final boolean light;
    final float density;

    final int winBg;
    final int barBg;
    final int cardBg;
    final int cardVehTop;
    final int cardVehBot;
    final int cardMusicTop;
    final int cardMusicBot;
    final int surface2;
    final int surface3;
    final int border;

    final int accent;
    final int accentSoft;
    final int accentBorder;

    final int emerald;
    final int emeraldSoft;
    final int emeraldBorder;
    final int amber;
    final int crimson;

    final int text1;
    final int text2;
    final int textMuted;
    final int trackBg;

    ThemePalette(boolean light, float density) {
        this.light = light;
        this.density = density;

        if (light) {
            winBg = 0xFFE8EEF6;
            barBg = 0xFFF4F7FB;
            cardBg = 0xFFFFFFFF;
            cardVehTop = 0xFFFFFFFF;
            cardVehBot = 0xFFF5F9FF;
            cardMusicTop = 0xFFFFFFFF;
            cardMusicBot = 0xFFF7FAFE;
            surface2 = 0xFFEEF2F8;
            surface3 = 0xFFE2E8F0;
            border = 0xFFD8E1EC;

            accent = 0xFF2563EB;
            accentSoft = 0x1F2563EB;
            accentBorder = 0x662563EB;

            emerald = 0xFF059669;
            emeraldSoft = 0x1A059669;
            emeraldBorder = 0x59059669;
            amber = 0xFFD97706;
            crimson = 0xFFEF4444;

            text1 = 0xFF0F172A;
            text2 = 0xFF64748B;
            textMuted = 0xFF94A3B8;
            trackBg = 0xFFE2E8F0;
        } else {
            winBg = 0xFF080B10;
            barBg = 0xFF090D14;
            cardBg = 0xFF0E131C;
            cardVehTop = 0xFF10202E;
            cardVehBot = 0xFF0C1017;
            cardMusicTop = 0xFF141A26;
            cardMusicBot = 0xFF0C1017;
            surface2 = 0xFF131924;
            surface3 = 0xFF1B2434;
            border = 0x16FFFFFF;

            accent = 0xFF00D2FF;
            accentSoft = 0x2200D2FF;
            accentBorder = 0x7300D2FF;

            emerald = 0xFF00E676;
            emeraldSoft = 0x1A00E676;
            emeraldBorder = 0x6600E676;
            amber = 0xFFFFAA00;
            crimson = 0xFFFF3366;

            text1 = 0xFFFFFFFF;
            text2 = 0xFF94A3B8;
            textMuted = 0xFF526077;
            trackBg = 0x1AFFFFFF;
        }
    }

    int dp(float v) {
        return Math.max(1, Math.round(v * density));
    }

    GradientDrawable box(int fill, int strokeColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radiusDp * density);
        if (strokeColor != 0) d.setStroke(dp(1f), strokeColor);
        return d;
    }

    GradientDrawable gradientCard(int topColor, int botColor, int strokeColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{topColor, botColor});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp * density);
        d.setStroke(dp(1f), strokeColor);
        return d;
    }

    GradientDrawable circle(int fill, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (strokeColor != 0 && strokeDp > 0f) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    /** Selector de pestañas (JH MUSIC / BLUETOOTH AUDIO o Negro / Gris Acero). */
    Drawable selectableTab(float radiusDp) {
        StateListDrawable sld = new StateListDrawable();
        int activeFill = light ? cardBg : accentSoft;
        int activeStroke = light ? border : accentBorder;
        sld.addState(new int[]{android.R.attr.state_selected}, box(activeFill, activeStroke, radiusDp));
        sld.addState(new int[]{}, box(0x00000000, 0, radiusDp));
        return sld;
    }

    /** Botón abrir app (esquina superior derecha de Música y Video). */
    Drawable openButtonBg() {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, box(accent, accent, 12f));
        sld.addState(new int[]{android.R.attr.state_activated}, box(accent, accent, 12f));
        sld.addState(new int[]{}, box(surface2, border, 12f));
        return sld;
    }

    /** Botones Anterior y Siguiente estilo "Limpios" (sin caja fija, iluminan al tocar). */
    Drawable cleanSkipBg() {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, box(accentSoft, accentBorder, 12f));
        sld.addState(new int[]{android.R.attr.state_activated}, box(accentSoft, accentBorder, 12f));
        sld.addState(new int[]{}, box(0x00000000, 0, 12f));
        return sld;
    }

    /** Botón central Play/Pausa circular suave (estilo "Limpios"). */
    Drawable cleanPlayBg() {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, circle(accent, accent, 1.5f));
        sld.addState(new int[]{android.R.attr.state_activated}, circle(accent, accent, 1.5f));
        sld.addState(new int[]{}, circle(accentSoft, accentBorder, 1.5f));
        return sld;
    }

    /** Botón del dock inferior. */
    Drawable dockBtnBg(boolean highlightBorder) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, box(accent, accent, 10f));
        sld.addState(new int[]{android.R.attr.state_activated}, box(accentSoft, accent, 10f));
        sld.addState(new int[]{}, box(surface2, highlightBorder ? accentBorder : border, 10f));
        return sld;
    }

    /** Puntos de ángulo del Tucson (0, 1, 2). */
    Drawable angleDotBg() {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_selected}, box(accent, 0, 3f));
        sld.addState(new int[]{}, circle(textMuted, 0, 0f));
        return sld;
    }

    ColorStateList selectableText() {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_selected}, {}},
                new int[]{text1, textMuted});
    }

    ColorStateList selectableIcon() {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_selected}, {}},
                new int[]{accent, textMuted});
    }

    ColorStateList cleanSkipIconTint() {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {android.R.attr.state_activated}, {}},
                new int[]{accent, accent, text2});
    }

    ColorStateList cleanPlayIconTint() {
        int pressedIcon = light ? 0xFFFFFFFF : 0xFF06090F;
        return new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {android.R.attr.state_activated}, {}},
                new int[]{pressedIcon, pressedIcon, accent});
    }

    ColorStateList openIconTint() {
        int pressedIcon = light ? 0xFFFFFFFF : 0xFF06090F;
        return new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {android.R.attr.state_activated}, {}},
                new int[]{pressedIcon, pressedIcon, text1});
    }

    Drawable shuffleButtonBg(boolean active) {
        StateListDrawable sld = new StateListDrawable();
        if (active) {
            int fill = light ? 0xFFDBEAFE : accentSoft;
            int stroke = light ? 0xFF93C5FD : accentBorder;
            sld.addState(new int[]{android.R.attr.state_pressed}, box(accent, accent, 12f));
            sld.addState(new int[]{}, box(fill, stroke, 12f));
        } else {
            sld.addState(new int[]{android.R.attr.state_pressed}, box(accentSoft, accentBorder, 12f));
            sld.addState(new int[]{}, box(surface2, border, 12f));
        }
        return sld;
    }

    ColorStateList shuffleIconTint(boolean active) {
        int pressedIcon = light ? 0xFFFFFFFF : 0xFF06090F;
        int normalIcon = active ? (light ? 0xFF1D4ED8 : accent) : textMuted;
        return new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {}},
                new int[]{pressedIcon, normalIcon});
    }

    ColorStateList dockContentTint(boolean accentDefault) {
        int pressedColor = light ? 0xFFFFFFFF : 0xFF06090F;
        int normalColor = accentDefault ? accent : text2;
        return new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {android.R.attr.state_activated}, {}},
                new int[]{pressedColor, text1, normalColor});
    }

    ColorStateList dockIconTint(boolean accentDefault) {
        int pressedColor = light ? 0xFFFFFFFF : 0xFF06090F;
        int normalColor = accentDefault ? accent : text2;
        return new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {android.R.attr.state_activated}, {}},
                new int[]{pressedColor, accent, normalColor});
    }
}
