package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.Arrays;

final class YawOrderDp {

    static final int N = 4096;
    static final double H = 360.0 / N;
    private static final double NEG = Double.NEGATIVE_INFINITY;

    private YawOrderDp() {
    }

    static double[] orderedYaws(YawOrder order, YawTies ties, int n, double[] amp, double[] phase) {
        int[] unitOf = new int[n];
        double[] off = new double[n];
        double[] pin = new double[n];
        int units = 0;
        int lastGroup = Integer.MIN_VALUE;
        for (int t = 0; t < n; t++) {
            int g = ties == null ? t : ties.groupOf(t);
            if (ties == null || g != lastGroup) {
                if (ties != null) {
                    for (int s = 0; s < t; s++) if (ties.groupOf(s) == g) return null;
                }
                units++;
                lastGroup = g;
            }
            unitOf[t] = units - 1;
            off[t] = ties == null ? 0.0 : ties.offsetOf(t);
            pin[t] = ties == null || ties.varOf(t) >= 0 ? Double.NaN : ties.pinOf(t);
        }
        int[] linkOf = new int[units];
        Arrays.fill(linkOf, -1);
        for (int l = 0; l < order.links().size(); l++) {
            YawOrder.Link link = order.links().get(l);
            int a = Math.min(link.hiTick, link.loTick);
            int b = Math.max(link.hiTick, link.loTick);
            if (b >= n || unitOf[a] == unitOf[b]) continue;
            if (unitOf[b] != unitOf[a] + 1 || linkOf[unitOf[b]] >= 0) return null;
            linkOf[unitOf[b]] = l;
        }

        double[] prev = null;
        int[][] back = new int[units][];
        double[] cur = new double[N];
        int[] firstTick = new int[units];
        Arrays.fill(firstTick, -1);
        for (int t = 0; t < n; t++) if (firstTick[unitOf[t]] < 0) firstTick[unitOf[t]] = t;
        for (int u = 0; u < units; u++) {
            Arrays.fill(cur, 0.0);
            for (int t = 0; t < n; t++) {
                if (unitOf[t] != u) continue;
                if (!Double.isNaN(pin[t])) {
                    int j = indexOf(pin[t] - off[t]);
                    for (int i = 0; i < N; i++) if (i != j) cur[i] = NEG;
                    continue;
                }
                if (amp[t] <= 0.0) continue;
                double base = off[t] - phase[t];
                for (int j = 0; j < N; j++) {
                    cur[j] += amp[t] * StrictMath.cos(Angles.rad(-180.0 + j * H + base));
                }
            }
            for (YawOrder.Bound b : order.bounds()) {
                if (b.tick >= n || unitOf[b.tick] != u) continue;
                double mid = 0.5 * (b.loDeg + b.hiDeg);
                for (int j = 0; j < N; j++) {
                    double rel = mid + Angles.wrapDelta(-180.0 + j * H + off[b.tick] - mid);
                    if (rel < b.loDeg - 0.5 * H || rel > b.hiDeg + 0.5 * H) cur[j] = NEG;
                }
            }
            if (u > 0) {
                int[] ptr = new int[N];
                int l = linkOf[u];
                if (l < 0) {
                    int bestJ = 0;
                    for (int j = 1; j < N; j++) if (prev[j] > prev[bestJ]) bestJ = j;
                    for (int j = 0; j < N; j++) {
                        cur[j] += prev[bestJ];
                        ptr[j] = bestJ;
                    }
                } else {
                    YawOrder.Link link = order.links().get(l);
                    int a = Math.min(link.hiTick, link.loTick);
                    int b = Math.max(link.hiTick, link.loTick);
                    double shift = off[a] - off[b];
                    double lo;
                    double hi;
                    if (link.hiTick == b) {
                        lo = link.minTurnDeg + shift;
                        hi = 180.0 + shift;
                    } else {
                        lo = -180.0 + shift;
                        hi = -link.minTurnDeg + shift;
                    }
                    int wLo = (int) Math.ceil(lo / H - 1.0e-9);
                    int wHi = (int) Math.floor(hi / H + 1.0e-9);
                    if (wHi < wLo) return null;
                    slidingMax(prev, cur, ptr, wLo, wHi);
                }
                back[u] = ptr;
            }
            double[] tmp = prev == null ? new double[N] : prev;
            prev = cur;
            cur = tmp;
        }
        int bestJ = 0;
        for (int j = 1; j < N; j++) if (prev[j] > prev[bestJ]) bestJ = j;
        if (prev[bestJ] == NEG) return null;
        int[] choice = new int[units];
        choice[units - 1] = bestJ;
        for (int u = units - 1; u > 0; u--) choice[u - 1] = back[u][choice[u]];
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            yaws[t] = Double.isNaN(pin[t]) ? Angles.wrap(-180.0 + choice[unitOf[t]] * H + off[t]) : Angles.wrap(pin[t]);
        }
        return yaws;
    }

    private static int indexOf(double deg) {
        int j = (int) Math.round((Angles.wrap(deg) + 180.0) / H);
        return ((j % N) + N) % N;
    }

    private static void slidingMax(double[] prev, double[] cur, int[] ptr, int wLo, int wHi) {
        int w = wHi - wLo + 1;
        if (w >= N) {
            int bestJ = 0;
            for (int j = 1; j < N; j++) if (prev[j] > prev[bestJ]) bestJ = j;
            for (int j = 0; j < N; j++) {
                cur[j] += prev[bestJ];
                ptr[j] = bestJ;
            }
            return;
        }
        int[] deque = new int[N + w + 1];
        int head = 0;
        int tail = 0;
        int next = -wHi;
        for (int j = 0; j < N; j++) {
            int last = j - wLo;
            for (; next <= last; next++) {
                int idx = ((next % N) + N) % N;
                while (tail > head && prev[((deque[tail - 1] % N) + N) % N] <= prev[idx]) tail--;
                deque[tail++] = next;
            }
            int first = j - wHi;
            while (tail > head && deque[head] < first) head++;
            int pick = ((deque[head] % N) + N) % N;
            cur[j] += prev[pick];
            ptr[j] = pick;
        }
    }
}
