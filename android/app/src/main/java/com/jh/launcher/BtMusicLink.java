package com.jh.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.jancar.btservice.bluetooth.IBluetooth;
import com.jancar.btservice.bluetooth.IBluetoothCallback;
import com.jancar.btservice.bluetooth.IBluetoothExecCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enlace con el módulo Bluetooth de fábrica (com.jancar.btservice): quién está conectado, qué canción
 * suena en el teléfono y los botones de reproducir / pausar / siguiente / anterior. Las teclas multimedia
 * del sistema no llegan a ese módulo, por eso hay que pedírselo a su servicio.
 *
 * Todos los avisos llegan en el hilo principal. Sin el servicio (por ejemplo en el emulador) no hace nada.
 */
final class BtMusicLink {

    interface Listener {
        /** Se conectó o se perdió la conexión con el servicio Bluetooth de fábrica. */
        void onLink(boolean linked);

        /** Hay (o ya no hay) un teléfono conectado; el nombre puede ir vacío. */
        void onPhone(boolean connected, String name);

        /** El teléfono está transmitiendo audio (true) o está en pausa (false). */
        void onStreaming(boolean streaming);

        /** Datos de la canción que suena en el teléfono. */
        void onTrack(String title, String artist, String album, long durationMs);
    }

    private static final String TAG = "JhBt";
    private static final String BT_PKG = "com.jancar.btservice";
    private static final String ACTION_BT = "com.jancar.btservice.action.bluetooth";

    private static final int CONNECTED = 1;
    private static final int CONNECTING = 2;
    private static final int A2DP_STREAMING = 4;

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private volatile IBluetooth bt;
    private boolean bound;

    BtMusicLink(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    boolean isLinked() {
        return bt != null;
    }

    void start() {
        if (bound) return;
        try {
            // igual que el SDK de Jancar: primero se arranca el servicio y luego se conecta
            ctx.startService(new Intent(ACTION_BT).setPackage(BT_PKG));
        } catch (RuntimeException e) {
            Log.w(TAG, "startService falló: " + e);
        }
        try {
            bound = ctx.bindService(new Intent(ACTION_BT).setPackage(BT_PKG), conn, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) {
            Log.w(TAG, "bindService falló: " + e);
        }
        Log.d(TAG, "bindService → " + bound);
    }

    void stop() {
        final IBluetooth b = bt;
        bt = null;
        if (b != null) {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        b.unrequestBluetoothListener(callback);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
        if (bound) {
            try {
                ctx.unbindService(conn);
            } catch (RuntimeException ignored) {
            }
            bound = false;
        }
    }

    // ------------------------------------------------------------------ órdenes

    void next() {
        command(8);
    }

    void prev() {
        command(9);
    }

    void playPause() {
        command(10);
    }

    /**
     * Silencia o activa el audio del módulo Bluetooth. El silencio de Android no basta: ese audio entra por el
     * módulo del radio, que tiene su propio silencio y puede quedarse callado aunque Android ya suene.
     */
    void muteMusic(final boolean mute) {
        final IBluetooth b = bt;
        if (b == null) return;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    b.muteBtMusic(mute, exec);
                    if (!mute) b.muteBluetoothModule(false, exec);
                } catch (Throwable t) {
                    Log.w(TAG, "silencio BT falló: " + t);
                }
            }
        });
    }

    /** Vuelve a pedir el nombre del teléfono y la canción actual. */
    void refresh() {
        command(14);
        command(52);
    }

    /** Código = número de transacción de IBluetooth (8 siguiente, 9 anterior, 10 reproducir/pausar…). */
    private void command(final int what) {
        final IBluetooth b = bt;
        if (b == null) return;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    switch (what) {
                        case 8: b.nextBtMusic(exec); break;
                        case 9: b.prevBtMusic(exec); break;
                        case 10: b.playAndPause(exec); break;
                        case 14: b.getBtMusicId3Info(exec); break;
                        case 52: b.getCurrentDeviceName(execName); break;
                        default: break;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "orden " + what + " falló: " + t);
                }
            }
        });
    }

    // ------------------------------------------------------------------ conexión

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            final IBluetooth b = IBluetooth.Stub.asInterface(binder);
            bt = b;
            io.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        b.requestBluetoothListener(callback);
                        b.getCurrentDeviceName(execName);
                        b.getBtMusicId3Info(exec);
                    } catch (Throwable t) {
                        Log.w(TAG, "registro falló: " + t);
                    }
                }
            });
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onLink(true);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bt = null;
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onLink(false);
                }
            });
        }
    };

    // ------------------------------------------------------------------ avisos del servicio

    /** Respuesta a las órdenes: solo se anota (el resultado real llega por los avisos). */
    private final IBluetoothExecCallback.Stub exec = new IBluetoothExecCallback.Stub() {
        @Override
        public void onSuccess(String result) {
            Log.d(TAG, "ok: " + result);
        }

        @Override
        public void onFailure(int code) {
            Log.d(TAG, "falló: " + code);
        }
    };

    /** Respuesta de «nombre del teléfono conectado». */
    private final IBluetoothExecCallback.Stub execName = new IBluetoothExecCallback.Stub() {
        @Override
        public void onSuccess(final String result) {
            Log.d(TAG, "teléfono: " + result);
            if (result == null || result.trim().isEmpty()) return;
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onPhone(true, result.trim());
                }
            });
        }

        @Override
        public void onFailure(int code) {
            Log.d(TAG, "teléfono: falló " + code);
        }
    };

    private final IBluetoothCallback.Stub callback = new IBluetoothCallback.Stub() {
        @Override
        public void onConnectStatus(final int status, final String name, String addr) {
            Log.d(TAG, "conexión " + status + " " + name + " " + addr);
            if (status == CONNECTING) return;
            // 1 = conectado; 3 = falló; cualquier otro valor: sin teléfono
            final boolean connected = status == CONNECTED;
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onPhone(connected, name == null ? "" : name.trim());
                }
            });
        }

        @Override
        public void onCallStatus(int status, String number, String name) {
        }

        @Override
        public void onVoiceChange(int value) {
        }

        @Override
        public void onA2DPConnectStatus(final int status, boolean flag) {
            Log.d(TAG, "A2DP " + status + " " + flag);
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onStreaming(status == A2DP_STREAMING);
                }
            });
        }

        @Override
        public void onBtMusicId3Info(final String title, final String artist, final String album, final long duration) {
            Log.d(TAG, "canción: " + title + " / " + artist + " / " + album + " / " + duration);
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onTrack(title == null ? "" : title.trim(), artist == null ? "" : artist.trim(),
                            album == null ? "" : album.trim(), duration);
                }
            });
        }

        @Override
        public void onBtBatteryValue(int value) {
        }

        @Override
        public void onBtSignalValue(int value) {
        }

        @Override
        public void onPowerStatus(boolean on) {
        }
    };
}
