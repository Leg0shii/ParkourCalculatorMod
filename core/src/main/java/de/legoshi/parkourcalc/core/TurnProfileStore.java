package de.legoshi.parkourcalc.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class TurnProfileStore {

    public static final String DIRECTORY = ".onejump";
    public static final String LEGACY_DIRECTORY = "parkourcalculator-onejump";
    public static final String EXTENSION = ".jsonl";
    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    static final class RowData {
        String keys;
        Double yaw;
        boolean checkKeys;
        boolean checkYaw;
        boolean still;
        String optional;
    }

    static final class LandingData {
        int tick;
        Double xLo;
        Double xHi;
        Double zLo;
        Double zHi;
        Double y;
    }

    static final class HeaderData {
        int version = 2;
        Integer tasFirstTick;
        Integer axis;
        List<RowData> rows = new ArrayList<RowData>();
        LandingData landing;
    }

    static final class AttemptData {
        int number;
        int firstTick;
        double[] yaws;
        int recorded;
        boolean complete;
        boolean landed;
        boolean inputFailure;
        String verdict;
        Double margin;
        Double marginX;
        Double marginZ;
        int worstTick;
        int failTick;
        String failKeys;
        String expectedKeys;
        int macro;
        float[] turnStart;
        float[] turnEnd;
        boolean turnFailure;
        Double failTurn;
        ForecastData forecast;
        boolean favourite;
        Integer tasFirstTick;
        Double[] solvedOffset;
        int[] pressed;
        boolean[] keysFailed;
        double[] start;
    }

    static final class ForecastData {
        Double[] held;
        Double[] best;
        Double[] bestOffset;
        @SerializedName(value = "failedTick", alternate = "lostTick")
        int failedTick = -1;
        Double[] x;
        Double[] z;
        Double[] vx;
        Double[] vz;
        boolean[] ground;
    }

    private static final float NO_TURN = -1f;

    private final Supplier<FileSystemSaveStore> store;
    private volatile String lastError;
    private int skippedLines;

    public TurnProfileStore(Supplier<FileSystemSaveStore> store) {
        this.store = store;
    }

    public String lastError() {
        return lastError;
    }

    public int skippedLines() {
        return skippedLines;
    }

    public Path fileFor(String name) {
        FileSystemSaveStore s = store.get();
        if (s == null || name == null || name.isEmpty()) return null;
        Path dir = s.getSaveDir().toAbsolutePath().normalize().resolve(DIRECTORY);
        Path p = dir.resolve(name.replace('\\', '/') + EXTENSION).normalize();
        return p.startsWith(dir) ? p : null;
    }

    public void migrateLegacyFolder() {
        FileSystemSaveStore s = store.get();
        if (s == null) return;
        Path saveDir = s.getSaveDir().toAbsolutePath().normalize();
        Path legacy = saveDir.resolveSibling(LEGACY_DIRECTORY);
        Path target = saveDir.resolve(DIRECTORY);
        if (!Files.isDirectory(legacy) || Files.exists(target)) return;
        try {
            Files.createDirectories(saveDir);
            Files.move(legacy, target);
        } catch (Exception e) {
            lastError = e.getMessage();
        }
    }

    public boolean moveToTrash(String name) {
        Path p = fileFor(name);
        if (p == null || !Files.exists(p)) return false;
        Path trash = store.get().getSaveDir().toAbsolutePath().normalize().resolve(FileSystemSaveStore.TRASH_DIR);
        String flat = name.replace('/', '_').replace('\\', '_');
        try {
            Files.createDirectories(trash);
            Files.move(p, trash.resolve("onejump_" + flat + "_" + System.currentTimeMillis() + EXTENSION));
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean load(String name, TurnProfileDocument doc) {
        Path p = fileFor(name);
        skippedLines = 0;
        lastError = null;
        if (p == null || !Files.exists(p)) {
            doc.reset();
            return false;
        }
        try (BufferedReader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            TurnReference ref = null;
            List<TurnAttempt> attempts = new ArrayList<TurnAttempt>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (ref == null) {
                    HeaderData h = GSON.fromJson(line, HeaderData.class);
                    ref = toReference(h == null ? new HeaderData() : h);
                    continue;
                }
                TurnAttempt a;
                try {
                    a = toAttempt(GSON.fromJson(line, AttemptData.class), ref.tasFirstTick());
                } catch (RuntimeException e) {
                    a = null;
                }
                if (a != null) attempts.add(a);
                else skippedLines++;
            }
            doc.load(ref == null ? new TurnReference() : ref, attempts);
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            doc.reset();
            return false;
        }
    }

    public boolean save(String name, TurnReference ref, List<TurnAttempt> attempts) {
        Path p = fileFor(name);
        if (p == null) return false;
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        try {
            Files.createDirectories(p.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                out.write(GSON.toJson(toHeader(ref)));
                out.write('\n');
                for (TurnAttempt a : attempts) {
                    out.write(GSON.toJson(toData(a)));
                    out.write('\n');
                }
            }
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
            lastError = null;
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean append(String name, List<TurnAttempt> attempts) {
        Path p = fileFor(name);
        if (p == null || !Files.exists(p)) return false;
        try (BufferedWriter out = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            for (TurnAttempt a : attempts) {
                out.write(GSON.toJson(toData(a)));
                out.write('\n');
            }
            lastError = null;
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
    }

    static HeaderData toHeader(TurnReference ref) {
        HeaderData h = new HeaderData();
        h.tasFirstTick = ref.tasFirstTick() < 0 ? null : ref.tasFirstTick();
        h.axis = ref.axis() == TurnReference.AXIS_BOTH ? null : ref.axis();
        for (int i = 0; i < ref.size(); i++) {
            InputRow r = ref.row(i);
            RowData d = new RowData();
            d.keys = TurnReference.keysText(TurnReference.mask(r));
            d.yaw = box(ref.facing(i));
            d.checkKeys = ref.checkKeys(r);
            d.checkYaw = ref.checkYaw(r);
            d.still = ref.still(r);
            int optional = ref.optionalKeys(r);
            d.optional = optional == 0 ? null : TurnReference.keysText(optional);
            h.rows.add(d);
        }
        TurnReference.Landing l = ref.landing();
        if (l != null) {
            h.landing = new LandingData();
            h.landing.tick = l.tick;
            h.landing.xLo = box(l.xLo);
            h.landing.xHi = box(l.xHi);
            h.landing.zLo = box(l.zLo);
            h.landing.zHi = box(l.zHi);
            h.landing.y = box(l.y);
        }
        return h;
    }

    static AttemptData toData(TurnAttempt a) {
        AttemptData d = new AttemptData();
        d.number = a.number;
        d.firstTick = a.firstTick;
        d.yaws = a.yaws;
        d.recorded = a.recorded;
        d.complete = a.complete;
        d.landed = a.landed;
        d.inputFailure = a.inputFailure;
        d.verdict = a.verdict;
        d.margin = a.hasMargin() ? a.margin : null;
        d.marginX = box(a.marginX);
        d.marginZ = box(a.marginZ);
        d.worstTick = a.worstTick;
        d.failTick = a.failTick;
        d.failKeys = TurnReference.keysText(a.failKeys);
        d.expectedKeys = TurnReference.keysText(a.expectedKeys);
        d.macro = a.macro;
        d.turnStart = packPhases(a.turnStart);
        d.turnEnd = packPhases(a.turnEnd);
        d.turnFailure = a.turnFailure;
        d.failTurn = Double.isNaN(a.failTurn) ? null : a.failTurn;
        d.favourite = a.favourite;
        d.tasFirstTick = a.tasFirstTick < 0 ? null : a.tasFirstTick;
        d.solvedOffset = boxAll(a.solvedOffset);
        d.pressed = a.pressedKeys;
        d.keysFailed = a.keysFailed;
        d.start = a.start;
        if (a.forecast != null) {
            ForecastData f = new ForecastData();
            f.held = boxAll(a.forecast.held);
            f.best = boxAll(a.forecast.best);
            f.bestOffset = boxAll(a.forecast.bestOffset);
            f.failedTick = a.forecast.failedTick;
            f.x = boxAll(a.forecast.x);
            f.z = boxAll(a.forecast.z);
            f.vx = boxAll(a.forecast.vx);
            f.vz = boxAll(a.forecast.vz);
            f.ground = a.forecast.ground;
            d.forecast = f;
        }
        return d;
    }

    private static TurnAttempt.Forecast toForecast(ForecastData f, int n) {
        if (f == null) return null;
        return new TurnAttempt.Forecast(unboxAll(f.held, n), unboxAll(f.best, n), unboxAll(f.bestOffset, n),
                f.failedTick, unboxAll(f.x, n), unboxAll(f.z, n), unboxAll(f.vx, n), unboxAll(f.vz, n), f.ground);
    }

    private static Double[] boxAll(double[] v) {
        if (v == null) return null;
        Double[] out = new Double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = box(v[i]);
        return out;
    }

    private static double[] unboxAll(Double[] v, int n) {
        if (v == null) return null;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = i < v.length ? unbox(v[i]) : Double.NaN;
        return out;
    }

    private static float[] packPhases(float[] phases) {
        if (phases == null) return null;
        float[] out = new float[phases.length];
        for (int i = 0; i < phases.length; i++) out[i] = Float.isNaN(phases[i]) ? NO_TURN : phases[i];
        return out;
    }

    private static float[] unpackPhases(float[] packed, int n) {
        if (packed == null) return null;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = i < packed.length && packed[i] >= 0f ? packed[i] : Float.NaN;
        return out;
    }

    static TurnReference toReference(HeaderData h) {
        TurnReference ref = new TurnReference();
        List<InputRow> rows = new ArrayList<InputRow>();
        List<Double> yaws = new ArrayList<Double>();
        if (h.rows != null) {
            for (RowData d : h.rows) {
                if (d == null) continue;
                InputRow r = new InputRow();
                TurnReference.applyKeys(r, TurnReference.parseKeys(d.keys == null ? "" : d.keys));
                if (d.optional != null) TurnReference.applyOptional(r, TurnReference.parseKeys(d.optional));
                r.setOnejumpKeys(d.checkKeys);
                r.setOnejumpFace(d.still ? InputRow.ONEJUMP_FACE_STILL
                        : d.checkYaw ? InputRow.ONEJUMP_FACE_CHECK : InputRow.ONEJUMP_FACE_OFF);
                rows.add(r);
                yaws.add(d.yaw);
            }
        }
        double[] facings = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) facings[i] = unbox(yaws.get(i));
        ref.replace(rows, facings);
        ref.setTasFirstTick(h.tasFirstTick == null ? -1 : h.tasFirstTick);
        ref.setAxis(h.axis == null ? TurnReference.AXIS_BOTH : h.axis);
        if (h.landing != null) {
            ref.setLanding(new TurnReference.Landing(h.landing.tick, unbox(h.landing.xLo), unbox(h.landing.xHi),
                    unbox(h.landing.zLo), unbox(h.landing.zHi), unbox(h.landing.y)));
        }
        return ref;
    }

    static TurnAttempt toAttempt(AttemptData d, int headerFirstTick) {
        if (d == null || d.yaws == null) return null;
        boolean timed = d.turnStart != null && d.turnEnd != null;
        TurnAttempt a = new TurnAttempt(d.number, d.firstTick, d.yaws, Math.min(d.recorded, d.yaws.length), d.complete,
                d.landed, d.inputFailure, d.verdict == null ? "" : d.verdict,
                d.margin == null ? Double.NaN : d.margin, d.worstTick, d.failTick,
                TurnReference.parseKeys(d.failKeys == null ? "" : d.failKeys),
                TurnReference.parseKeys(d.expectedKeys == null ? "" : d.expectedKeys), d.macro,
                timed ? unpackPhases(d.turnStart, d.yaws.length) : null,
                timed ? unpackPhases(d.turnEnd, d.yaws.length) : null, null, d.turnFailure,
                d.failTurn == null ? Double.NaN : d.failTurn, toForecast(d.forecast, d.yaws.length));
        a.favourite = d.favourite;
        a.setAxisMargins(unbox(d.marginX), unbox(d.marginZ));
        a.tasFirstTick = d.tasFirstTick == null ? headerFirstTick : d.tasFirstTick;
        a.solvedOffset = unboxAll(d.solvedOffset, d.yaws.length);
        a.pressedKeys = d.pressed;
        a.keysFailed = d.keysFailed;
        a.start = d.start;
        return a;
    }

    private static Double box(double v) {
        return Double.isNaN(v) ? null : v;
    }

    private static double unbox(Double v) {
        return v == null ? Double.NaN : v;
    }
}
