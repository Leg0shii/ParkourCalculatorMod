package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class SmoothProfile {

    static final boolean ON = "1".equals(System.getenv("PKC_SMOOTH_PROF"));

    private static final Map<String, long[]> CELLS = new TreeMap<>();

    private SmoothProfile() {
    }

    static synchronized void add(String key, long t0) {
        if (!ON) return;
        long[] c = CELLS.computeIfAbsent(key, k -> new long[2]);
        c[0] += System.nanoTime() - t0;
        c[1]++;
    }

    static synchronized void count(String key) {
        if (!ON) return;
        long[] c = CELLS.computeIfAbsent(key, k -> new long[2]);
        c[1]++;
    }

    static synchronized void reset() {
        CELLS.clear();
    }

    static synchronized void report(long totalNanos) {
        if (!ON) return;
        List<Map.Entry<String, long[]>> rows = new ArrayList<>(CELLS.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        System.out.printf(Locale.ROOT, "PROF total=%.1fs%n", totalNanos / 1e9);
        for (Map.Entry<String, long[]> e : rows) {
            long[] c = e.getValue();
            System.out.printf(Locale.ROOT, "PROF %-28s %8.2fs %6.1f%% calls=%-7d avg=%.2fms%n", e.getKey(), c[0] / 1e9,
                    totalNanos > 0 ? 100.0 * c[0] / totalNanos : 0.0, c[1], c[1] > 0 ? c[0] / 1e6 / c[1] : 0.0);
        }
    }
}
