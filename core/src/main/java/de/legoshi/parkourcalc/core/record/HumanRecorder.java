package de.legoshi.parkourcalc.core.record;

import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class HumanRecorder {

    public static final String FORMAT = "pkc-human-recording";
    public static final int FORMAT_VERSION = 1;
    public static final String DIRECTORY = "recordings";
    public static final int MAX_TICKS = 20 * 60 * 10;
    public static final int MAX_MOUSE_ROWS = 500_000;

    public static final class Tick {
        public final int index;
        public final long us;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        public final boolean ground;
        String keys = "";
        boolean sprinting;
        long endUs = -1;

        Tick(int index, long us, double x, double y, double z, float yaw, float pitch, boolean ground) {
            this.index = index;
            this.us = us;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.ground = ground;
        }
    }

    private static final class Mouse {
        final long us;
        final long eventNs;
        final double dx;
        final double dy;
        final float yaw;
        final float pitch;

        Mouse(long us, long eventNs, double dx, double dy, float yaw, float pitch) {
            this.us = us;
            this.eventNs = eventNs;
            this.dx = dx;
            this.dy = dy;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class Key {
        final long us;
        final long eventNs;
        final int code;
        final boolean down;

        Key(long us, long eventNs, int code, boolean down) {
            this.us = us;
            this.eventNs = eventNs;
            this.code = code;
            this.down = down;
        }
    }

    private final Supplier<FileSystemSaveStore> store;
    private final Supplier<Float> sensitivity;
    private final List<Tick> ticks = new ArrayList<>();
    private final List<Mouse> mouse = new ArrayList<>();
    private final List<Key> keys = new ArrayList<>();
    private volatile boolean recording;
    private long startNanos;
    private long startedAtEpochMs;
    private float startSensitivity;
    private Path lastFile;
    private String lastError;
    private boolean hitLimit;

    public HumanRecorder(Supplier<FileSystemSaveStore> store, Supplier<Float> sensitivity) {
        this.store = store;
        this.sensitivity = sensitivity;
    }

    public boolean isRecording() {
        return recording;
    }

    public synchronized int tickCount() {
        return ticks.size();
    }

    public synchronized int mouseCount() {
        return mouse.size();
    }

    public synchronized int keyCount() {
        return keys.size();
    }

    public synchronized Path lastFile() {
        return lastFile;
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized boolean hitLimit() {
        return hitLimit;
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
        ticks.clear();
        mouse.clear();
        keys.clear();
        startNanos = System.nanoTime();
        startedAtEpochMs = System.currentTimeMillis();
        startSensitivity = sensitivity.get();
        lastError = null;
        hitLimit = false;
        recording = true;
        return true;
    }

    public synchronized Path stop() {
        if (!recording) return null;
        recording = false;
        Path dir = directory();
        if (dir == null) {
            lastError = "no save folder";
            return null;
        }
        try {
            Files.createDirectories(dir);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(startedAtEpochMs));
            Path file = dir.resolve("recording_" + stamp + ".json");
            Files.write(file, toJson().getBytes(StandardCharsets.UTF_8));
            lastFile = file;
            return file;
        } catch (IOException e) {
            lastError = e.getMessage();
            return null;
        }
    }

    public synchronized void tickStart(double x, double y, double z, float yaw, float pitch, boolean ground) {
        if (!recording) return;
        if (ticks.size() >= MAX_TICKS) {
            hitLimit = true;
            return;
        }
        ticks.add(new Tick(ticks.size(), micros(), x, y, z, yaw, pitch, ground));
    }

    public synchronized void tickEnd(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sneak,
                                     boolean sprintKey, boolean sprinting) {
        if (!recording || ticks.isEmpty()) return;
        Tick t = ticks.get(ticks.size() - 1);
        if (t.endUs >= 0) return;
        StringBuilder k = new StringBuilder();
        if (w) k.append('W');
        if (a) k.append('A');
        if (s) k.append('S');
        if (d) k.append('D');
        if (jump) k.append('J');
        if (sneak) k.append('N');
        if (sprintKey) k.append('P');
        t.keys = k.toString();
        t.sprinting = sprinting;
        t.endUs = micros();
    }

    public synchronized void mouse(long eventNs, double dx, double dy, float yaw, float pitch) {
        if (!recording || (dx == 0.0 && dy == 0.0)) return;
        if (mouse.size() >= MAX_MOUSE_ROWS) {
            hitLimit = true;
            return;
        }
        mouse.add(new Mouse(micros(), eventNs, dx, dy, yaw, pitch));
    }

    public synchronized void key(long eventNs, int code, boolean down) {
        if (!recording) return;
        keys.add(new Key(micros(), eventNs, code, down));
    }

    private long micros() {
        return (System.nanoTime() - startNanos) / 1000L;
    }

    synchronized String toJson() {
        FileSystemSaveStore s = store.get();
        StringBuilder b = new StringBuilder(64 + ticks.size() * 120 + mouse.size() * 60 + keys.size() * 40);
        b.append("{\"format\":\"").append(FORMAT).append("\",\"version\":").append(FORMAT_VERSION);
        b.append(",\"modVersion\":").append(quote(s == null ? null : s.getModVersion()));
        b.append(",\"mcVersion\":").append(quote(s == null ? null : s.getMcVersion()));
        b.append(",\"startedAtEpochMs\":").append(startedAtEpochMs);
        b.append(",\"sensitivity\":").append(fmt(startSensitivity));
        b.append(",\"pixelDeg\":").append(fmt(TurnProfile.pixelDeg(startSensitivity)));
        b.append(",\"timeUnit\":\"us since start\"");
        b.append(",\"ticks\":[");
        for (int i = 0; i < ticks.size(); i++) {
            Tick t = ticks.get(i);
            if (i > 0) b.append(',');
            b.append("{\"t\":").append(t.index).append(",\"us\":").append(t.us).append(",\"endUs\":").append(t.endUs);
            b.append(",\"x\":").append(fmt(t.x)).append(",\"y\":").append(fmt(t.y)).append(",\"z\":").append(fmt(t.z));
            b.append(",\"yaw\":").append(fmt(t.yaw)).append(",\"pitch\":").append(fmt(t.pitch));
            b.append(",\"ground\":").append(t.ground).append(",\"keys\":\"").append(t.keys).append('"');
            b.append(",\"sprinting\":").append(t.sprinting).append('}');
        }
        b.append("],\"mouse\":[");
        for (int i = 0; i < mouse.size(); i++) {
            Mouse m = mouse.get(i);
            if (i > 0) b.append(',');
            b.append("{\"us\":").append(m.us);
            if (m.eventNs != 0L) b.append(",\"eventNs\":").append(m.eventNs);
            b.append(",\"dx\":").append(fmt(m.dx)).append(",\"dy\":").append(fmt(m.dy));
            b.append(",\"yaw\":").append(fmt(m.yaw)).append(",\"pitch\":").append(fmt(m.pitch)).append('}');
        }
        b.append("],\"keys\":[");
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            if (i > 0) b.append(',');
            b.append("{\"us\":").append(k.us);
            if (k.eventNs != 0L) b.append(",\"eventNs\":").append(k.eventNs);
            b.append(",\"code\":").append(k.code).append(",\"down\":").append(k.down).append('}');
        }
        b.append("]}");
        return b.toString();
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
