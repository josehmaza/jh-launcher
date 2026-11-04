package com.jh.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Cajón de apps propio del launcher (botón APPS). No depende del launcher de fábrica del
 * radio: lista todo lo que tenga ícono de lanzamiento, ordenado por nombre. Los íconos se
 * reducen una sola vez a bitmaps pequeños para que el desplazamiento sea liviano.
 */
public class AppsActivity extends Activity {

    private static final int ICON_DP = 64;

    private GridView grid;
    private TextView count;
    private List<AppEntry> apps = new ArrayList<>();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        grid = findViewById(R.id.appsGrid);
        count = findViewById(R.id.appsCount);
        findViewById(R.id.appsBack).setOnClickListener(v -> finish());
        grid.setOnItemClickListener((parent, view, position, id) -> open(apps.get(position)));
        // Mantener pulsada una app muestra su paquete (sirve para identificar apps de fábrica del radio)
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            Toast.makeText(this, apps.get(position).component.getPackageName(), Toast.LENGTH_LONG).show();
            return true;
        });
        setupFactoryLauncher();
        loadApps();
    }

    /**
     * Busca otro launcher instalado (el de fábrica, normalmente Launcher3) y deja un botón para
     * abrirlo sin pasar por los ajustes aunque JH Launcher esté como inicio predeterminado.
     * Con el componente explícito Android no pregunta ni cambia el inicio predeterminado.
     */
    private void setupFactoryLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ComponentName found = null;
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
            ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            // Si hay varios, se prefiere el que se llama Launcher3
            if (found == null || cn.getPackageName().contains("launcher3")) found = cn;
        }
        if (found == null) return;
        final ComponentName target = found;
        View btn = findViewById(R.id.btnFactoryLauncher);
        btn.setVisibility(View.VISIBLE);
        btn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setComponent(target)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                Toast.makeText(this, "No se pudo abrir el launcher de fábrica", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
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

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final int iconPx = Math.round(ICON_DP * getResources().getDisplayMetrics().density);
        new Thread(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
            final List<AppEntry> list = new ArrayList<>(found.size());
            for (ResolveInfo ri : found) {
                if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
                CharSequence label = ri.loadLabel(pm);
                list.add(new AppEntry(
                        label != null ? label.toString() : ri.activityInfo.packageName,
                        new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                        toBitmap(ri.loadIcon(pm), iconPx)));
            }
            Collections.sort(list, new Comparator<AppEntry>() {
                @Override
                public int compare(AppEntry a, AppEntry b) {
                    return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
                }
            });
            runOnUiThread(() -> {
                if (destroyed) return;
                apps = list;
                count.setText(list.size() + " apps");
                grid.setAdapter(new AppsAdapter());
            });
        }, "apps-loader").start();
    }

    private static Bitmap toBitmap(Drawable d, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bmp;
    }

    private void open(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "No se pudo abrir " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private static final class AppEntry {
        final String label;
        final ComponentName component;
        final Bitmap icon;

        AppEntry(String label, ComponentName component, Bitmap icon) {
            this.label = label;
            this.component = component;
            this.icon = icon;
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(AppsActivity.this);

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
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.item_app, parent, false);
                v.setTag(new Holder(v));
            }
            Holder h = (Holder) v.getTag();
            AppEntry app = apps.get(position);
            h.icon.setImageBitmap(app.icon);
            h.label.setText(app.label);
            return v;
        }
    }

    private static final class Holder {
        final ImageView icon;
        final TextView label;

        Holder(View v) {
            icon = v.findViewById(R.id.appIcon);
            label = v.findViewById(R.id.appLabel);
        }
    }
}
