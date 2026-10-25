package com.jh.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;
    private AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Pantalla completa inmersiva automotriz
        setImmersiveMode();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        // Aceleración de gráficos para Mali-400
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Puente nativo JavaScript <-> Android Java
        webView.addJavascriptInterface(new LauncherBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inyecta indicador de que está corriendo en entorno nativo Android
                webView.evaluateJavascript("if (window.onNativeAndroidReady) { window.onNativeAndroidReady(); }", null);
            }
        });

        // Carga la interfaz local desde los assets del APK (100% Offline)
        webView.loadUrl("file:///android_asset/index.html");

        setContentView(webView);
    }

    private void setImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }

    @Override
    public void onBackPressed() {
        // En un Launcher el botón atrás no debe cerrar la aplicación de inicio
    }

    /**
     * Puente que expone métodos nativos a la interfaz JavaScript
     */
    public class LauncherBridge {

        @JavascriptInterface
        public void launchJhMusic() {
            // Intenta los paquetes conocidos de JH Music
            String[] candidates = {"com.jh.caraudio", "com.jh.music"};
            launchCandidatePackages(candidates, "JH Music");
        }

        @JavascriptInterface
        public void launchJhVideo() {
            // Intenta los paquetes conocidos de JH Video
            String[] candidates = {"com.jh.carvideo", "com.jh.video"};
            launchCandidatePackages(candidates, "JH Video");
        }

        @JavascriptInterface
        public void launchJhMonitor() {
            String[] candidates = {"com.jh.monitor"};
            launchCandidatePackages(candidates, "JH Monitor");
        }

        @JavascriptInterface
        public void launchRadio() {
            String[] candidates = {
                    "com.autochips.radio",
                    "com.xyauto.radio",
                    "com.android.fmradio",
                    "com.microntek.radio",
                    "com.syu.radio"
            };
            launchCandidatePackages(candidates, "Radio FM");
        }

        @JavascriptInterface
        public void tuneRadio(String direction) {
            mainHandler.post(() -> {
                // Envía broadcast a la app de radio del chip AC8227L
                Intent intent = new Intent();
                if ("next".equalsIgnoreCase(direction)) {
                    intent.setAction("com.autochips.radio.action.RADIO_NEXT");
                } else {
                    intent.setAction("com.autochips.radio.action.RADIO_PREV");
                }
                sendBroadcast(intent);
            });
        }

        @JavascriptInterface
        public void sendMediaKey(String action) {
            mainHandler.post(() -> {
                int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                if ("next".equalsIgnoreCase(action)) {
                    keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                } else if ("prev".equalsIgnoreCase(action)) {
                    keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                } else if ("play".equalsIgnoreCase(action)) {
                    keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
                } else if ("pause".equalsIgnoreCase(action)) {
                    keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE;
                }

                long eventTime = SystemClock.uptimeMillis();
                KeyEvent downEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);

                if (audioManager != null) {
                    audioManager.dispatchMediaKeyEvent(downEvent);
                    audioManager.dispatchMediaKeyEvent(upEvent);
                }
            });
        }

        @JavascriptInterface
        public void launchBluetooth() {
            String[] candidates = {
                    "com.autochips.bluetooth",
                    "com.xyauto.bluetooth",
                    "com.ts.bluetooth",
                    "com.microntek.bluetooth"
            };
            boolean launched = launchCandidatePackages(candidates, "Bluetooth Manos Libres");
            if (!launched) {
                // Fallback a los ajustes de Bluetooth estándar
                try {
                    Intent btIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(btIntent);
                } catch (Exception ignored) {}
            }
        }

        @JavascriptInterface
        public void launchSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                showToast("No se pudo abrir Ajustes");
            }
        }

        @JavascriptInterface
        public void launchAllApps() {
            // Abre selector de aplicaciones instaladas
            try {
                Intent intent = new Intent(Intent.ACTION_ALL_APPS);
                startActivity(intent);
            } catch (Exception e) {
                // Fallback: Abre Ajustes de Aplicaciones
                try {
                    Intent appSettings = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                    startActivity(appSettings);
                } catch (Exception ex) {
                    showToast("Abriendo cajón de aplicaciones...");
                }
            }
        }

        @JavascriptInterface
        public String getSystemMemoryJson() {
            // Lee la memoria RAM real desde /proc/meminfo
            long totalMem = 0;
            long freeMem = 0;
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        totalMem = parseMemLine(line);
                    } else if (line.startsWith("MemAvailable:")) {
                        freeMem = parseMemLine(line);
                        break;
                    } else if (line.startsWith("MemFree:") && freeMem == 0) {
                        freeMem = parseMemLine(line);
                    }
                }
            } catch (Exception ignored) {}

            int percentUsed = 50;
            if (totalMem > 0) {
                long used = totalMem - freeMem;
                percentUsed = (int) ((used * 100) / totalMem);
            }
            return "{\"totalMb\":" + (totalMem / 1024) + ",\"usedPercent\":" + percentUsed + "}";
        }

        private long parseMemLine(String line) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                try {
                    return Long.parseLong(parts[1]); // En kB
                } catch (NumberFormatException ignored) {}
            }
            return 0;
        }

        private boolean launchCandidatePackages(String[] candidates, String appLabel) {
            PackageManager pm = getPackageManager();
            for (String pkg : candidates) {
                Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    launchIntent.removeFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    mainHandler.post(() -> startActivity(launchIntent));
                    return true;
                }
            }

            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this,
                        "App " + appLabel + " no encontrada en este radio",
                        Toast.LENGTH_SHORT).show();
            });
            return false;
        }

        private void showToast(final String msg) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }
}
