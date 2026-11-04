package com.jh.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Apps que se abrieron desde JH Launcher desde que se encendió el radio. Android 7 en adelante no deja ver los
 * procesos de otras apps, así que el panel ABIERTAS usa esta lista cuando no puede verlos directamente.
 * La lista se vacía sola al reiniciar el radio.
 */
final class OpenedApps {

    private static final String PREFS = "opened_apps";

    private OpenedApps() {
    }

    /** Anota la app a la que apunta el intent (si apunta a una). */
    static void record(Context c, Intent intent) {
        if (intent == null) return;
        ComponentName cn = intent.getComponent();
        String pkg = cn != null ? cn.getPackageName() : intent.getPackage();
        if (pkg == null || pkg.equals(c.getPackageName())) return;
        Set<String> set = get(c);
        if (set.add(pkg)) save(c, set);
    }

    static Set<String> get(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long savedBoot = p.getLong("boot", 0);
        if (Math.abs(bootAt - savedBoot) > 60000) {
            // otro encendido del radio: lo anotado ya no está abierto
            p.edit().clear().putLong("boot", bootAt).apply();
            return new LinkedHashSet<>();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String s : p.getString("pkgs", "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void remove(Context c, String pkg) {
        Set<String> set = get(c);
        if (set.remove(pkg)) save(c, set);
    }

    private static void save(Context c, Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) sb.append(s).append(',');
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("pkgs", sb.toString()).putLong("boot", bootAt).apply();
    }
}
