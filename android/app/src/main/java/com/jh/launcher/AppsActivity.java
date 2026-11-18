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
 * Cajón de apps propio del launcher (botón APPS). Respeta el tema activo (Claro / Oscuro)
 * guardado en SharedPreferences.
 */
public class AppsActivity extends Activity {

    private static final int ICON_DP = 64;

    private GridView grid;
    private TextView count;
    private ThemePalette palette;
    private List<AppEntry> apps = new ArrayList<>();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        boolean light = getSharedPreferences("jh_launcher", MODE_PRIVATE).getBoolean("theme_light", false);
        palette = new ThemePalette(light, getResources().getDisplayMetrics().density);

        grid = findViewById(R.id.appsGrid);
        count = findViewById(R.id.appsCount);
        findViewById(R.id.appsBack).setOnClickListener(v -> finish());
        applyTheme();

        grid.setOnItemClickListener((parent, view, position, id) -> open(apps.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            Toast.makeText(this, apps.get(position).component.getPackageName(), Toast.LENGTH_LONG).show();
            return true;
        });
        setupFactoryLauncher();
        loadApps();
    }

    private void applyTheme() {
        getWindow().getDecorView().setBackgroundColor(palette.winBg);
        findViewById(R.id.appsRoot).setBackgroundColor(palette.winBg);
        findViewById(R.id.appsTopBar).setBackgroundColor(palette.barBg);

        View backBtn = findViewById(R.id.appsBack);
        backBtn.setBackground(palette.dockBtnBg(false));
        ImageView backIcon = findViewById(R.id.appsBackIcon);
        if (backIcon != null) backIcon.setImageTintList(palette.dockIconTint(false));

        TextView title = findViewById(R.id.appsTitle);
        if (title != null) title.setTextColor(palette.text1);

        TextView factory = findViewById(R.id.btnFactoryLauncher);
        if (factory != null) {
            factory.setBackground(palette.box(palette.accentSoft, palette.accentBorder, 10f));
            factory.setTextColor(palette.accent);
        }
        count.setTextColor(palette.text2);
    }

    private void setupFactoryLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ComponentName found = null;
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
            ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
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
            v.setBackground(palette.box(palette.cardBg, palette.border, 12f));
            Holder h = (Holder) v.getTag();
            AppEntry app = apps.get(position);
            h.icon.setImageBitmap(app.icon);
            h.label.setText(app.label);
            h.label.setTextColor(palette.text2);
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
