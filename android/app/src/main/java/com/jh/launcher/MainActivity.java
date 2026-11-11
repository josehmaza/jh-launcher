package com.jh.launcher;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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
    private static final String[] BT_KEYWORDS = {"bluetooth", "btphone"};
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
    // JH Music abierta sin canción cargada (su última canción era de un pendrive que no está puesto)
    private static final Track NO_TRACK = new Track("Selecciona una canción", "JH Music · elige en Librería", "", "00:00", 0);
    // Audio Bluetooth sin teléfono conectado, o con teléfono pero sin datos de la canción
    private static final Track BT_NO_PHONE = new Track("Sin teléfono conectado", "Conecta tu celular por Bluetooth", "", "00:00", 0);
    private static final Track BT_NO_INFO = new Track("Teléfono conectado", "Reproduce música en tu celular", "", "00:00", 0);

    // Radio FM de fábrica del auto (Jancar). JH Radio ya no se usa.
    private static final String NATIVE_RADIO_PKG = "com.jancar.radio";

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
    private TextView openTitle, openSub, muteLabel, launchToast;
    private ImageView vinylDisc, artCoverIcon, playPauseIcon, muteIcon;
    private View musicProgressFill, musicProgressThumb, musicProgressTouch, stealthOverlay;
    private boolean seekDragging;
    private View usb1Badge, usb2Badge;
    private ImageView usb1Icon, usb2Icon;
    private TextView usb1Text, usb2Text;
    private View artCover, playStatus, playStatusDot;
    private TextView playStatusText;

    // Zonas táctiles gigantes de música (Estilo JH Music para manejo a ciegas)
    private static final int PLAY_ZONE_EXTRA_DP = 12;
    private View cardMusic;
    private View musicProgressRow;
    private View btnPrevTrack;
    private View btnPlayPause;
    private View btnNextTrack;
    private View navZoneTarget;

    private final Runnable navZoneUnflash = () -> {
        if (btnPrevTrack != null) {
            btnPrevTrack.setActivated(false);
            btnPrevTrack.setPressed(false);
        }
        if (btnPlayPause != null) {
            btnPlayPause.setActivated(false);
            btnPlayPause.setPressed(false);
        }
        if (btnNextTrack != null) {
            btnNextTrack.setActivated(false);
            btnNextTrack.setPressed(false);
        }
    };

    // Estado
    private boolean resumed;
    private boolean stealthActive;
    private long stealthSince;
    private boolean sourceBt;
    private int usbIdx;
    // Audio Bluetooth: lo que informa el módulo Bluetooth de fábrica
    private BtMusicLink btLink;
    private boolean btLinked;
    private String btPhone;   // null = sin teléfono conectado
    private Track btTrack;    // null = sin datos de la canción
    private boolean isPlaying;
    private int progressSec;
    private long progressTickAt;
    private Track jhTrack;
    private long audioGraceUntil;
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
            t.fromMix = intent.getBooleanExtra("extra_from_mix", false);
            // JH Music 2.9.9+ manda la posición real; antes no la mandaba (-1: se sigue estimando)
            t.positionSec = intent.getIntExtra("extra_position_sec", -1);
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
            beatPlayStatus();
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
        setImmersiveMode();
        setContentView(R.layout.activity_main);

        density = getResources().getDisplayMetrics().density;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        hwMonitor = new HwMonitor(this);
        ioThread = new HandlerThread("jh-io", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());

        bindViews();
        bindActions();

        turntable = new CarTurntable((ImageView) findViewById(R.id.carImage), mainHandler);
        turntable.setIndicator(new View[]{
                findViewById(R.id.angleDot0), findViewById(R.id.angleDot1), findViewById(R.id.angleDot2)});
        turntable.preload(getAssets(), ioHandler);

        int vinylPx = Math.round(78 * density);
        vinylDisc.setImageBitmap(VinylArt.create(vinylPx));

        btnColorBlack.setSelected(true);
        findViewById(R.id.dockMusic).setActivated(true);
        setAudioSource(false);

        // Mantener el estado de JH Music al día aunque el launcher esté en segundo plano
        registerReceiver(jhMusicReceiver, new IntentFilter(ACTION_TRACK_CHANGED));

        // Audio Bluetooth del teléfono: se escucha al módulo Bluetooth de fábrica mientras el launcher viva
        btLink = new BtMusicLink(this, btListener);
        btLink.start();
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
            Intent req = new Intent(ACTION_REQUEST_STATE);
            req.setPackage(JH_MUSIC_PKG);
            req.setComponent(new ComponentName(JH_MUSIC_PKG, JH_MUSIC_PKG + ".MusicRemoteReceiver"));
            req.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(req);
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
        try {
            unregisterReceiver(jhMusicReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (btLink != null) btLink.stop();
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
        artCover = findViewById(R.id.artCover);
        playStatus = findViewById(R.id.playStatus);
        playStatusDot = findViewById(R.id.playStatusDot);
        playStatusText = findViewById(R.id.playStatusText);
        trackTitle = findViewById(R.id.trackTitle);
        trackArtist = findViewById(R.id.trackArtist);
        musicProgressFill = findViewById(R.id.musicProgressFill);
        musicProgressThumb = findViewById(R.id.musicProgressThumb);
        musicProgressTouch = findViewById(R.id.musicProgressTouch);
        musicProgressTouch.setOnTouchListener(this::onProgressTouch);
        // la perilla se ubica cuando ya se conoce el ancho de la barra
        musicProgressTouch.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) updateProgressUi();
        });
        timeReadout = findViewById(R.id.timeReadout);
        playPauseIcon = findViewById(R.id.playPauseIcon);
        openTitle = findViewById(R.id.openTitle);
        openSub = findViewById(R.id.openSub);
        muteIcon = findViewById(R.id.muteIcon);
        muteLabel = findViewById(R.id.muteLabel);
        launchToast = findViewById(R.id.launchToast);
        usb1Badge = findViewById(R.id.usb1Badge);
        usb1Icon = findViewById(R.id.usb1Icon);
        usb1Text = findViewById(R.id.usb1Text);
        usb2Badge = findViewById(R.id.usb2Badge);
        usb2Icon = findViewById(R.id.usb2Icon);
        usb2Text = findViewById(R.id.usb2Text);
        cardMusic = findViewById(R.id.cardMusic);
        musicProgressRow = findViewById(R.id.musicProgressRow);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrevTrack = findViewById(R.id.btnPrevTrack);
        btnNextTrack = findViewById(R.id.btnNextTrack);
    }

    private void bindActions() {
        btnColorBlack.setOnClickListener(v -> setCarColor(CarTurntable.COLOR_BLACK));
        btnColorGrey.setOnClickListener(v -> setCarColor(CarTurntable.COLOR_GREY));
        findViewById(R.id.turntableStage).setOnTouchListener(new BlindGestureListener(density, this));

        btnSourceUsb.setOnClickListener(v -> setAudioSource(false));
        btnSourceBt.setOnClickListener(v -> setAudioSource(true));
        btnPlayPause.setOnClickListener(v -> onPlayPause());
        btnPrevTrack.setOnClickListener(v -> onSkip(false));
        btnNextTrack.setOnClickListener(v -> onSkip(true));
        View.OnClickListener openMusicListener = v -> {
            if (sourceBt) launchBtMusic();
            // Si la canción que suena viene de Mi Mix, se abre esa pestaña y no Canciones
            else launchJhMusic(jhTrack != null && jhTrack != NO_TRACK && jhTrack.fromMix ? "mix" : "tracks");
        };
        findViewById(R.id.btnOpenMusic).setOnClickListener(openMusicListener);
        View artCover = findViewById(R.id.artCover);
        if (artCover != null) artCover.setOnClickListener(openMusicListener);

        findViewById(R.id.btnLaunchJhVideo).setOnClickListener(v -> launchJhVideo());
        findViewById(R.id.cardJhVideo).setOnClickListener(v -> launchJhVideo());

        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnStealth).setOnClickListener(v -> enterStealth());
        stealthOverlay.setOnClickListener(v -> exitStealth());
        findViewById(R.id.dockMusic).setOnClickListener(v -> launchJhMusic(null));
        findViewById(R.id.dockVideo).setOnClickListener(v -> launchJhVideo());
        findViewById(R.id.dockRadio).setOnClickListener(v -> launchRadio());
        findViewById(R.id.dockBluetooth).setOnClickListener(v -> launchBtCalls());
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
    // Zonas táctiles gigantes de control de música (Estilo JH Music)
    // Al manejar no hace falta acertarle al botón: toda la franja debajo de la barra se divide en tres:
    // izquierda = Anterior, centro = Play/Pausa, derecha = Siguiente
    // ------------------------------------------------------------------

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!stealthActive && handleNavZoneTouch(ev)) return true;
        return super.dispatchTouchEvent(ev);
    }

    private boolean handleNavZoneTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            navZoneTarget = navZoneAt(ev.getRawX(), ev.getRawY());
            if (navZoneTarget == null) return false;
            mainHandler.removeCallbacks(navZoneUnflash);
            navZoneUnflash.run();
            navZoneTarget.setActivated(true);
            navZoneTarget.setPressed(true);
            return true;
        }
        if (navZoneTarget == null) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            View current = navZoneAt(ev.getRawX(), ev.getRawY());
            if (current != navZoneTarget) {
                if (navZoneTarget != null) {
                    navZoneTarget.setActivated(false);
                    navZoneTarget.setPressed(false);
                }
                navZoneTarget = current;
                if (navZoneTarget != null) {
                    navZoneTarget.setActivated(true);
                    navZoneTarget.setPressed(true);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            View target = navZoneTarget;
            navZoneTarget = null;
            mainHandler.postDelayed(navZoneUnflash, 180);
            if (action == MotionEvent.ACTION_UP && target != null) {
                target.performClick();
            }
            return true;
        }
        return true;
    }

    private View navZoneAt(float x, float y) {
        if (cardMusic == null) cardMusic = findViewById(R.id.cardMusic);
        if (musicProgressRow == null) musicProgressRow = findViewById(R.id.musicProgressRow);
        if (btnPlayPause == null) btnPlayPause = findViewById(R.id.btnPlayPause);
        if (btnPrevTrack == null) btnPrevTrack = findViewById(R.id.btnPrevTrack);
        if (btnNextTrack == null) btnNextTrack = findViewById(R.id.btnNextTrack);

        if (cardMusic == null || musicProgressRow == null || btnPlayPause == null
                || btnPrevTrack == null || btnNextTrack == null) return null;

        Rect cardRect = new Rect();
        Rect progRect = new Rect();
        Rect playRect = new Rect();
        if (!cardMusic.getGlobalVisibleRect(cardRect)
                || !musicProgressRow.getGlobalVisibleRect(progRect)
                || !btnPlayPause.getGlobalVisibleRect(playRect)) {
            return null;
        }

        // El toque debe estar dentro de la tarjeta de música y debajo de la barra de progreso
        if (!cardRect.contains((int) x, (int) y) || y < progRect.bottom) {
            return null;
        }

        int extra = (int) (PLAY_ZONE_EXTRA_DP * density);
        if (x < playRect.left - extra) return btnPrevTrack;
        if (x > playRect.right + extra) return btnNextTrack;
        return btnPlayPause;
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
                percent + "%", percent / 100f, 0);
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
            // Gesto de volumen: solo el número en tamaño grande para legibilidad al conducir
            gestureHudLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 60f);
            gestureHudLabel.setLetterSpacing(0.04f);
            gestureHudBarFill.setScaleX(Math.min(1f, level));
            gestureHudBar.setVisibility(View.VISIBLE);
        } else {
            // Acciones de pista (SIGUIENTE, ANTERIOR, PLAY, PAUSA)
            gestureHudLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
            gestureHudLabel.setLetterSpacing(0.12f);
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
            sourceExtraBadge.setText(jhTrack == NO_TRACK ? "SIN CANCIÓN"
                    : jhTrack != null && !jhTrack.path.isEmpty() ? "USB 1" : "MI MIX (109)");
            sourceStatusChip.setText("USB 1");
            artCoverIcon.setImageResource(R.drawable.ic_disc);
            if (openTitle != null) openTitle.setText("ABRIR JH MUSIC");
            if (openSub != null) openSub.setText("Ver carpetas y listas de reproducción");
            sendMusicCommand("REQUEST_STATE");
            syncFromStateFile();
        } else {
            sourceStatusChip.setText("BT AUDIO");
            artCoverIcon.setImageResource(R.drawable.ic_bluetooth);
            if (openTitle != null) openTitle.setText("ABRIR MANOS LIBRES");
            if (openSub != null) openSub.setText("Ver llamadas y teléfono conectado");
            if (btLink != null) {
                btLink.refresh();
                // por si el módulo quedó callado de antes: si aquí no hay silencio, el módulo tampoco debe tenerlo
                if (!muted) btLink.muteMusic(false);
            }
            refreshBtUi();
        }
        updateTrackDisplay();
        updateProgressUi();
    }

    private Track currentTrack() {
        if (sourceBt) return btTrack != null ? btTrack : btPhone != null ? BT_NO_INFO : BT_NO_PHONE;
        return jhTrack != null ? jhTrack : USB_DEMO[usbIdx];
    }

    // ------------------------------------------------------------------
    // Audio Bluetooth (módulo Bluetooth de fábrica)
    // ------------------------------------------------------------------

    private final BtMusicLink.Listener btListener = new BtMusicLink.Listener() {
        @Override
        public void onLink(boolean linked) {
            btLinked = linked;
            refreshBtUi();
        }

        @Override
        public void onPhone(boolean connected, String name) {
            btPhone = connected ? name : null;
            if (!connected) {
                btTrack = null;
                if (sourceBt) setPlaying(false);
            }
            refreshBtUi();
        }

        @Override
        public void onStreaming(boolean streaming) {
            // Si el teléfono empieza a sonar, la tarjeta pasa a Bluetooth: así los botones y los gestos sobre el
            // Tucson (deslizar = siguiente / anterior) mandan al teléfono y no a JH Music
            if (streaming && !sourceBt) setAudioSource(true);
            if (sourceBt) setPlaying(streaming);
        }

        @Override
        public void onTrack(String title, String artist, String album, long durationMs) {
            if (title.isEmpty()) return;
            int sec = (int) (durationMs >= 10000 ? durationMs / 1000 : durationMs);
            String detail = !artist.isEmpty() ? artist : "Teléfono";
            if (!album.isEmpty() && !album.equals(artist)) detail += " · " + album;
            boolean changed = btTrack == null || !btTrack.title.equals(title) || !btTrack.artist.equals(detail);
            btTrack = new Track(title, detail, "", String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60), sec);
            if (btPhone == null) btPhone = "";
            if (changed) resetProgress();
            refreshBtUi();
        }
    };

    /** Pone al día la tarjeta de música con lo último que dijo el módulo Bluetooth (solo se ve en la pestaña BT). */
    private void refreshBtUi() {
        if (!sourceBt) return;
        String badge;
        if (!btLinked) badge = "SIN MÓDULO BT";
        else if (btPhone == null) badge = "SIN TELÉFONO";
        else badge = btPhone.isEmpty() ? "TELÉFONO" : btPhone.toUpperCase(Locale.ROOT);
        sourceExtraBadge.setText(badge);
        updateTrackDisplay();
        updateProgressUi();
    }

    private void updateTrackDisplay() {
        Track t = currentTrack();
        trackTitle.setText(t.title);
        trackArtist.setText(t.artist);
        stealthTrackName.setText(t.title + " — " + t.artist);
    }

    /** JH Music avisa que no tiene canción cargada: se deja de mostrar la que había. */
    private void showNoTrack() {
        jhTrack = NO_TRACK;
        if (sourceBt) return;
        updateTrackDisplay();
        sourceExtraBadge.setText("SIN CANCIÓN");
        resetProgress();
        setPlaying(false);
        updateProgressUi();
    }

    private void applyJhMusicData(Track t) {
        if (t == null) return;
        if (t.title.isEmpty()) {
            showNoTrack();
            return;
        }
        // Al cambiar de canción el progreso vuelve a cero; si JH Music manda la posición real, manda esa
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
        if (t.positionSec >= 0 && !seekDragging) {
            progressSec = t.positionSec;
            progressTickAt = SystemClock.elapsedRealtime();
        }
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
            t.fromMix = json.optBoolean("fromMix");
            // Posición al escribir el archivo, más lo que siguió sonando desde entonces
            t.positionSec = json.optInt("positionSec", -1);
            long writtenAt = json.optLong("timestamp", 0);
            if (t.positionSec >= 0 && t.isPlaying && writtenAt > 0) {
                t.positionSec += (int) Math.max(0, (System.currentTimeMillis() - writtenAt) / 1000);
            }
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private void onPlayPause() {
        if (needsSongChoice()) return;
        setPlaying(!isPlaying);
        if (!sourceBt) {
            audioGraceUntil = SystemClock.elapsedRealtime() + AUDIO_GRACE_MS;
            sendMusicCommand("PLAY_PAUSE");
        } else if (btLink != null && btLink.isLinked()) {
            btLink.playPause();
        } else {
            sendMediaKey(isPlaying ? KeyEvent.KEYCODE_MEDIA_PLAY : KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    /** Sin canción cargada en JH Music play y saltar no hacen nada: avisa en vez de aparentar que funcionó. */
    private boolean needsSongChoice() {
        if (sourceBt || jhTrack != NO_TRACK) return false;
        showToast("Elige una canción en JH Music");
        return true;
    }

    private void onSkip(boolean next) {
        if (needsSongChoice()) return;
        if (!sourceBt) {
            audioGraceUntil = SystemClock.elapsedRealtime() + AUDIO_GRACE_MS;
            sendMusicCommand(next ? "NEXT" : "PREV");
            sendMediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            usbIdx = (usbIdx + (next ? 1 : USB_DEMO.length - 1)) % USB_DEMO.length;
            if (jhTrack == null) {
                updateTrackDisplay();
            }
        } else if (btLink != null && btLink.isLinked()) {
            // la canción nueva la anuncia el teléfono por Bluetooth: aquí solo se pide el salto
            if (next) btLink.next();
            else btLink.prev();
        } else {
            sendMediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
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
        boolean changed = playing != isPlaying;
        isPlaying = playing;
        playPauseIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (!changed) return;

        // Indicador de reproducción sin animaciones continuas (opción B "latido con el segundero"):
        // vinilo fuera de la carátula pero quieto, carátula iluminada y "● REPRODUCIENDO".
        // Un vinilo girando obligaba a repintar la pantalla entera ~56 veces por segundo
        artCover.setActivated(playing);
        playStatus.setActivated(playing);
        playStatusText.setText(playing ? "REPRODUCIENDO" : "EN PAUSA");
        playStatusDot.setAlpha(1f);
        vinylDisc.animate().translationX(playing ? 20 * density : 0f).setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.4f));
    }

    /**
     * El punto de "REPRODUCIENDO" late una vez por segundo dentro del mismo tick que actualiza
     * el contador de tiempo: cambia en el repintado que ya ocurría, así que no agrega ninguno.
     */
    private void beatPlayStatus() {
        if (!isPlaying || stealthActive) return;
        playStatusDot.setAlpha(playStatusDot.getAlpha() > 0.5f ? 0.25f : 1f);
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
        if (seekDragging) return; // mientras se arrastra manda el dedo, no el reloj
        Track t = currentTrack();
        musicProgressThumb.setVisibility(canSeek(t) ? View.VISIBLE : View.GONE);
        if (t == NO_TRACK) {
            setProgressFraction(0f);
            timeReadout.setText("00:00 / 00:00");
            return;
        }
        if (t.totalSec <= 0) return;
        progressSec = Math.min(progressSec, t.totalSec);
        setProgressFraction(progressSec / (float) t.totalSec);
        timeReadout.setText(formatClock(progressSec) + " / " + t.duration);
    }

    private static String formatClock(int sec) {
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    /** Solo se puede adelantar una canción de JH Music con duración conocida (el audio Bluetooth no lo permite). */
    private boolean canSeek(Track t) {
        return !sourceBt && t != NO_TRACK && t != null && t.totalSec > 0;
    }

    private void setProgressFraction(float f) {
        musicProgressFill.setScaleX(f);
        int w = musicProgressTouch.getWidth();
        int tw = musicProgressThumb.getWidth();
        musicProgressThumb.setTranslationX(Math.max(0f, Math.min(w - tw, f * w - tw / 2f)));
    }

    /** Barra de tiempo: tocar o arrastrar muestra el tiempo de destino; al soltar, JH Music salta ahí. */
    private boolean onProgressTouch(View v, MotionEvent e) {
        Track t = currentTrack();
        if (!canSeek(t)) return false;
        float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, v.getWidth())));
        int sec = Math.round(f * t.totalSec);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                seekDragging = true;
                setProgressFraction(f);
                timeReadout.setText(formatClock(sec) + " / " + t.duration);
                return true;
            case MotionEvent.ACTION_UP:
                seekDragging = false;
                progressSec = sec;
                progressTickAt = SystemClock.elapsedRealtime();
                audioGraceUntil = SystemClock.elapsedRealtime() + AUDIO_GRACE_MS;
                sendMusicSeek(sec);
                updateProgressUi();
                return true;
            case MotionEvent.ACTION_CANCEL:
                seekDragging = false;
                updateProgressUi();
                return true;
            default:
                return true;
        }
    }

    private void sendMusicSeek(int sec) {
        try {
            Intent cmd = new Intent("com.jh.music.action.SEEK");
            cmd.setComponent(new ComponentName(JH_MUSIC_PKG, JH_MUSIC_PKG + ".MusicRemoteReceiver"));
            cmd.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
            cmd.putExtra("extra_position_sec", sec);
            sendBroadcast(cmd);
        } catch (RuntimeException ignored) {
        }
    }

    private void sendMusicCommand(String action) {
        try {
            Intent cmd = new Intent("com.jh.music.action." + action);
            cmd.setPackage(JH_MUSIC_PKG);
            cmd.setComponent(new ComponentName(JH_MUSIC_PKG, JH_MUSIC_PKG + ".MusicRemoteReceiver"));
            cmd.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
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

    /**
     * Silenciado = volumen de música en 0 (o, si quedó pegado de antes, el silencio de Android). El silencio de
     * Android (ADJUST_MUTE) en este radio dejó al audio Bluetooth mudo para siempre; el volumen sí funciona con
     * todo (JH Music, FM y Bluetooth), así que MUTE baja el volumen a 0 y al activar lo devuelve al de antes.
     */
    private void refreshMuteState() {
        if (audioManager != null) {
            boolean stuck = Build.VERSION.SDK_INT >= 23 && audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
            muted = stuck || audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
        }
        updateMuteUi();
    }

    private void toggleMute() {
        setMuted(!muted, true);
    }

    private static final String PREF_VOLUME_BEFORE_MUTE = "volume_before_mute";

    @SuppressWarnings("deprecation")
    private void setMuted(boolean target, boolean notify) {
        if (audioManager == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("jh_launcher", MODE_PRIVATE);
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        try {
            if (target) {
                int now = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                // se recuerda en disco por si el launcher se cierra estando en silencio
                if (now > 0) prefs.edit().putInt(PREF_VOLUME_BEFORE_MUTE, now).apply();
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            } else {
                // limpia un silencio de Android que haya quedado pegado y devuelve el volumen de antes
                if (Build.VERSION.SDK_INT >= 23) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                } else {
                    audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                }
                int back = prefs.getInt(PREF_VOLUME_BEFORE_MUTE, Math.round(max / 2f));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, Math.min(max, back)), 0);
                // el módulo Bluetooth tampoco debe tener silencio (de pruebas o de antes)
                if (btLink != null && btLink.isLinked()) btLink.muteMusic(false);
            }
        } catch (RuntimeException e) {
            if (notify) showToast("No se pudo cambiar el audio");
            return;
        }
        muted = target;
        volumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
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
        launchCandidates(new String[]{NATIVE_RADIO_PKG, "com.autochips.radio", "com.xyauto.radio", "com.android.fmradio",
                "com.microntek.radio", "com.syu.radio"}, "Radio FM", null, "radio");
    }

    /** «BT música» de fábrica: la app que ves en el menú con ese nombre (no la de llamadas). */
    private void launchBtMusic() {
        Intent intent = findLaunchIntentByLabel("bt musica", "bt music", "btmusic", "musica bt", "musica bluetooth");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startSafely(intent, "No se pudo abrir BT música");
        } else {
            showToast("No encuentro «BT música»: abro Llamadas BT");
            launchBluetooth();
        }
    }

    /** Teléfono / manos libres de fábrica: la app que ves en el menú como «Llamada BT». */
    private void launchBtCalls() {
        Intent intent = findLaunchIntentByLabel("llamada", "manos libres", "telefono");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startSafely(intent, "No se pudo abrir Llamada BT");
        } else {
            launchBluetooth();
        }
    }

    /**
     * Busca una app por el nombre que muestra el menú (la etiqueta de su ícono), sin tildes ni mayúsculas.
     * El nombre del paquete no sirve: «BT música» y «Llamada BT» pueden compartir la palabra «bluetooth».
     */
    private Intent findLaunchIntentByLabel(String... words) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.startsWith("com.jh.")) continue;
            String label = Normalizer.normalize(String.valueOf(ri.loadLabel(pm)), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
            for (String w : words) {
                if (label.contains(w)) {
                    return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                }
            }
        }
        return null;
    }

    private void launchBluetooth() {
        String[] candidates = {"com.autochips.bluetooth", "com.xyauto.bluetooth", "com.ts.bluetooth",
                "com.microntek.bluetooth"};
        if (findLaunchIntent(candidates) != null || findLaunchIntentByKeyword(BT_KEYWORDS) != null) {
            launchCandidates(candidates, "Bluetooth Manos Libres", null, BT_KEYWORDS);
        } else {
            startSafely(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "No se pudo abrir Bluetooth");
        }
    }

    /** Cajón de apps propio: ACTION_ALL_APPS solo lo atiende un launcher con cajón, y este no lo tiene. */
    private void launchAllApps() {
        startSafely(new Intent(this, AppsActivity.class), "No se pudo abrir el cajón de apps");
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

    /**
     * Respaldo cuando ningún paquete conocido está instalado: busca entre las apps con ícono
     * una cuyo nombre o paquete contenga alguna palabra clave, prefiriendo las del sistema
     * (la app de radio o de Bluetooth de fábrica) y sin tocar las propias de JH.
     */
    private Intent findLaunchIntentByKeyword(String[] keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        ResolveInfo best = null;
        boolean bestIsSystem = false;
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.startsWith("com.jh.")) continue;
            String haystack = (pkg + " " + ri.loadLabel(pm)).toLowerCase(Locale.ROOT);
            boolean match = false;
            for (String k : keywords) {
                if (haystack.contains(k)) { match = true; break; }
            }
            if (!match) continue;
            boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (best == null || (system && !bestIsSystem)) {
                best = ri;
                bestIsSystem = system;
            }
        }
        if (best == null) return null;
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(best.activityInfo.packageName, best.activityInfo.name));
    }

    private void launchCandidates(String[] packages, String label) {
        launchCandidates(packages, label, null);
    }

    private void launchCandidates(String[] packages, String label, Bundle extras, String... keywords) {
        Intent intent = findLaunchIntent(packages);
        if (intent == null && keywords.length > 0) intent = findLaunchIntentByKeyword(keywords);
        if (intent == null) {
            showToast("App " + label + " no encontrada: ábrela desde APPS");
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
        /** La canción viene de Mi Mix (JH Music lo avisa en extra_from_mix y fromMix); false si no lo dice. */
        boolean fromMix;
        /** Posición real que informó JH Music, o -1 si no la mandó (versiones viejas). */
        int positionSec = -1;

        Track(String title, String artist, String path, String duration, int totalSec) {
            this.title = title;
            this.artist = artist.isEmpty() ? "JH Music · USB 1" : artist;
            this.path = path;
            this.duration = duration;
            this.totalSec = totalSec;
        }
    }
}
