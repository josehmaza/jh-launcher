package com.jh.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Lectura real de CPU, RAM y almacenamiento interno para la mini telemetría bajo el auto.
 * Se llama desde el hilo de E/S, nunca desde el hilo de la interfaz.
 */
final class HwMonitor {

    /** Valor devuelto cuando el sistema no permite leer el dato. */
    static final int UNKNOWN = -1;

    private final ActivityManager activityManager;
    private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

    private boolean procStatReadable = true;
    private long prevIdle = -1;
    private long prevTotal = -1;

    HwMonitor(Context context) {
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    int readRamPercent() {
        if (activityManager == null) return UNKNOWN;
        activityManager.getMemoryInfo(memoryInfo);
        if (memoryInfo.totalMem <= 0) return UNKNOWN;
        return (int) ((memoryInfo.totalMem - memoryInfo.availMem) * 100 / memoryInfo.totalMem);
    }

    int readStoragePercent() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getBlockCountLong();
            if (total <= 0) return UNKNOWN;
            return (int) ((total - stat.getAvailableBlocksLong()) * 100 / total);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * Uso de CPU entre esta lectura y la anterior. Desde Android 8 las apps ya no pueden
     * leer /proc/stat; en ese caso se estima con la frecuencia actual de cada núcleo
     * respecto a su máxima (el gobernador sube la frecuencia cuando hay carga).
     */
    int readCpuPercent() {
        if (procStatReadable) {
            int fromStat = readProcStat();
            if (fromStat != UNKNOWN || procStatReadable) return fromStat;
        }
        return readCpuFreqLoad();
    }

    private int readProcStat() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                procStatReadable = false;
                return UNKNOWN;
            }
            String[] parts = line.trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < parts.length; i++) total += Long.parseLong(parts[i]);
            // idle + iowait
            long idle = Long.parseLong(parts[4]) + (parts.length > 5 ? Long.parseLong(parts[5]) : 0);

            int result = UNKNOWN;
            if (prevTotal >= 0 && total > prevTotal) {
                long dTotal = total - prevTotal;
                long dIdle = idle - prevIdle;
                result = (int) ((dTotal - dIdle) * 100 / dTotal);
            }
            prevTotal = total;
            prevIdle = idle;
            return result;
        } catch (IOException | RuntimeException e) {
            procStatReadable = false;
            return UNKNOWN;
        }
    }

    private int readCpuFreqLoad() {
        long cur = 0;
        long max = 0;
        int cpus = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cpus; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            long coreMax = readLong(base + "cpuinfo_max_freq");
            if (coreMax <= 0) continue;
            max += coreMax;
            // Un núcleo apagado (hotplug) no tiene scaling_cur_freq: cuenta como 0
            cur += Math.max(0, readLong(base + "scaling_cur_freq"));
        }
        if (max <= 0) return UNKNOWN;
        return (int) (cur * 100 / max);
    }

    private static final String[] USB1_PATHS = {
            "/mnt/udisk1", "/storage/udisk1", "/storage/usb0", "/mnt/media_rw/udisk1", "/storage/usbotg"
    };

    private static final String[] USB2_PATHS = {
            "/mnt/udisk2", "/storage/udisk2", "/storage/usb1", "/mnt/media_rw/udisk2"
    };

    boolean isUsb1Connected() {
        return checkUsbPaths(USB1_PATHS);
    }

    boolean isUsb2Connected() {
        return checkUsbPaths(USB2_PATHS);
    }

    private static boolean checkUsbPaths(String[] paths) {
        for (String p : paths) {
            try {
                File f = new File(p);
                if (f.exists()) {
                    File target = f;
                    try {
                        target = f.getCanonicalFile();
                    } catch (IOException ignored) {}
                    String[] list = target.list();
                    if (list == null) list = f.list();
                    int count = list != null ? list.length : 0;
                    if (count > 0 || f.canRead()) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static long readLong(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            return line != null ? Long.parseLong(line.trim()) : -1;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }
}
