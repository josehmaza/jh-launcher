package com.jh.launcher;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * JH LAUNCHER · Hyundai Tucson ix35 2013.
 *
 * Interfaz 100% nativa (antes era un WebView con HTML/CSS/JS). Pensada para el
 * Autochips AC8227L + Mali-400: nada se anima ni se consulta mientras el launcher
 * no está a la vista o el modo Stealth lo tapa, los textos que cambian seguido tienen
 * ancho fijo para no forzar un re-layout de toda la pantalla, y las barras se mueven
 * con scaleX (solo GPU, sin layout).
 */
public class MainActivity extends Activity implements BlindGestureListener.Callback {

    private static final String JH_MUSIC_PKG = "com.jh.caraudio";
    private static final String ACTION_TRACK_CHANGED = "com.jh.music.action.TRACK_CHANGED";
    private static final String ACTION_REQUEST_STATE = "com.jh.music.action.REQUEST_STATE";

    private static final long AUDIO_CHECK_MS = 1500;
    private static final long AUDIO_GRACE_MS = 3500;
    private static final long HW_POLL_MS = 3000;
    // El punto "360° ACTIVO" late a 10 cuadros por segundo en vez de 60: para un punto
    // de 6dp no se nota la diferencia y la GPU redibuja 6 veces menos
    private static final long PULSE_STEP_MS = 100;
    private static final long PULSE_PERIOD_MS = 2000;
    private static final long STEALTH_EXIT_GUARD_MS = 500;

    private static final String[] DAYS = {"DOMINGO", "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO"};
    private static final String[] MONTHS = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO",
            "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};

    // Pistas de ejemplo mientras JH Music no haya informado nada (las mismas de la maqueta)
    private static final Track[] USB_DEMO = {
            new Track("I'm Sorry", "Aventura · The Last", "", "04:36", 276),
            new Track("Ella y Yo", "Don Omar ft. Aventura", "", "03:27", 207),
            new Track("Dile al Amor", "Aventura · The Last", "", "03:49", 229),
            new Track("Obsesión", "Aventura · We Broke The Rules", "", "04:12", 252)
    };
    private static final Track[] BT_DEMO = {
            new Track("Video en YouTube (Teléfono)", "YouTube Music / App · Transmisión Bluetooth", "", "03:45", 225),
            new Track("Canción en Spotify", "Spotify Móvil · Audio A2DP / AVRCP", "", "04:10", 250),
            new Track("Podcast / Audio en Vivo", "Celular Conectado · Manos Libres", "", "12:00", 720)
    };

    private static final float[] FM_PRESETS = {88.5f, 91.3f, 93.7f, 96.1f, 98.9f, 101.7f, 104.5f, 107.9f};

    private static final TimeInterpolator EASE = new AccelerateDecelerateInterpolator();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread ioThread;
    private Handler ioHandler;
    private AudioManager audioManager;
    private HwMonitor hwMonitor;
    private CarTurntable turntable;
    private float density;

    // Vistas
    private View mainContent;
    private TextView statusClock, statusDate, stealthClock, stealthDate, stealthTrackName;
    private View btnColorBlack, btnColorGrey, spinDot, gestureHud, gestureHudBar, gestureHudBarFill;
    private ImageView gestureHudIcon;
    private TextView gestureHudLabel;
    private TextView hwCpuVal, hwRamVal, hwStorageVal;
    private View hwCpuFill, hwRamFill, hwStorageFill;
    private View btnSourceUsb, btnSourceBt;
    private TextView sourceExtraBadge, sourceStatusChip, trackTitle, trackArtist, timeReadout;
    private TextView openTitle, openSub, radioFreqDisplay, muteLabel, launchToast;
    private ImageView vinylDisc, artCoverIcon, playPauseIcon, muteIcon;
    private View musicProgressFill, stealthOverlay;
    private View usb1Badge, usb2Badge;
    private ImageView usb1Icon, usb2Icon;
    private TextView usb1Text, usb2Text;
    private ObjectAnimator vinylSpin;

    // Estado
    private boolean resumed;
    private boolean stealthActive;
    private long stealthSince;
    private boolean sourceBt;
    private int usbIdx;
    private int btIdx;
    private boolean isPlaying;
    private boolean vinylOut;
    private int progressSec;
    private long progressTickAt;
    private Track jhTrack;
    private long audioGraceUntil;
    private int fmIdx = 3; // 96.1 MHz
    private boolean muted;
    private int lastClockMinute = -1;
    private int volumeMax = 1;
    private int volumeIndex;
    private int volumeStartPercent;
    private int volumePercent = -1;

