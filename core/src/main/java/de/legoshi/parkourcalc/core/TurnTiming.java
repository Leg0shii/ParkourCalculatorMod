package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TurnTiming {

    public static final double TICK_MS = 50.0;

    public static final class Onset {
        public final int attempts;
        public final double median;
        public final double lo;
        public final double hi;

        Onset(int attempts, double median, double lo, double hi) {
            this.attempts = attempts;
            this.median = median;
            this.lo = lo;
            this.hi = hi;
        }
    }

    public static final class TickTrace {
        private static final int INITIAL = 32;
        private long startNs = Long.MIN_VALUE;
        private long[] ns = new long[INITIAL];
        private float[] yaw = new float[INITIAL];
        private int count;
        private long pendingNs = -1L;
        private float pendingYaw;
        private float lastYaw;

        public boolean active() {
            return startNs != Long.MIN_VALUE;
        }

        public void begin(long nowNs, float yaw) {
            startNs = nowNs;
            count = 0;
            pendingNs = -1L;
            lastYaw = yaw;
            push(nowNs, yaw);
        }

        public void clear() {
            startNs = Long.MIN_VALUE;
            count = 0;
            pendingNs = -1L;
        }

        public void frame(long nowNs, float yaw) {
            if (!active() || nowNs < startNs) return;
            if (yaw == lastYaw) {
                pendingNs = nowNs;
                pendingYaw = yaw;
                return;
            }
            if (pendingNs >= 0L) push(pendingNs, pendingYaw);
            pendingNs = -1L;
            push(nowNs, yaw);
            lastYaw = yaw;
        }

        public boolean moved() {
            return count > 1;
        }

        public float onset(long endNs) {
            if (!moved()) return Float.NaN;
            for (int i = 1; i < count; i++) if (yaw[i] != yaw[0]) return phase(ns[i], endNs);
            return Float.NaN;
        }

        public float end(long endNs) {
            if (!moved()) return Float.NaN;
            return phase(ns[count - 1], endNs);
        }

        public float[] points(long endNs) {
            if (!moved()) return null;
            float[] out = new float[count * 2];
            for (int i = 0; i < count; i++) {
                out[i * 2] = phase(ns[i], endNs);
                out[i * 2 + 1] = yaw[i];
            }
            return out;
        }

        private float phase(long t, long endNs) {
            long span = endNs - startNs;
            if (span <= 0L) return Float.NaN;
            double f = (t - startNs) / (double) span;
            return (float) Math.max(0.0, Math.min(1.0, f));
        }

        private void push(long t, float y) {
            if (count == ns.length) {
                ns = Arrays.copyOf(ns, count * 2);
                yaw = Arrays.copyOf(yaw, count * 2);
            }
            ns[count] = t;
            yaw[count] = y;
            count++;
        }
    }

    private TurnTiming() {
    }

    public static int mainTurnTick(TurnProfileController.Current cur) {
        int best = -1;
        double bestDelta = 0.0;
        for (int t = 0; t + 1 < cur.n; t++) {
            double d = Math.abs(Angles.wrapDelta(cur.facing[t + 1] - cur.facing[t]));
            if (best < 0 || d > bestDelta) {
                best = t;
                bestDelta = d;
            }
        }
        return best;
    }

    public static Onset onset(List<TurnAttempt> attempts, int tick, int limit) {
        List<Float> phases = new ArrayList<Float>();
        for (int i = attempts.size() - 1; i >= 0 && phases.size() < limit; i--) {
            TurnAttempt a = attempts.get(i);
            if (!a.judged()) continue;
            float p = a.turnStartAt(tick);
            if (!Float.isNaN(p)) phases.add(p);
        }
        if (phases.isEmpty()) return null;
        float[] v = new float[phases.size()];
        for (int i = 0; i < v.length; i++) v[i] = phases.get(i);
        Arrays.sort(v);
        return new Onset(v.length, quantile(v, 0.5), quantile(v, 0.1), quantile(v, 0.9));
    }

    public static String ms(double phase) {
        return String.format(Locale.ROOT, "%.0f ms", phase * TICK_MS);
    }

    private static double quantile(float[] sorted, double q) {
        if (sorted.length == 1) return sorted[0];
        double pos = q * (sorted.length - 1);
        int i = (int) Math.floor(pos);
        double f = pos - i;
        if (i + 1 >= sorted.length) return sorted[sorted.length - 1];
        return sorted[i] + (sorted[i + 1] - sorted[i]) * f;
    }
}
