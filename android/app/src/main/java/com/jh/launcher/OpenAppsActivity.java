package com.jh.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Apps abiertas (reemplaza al botón «recientes» de la barra del launcher de fábrica, que JH Launcher oculta).
 * Lista las apps con ícono que tienen un proceso en marcha, deja cerrarlas una a una o todas, y abrir una
 * tocando su fila. Nunca toca las apps de fábrica del radio (Jancar, Autochips, MediaTek, Android).
 */
public class OpenAppsActivity extends Activity {

    private static final int ICON_DP = 52;
    private static final String[] PROTECTED = {"com.jancar.", "com.autochips.", "com.mediatek.", "com.android.systemui",
            "com.android.phone", "com.android.settings", "android"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private ListView list;
    private TextView count, empty, info;
    private List<Entry> apps = new ArrayList<>();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_apps);
        list = findViewById(R.id.openAppsList);
        count = findViewById(R.id.openAppsCount);
        empty = findViewById(R.id.openAppsEmpty);
        info = findViewById(R.id.openAppsInfo);
        findViewById(R.id.openAppsBack).setOnClickListener(v -> finish());
        findViewById(R.id.openAppsCloseAll).setOnClickListener(v -> closeAll());
        list.setAdapter(new Adapter());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        main.removeCallbacksAndMessages(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    // ------------------------------------------------------------------ datos

    private void load() {
        final PackageManager pm = getPackageManager();
        final int iconPx = Math.round(ICON_DP * getResources().getDisplayMetrics().density);
        new Thread(() -> {
            Set<String> running = new HashSet<>();
            int[] stats = new int[2]; // procesos vistos por ps, de ellos de usuario
            readProcesses(running, stats);
            // Si ps solo enseña procesos propios, Android está escondiendo los de las demás apps (Android 7 en
            // adelante): se usa la lista de lo que se abrió desde este launcher desde que se encendió el radio
            final boolean sees = stats[0] - stats[1] >= 8;
            if (!sees) running.addAll(OpenedApps.get(this));

            // Solo apps con ícono, para mostrar lo mismo que ve la persona en el menú
            Set<String> homes = new HashSet<>();
            for (ResolveInfo ri : pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
                homes.add(ri.activityInfo.packageName);
            }
            Map<String, Entry> byPkg = new HashMap<>();
            for (ResolveInfo ri : pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || homes.contains(pkg) || isProtected(pkg)) continue;
                if (!running.contains(pkg) || byPkg.containsKey(pkg)) continue;
                CharSequence label = ri.loadLabel(pm);
                byPkg.put(pkg, new Entry(label != null ? label.toString() : pkg, pkg,
                        new ComponentName(pkg, ri.activityInfo.name), toBitmap(ri.loadIcon(pm), iconPx)));
            }
            final List<Entry> found = new ArrayList<>(byPkg.values());
            Collections.sort(found, new Comparator<Entry>() {
                @Override
                public int compare(Entry a, Entry b) {
                    return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
                }
            });
            final String text = (sees ? "Procesos reales" : "Android oculta los procesos de otras apps: "
                    + "muestro las que abriste desde este launcher")
                    + " · vistos: " + stats[0] + " · de usuario: " + stats[1]
                    + " · cerrables: " + found.size();
            runOnUiThread(() -> {
                if (destroyed) return;
                apps = found;
                count.setText(found.size() + (found.size() == 1 ? " app" : " apps"));
                empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                info.setText(text);
                ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            });
        }, "open-apps").start();
    }

    /** Procesos en marcha: lo que ve «ps» más lo que informa ActivityManager (en Android 6 ven todo el equipo). */
    private void readProcesses(Set<String> out, int[] stats) {
        try {
            Process p = new ProcessBuilder("ps").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] t = line.trim().split("\\s+");
                    if (t.length < 2 || t[0].equals("USER")) continue;
                    stats[0]++;
                    // Las apps instaladas corren como u0_aNN; las de fábrica con uid system/radio/etc. quedan fuera
                    if (!t[0].startsWith("u") || !t[0].contains("_a")) continue;
                    stats[1]++;
                    out.add(basePackage(t[t.length - 1]));
                }
            }
            p.destroy();
        } catch (Exception ignored) {
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procs = am == null ? null : am.getRunningAppProcesses();
        if (procs != null) {
            for (ActivityManager.RunningAppProcessInfo pi : procs) out.add(basePackage(pi.processName));
        }
    }

    private static String basePackage(String processName) {
        int colon = processName.indexOf(':');
        return colon > 0 ? processName.substring(0, colon) : processName;
    }

    private static boolean isProtected(String pkg) {
        for (String p : PROTECTED) {
            if (pkg.equals(p) || (p.endsWith(".") && pkg.startsWith(p))) return true;
        }
        return false;
    }

    private static Bitmap toBitmap(Drawable d, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bmp;
    }

    // ------------------------------------------------------------------ acciones

    private void open(Entry e) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(e.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            OpenedApps.record(this, new Intent().setComponent(e.component));
        } catch (RuntimeException ex) {
            Toast.makeText(this, "No se pudo abrir " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void close(Entry e) {
        kill(e.pkg);
        OpenedApps.remove(this, e.pkg);
        main.postDelayed(this::load, 700);
        Toast.makeText(this, "Cerrando " + e.label + "…", Toast.LENGTH_SHORT).show();
    }

    private void closeAll() {
        if (apps.isEmpty()) return;
        for (Entry e : new ArrayList<>(apps)) {
            kill(e.pkg);
            OpenedApps.remove(this, e.pkg);
        }
        main.postDelayed(this::load, 900);
        Toast.makeText(this, "Cerrando " + apps.size() + " apps…", Toast.LENGTH_SHORT).show();
    }

    private void kill(String pkg) {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return;
        try {
            am.killBackgroundProcesses(pkg);
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------ lista

    private static final class Entry {
        final String label;
        final String pkg;
        final ComponentName component;
        final Bitmap icon;

        Entry(String label, String pkg, ComponentName component, Bitmap icon) {
            this.label = label;
            this.pkg = pkg;
            this.component = component;
            this.icon = icon;
        }
    }

    private final class Adapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(OpenAppsActivity.this);

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView : inflater.inflate(R.layout.item_open_app, parent, false);
            final Entry e = apps.get(position);
            ((ImageView) v.findViewById(R.id.openAppIcon)).setImageBitmap(e.icon);
            ((TextView) v.findViewById(R.id.openAppLabel)).setText(e.label);
            ((TextView) v.findViewById(R.id.openAppPkg)).setText(e.pkg);
            v.setOnClickListener(x -> open(e));
            v.findViewById(R.id.openAppClose).setOnClickListener(x -> close(e));
            return v;
        }
    }
}