    // ------------------------------------------------------------------
    // Receptores
    // ------------------------------------------------------------------

    private final BroadcastReceiver jhMusicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_TRACK_CHANGED.equals(intent.getAction())) return;
            String duration = intent.getStringExtra("extra_duration");
            Track t = new Track(
                    nonNull(intent.getStringExtra("extra_title")),
                    nonNull(intent.getStringExtra("extra_artist")),
                    nonNull(intent.getStringExtra("extra_path")),
                    duration != null ? duration : "00:00",
                    intent.getIntExtra("extra_total_sec", 0));
            t.isPlaying = intent.getBooleanExtra("extra_is_playing", false);
            applyJhMusicData(t);
        }
    };

    // Reloj: el sistema avisa una vez por minuto (ACTION_TIME_TICK), sin timers propios
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastClockMinute = -1;
            updateClock();
        }
    };

    // Pendrives: el sistema notifica cuando se monta o desmonta un almacenamiento
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mainHandler.removeCallbacks(hwPoll);
            mainHandler.post(hwPoll);
        }
    };

    // ------------------------------------------------------------------
    // Tareas periódicas (solo corren con el launcher visible)
    // ------------------------------------------------------------------

    private final Runnable audioCheck = new Runnable() {
        @Override
        public void run() {
            if (audioManager != null && !audioManager.isMusicActive()) onSystemMusicStopped();
            mainHandler.postDelayed(this, AUDIO_CHECK_MS);
        }
    };

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            advanceProgress();
            mainHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable hwPoll = new Runnable() {
        @Override
        public void run() {
            ioHandler.post(() -> {
                try {
                    final boolean usb1 = hwMonitor.isUsb1Connected();
                    final boolean usb2 = hwMonitor.isUsb2Connected();
                    final int cpu = hwMonitor.readCpuPercent();
                    final int ram = hwMonitor.readRamPercent();
                    final int storage = hwMonitor.readStoragePercent();
                    mainHandler.post(() -> {
                        setHwStat(hwCpuVal, hwCpuFill, cpu);
                        setHwStat(hwRamVal, hwRamFill, ram);
                        setHwStat(hwStorageVal, hwStorageFill, storage);
                        updateUsbIndicators(usb1, usb2);
                    });
                } catch (Throwable t) {
                    android.util.Log.e("JH-LAUNCHER", "Error en hwPoll", t);
                }
            });
            mainHandler.postDelayed(this, HW_POLL_MS);
        }
    };

    private final Runnable pulseStep = new Runnable() {
        @Override
        public void run() {
            double phase = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
            spinDot.setAlpha((float) (0.7 - 0.3 * Math.cos(phase * 2 * Math.PI)));
            mainHandler.postDelayed(this, PULSE_STEP_MS);
        }
    };

    private final Runnable hideHud = () -> gestureHud.animate().alpha(0f).scaleX(0.82f).scaleY(0.82f)
            .setDuration(180).setInterpolator(EASE)
            .withEndAction(() -> gestureHud.setVisibility(View.INVISIBLE));

    private final Runnable hideToast = () -> launchToast.animate().alpha(0f).translationY(-20 * density)
            .setDuration(250).setInterpolator(EASE)
            .withEndAction(() -> launchToast.setVisibility(View.INVISIBLE));

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long tPROF = System.nanoTime(); // PROF
        setImmersiveMode();
        setContentView(R.layout.activity_main);
        android.util.Log.i("JH-PROF", "  de eso, JhTextView (fuentes+atributos) " + JhTextView.FONT_NS / 1000000 + " ms"); // PROF
        android.util.Log.i("JH-PROF", "setContentView " + (System.nanoTime() - tPROF) / 1000000 + " ms"); tPROF = System.nanoTime(); // PROF

        density = getResources().getDisplayMetrics().density;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        hwMonitor = new HwMonitor(this);
        ioThread = new HandlerThread("jh-io", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());

        bindViews();
        bindActions();

        turntable = new CarTurntable((ImageView) findViewById(R.id.carImage), mainHandler);
        turntable.preload(getAssets(), ioHandler);

        int vinylPx = Math.round(78 * density);
        android.util.Log.i("JH-PROF", "init+bind " + (System.nanoTime() - tPROF) / 1000000 + " ms"); tPROF = System.nanoTime(); // PROF
        vinylDisc.setImageBitmap(VinylArt.create(vinylPx));
        android.util.Log.i("JH-PROF", "vinilo " + (System.nanoTime() - tPROF) / 1000000 + " ms"); tPROF = System.nanoTime(); // PROF
        vinylSpin = ObjectAnimator.ofFloat(vinylDisc, View.ROTATION, 0f, 360f);
        vinylSpin.setDuration(2800);
        vinylSpin.setRepeatCount(ValueAnimator.INFINITE);
        vinylSpin.setInterpolator(new LinearInterpolator());

        btnColorBlack.setSelected(true);
        findViewById(R.id.dockMusic).setActivated(true);
        radioFreqDisplay.setText(formatFreq(FM_PRESETS[fmIdx]));
        setAudioSource(false);

        // Mantener el estado de JH Music al día aunque el launcher esté en segundo plano
        registerReceiver(jhMusicReceiver, new IntentFilter(ACTION_TRACK_CHANGED));
        android.util.Log.i("JH-PROF", "resto onCreate " + (System.nanoTime() - tPROF) / 1000000 + " ms"); // PROF
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        setImmersiveMode();

        IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, timeFilter);
        lastClockMinute = -1;
        updateClock();

        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        usbFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        usbFilter.addDataScheme("file");
        registerReceiver(usbReceiver, usbFilter);

        refreshMuteState();
        progressTickAt = SystemClock.elapsedRealtime();
        mainHandler.postDelayed(audioCheck, 1000);
        mainHandler.postDelayed(progressTick, 1000);
        updateLiveAnimations();

        // Pide a JH Music su estado actual por si cambió mientras no estábamos visibles
        try {
            sendBroadcast(new Intent(ACTION_REQUEST_STATE));
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        try {
            unregisterReceiver(timeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        mainHandler.removeCallbacks(audioCheck);
        mainHandler.removeCallbacks(progressTick);
        updateLiveAnimations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        vinylSpin.cancel();
        try {
            unregisterReceiver(jhMusicReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        ioThread.quitSafely();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersiveMode();
    }

    @Override
    public void onBackPressed() {
        // Un launcher no se cierra con Atrás; solo sirve para salir del modo Stealth
        if (stealthActive) exitStealth();
    }

    private void setImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Arranca o detiene todo lo que se mueve en pantalla según si se está viendo. */
    private void updateLiveAnimations() {
        boolean live = resumed && !stealthActive;
        mainHandler.removeCallbacks(pulseStep);
        mainHandler.removeCallbacks(hwPoll);
        if (live) {
            turntable.start();
            mainHandler.post(pulseStep);
            mainHandler.post(hwPoll);
        } else {
            turntable.stop();
        }
        updateVinyl();
    }

    // ------------------------------------------------------------------
    // Vistas y acciones
    // ------------------------------------------------------------------

    private void bindViews() {
        mainContent = findViewById(R.id.mainContent);
        statusClock = findViewById(R.id.statusClock);
        statusDate = findViewById(R.id.statusDate);
        stealthOverlay = findViewById(R.id.stealthOverlay);
        stealthClock = findViewById(R.id.stealthClock);
        stealthDate = findViewById(R.id.stealthDate);
        stealthTrackName = findViewById(R.id.stealthTrackName);
        btnColorBlack = findViewById(R.id.btnColorBlack);
        btnColorGrey = findViewById(R.id.btnColorGrey);
        spinDot = findViewById(R.id.spinDot);
        gestureHud = findViewById(R.id.gestureHud);
        gestureHudIcon = findViewById(R.id.gestureHudIcon);
        gestureHudLabel = findViewById(R.id.gestureHudLabel);
        gestureHudBar = findViewById(R.id.gestureHudBar);
        gestureHudBarFill = findViewById(R.id.gestureHudBarFill);
        hwCpuVal = findViewById(R.id.hwCpuVal);
        hwRamVal = findViewById(R.id.hwRamVal);
        hwStorageVal = findViewById(R.id.hwStorageVal);
        hwCpuFill = findViewById(R.id.hwCpuFill);
        hwRamFill = findViewById(R.id.hwRamFill);
        hwStorageFill = findViewById(R.id.hwStorageFill);
        btnSourceUsb = findViewById(R.id.btnSourceUsb);
        btnSourceBt = findViewById(R.id.btnSourceBt);
        sourceExtraBadge = findViewById(R.id.sourceExtraBadge);
        sourceStatusChip = findViewById(R.id.sourceStatusChip);
        vinylDisc = findViewById(R.id.vinylDisc);
        artCoverIcon = findViewById(R.id.artCoverIcon);
        trackTitle = findViewById(R.id.trackTitle);
        trackArtist = findViewById(R.id.trackArtist);
        musicProgressFill = findViewById(R.id.musicProgressFill);
        timeReadout = findViewById(R.id.timeReadout);
        playPauseIcon = findViewById(R.id.playPauseIcon);
        openTitle = findViewById(R.id.openTitle);
        openSub = findViewById(R.id.openSub);
        radioFreqDisplay = findViewById(R.id.radioFreqDisplay);
        muteIcon = findViewById(R.id.muteIcon);
        muteLabel = findViewById(R.id.muteLabel);
        launchToast = findViewById(R.id.launchToast);
        usb1Badge = findViewById(R.id.usb1Badge);
        usb1Icon = findViewById(R.id.usb1Icon);
        usb1Text = findViewById(R.id.usb1Text);
        usb2Badge = findViewById(R.id.usb2Badge);
        usb2Icon = findViewById(R.id.usb2Icon);
        usb2Text = findViewById(R.id.usb2Text);
    }

    private void bindActions() {
        btnColorBlack.setOnClickListener(v -> setCarColor(CarTurntable.COLOR_BLACK));
        btnColorGrey.setOnClickListener(v -> setCarColor(CarTurntable.COLOR_GREY));
        findViewById(R.id.turntableStage).setOnTouchListener(new BlindGestureListener(density, this));

        btnSourceUsb.setOnClickListener(v -> setAudioSource(false));
        btnSourceBt.setOnClickListener(v -> setAudioSource(true));
        findViewById(R.id.btnPlayPause).setOnClickListener(v -> onPlayPause());
        findViewById(R.id.btnPrevTrack).setOnClickListener(v -> onSkip(false));
        findViewById(R.id.btnNextTrack).setOnClickListener(v -> onSkip(true));
        findViewById(R.id.btnOpenMusic).setOnClickListener(v -> {
            if (sourceBt) launchBluetooth();
            else launchJhMusic("tracks");
        });

        findViewById(R.id.btnLaunchJhVideo).setOnClickListener(v -> launchJhVideo());
        findViewById(R.id.btnLaunchRadio).setOnClickListener(v -> launchRadio());
        findViewById(R.id.radioFreqBox).setOnClickListener(v -> launchRadio());
        findViewById(R.id.btnRadioPrev).setOnClickListener(v -> tuneRadio(false));
        findViewById(R.id.btnRadioNext).setOnClickListener(v -> tuneRadio(true));

        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnStealth).setOnClickListener(v -> enterStealth());
        stealthOverlay.setOnClickListener(v -> exitStealth());
        findViewById(R.id.dockMusic).setOnClickListener(v -> launchJhMusic(null));
        findViewById(R.id.dockVideo).setOnClickListener(v -> launchJhVideo());
        findViewById(R.id.dockRadio).setOnClickListener(v -> launchRadio());
        findViewById(R.id.dockBluetooth).setOnClickListener(v -> launchBluetooth());
        findViewById(R.id.btnAllApps).setOnClickListener(v -> launchAllApps());
        findViewById(R.id.btnSettings).setOnClickListener(v -> launchSettings());
    }

    // ------------------------------------------------------------------
    // Reloj
    // ------------------------------------------------------------------

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (minuteOfDay == lastClockMinute) return;
        lastClockMinute = minuteOfDay;

        String time = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        String date = DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + now.get(Calendar.DAY_OF_MONTH)
                + " DE " + MONTHS[now.get(Calendar.MONTH)];
        statusClock.setText(time);
        stealthClock.setText(time);
        statusDate.setText(date);
        stealthDate.setText(date);
    }

    // ------------------------------------------------------------------
    // Auto y telemetría
    // ------------------------------------------------------------------

    private void setCarColor(int color) {
        btnColorBlack.setSelected(color == CarTurntable.COLOR_BLACK);
        btnColorGrey.setSelected(color == CarTurntable.COLOR_GREY);
        turntable.setColor(color);
    }

    private void setHwStat(TextView value, View fill, int percent) {
        if (percent == HwMonitor.UNKNOWN) {
            value.setText("--");
            fill.setScaleX(0f);
            return;
        }
        percent = Math.max(0, Math.min(100, percent));
        value.setText(percent + "%");
        fill.setScaleX(percent / 100f);
    }

    private void updateUsbIndicators(boolean usb1, boolean usb2) {
        setUsbIndicatorState(usb1Badge, usb1Icon, usb1Text, usb1);
        setUsbIndicatorState(usb2Badge, usb2Icon, usb2Text, usb2);
    }

    private void setUsbIndicatorState(View badge, ImageView icon, TextView text, boolean active) {
        if (badge == null || icon == null || text == null) return;
        if (active) {
            badge.setBackgroundResource(R.drawable.bg_usb_active);
            int color = getResources().getColor(R.color.accent_emerald);
            icon.setColorFilter(color);
            text.setTextColor(color);
        } else {
            badge.setBackgroundResource(R.drawable.bg_pill);
            int color = getResources().getColor(R.color.text_muted);
            icon.setColorFilter(color);
            text.setTextColor(color);
        }
    }

    // ------------------------------------------------------------------
    // Gestos a ciegas
    // ------------------------------------------------------------------

    @Override
    public void onGestureNext() {
        showGestureHud(R.drawable.ic_next, "SIGUIENTE", -1f, 650);
        onSkip(true);
    }

    @Override
    public void onGesturePrev() {
        showGestureHud(R.drawable.ic_prev, "ANTERIOR", -1f, 650);
        onSkip(false);
    }

    @Override
    public void onGestureToggle() {
        showGestureHud(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, isPlaying ? "PAUSA" : "PLAY", -1f, 650);
        onPlayPause();
    }

    // Volumen igual que el gesto ciego de JH Music: porcentaje relativo al inicio del arrastre
    @Override
    public void onVolumeGestureStart() {
        if (audioManager == null) return;
        volumeMax = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumeStartPercent = Math.round(volumeIndex * 100f / volumeMax);
        volumePercent = -1;
    }

    @Override
    public void onVolumeGestureMove(float delta) {
        if (audioManager == null) return;
        int percent = Math.max(0, Math.min(100, Math.round(volumeStartPercent + delta * 100f)));
        if (percent == volumePercent) return;
        volumePercent = percent;

        if (muted && percent > 0) setMuted(false, false);
        // Solo se llama al sistema cuando cambia el nivel real (la escala del radio es corta)
        int index = Math.round(percent / 100f * volumeMax);
        if (index != volumeIndex) {
            try {
                // Sin FLAG_SHOW_UI: el indicador propio del launcher reemplaza al del sistema
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
                volumeIndex = index;
            } catch (RuntimeException ignored) {
            }
        }
        showGestureHud(percent == 0 || muted ? R.drawable.ic_volume_off : R.drawable.ic_volume,
                "VOLUMEN " + percent + "%", percent / 100f, 0);
    }

    @Override
    public void onVolumeGestureEnd() {
        mainHandler.removeCallbacks(hideHud);
        mainHandler.postDelayed(hideHud, 1100);
    }

    /**
     * @param level  nivel 0..1 para la barra de volumen, o negativo para ocultarla.
     * @param hideMs se oculta tras este tiempo; 0 = sigue visible (mientras se arrastra el volumen).
     */
    private void showGestureHud(int iconRes, String label, float level, long hideMs) {
        gestureHudIcon.setImageResource(iconRes);
        gestureHudLabel.setText(label);
        if (level >= 0f) {
            gestureHudBarFill.setScaleX(Math.min(1f, level));
            gestureHudBar.setVisibility(View.VISIBLE);
        } else {
            gestureHudBar.setVisibility(View.GONE);
        }
        mainHandler.removeCallbacks(hideHud);
        if (gestureHud.getVisibility() != View.VISIBLE) {
            gestureHud.setScaleX(0.82f);
            gestureHud.setScaleY(0.82f);
            gestureHud.setVisibility(View.VISIBLE);
        }
        gestureHud.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(new OvershootInterpolator(1.5f));
        if (hideMs > 0) mainHandler.postDelayed(hideHud, hideMs);
    }

    // ------------------------------------------------------------------
    // Música (JH Music por USB o audio Bluetooth del teléfono)
    // ------------------------------------------------------------------

    private void setAudioSource(boolean bt) {
        sourceBt = bt;
        btnSourceUsb.setSelected(!bt);
        btnSourceBt.setSelected(bt);

        if (!bt) {
            sourceExtraBadge.setText(jhTrack != null && !jhTrack.path.isEmpty() ? "USB 1" : "MI MIX (109)");
            sourceStatusChip.setText("USB 1");
            artCoverIcon.setImageResource(R.drawable.ic_disc);
            openTitle.setText("ABRIR JH MUSIC");
            openSub.setText("Ver carpetas y listas de reproducción");
            syncFromStateFile();
        } else {
            sourceExtraBadge.setText("AVRCP REMOTO");
            sourceStatusChip.setText("BT AUDIO");
            artCoverIcon.setImageResource(R.drawable.ic_bluetooth);
            openTitle.setText("ABRIR MANOS LIBRES");
            openSub.setText("Ver llamadas y teléfono conectado");
        }
        updateTrackDisplay();
        updateProgressUi();
    }

    private Track currentTrack() {
        if (sourceBt) return BT_DEMO[btIdx];
        return jhTrack != null ? jhTrack : USB_DEMO[usbIdx];
    }

    private void updateTrackDisplay() {
        Track t = currentTrack();
        trackTitle.setText(t.title);
        trackArtist.setText(t.artist);
        stealthTrackName.setText(t.title + " — " + t.artist);
    }

    private void applyJhMusicData(Track t) {
        if (t == null || t.title.isEmpty()) return;
        // JH Music no envía la posición: al cambiar de canción el progreso vuelve a cero
        boolean trackChanged = jhTrack == null || !jhTrack.path.equals(t.path) || !jhTrack.title.equals(t.title);
        jhTrack = t;
        if (sourceBt) return;

        updateTrackDisplay();
        if (!t.path.isEmpty()) {
            String[] parts = t.path.split("/");
            if (parts.length >= 2) sourceExtraBadge.setText(parts[parts.length - 2].toUpperCase(Locale.ROOT));
        }
        if (trackChanged) resetProgress();
        setPlaying(t.isPlaying);
        updateProgressUi();
    }

    /** Lee el último estado que JH Music dejó guardado (en el hilo de E/S). */
    private void syncFromStateFile() {
        ioHandler.post(() -> {
            final Track t = readStateFile();
            if (t != null) mainHandler.post(() -> applyJhMusicData(t));
        });
    }

    private static Track readStateFile() {
        File file = new File(Environment.getExternalStorageDirectory(), ".jh_music_state.json");
        if (!file.exists() || file.length() == 0 || file.length() > 64 * 1024) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = fis.read(buffer);
            if (read <= 0) return null;
            JSONObject json = new JSONObject(new String(buffer, 0, read, StandardCharsets.UTF_8));
            Track t = new Track(json.optString("title"), json.optString("artist"), json.optString("path"),
                    json.optString("duration", "00:00"), json.optInt("totalSec"));
            t.isPlaying = json.optBoolean("isPlaying");
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private void onPlayPause() {
        setPlaying(!isPlaying);
        if (!sourceBt) {
            audioGraceUntil = SystemClock.elapsedRealtime() + AUDIO_GRACE_MS;
            sendMusicCommand("PLAY_PAUSE");
        } else {
            sendMediaKey(isPlaying ? KeyEvent.KEYCODE_MEDIA_PLAY : KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    private void onSkip(boolean next) {
        if (!sourceBt) {
            audioGraceUntil = SystemClock.elapsedRealtime() + AUDIO_GRACE_MS;
            sendMusicCommand(next ? "NEXT" : "PREV");
        } else {
            btIdx = (btIdx + (next ? 1 : BT_DEMO.length - 1)) % BT_DEMO.length;
            sendMediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            updateTrackDisplay();
        }
        resetProgress();
        updateProgressUi();
    }

    /** El sistema dice que no suena nada: JH Music se pausó o se cerró por fuera. */
    private void onSystemMusicStopped() {
        if (SystemClock.elapsedRealtime() < audioGraceUntil) return;
        if (!sourceBt && isPlaying) setPlaying(false);
    }

    private void setPlaying(boolean playing) {
        if (playing && !isPlaying) progressTickAt = SystemClock.elapsedRealtime();
        isPlaying = playing;
        playPauseIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        updateVinyl();
    }

    /** El vinilo sale de la carátula al reproducir y solo gira si el launcher está a la vista. */
    private void updateVinyl() {
        if (isPlaying != vinylOut) {
            vinylOut = isPlaying;
            vinylDisc.animate().translationX(isPlaying ? 20 * density : 0f).setDuration(500)
                    .setInterpolator(new OvershootInterpolator(1.4f));
        }
        boolean spin = isPlaying && resumed && !stealthActive;
        if (spin) {
            if (!vinylSpin.isStarted()) vinylSpin.start();
            else if (vinylSpin.isPaused()) vinylSpin.resume();
        } else if (vinylSpin.isStarted()) {
            if (isPlaying) {
                vinylSpin.pause();
            } else {
                vinylSpin.cancel();
                vinylDisc.setRotation(0f);
            }
        }
    }

    private void resetProgress() {
        progressSec = 0;
        progressTickAt = SystemClock.elapsedRealtime();
    }

    /** Avanza el progreso con el reloj real, así se pone al día tras estar en segundo plano. */
    private void advanceProgress() {
        long now = SystemClock.elapsedRealtime();
        if (!isPlaying) {
            progressTickAt = now;
            return;
        }
        long elapsedSec = (now - progressTickAt) / 1000;
        if (elapsedSec > 0) {
            progressSec += elapsedSec;
            progressTickAt += elapsedSec * 1000;
            updateProgressUi();
        }
    }

    private void updateProgressUi() {
        Track t = currentTrack();
        if (t.totalSec <= 0) return;
        progressSec = Math.min(progressSec, t.totalSec);
        musicProgressFill.setScaleX(progressSec / (float) t.totalSec);
        timeReadout.setText(String.format(Locale.US, "%02d:%02d / %s", progressSec / 60, progressSec % 60, t.duration));
    }

    private void sendMusicCommand(String action) {
        try {
            Intent cmd = new Intent("com.jh.music.action." + action);
            cmd.setComponent(new ComponentName(JH_MUSIC_PKG, JH_MUSIC_PKG + ".MusicRemoteReceiver"));
            cmd.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(cmd);
        } catch (RuntimeException ignored) {
        }
    }

    private void sendMediaKey(int keyCode) {
        if (audioManager == null) return;
        long t = SystemClock.uptimeMillis();
        audioManager.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0));
        audioManager.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0));
    }

    // ------------------------------------------------------------------
    // Silencio (real, sobre el volumen multimedia del sistema)
    // ------------------------------------------------------------------

    private void refreshMuteState() {
        if (audioManager != null && Build.VERSION.SDK_INT >= 23) {
            muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        }
        updateMuteUi();
    }

    private void toggleMute() {
        setMuted(!muted, true);
    }

    @SuppressWarnings("deprecation")
    private void setMuted(boolean target, boolean notify) {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        target ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, target);
            }
        } catch (RuntimeException e) {
            if (notify) showToast("No se pudo cambiar el audio");
            return;
        }
        muted = target;
        updateMuteUi();
        if (notify) showToast(muted ? "Audio Silenciado (MUTE)" : "Audio Activado");
    }

    private void updateMuteUi() {
        ColorStateList color = muted
                ? new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}},
                new int[]{Color.BLACK, getResources().getColor(R.color.accent_crimson)})
                : getResources().getColorStateList(R.color.tint_dock);
        muteIcon.setImageResource(muted ? R.drawable.ic_volume_off : R.drawable.ic_volume);
        muteIcon.setImageTintList(color);
        muteLabel.setTextColor(color);
        muteLabel.setText(muted ? "MUTED" : "AUDIO");
    }

    // ------------------------------------------------------------------
    // Modo Stealth
    // ------------------------------------------------------------------

    private void enterStealth() {
        if (stealthActive) return;
        stealthActive = true;
        stealthSince = SystemClock.elapsedRealtime();
        updateLiveAnimations();
        stealthOverlay.animate().cancel();
        stealthOverlay.setAlpha(0f);
        stealthOverlay.setVisibility(View.VISIBLE);
        // Con la capa negra ya opaca se oculta la interfaz de abajo: así no se redibuja
        stealthOverlay.animate().alpha(1f).setDuration(350)
                .withEndAction(() -> mainContent.setVisibility(View.INVISIBLE));
    }

    private void exitStealth() {
        // Evita que el mismo toque que activó el modo lo cierre al instante
        if (!stealthActive || SystemClock.elapsedRealtime() - stealthSince < STEALTH_EXIT_GUARD_MS) return;
        stealthActive = false;
        mainContent.setVisibility(View.VISIBLE);
        stealthOverlay.animate().cancel();
        stealthOverlay.animate().alpha(0f).setDuration(350)
                .withEndAction(() -> stealthOverlay.setVisibility(View.GONE));
        updateLiveAnimations();
        showToast("Pantalla restaurada");
    }

    // ------------------------------------------------------------------
    // Radio FM
    // ------------------------------------------------------------------

    private void tuneRadio(boolean next) {
        fmIdx = (fmIdx + (next ? 1 : FM_PRESETS.length - 1)) % FM_PRESETS.length;
        String freq = formatFreq(FM_PRESETS[fmIdx]);
        radioFreqDisplay.setText(freq);
        showToast("Radio FM: " + freq + " MHz");
        try {
            sendBroadcast(new Intent(next ? "com.autochips.radio.action.RADIO_NEXT"
                    : "com.autochips.radio.action.RADIO_PREV"));
        } catch (RuntimeException ignored) {
        }
    }

    private static String formatFreq(float freq) {
        return String.format(Locale.US, "%.1f", freq);
    }

    // ------------------------------------------------------------------
    // Lanzadores de apps
    // ------------------------------------------------------------------

    /** @param tab pestaña de JH Music a mostrar ("folders", "tracks" o "mix"), o null para la última usada. */
    private void launchJhMusic(String tab) {
        Bundle extras = null;
        if (tab != null) {
            extras = new Bundle();
            extras.putString("extra_open_tab", tab);
        }
        launchCandidates(new String[]{JH_MUSIC_PKG, "com.jh.music"}, "JH Music", extras);
    }

    private void launchJhVideo() {
        launchCandidates(new String[]{"com.jh.carvideo", "com.jh.video"}, "JH Video");
    }

    private void launchRadio() {
        launchCandidates(new String[]{"com.autochips.radio", "com.xyauto.radio", "com.android.fmradio",
                "com.microntek.radio", "com.syu.radio"}, "Radio FM");
    }

    private void launchBluetooth() {
        String[] candidates = {"com.autochips.bluetooth", "com.xyauto.bluetooth", "com.ts.bluetooth",
                "com.microntek.bluetooth"};
        if (findLaunchIntent(candidates) != null) {
            launchCandidates(candidates, "Bluetooth Manos Libres");
        } else {
            startSafely(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "No se pudo abrir Bluetooth");
        }
    }

    private void launchAllApps() {
        if (!startSafely(new Intent(Intent.ACTION_ALL_APPS), null)) {
            startSafely(new Intent(Settings.ACTION_APPLICATION_SETTINGS), "No se pudo abrir el cajón de apps");
        }
    }

    private void launchSettings() {
        startSafely(new Intent(Settings.ACTION_SETTINGS), "No se pudo abrir Ajustes");
    }

    private Intent findLaunchIntent(String[] packages) {
        PackageManager pm = getPackageManager();
        for (String pkg : packages) {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) return intent;
        }
        return null;
    }

    private void launchCandidates(String[] packages, String label) {
        launchCandidates(packages, label, null);
    }

    private void launchCandidates(String[] packages, String label, Bundle extras) {
        Intent intent = findLaunchIntent(packages);
        if (intent == null) {
            showToast("App " + label + " no encontrada en este radio");
            return;
        }
        if (extras != null) intent.putExtras(extras);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.removeFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafely(intent, "No se pudo abrir " + label);
    }

    private boolean startSafely(Intent intent, String errorMsg) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            if (errorMsg != null) showToast(errorMsg);
            return false;
        }
    }

    /** Aviso flotante propio (sin emojis: cargar la fuente de emojis cuesta varios MB). */
    private void showToast(String msg) {
        launchToast.setText(msg);
        mainHandler.removeCallbacks(hideToast);
        if (launchToast.getVisibility() != View.VISIBLE) {
            launchToast.setTranslationY(-20 * density);
            launchToast.setVisibility(View.VISIBLE);
        }
        launchToast.animate().alpha(1f).translationY(0f).setDuration(250)
                .setInterpolator(new OvershootInterpolator(1.2f));
        mainHandler.postDelayed(hideToast, 2200);
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }

    /** Canción mostrada en la tarjeta de música. */
    private static final class Track {
        final String title;
        final String artist;
        final String path;
        final String duration;
        final int totalSec;
        boolean isPlaying;

        Track(String title, String artist, String path, String duration, int totalSec) {
            this.title = title;
            this.artist = artist.isEmpty() ? "JH Music · USB 1" : artist;
            this.path = path;
            this.duration = duration;
            this.totalSec = totalSec;
        }
    }
}
