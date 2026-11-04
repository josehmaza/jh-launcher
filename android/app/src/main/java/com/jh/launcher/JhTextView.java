package com.jh.launcher;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * TextView con las tipografías de la maqueta (Plus Jakarta Sans, JetBrains Mono y
 * Space Grotesk), cargadas una sola vez desde assets/fonts y compartidas por todas las vistas.
 */
public class JhTextView extends TextView {

    private static final String[] FONT_FILES = {
            "fonts/jakarta_semibold.ttf",   // semibold (600)
            "fonts/jakarta_bold.ttf",       // bold (700)
            "fonts/jakarta_extrabold.ttf",  // extrabold (800)
            "fonts/mono_extrabold.ttf",     // mono: reloj, frecuencia, porcentajes
            "fonts/grotesk_bold.ttf"        // brand: logotipo HYUNDAI y HUD de gestos
    };
    private static final Typeface[] CACHE = new Typeface[FONT_FILES.length];

    public JhTextView(Context context) {
        this(context, null);
    }

    public JhTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.JhTextView);
        int font = a.getInt(R.styleable.JhTextView_jhFont, 1);
        a.recycle();
        setTypeface(typeface(context, font));
        // Igual que el line-height ajustado de la maqueta
        setIncludeFontPadding(false);
    }

    /** Para vistas creadas por código: 0 semibold, 1 bold, 2 extrabold, 3 mono, 4 grotesk. */
    public void setJhFont(int index) {
        setTypeface(typeface(getContext(), index));
    }

    private static Typeface typeface(Context context, int index) {
        if (CACHE[index] == null) {
            try {
                CACHE[index] = Typeface.createFromAsset(context.getAssets(), FONT_FILES[index]);
            } catch (RuntimeException e) {
                CACHE[index] = index == 3 ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD;
            }
        }
        return CACHE[index];
    }
}
