package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public final class SolverTrace {

    private static final Object LOCK = new Object();
    private static volatile boolean on;
    private static Writer writer;
    private static long epoch;

    static {
        String v = System.getProperty("pkc.solver.trace");
        if (v == null || v.isEmpty()) v = System.getenv("PKC_SOLVER_TRACE");
        if (v != null && !v.isEmpty() && !"0".equals(v)) {
            enable("1".equals(v) ? defaultTag() : v);
        }
    }

    private SolverTrace() {
    }

    public static boolean on() {
        return on;
    }

    public static void enable(String tag) {
        synchronized (LOCK) {
            closeLocked();
            File dir = new File("build/reports");
            dir.mkdirs();
            File out = new File(dir, "solver-trace-" + tag + ".txt");
            try {
                writer = new BufferedWriter(new FileWriter(out, false));
            } catch (IOException e) {
                writer = null;
                return;
            }
            epoch = System.nanoTime();
            on = true;
        }
    }

    public static void disable() {
        synchronized (LOCK) {
            closeLocked();
        }
    }

    public static void solveStart(String label) {
        if (!on) return;
        synchronized (LOCK) {
            if (!on) return;
            epoch = System.nanoTime();
            writeLocked("SOLVE", "start " + label);
        }
    }

    public static void log(String stage, String message) {
        if (!on) return;
        synchronized (LOCK) {
            if (!on) return;
            writeLocked(stage, message);
        }
    }

    public static void log(String stage, String format, Object... args) {
        if (!on) return;
        synchronized (LOCK) {
            if (!on) return;
            writeLocked(stage, String.format(java.util.Locale.ROOT, format, args));
        }
    }

    private static void writeLocked(String stage, String message) {
        try {
            double ms = (System.nanoTime() - epoch) / 1.0e6;
            writer.write(String.format(java.util.Locale.ROOT, "%10.2f  t%-3d %-7s %s%n",
                    ms, Thread.currentThread().getId() % 1000, stage, message));
            writer.flush();
        } catch (IOException e) {
            closeLocked();
        }
    }

    private static void closeLocked() {
        on = false;
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    private static String defaultTag() {
        return new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
    }

    public static String fmt(String format, Object... args) {
        return String.format(java.util.Locale.ROOT, format, args);
    }

    public static String patternLabel(boolean[] zeroX, boolean[] zeroZ) {
        String x = axisRuns(zeroX);
        String z = axisRuns(zeroZ);
        if (x.isEmpty() && z.isEmpty()) return "free";
        StringBuilder sb = new StringBuilder();
        if (!x.isEmpty()) sb.append("x").append(x);
        if (!z.isEmpty()) {
            if (sb.length() > 0) sb.append('+');
            sb.append("z").append(z);
        }
        return sb.toString();
    }

    private static String axisRuns(boolean[] zero) {
        if (zero == null) return "";
        StringBuilder sb = new StringBuilder();
        int t = 0;
        while (t < zero.length) {
            if (!zero[t]) {
                t++;
                continue;
            }
            int end = t;
            while (end + 1 < zero.length && zero[end + 1]) end++;
            if (sb.length() > 0) sb.append(',');
            sb.append('@').append(t);
            if (end != t) sb.append('-').append(end);
            t = end + 1;
        }
        return sb.toString();
    }
}
