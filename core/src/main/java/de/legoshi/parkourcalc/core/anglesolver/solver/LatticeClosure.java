package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class LatticeClosure {

    private static final double TIGHT_SLACK = 2.0e-5;
    private static final double MU_FLOOR = 1.0e-3;
    private static final double MU_ACTIVE = 1.0e-9;
    private static final double PRUNE_SLOP = 1.0e-7;
    private static final double VERIFY_SLOP = 2.0e-9;
    private static final int GATE_ROUNDS = 12;
    private static final int GATE_REACH = 3;
    private static final int LEVEL_SLACK = 8;
    private static final int SCAN_DEPTH = 32;
    private static final double ROUND_FLOOR = 2.0e-7;

    static final class Config {
        int rounds = 16;
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
    private long scanUnits;

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
        return new LatticeClosure(m, z, cfg, deadline).search(z, mu, yaws, null);
    }

    static Result refine(TrigRowModel m, double[] z, double[] mu, Result from, Config cfg, long deadline) {
        Result r = new LatticeClosure(m, z, cfg, deadline).search(z, mu, from.yaws, from);
        return r == null ? from : r;
    }

    private Result search(double[] z, double[] mu, double[] yaws, Result from) {
        double[] base = yaws.clone();
        List<Integer> lights = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            int v = m.varOfTick[t];
            if (v < 0 || m.fixed[v]) continue;
            if (m.ticksInVar[v] != 1) return null;
            lights.add(t);
            if (from == null) base[t] = Angles.wrap(TrigRowModel.centre(base[t]));
        }
        if (from == null && !settleFacingRows(base, lights)) return null;
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
        if (from != null) return again(from, z, mu, g, tight, coupled, loose);
        if (!settleGates(base, lights, mu)) return null;
        Result first = round(base, z, mu, g, tight, coupled, loose, 0);
        return first == null ? null : again(first, z, mu, g, tight, coupled, loose);
    }

    private Result again(Result from, double[] z, double[] mu, double[] g, boolean[] tight, List<Integer> coupled,
                         List<Integer> loose) {
        int subsets = coupled.size() <= cfg.maxAdjusters ? 1
                : (coupled.size() + cfg.maxAdjusters / 2 - 1) / (cfg.maxAdjusters / 2);
        Result best = from;
        double reach = m.objectiveAt(z) + ROUND_FLOOR;
        int idle = 0;
        for (int round = 1; round < cfg.rounds && idle < subsets; round++) {
            if (sign * best.objective <= reach) break;
            if (deadline > 0 && SolveClock.nanoTime() > deadline) break;
            Result next = round(best.yaws.clone(), z, mu, g, tight, coupled, loose, round);
            if (next != null && sign * next.objective < sign * best.objective) {
                best = next;
                idle = 0;
            } else {
                idle++;
            }
        }
        return best;
    }

    private Result round(double[] base, double[] z, double[] mu, double[] g, boolean[] tight, List<Integer> coupled,
                         List<Integer> free, int round) {
        int rows = m.rows.length;
        List<Integer> loose = new ArrayList<>(free);
        List<Integer> adjusters = new ArrayList<>();
        if (coupled.size() <= cfg.maxAdjusters) {
            adjusters.addAll(coupled);
        } else {
            int high = cfg.maxAdjusters / 2;
            int low = cfg.maxAdjusters - high;
            int size = coupled.size();
            int shift = (round * high) % size;
            boolean[] picked = new boolean[size];
            for (int i = 0; i < high; i++) picked[(shift + i) % size] = true;
            for (int i = size - low; i < size; i++) picked[(shift + i) % size] = true;
            for (int i = 0; i < size; i++) {
                if (picked[i]) adjusters.add(coupled.get(i));
                else loose.add(coupled.get(i));
            }
        }
        if (round > 0) loose.clear();

        ForwardPath p0 = m.exact.forward(at, at.toGameFacings(base));
        double[] e0 = new double[rows];
        for (int r = 0; r < rows; r++) e0[r] = m.rows[r].excess(p0, base);
        double o0 = sign * m.objective.value(p0);
        for (int t : loose) {
            double bestCost = 0.0;
            int bestStep = 0;
            for (int j = -1; j <= 1; j += 2) {
                double[] y = base.clone();
                y[t] = Angles.wrap(base[t] + j * TrigRowModel.STEP_DEG);
                ForwardPath p = m.exact.forward(at, at.toGameFacings(y));
                double cost = sign * m.objective.value(p) - o0;
                boolean leaves = false;
                for (int r = 0; r < rows; r++) {
                    double e = m.rows[r].excess(p, y);
                    if ((m.rows[r].facing || m.rows[r].gate) && e > 0.0 && e > e0[r]) leaves = true;
                    if (mu[r] > 0.0) cost += mu[r] * (e - e0[r]);
                }
                if (!leaves && cost < bestCost) {
                    bestCost = cost;
                    bestStep = j;
                }
            }
            if (bestStep != 0) {
                base[t] = Angles.wrap(base[t] + bestStep * TrigRowModel.STEP_DEG);
                p0 = m.exact.forward(at, at.toGameFacings(base));
                for (int r = 0; r < rows; r++) e0[r] = m.rows[r].excess(p0, base);
                o0 = sign * m.objective.value(p0);
            }
        }

        Collections.sort(adjusters);
        int count = adjusters.size();
        int sizeA = (count + 1) / 2;
        int sizeB = count - sizeA;
        int[] ticksA = new int[sizeA];
        int[] ticksB = new int[sizeB];
        for (int i = 0; i < count; i++) {
            if (i < sizeA) ticksA[i] = adjusters.get(i);
            else ticksB[i - sizeA] = adjusters.get(i);
        }
        int reach = count > 0 ? cfg.maxOffset : 0;

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

        int stride = width + 1;
        double[] seedA = new double[stride];
        double[] carriedB = new double[width];
        for (int i = 0; i < width; i++) {
            seedA[i] = e0[rel[i]];
            carriedB[i] = e0[rel[i]];
        }
        seedA[width] = o0;
        Half halfA = widest(tableA, tableB, seedA, new double[width], reach);
        Half halfB = widest(tableB, tableA, new double[stride], carriedB, reach);
        if (halfA == null || halfB == null || halfA.count == 0 || halfB.count == 0) return null;
        double[] sumsA = halfA.sums;
        double[] sumsB = halfB.sums;
        int countA = halfA.count;
        int countB = halfB.count;

        SolveClock.charge(SolveClock.POLISH, (long) countA * (64 - Long.numberOfLeadingZeros(countA)));
        Key key = chooseKey(mu, tight, sumsA, countA, stride);
        SolveClock.charge(SolveClock.POLISH, (long) countA * stride);
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
        int scanned = cfg.window * SCAN_DEPTH;
        for (int ib = 0; ib < countB; ib++) {
            if ((ib & 255) == 0) {
                SolveClock.charge(SolveClock.POLISH, scanUnits);
                scanUnits = 0L;
                if (deadline > 0 && SolveClock.nanoTime() > deadline) break;
            }
            int offB = ib * stride;
            scanUnits += 8;
            if (key.separable) {
                for (int i = 0, fits = 0; i < countA && i < scanned && fits < cfg.window; i++) {
                    if (consider(sumsA, order[i] * stride, sumsB, offB, order[i], ib, width)) fits++;
                }
                continue;
            }
            double target = -keyOf(key, sumsB, offB);
            int pos = upperBound(sorted, target);
            for (int i = pos - 1, steps = 0, fits = 0; i >= 0 && steps < scanned && fits < cfg.window; i--, steps++) {
                if (floor + key.lossBelow * (target - sorted[i]) > worst()) break;
                if (consider(sumsA, order[i] * stride, sumsB, offB, order[i], ib, width)) fits++;
            }
            if (key.lossAbove < Double.POSITIVE_INFINITY) {
                for (int i = pos, steps = 0, fits = 0; i < countA && steps < scanned && fits < cfg.window; i++, steps++) {
                    if (floor + key.lossAbove * (sorted[i] - target) > worst()) break;
                    if (consider(sumsA, order[i] * stride, sumsB, offB, order[i], ib, width)) fits++;
                }
            }
        }

        SolveClock.charge(SolveClock.POLISH, scanUnits);
        scanUnits = 0L;

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(m.spec);
        Result best = null;
        for (int i = 0; i < topCount; i++) {
            if (best != null && topScore[i] > sign * best.objective + VERIFY_SLOP) break;
            double[] y = base.clone();
            place(y, base, ticksA, halfA, topA[i], reach);
            place(y, base, ticksB, halfB, topB[i], reach);
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

    private boolean settleFacingRows(double[] base, List<Integer> lights) {
        boolean any = false;
        for (TrigRowModel.Row row : m.rows) any |= row.facing;
        if (!any) return true;
        boolean[] movable = new boolean[n];
        for (int t : lights) movable[t] = true;
        for (int pass = 0; pass <= n; pass++) {
            boolean moved = false;
            for (TrigRowModel.Row row : m.rows) {
                if (!row.facing) continue;
                double e = row.excess(null, base);
                if (e <= 0.0) continue;
                int pick = -1;
                for (int i = 0; i < row.ticks.length; i++) {
                    if (movable[row.ticks[i]] && (pick < 0 || row.ticks[i] > row.ticks[pick])) pick = i;
                }
                if (pick < 0 || row.wrapSide) return false;
                double steps = StrictMath.ceil(e / TrigRowModel.STEP_RAD - 1.0e-6);
                int t = row.ticks[pick];
                base[t] = Angles.wrap(base[t] - row.sign * row.weights[pick] * steps * TrigRowModel.STEP_DEG);
                moved = true;
            }
            if (!moved) return true;
        }
        return false;
    }

    private boolean settleGates(double[] base, List<Integer> lights, double[] mu) {
        int rows = m.rows.length;
        double[] e0 = new double[rows];
        double[] e = new double[rows];
        for (int round = 0; round < GATE_ROUNDS; round++) {
            ForwardPath p0 = m.exact.forward(at, at.toGameFacings(base));
            int open = -1;
            for (int r = 0; r < rows; r++) {
                e0[r] = m.rows[r].excess(p0, base);
                if (open < 0 && m.rows[r].gate && e0[r] > 0.0) open = r;
            }
            if (open < 0) return true;
            double o0 = sign * m.objective.value(p0);
            int gateTick = m.rows[open].ticks[0];
            int bestTick = -1;
            int bestStep = 0;
            boolean bestCloses = false;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int t : lights) {
                if (t >= gateTick) continue;
                for (int j = -GATE_REACH; j <= GATE_REACH; j++) {
                    if (j == 0) continue;
                    double[] y = base.clone();
                    y[t] = Angles.wrap(base[t] + j * TrigRowModel.STEP_DEG);
                    ForwardPath p = m.exact.forward(at, at.toGameFacings(y));
                    boolean leaves = false;
                    double cost = sign * m.objective.value(p) - o0;
                    for (int r = 0; r < rows; r++) {
                        e[r] = m.rows[r].excess(p, y);
                        boolean held = m.rows[r].facing || (m.rows[r].gate && r < open);
                        if (held && e[r] > 0.0) leaves = true;
                        if (r != open) cost += Math.max(mu[r], MU_FLOOR) * Math.max(0.0, e[r] - Math.max(e0[r], 0.0));
                    }
                    if (leaves || e[open] >= e0[open]) continue;
                    boolean closes = e[open] <= 0.0;
                    double rated = closes ? cost : e[open];
                    if ((closes && !bestCloses) || (closes == bestCloses && rated < bestCost)) {
                        bestCloses = closes;
                        bestCost = rated;
                        bestTick = t;
                        bestStep = j;
                    }
                }
            }
            if (bestTick < 0) return false;
            base[bestTick] = Angles.wrap(base[bestTick] + bestStep * TrigRowModel.STEP_DEG);
        }
        return false;
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
                for (int r = 0; r < rows; r++) d[r] = m.rows[r].excess(p, y) - e0[r];
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

    private static final class Half {
        double[] sums;
        int[] picks;
        int count;
        int reach;
    }

    private Half widest(double[][][] own, double[][][] other, double[] seed, double[] carried, int reach) {
        Half best = null;
        for (int r = Math.min(1, reach); r <= reach; r++) {
            Half h = enumerate(own, other, seed, carried, reach, r);
            if (h == null) break;
            best = h;
            if (own.length == 0) break;
        }
        return best;
    }

    private Half enumerate(double[][][] own, double[][][] other, double[] seed, double[] carried, int reach,
                           int used) {
        int rows = m.rows.length;
        int width = rel.length;
        int stride = width + 1;
        int size = own.length;
        boolean pinned = !m.freeStart;
        int[] closesAt = new int[width];
        for (int k = 0; k < width; k++) {
            boolean fixable = touches(other, rel[k]) || (!pinned && (qx[k] != 0.0 || qz[k] != 0.0));
            int last = -1;
            for (int d = 0; d < size; d++) {
                if (touches(own[d], rel[k])) last = d;
            }
            closesAt[k] = fixable ? size : last;
        }
        Half cur = new Half();
        cur.sums = seed.clone();
        cur.picks = new int[0];
        cur.count = 1;
        cur.reach = used;
        for (int k = 0; k < width; k++) {
            if (closesAt[k] < 0 && carried[k] + seed[k] > 0.0) cur.count = 0;
        }
        int span = 2 * used + 1;
        for (int d = 0; d < size; d++) {
            double[][] tick = own[d];
            if ((long) cur.count * span > (long) cfg.halfCap * LEVEL_SLACK) return null;
            Half next = new Half();
            next.reach = used;
            SolveClock.charge(SolveClock.POLISH, (long) cur.count * span * stride);
            next.sums = new double[cur.count * span * stride];
            next.picks = new int[cur.count * span * (d + 1)];
            for (int i = 0; i < cur.count; i++) {
                int from = i * stride;
                for (int c = reach - used; c <= reach + used; c++) {
                    double[] delta = tick[c];
                    int to = next.count * stride;
                    boolean open = true;
                    for (int k = 0; k < width && open; k++) {
                        double v = cur.sums[from + k] + delta[rel[k]];
                        next.sums[to + k] = v;
                        if (closesAt[k] == d) open = carried[k] + v <= 0.0;
                    }
                    if (!open) continue;
                    next.sums[to + width] = cur.sums[from + width] + delta[rows];
                    System.arraycopy(cur.picks, i * d, next.picks, next.count * (d + 1), d);
                    next.picks[next.count * (d + 1) + d] = c;
                    next.count++;
                }
            }
            if (d == size - 1 && next.count > cfg.halfCap) return null;
            cur = next;
        }
        if (cur.sums.length != cur.count * stride) cur.sums = Arrays.copyOf(cur.sums, cur.count * stride);
        return cur;
    }

    private static boolean touches(double[][][] table, int row) {
        for (double[][] tick : table) {
            if (touches(tick, row)) return true;
        }
        return false;
    }

    private static boolean touches(double[][] tick, int row) {
        for (double[] delta : tick) {
            if (delta[row] != 0.0) return true;
        }
        return false;
    }

    private void place(double[] y, double[] base, int[] ticks, Half half, int index, int reach) {
        for (int i = 0; i < ticks.length; i++) {
            int c = half.picks[index * ticks.length + i];
            y[ticks[i]] = Angles.wrap(base[ticks[i]] + (c - reach) * TrigRowModel.STEP_DEG);
        }
    }

    private Key chooseKey(double[] mu, boolean[] tight, double[] sumsA, int countA, int stride) {
        int width = rel.length;
        int rows = m.rows.length;
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            if (!tight[rel[i]] || qx[i] != 0.0 || qz[i] != 0.0 || m.rows[rel[i]].facing) continue;
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
        SolveClock.charge(SolveClock.POLISH, (long) keys.size() * countA * stride);
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

    private boolean consider(double[] sumsA, int offA, double[] sumsB, int offB, int ia, int ib, int width) {
        double loX = txLo;
        double hiX = txHi;
        double loZ = tzLo;
        double hiZ = tzHi;
        scanUnits += width + 1;
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
                return false;
            }
        }
        if (loX > hiX || loZ > hiZ) return false;
        double tx = m.opx > 0.0 ? loX : m.opx < 0.0 ? hiX : 0.5 * (loX + hiX);
        double tz = m.opz > 0.0 ? loZ : m.opz < 0.0 ? hiZ : 0.5 * (loZ + hiZ);
        double score = sumsA[offA + width] + sumsB[offB + width] + m.opx * tx + m.opz * tz;
        if (score >= worst()) return true;
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
        return true;
    }
}
