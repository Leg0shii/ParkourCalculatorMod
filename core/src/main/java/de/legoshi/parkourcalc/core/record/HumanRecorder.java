package de.legoshi.parkourcalc.core.record;

import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

public final class HumanRecorder {

    public static final String FORMAT = "pkc-human-recording";
    public static final int FORMAT_VERSION = 1;
    public static final String DIRECTORY = "recordings";
    public static final String EXTENSION = ".jsonl";

    private final Supplier<FileSystemSaveStore> store;
    private final Supplier<Float> sensitivity;
    private volatile boolean recording;
    private Writer out;
    private Path file;
    private long startNanos;
    private long startedAtEpochMs;
    private int tick = -1;
    private boolean tickOpen;
    private int tickCount;
    private int mouseCount;
    private int keyCount;
    private Path lastFile;
    private String lastError;

    public HumanRecorder(Supplier<FileSystemSaveStore> store, Supplier<Float> sensitivity) {
        this.store = store;
        this.sensitivity = sensitivity;
    }

    public boolean isRecording() {
        return recording;
    }

    public synchronized int tickCount() {
        return tickCount;
    }

    public synchronized int mouseCount() {
        return mouseCount;
    }

    public synchronized int keyCount() {
        return keyCount;
    }

    public synchronized Path lastFile() {
        return lastFile;
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized long elapsedMs() {
        return recording ? (System.nanoTime() - startNanos) / 1_000_000L : 0L;
    }

    public Path directory() {
        FileSystemSaveStore s = store.get();
        return s == null ? null : s.getSaveDir().resolve(DIRECTORY);
    }

    public synchronized boolean start() {
        if (recording) return false;
        Path dir = directory();
        lastError = null;
        if (dir == null) {
            lastError = "no save folder";
            return false;
        }
        startNanos = System.nanoTime();
        startedAtEpochMs = System.currentTimeMillis();
        tick = -1;
        tickOpen = false;
        tickCount = 0;
        mouseCount = 0;
        keyCount = 0;
        try {
            Files.createDirectories(dir);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(startedAtEpochMs));
            file = dir.resolve("recording_" + stamp + EXTENSION);
            out = new BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8), 1 << 16);
            out.write(header());
            out.write('\n');
        } catch (IOException e) {
            lastError = e.getMessage();
            closeQuietly();
            return false;
        }
        recording = true;
        return true;
    }

    public synchronized Path stop() {
        if (!recording) return null;
        recording = false;
        try {
            out.write("{\"e\":\"end\",\"us\":" + micros() + ",\"ticks\":" + tickCount + ",\"mouse\":" + mouseCount
                    + ",\"keys\":" + keyCount + "}\n");
            out.close();
            out = null;
            lastFile = file;
            return file;
        } catch (IOException e) {
            lastError = e.getMessage();
            closeQuietly();
            return null;
        }
    }

    public synchronized void tickStart(double x, double y, double z, float yaw, float pitch, boolean ground) {
        if (!recording) return;
        tick++;
        tickOpen = true;
        tickCount++;
        write("{\"e\":\"tick\",\"t\":" + tick + ",\"us\":" + micros()
                + ",\"x\":" + fmt(x) + ",\"y\":" + fmt(y) + ",\"z\":" + fmt(z)
                + ",\"yaw\":" + fmt(yaw) + ",\"pitch\":" + fmt(pitch) + ",\"ground\":" + ground + "}");
    }

    public synchronized void tickEnd(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sneak,
                                     boolean sprintKey, boolean sprinting) {
        if (!recording || !tickOpen) return;
        tickOpen = false;
        StringBuilder k = new StringBuilder();
        if (w) k.append('W');
        if (a) k.append('A');
        if (s) k.append('S');
        if (d) k.append('D');
        if (jump) k.append('J');
        if (sneak) k.append('N');
        if (sprintKey) k.append('P');
        write("{\"e\":\"tickEnd\",\"t\":" + tick + ",\"us\":" + micros() + ",\"keys\":\"" + k + "\",\"sprinting\":" + sprinting + "}");
    }

    public synchronized void mouse(long eventNs, double dx, double dy, float yaw, float pitch) {
        if (!recording || (dx == 0.0 && dy == 0.0)) return;
        mouseCount++;
        write("{\"e\":\"mouse\",\"us\":" + micros() + (eventNs != 0L ? ",\"eventNs\":" + eventNs : "")
                + ",\"dx\":" + fmt(dx) + ",\"dy\":" + fmt(dy) + ",\"yaw\":" + fmt(yaw) + ",\"pitch\":" + fmt(pitch) + "}");
    }

    public synchronized void key(long eventNs, int code, boolean down) {
        if (!recording) return;
        keyCount++;
        write("{\"e\":\"key\",\"us\":" + micros() + (eventNs != 0L ? ",\"eventNs\":" + eventNs : "")
                + ",\"code\":" + code + ",\"down\":" + down + "}");
    }

    public synchronized void button(long eventNs, int button, boolean down) {
        if (!recording) return;
        keyCount++;
        write("{\"e\":\"button\",\"us\":" + micros() + (eventNs != 0L ? ",\"eventNs\":" + eventNs : "")
                + ",\"button\":" + button + ",\"down\":" + down + "}");
    }

    private String header() {
        FileSystemSaveStore s = store.get();
        float sens = sensitivity.get();
        return "{\"e\":\"header\",\"format\":\"" + FORMAT + "\",\"version\":" + FORMAT_VERSION
                + ",\"modVersion\":" + quote(s == null ? null : s.getModVersion())
                + ",\"mcVersion\":" + quote(s == null ? null : s.getMcVersion())
                + ",\"startedAtEpochMs\":" + startedAtEpochMs
                + ",\"sensitivity\":" + fmt(sens) + ",\"pixelDeg\":" + fmt(TurnProfile.pixelDeg(sens))
                + ",\"timeUnit\":\"us since start\"}";
    }

    private void write(String row) {
        try {
            out.write(row);
            out.write('\n');
        } catch (IOException e) {
            lastError = e.getMessage();
            recording = false;
            closeQuietly();
        }
    }

    private void closeQuietly() {
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
        }
        out = null;
    }

    private long micros() {
        return (System.nanoTime() - startNanos) / 1000L;
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        return Double.toString(v);
    }

    private static String quote(String s) {
        if (s == null) return "null";
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
