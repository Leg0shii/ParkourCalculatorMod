package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class LatticeClosure {

    private static final double TIGHT_SLACK = 2.0e-5;
    private static final double MU_FLOOR = 1.0e-3;
    private static final double MU_ACTIVE = 1.0e-9;
    private static final double PRUNE_SLOP = 1.0e-7;
    private static final double VERIFY_SLOP = 2.0e-9;

    static final class Config {
        int maxAdjusters = 8;
        int halfCap = 2200;
        int maxOffset = 13;
        int window = 24;
        int verify = 12;
    }

    static final class Result {
        final double[] yaws;
        final double startX;
        final double startZ;
        final double objective;

        Result(double[] yaws, double startX, double startZ, double objective) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
            this.objective = objective;
        }
    }

    private static final class Key {
        final double[] coef;
        final double constant;
        final double lossBelow;
        final double lossAbove;
        final boolean separable;

        Key(double[] coef, double constant, double lossBelow, double lossAbove) {
            this(coef, constant, lossBelow, lossAbove, false);
        }

        Key(double[] coef, double constant, double lossBelow, double lossAbove, boolean separable) {
            this.coef = coef;
            this.constant = constant;
            this.lossBelow = lossBelow;
            this.lossAbove = lossAbove;
            this.separable = separable;
        }
    }

    private final TrigRowModel m;
    private final Config cfg;
    private final long deadline;
    private final int vars;
    private final int n;
    private final double startX;
    private final double startZ;
    private final JumpPhysicsInputs at;
    private final double sign;

    private int[] rel;
    private double[] qx;
    private double[] qz;
    private double txLo;
    private double txHi;
    private double tzLo;
    private double tzHi;
    private double[] topScore;
    private int[] topA;
    private int[] topB;
    private int topCount;

    private LatticeClosure(TrigRowModel m, double[] z, Config cfg, long deadline) {
        this.m = m;
        this.cfg = cfg;
        this.deadline = deadline;
        this.vars = m.vars;
        this.n = m.n;
        this.startX = m.refX + z[vars];
        this.startZ = m.refZ + z[vars + 1];
        this.at = m.scenario.pinnedAt(startX, startZ);
        this.sign = m.objective.sign;
    }

    static Result run(TrigRowModel m, double[] z, double[] mu, double[] yaws, Config cfg, long deadline) {
        return new LatticeClosure(m, z, cfg, deadline).search(z, mu, yaws);
    }

    private Result search(double[] z, double[] mu, double[] yaws) {
        double[] base = yaws.clone();
        List<Integer> lights = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            int v = m.varOfTick[t];
            if (v < 0 || m.fixed[v]) continue;
            if (m.ticksInVar[v] != 1) return null;
            lights.add(t);
            base[t] = Angles.wrap(TrigRowModel.centre(base[t]));
        }
        int rows = m.rows.length;
        double[] g = m.rowValues(z);
        boolean[] tight = new boolean[m.rowCount];
        for (int r = 0; r < m.rowCount; r++) tight[r] = g[r] > -TIGHT_SLACK || mu[r] > MU_ACTIVE;

        final double[] lever = new double[n];
        List<Integer> coupled = new ArrayList<>();
        List<Integer> loose = new ArrayList<>();
        for (int t : lights) {
            int v = m.varOfTick[t];
            double l = 0.0;
            for (int r = 0; r < rows; r++) {
                if (!tight[r]) continue;
                l += Math.max(mu[r], MU_FLOOR) * Math.abs(m.rowSlope(r, v, z)) * TrigRowModel.STEP_RAD;
            }
            lever[t] = l;
            if (l > 0.0) coupled.add(t);
            else loose.add(t);
        }
        coupled.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer u, Integer w) {
                return Double.compare(lever[w], lever[u]);
            }
        });
        List<Integer> adjusters = new ArrayList<>();
        if (coupled.size() <= cfg.maxAdjusters) {
            adjusters.addAll(coupled);
        } else {
            int high = cfg.maxAdjusters / 2;
            int low = cfg.maxAdjusters - high;
            for (int i = 0; i < high; i++) adjusters.add(coupled.get(i));
            for (int i = coupled.size() - low; i < coupled.size(); i++) adjusters.add(coupled.get(i));
            loose.addAll(coupled.subList(high, coupled.size() - low));
        }

        ForwardPath p0 = m.exact.forward(at, at.toGameFacings(base));
        double[] e0 = new double[rows];
        for (int r = 0; r < rows; r++) e0[r] = m.rows[r].excess(p0);
        double o0 = sign * m.objective.value(p0);
        for (int t : loose) {
            double bestCost = 0.0;
            int bestStep = 0;
            for (int j = -1; j <= 1; j += 2) {
                double[] y = base.clone();
                y[t] = Angles.wrap(base[t] + j * TrigRowModel.STEP_DEG);
                ForwardPath p = m.exact.forward(at, at.toGameFacings(y));
                double cost = sign * m.objective.value(p) - o0;
                for (int r = 0; r < rows; r++) {
                    if (mu[r] > 0.0) cost += mu[r] * (m.rows[r].excess(p) - e0[r]);
                }
                if (cost < bestCost) {
                    bestCost = cost;
                    bestStep = j;
                }
            }
            if (bestStep != 0) {
                base[t] = Angles.wrap(base[t] + bestStep * TrigRowModel.STEP_DEG);
                p0 = m.exact.forward(at, at.toGameFacings(base));
                for (int r = 0; r < rows; r++) e0[r] = m.rows[r].excess(p0);
                o0 = sign * m.objective.value(p0);
            }
        }

        int count = adjusters.size();
        int sizeA = (count + 1) / 2;
        int sizeB = count - sizeA;
        int[] ticksA = new int[sizeA];
        int[] ticksB = new int[sizeB];
        for (int i = 0; i < count; i++) {
            if ((i & 1) == 0) ticksA[i / 2] = adjusters.get(i);
            else ticksB[i / 2] = adjusters.get(i);
        }
        int reach = 0;
        if (count > 0) {
            reach = 1;
            while (reach < cfg.maxOffset && StrictMath.pow(2 * (reach + 1) + 1, sizeA) <= cfg.halfCap) reach++;
        }
        int options = 2 * reach + 1;

        double[][][] tableA = table(base, p0, e0, o0, ticksA, reach);
        double[][][] tableB = table(base, p0, e0, o0, ticksB, reach);
        double[] span = new double[rows];
        addSpan(span, tableA, rows);
        addSpan(span, tableB, rows);
        List<Integer> relevant = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            if (g[r] > -(2.0 * span[r] + 1.0e-6)) relevant.add(r);
        }
        int width = relevant.size();
        rel = new int[width];
        qx = new double[width];
        qz = new double[width];
        for (int i = 0; i < width; i++) {
            rel[i] = relevant.get(i);
            qx[i] = m.px[rel[i]];
            qz[i] = m.pz[rel[i]];
        }
        if (m.freeStart) {
            txLo = m.box.pxLo - startX;
            txHi = m.box.pxHi - startX;
            tzLo = m.box.pzLo - startZ;
            tzHi = m.box.pzHi - startZ;
        }

        double[] seedA = new double[width + 1];
        for (int i = 0; i < width; i++) seedA[i] = e0[rel[i]];
        seedA[width] = o0;
        double[] sumsA = expand(seedA, tableA, options, width);
        double[] sumsB = expand(new double[width + 1], tableB, options, width);
        int stride = width + 1;
        int countA = sumsA.length / stride;
        int countB = sumsB.length / stride;

        Key key = chooseKey(mu, tight, sumsA, countA, stride);
        final double[] keyA = new double[countA];
        for (int i = 0; i < countA; i++) keyA[i] = keyOf(key, sumsA, i * stride) + key.constant;
        Integer[] order = new Integer[countA];
        for (int i = 0; i < countA; i++) order[i] = i;
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer u, Integer w) {
                return Double.compare(keyA[u], keyA[w]);
            }
        });
        double[] sorted = new double[countA];
        for (int i = 0; i < countA; i++) sorted[i] = keyA[order[i]];

        topScore = new double[cfg.verify];
        topA = new int[cfg.verify];
        topB = new int[cfg.verify];
        topCount = 0;
        double floor = m.objectiveAt(z) - PRUNE_SLOP;
        for (int ib = 0; ib < countB; ib++) {
            if ((ib & 255) == 0 && deadline > 0 && System.nanoTime() > deadline) break;
            int offB = ib * stride;
            if (key.separable) {
                for (int i = 0; i < countA && i < cfg.window; i++) {
                    consider(sumsA, order[i] * stride, sumsB, offB, order[i], ib, width);
                }
                continue;
            }
            double target = -keyOf(key, sumsB, offB);
            int pos = upperBound(sorted, target);
            for (int i = pos - 1, steps = 0; i >= 0 && steps < cfg.window; i--, steps++) {
                if (floor + key.lossBelow * (target - sorted[i]) > worst()) break;
                consider(sumsA, order[i] * stride, sumsB, offB, order[i], ib, width);
            }
            if (key.lossAbove < Double.POSITIVE_INFINITY) {
                for (int i = pos, steps = 0; i < countA && steps < cfg.window; i++, steps++) {
                    if (floor + key.lossAbove * (sorted[i] - target) > worst()) break;
                    consider(sumsA, order[i] * stride, sumsB, offB, order[i], ib, width);
                }
            }
        }

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(m.spec);
        Result best = null;
        for (int i = 0; i < topCount; i++) {
            if (best != null && topScore[i] > sign * best.objective + VERIFY_SLOP) break;
            double[] y = base.clone();
            decode(y, base, ticksA, topA[i], options, reach);
            decode(y, base, ticksB, topB[i], options, reach);
            double[] gf = at.toGameFacings(y);
            ForwardPath p = m.exact.forward(at, gf);
            PathTranslation.Trans tr = PathTranslation.bestTranslationObj(compiled, gf, p,
                    txLo, txHi, tzLo, tzHi, m.spec.objective);
            double x = startX + tr.tx;
            double zz = startZ + tr.tz;
            if (m.freeStart && (x < m.box.pxLo || x > m.box.pxHi || zz < m.box.pzLo || zz > m.box.pzHi)) continue;
            JumpPhysicsInputs moved = m.scenario.pinnedAt(x, zz);
            double[] movedGf = moved.toGameFacings(y);
            ForwardPath movedPath = m.exact.forward(moved, movedGf);
            if (compiled.maxViolation(movedGf, movedPath) > 0.0) continue;
            double obj = m.spec.objective.evaluate(movedPath);
            if (best == null || sign * obj < sign * best.objective) best = new Result(y, x, zz, obj);
        }
        return best;
    }

    private double[][][] table(double[] base, ForwardPath p0, double[] e0, double o0, int[] ticks, int reach) {
        int rows = m.rows.length;
        int options = 2 * reach + 1;
        double[][][] out = new double[ticks.length][options][rows + 1];
        for (int i = 0; i < ticks.length; i++) {
            int t = ticks[i];
            for (int c = 0; c < options; c++) {
                if (c == reach) continue;
                double[] y = base.clone();
                y[t] = Angles.wrap(base[t] + (c - reach) * TrigRowModel.STEP_DEG);
                ForwardPath p = m.exact.forward(at, at.toGameFacings(y));
                double[] d = out[i][c];
                for (int r = 0; r < rows; r++) d[r] = m.rows[r].excess(p) - e0[r];
                d[rows] = sign * m.objective.value(p) - o0;
            }
        }
        return out;
    }

    private static void addSpan(double[] span, double[][][] table, int rows) {
        for (double[][] tick : table) {
            for (int r = 0; r < rows; r++) {
                double max = 0.0;
                for (double[] d : tick) max = Math.max(max, Math.abs(d[r]));
                span[r] += max;
            }
        }
    }

    private double[] expand(double[] seed, double[][][] table, int options, int width) {
        int rows = m.rows.length;
        int stride = width + 1;
        double[] cur = seed;
        for (double[][] tick : table) {
            int have = cur.length / stride;
            double[] next = new double[have * options * stride];
            for (int i = 0; i < have; i++) {
                for (int c = 0; c < options; c++) {
                    double[] d = tick[c];
                    int to = (i * options + c) * stride;
                    int from = i * stride;
                    for (int k = 0; k < width; k++) next[to + k] = cur[from + k] + d[rel[k]];
                    next[to + width] = cur[from + width] + d[rows];
                }
            }
            cur = next;
        }
        return cur;
    }

    private void decode(double[] y, double[] base, int[] ticks, int index, int options, int reach) {
        for (int i = ticks.length - 1; i >= 0; i--) {
            int c = index % options;
            index /= options;
            y[ticks[i]] = Angles.wrap(base[ticks[i]] + (c - reach) * TrigRowModel.STEP_DEG);
        }
    }

    private Key chooseKey(double[] mu, boolean[] tight, double[] sumsA, int countA, int stride) {
        int width = rel.length;
        int rows = m.rows.length;
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            if (!tight[rel[i]] || qx[i] != 0.0 || qz[i] != 0.0) continue;
            double[] coef = new double[width];
            coef[i] = 1.0;
            keys.add(new Key(coef, 0.0, Math.max(mu[rel[i]], MU_FLOOR), Double.POSITIVE_INFINITY));
        }
        if (m.freeStart) {
            axisKeys(keys, mu, tight, qx, txLo, txHi, mu[rows], mu[rows + 1]);
            axisKeys(keys, mu, tight, qz, tzLo, tzHi, mu[rows + 2], mu[rows + 3]);
        } else {
            for (int i = 0; i < width; i++) {
                if (!tight[rel[i]] || (qx[i] == 0.0 && qz[i] == 0.0)) continue;
                double[] coef = new double[width];
                coef[i] = 1.0;
                keys.add(new Key(coef, 0.0, Math.max(mu[rel[i]], MU_FLOOR), Double.POSITIVE_INFINITY));
            }
        }
        Key best = null;
        double bestScore = 0.0;
        for (Key k : keys) {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < countA; i++) {
                double v = keyOf(k, sumsA, i * stride);
                if (v < lo) lo = v;
                if (v > hi) hi = v;
            }
            double score = Math.min(k.lossBelow, k.lossAbove) * (hi - lo);
            if (best == null || score > bestScore) {
                best = k;
                bestScore = score;
            }
        }
        if (best != null) return best;
        double[] coef = new double[width + 1];
        coef[width] = 1.0;
        if (m.freeStart) {
            bindingShare(coef, mu, tight, qx, m.opx);
            bindingShare(coef, mu, tight, qz, m.opz);
        }
        return new Key(coef, 0.0, 0.0, Double.POSITIVE_INFINITY, true);
    }

    private void bindingShare(double[] coef, double[] mu, boolean[] tight, double[] q, double objectiveShift) {
        if (objectiveShift == 0.0) return;
        int binding = -1;
        double weight = 0.0;
        for (int i = 0; i < rel.length; i++) {
            if (q[i] == 0.0 || !tight[rel[i]]) continue;
            if ((q[i] > 0.0) != (objectiveShift < 0.0)) continue;
            double w = Math.max(mu[rel[i]], MU_FLOOR) * Math.abs(q[i]);
            if (w > weight) {
                weight = w;
                binding = i;
            }
        }
        if (binding >= 0) coef[binding] += -objectiveShift / q[binding];
    }

    private void axisKeys(List<Key> keys, double[] mu, boolean[] tight, double[] q,
                          double boxLo, double boxHi, double muHi, double muLo) {
        int width = rel.length;
        int anchor = -3;
        double anchorWeight = 0.0;
        for (int i = 0; i < width; i++) {
            if (q[i] == 0.0 || !tight[rel[i]]) continue;
            double w = Math.max(mu[rel[i]], MU_FLOOR) * Math.abs(q[i]);
            if (w > anchorWeight) {
                anchorWeight = w;
                anchor = i;
            }
        }
        if (muHi > anchorWeight) {
            anchorWeight = muHi;
            anchor = -1;
        }
        if (muLo > anchorWeight) {
            anchorWeight = muLo;
            anchor = -2;
        }
        if (anchor == -3) return;
        boolean anchorUpper = anchor == -1 || (anchor >= 0 && q[anchor] > 0.0);
        for (int i = -2; i < width; i++) {
            if (i == anchor) continue;
            double weight;
            boolean upper;
            if (i == -1) {
                if (muHi <= MU_ACTIVE) continue;
                weight = muHi;
                upper = true;
            } else if (i == -2) {
                if (muLo <= MU_ACTIVE) continue;
                weight = muLo;
                upper = false;
            } else {
                if (q[i] == 0.0 || !tight[rel[i]]) continue;
                weight = Math.max(mu[rel[i]], MU_FLOOR) * Math.abs(q[i]);
                upper = q[i] > 0.0;
            }
            double[] coef = new double[width];
            double constant = 0.0;
            if (i >= 0) coef[i] += -1.0 / q[i];
            else constant += i == -1 ? boxHi : boxLo;
            if (anchor >= 0) coef[anchor] -= -1.0 / q[anchor];
            else constant -= anchor == -1 ? boxHi : boxLo;
            if (upper == anchorUpper) {
                double lossAbove = upper ? weight : anchorWeight;
                double lossBelow = upper ? anchorWeight : weight;
                keys.add(new Key(coef, constant, lossBelow, lossAbove));
            } else if (upper) {
                negate(coef);
                keys.add(new Key(coef, -constant, Math.min(weight, anchorWeight), Double.POSITIVE_INFINITY));
            } else {
                keys.add(new Key(coef, constant, Math.min(weight, anchorWeight), Double.POSITIVE_INFINITY));
            }
        }
    }

    private static void negate(double[] v) {
        for (int i = 0; i < v.length; i++) v[i] = -v[i];
    }

    private static double keyOf(Key key, double[] sums, int off) {
        double v = 0.0;
        double[] coef = key.coef;
        for (int k = 0; k < coef.length; k++) {
            if (coef[k] != 0.0) v += coef[k] * sums[off + k];
        }
        return v;
    }

    private static int upperBound(double[] sorted, double target) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= target) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private double worst() {
        return topCount < topScore.length ? Double.POSITIVE_INFINITY : topScore[topCount - 1];
    }

    private void consider(double[] sumsA, int offA, double[] sumsB, int offB, int ia, int ib, int width) {
        double loX = txLo;
        double hiX = txHi;
        double loZ = tzLo;
        double hiZ = tzHi;
        for (int k = 0; k < width; k++) {
            double e = sumsA[offA + k] + sumsB[offB + k];
            if (qx[k] > 0.0) {
                double lim = -e / qx[k];
                if (lim < hiX) hiX = lim;
            } else if (qx[k] < 0.0) {
                double lim = -e / qx[k];
                if (lim > loX) loX = lim;
            } else if (qz[k] > 0.0) {
                double lim = -e / qz[k];
                if (lim < hiZ) hiZ = lim;
            } else if (qz[k] < 0.0) {
                double lim = -e / qz[k];
                if (lim > loZ) loZ = lim;
            } else if (e > 0.0) {
                return;
            }
        }
        if (loX > hiX || loZ > hiZ) return;
        double tx = m.opx > 0.0 ? loX : m.opx < 0.0 ? hiX : 0.5 * (loX + hiX);
        double tz = m.opz > 0.0 ? loZ : m.opz < 0.0 ? hiZ : 0.5 * (loZ + hiZ);
        double score = sumsA[offA + width] + sumsB[offB + width] + m.opx * tx + m.opz * tz;
        if (score >= worst()) return;
        int slot = topCount < topScore.length ? topCount++ : topCount - 1;
        while (slot > 0 && topScore[slot - 1] > score) {
            topScore[slot] = topScore[slot - 1];
            topA[slot] = topA[slot - 1];
            topB[slot] = topB[slot - 1];
            slot--;
        }
        topScore[slot] = score;
        topA[slot] = ia;
        topB[slot] = ib;
    }
}
