package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class UnitScreen {

    private static final int OUTER = 1;
    private static final int REF = 36;
    private static final int UNIT_STEPS = 12;
    private static final int UNIT_REFINE = 4;
    private static final int UNIT_LEVELS = 2;
    private static final int UNIT_LEVELS_FINE = 4;
    private static final int REFINE_BEST = 2;
    private static final int THETA_LEVELS = 4;
    private static final double THETA_FINE_RAD = 2.0e-5;
    private static final double POLISH_MAX_DEFICIT = 0.15;
    private static final int POLISH_MAX_TRIALS = 40;
    private static final int RESTART_EVERY = 6;
    private static final int WINDOW_CELLS = 2;
    private static final int WINDOW_CELLS_MULTI = 4;
    private static final int SWEEP_PASSES = 3;
    private static final int KINDS = 4;
    private static final int LE_X = 0;
    private static final int LE_Z = 1;
    private static final int GE_X = 2;
    private static final int GE_Z = 3;
    private static final double[] RESTART_OFFSETS = {0.0, 0.5 * Math.PI, -0.5 * Math.PI, Math.PI};
    private static final double[] STEP_COS = new double[UNIT_STEPS];
    private static final double[] STEP_SIN = new double[UNIT_STEPS];
    private static final double[][] REFINE_COS = new double[UNIT_LEVELS_FINE][2 * UNIT_REFINE + 1];
    private static final double[][] REFINE_SIN = new double[UNIT_LEVELS_FINE][2 * UNIT_REFINE + 1];

    static {
        for (int k = 0; k < UNIT_STEPS; k++) {
            double psi = -Math.PI + k * (2.0 * Math.PI / UNIT_STEPS);
            STEP_COS[k] = StrictMath.cos(psi);
            STEP_SIN[k] = StrictMath.sin(psi);
        }
        double rstep = (2.0 * Math.PI / UNIT_STEPS) / (UNIT_REFINE + 1);
        for (int level = 0; level < UNIT_LEVELS_FINE; level++) {
            for (int j = -UNIT_REFINE; j <= UNIT_REFINE; j++) {
                REFINE_COS[level][j + UNIT_REFINE] = StrictMath.cos(j * rstep);
                REFINE_SIN[level][j + UNIT_REFINE] = StrictMath.sin(j * rstep);
            }
            rstep /= (UNIT_REFINE + 1);
        }
    }

    private final StructurePoolDriver d;
    private final int groups;
    private final int mainGroup;
    private final int[] groupOf;
    private final int[][] groupTicks;
    private final int[] order;
    private final int used;
    private final int[] kindStart = new int[KINDS];
    private final int[] kindEnd = new int[KINDS];
    private final int[][] split;
    private final double[][][] coefWall;
    private final boolean[] wallAxisX;
    private final double[][] runA;
    private final double[][] runB;
    private final double[] shiftBase;
    private final double[] shiftAcc;
    private final double[] shiftNoU;
    private final double[] unitCos;
    private final double[] unitSin;
    private final double[] baseCos;
    private final double[] baseSin;
    private final double[] bestCos;
    private final double[] bestSin;
    private final double[] winCos;
    private final double[] winSin;
    private final double[][] coarseCos;
    private final double[][] coarseSin;
    private final double[] coarseViol;
    private final double[] thetas = new double[REF + 1];
    private final boolean[] skip = new boolean[REF + 1];
    private final boolean[] done = new boolean[REF + 1];
    private final double[] bestFacing;
    private final int[] freeGroups;
    private final double[] shiftJoint;
    private final double[] ab = new double[2];
    private double objBase;
    private double bestObj;
    private double bestLoX;
    private double bestHiX;
    private double bestLoZ;
    private double bestHiZ;
    private double baseLoX;
    private double baseHiX;
    private double baseLoZ;
    private double baseHiZ;

    UnitScreen(StructurePoolDriver d, JumpLinearModel lm) {
        this.d = d;
        NoTurnProblem problem = d.problem;
        int n = problem.n;
        int[] unitSize = new int[problem.unitCount];
        for (int t = 0; t < n; t++) unitSize[problem.unit[t]]++;
        int[] groupOfUnit = new int[problem.unitCount];
        Arrays.fill(groupOfUnit, -1);
        this.groupOf = new int[n];
        List<List<Integer>> members = new ArrayList<>();
        int openRun = -1;
        for (int t = 0; t < n; t++) {
            int u = problem.unit[t];
            int g;
            if (unitSize[u] > 1) {
                if (groupOfUnit[u] < 0) {
                    groupOfUnit[u] = members.size();
                    members.add(new ArrayList<>());
                }
                g = groupOfUnit[u];
                openRun = -1;
            } else if (problem.jump[t]) {
                g = members.size();
                members.add(new ArrayList<>());
                openRun = -1;
            } else {
                if (openRun < 0) {
                    openRun = members.size();
                    members.add(new ArrayList<>());
                }
                g = openRun;
            }
            groupOf[t] = g;
            members.get(g).add(t);
        }
        this.groups = members.size();
        this.mainGroup = problem.mainUnit >= 0 && unitSize[problem.mainUnit] > 1 ? groupOfUnit[problem.mainUnit] : -1;
        int walls = d.byteWalls.size();
        this.order = sortWalls(d.byteWalls);
        int usedCount = 0;
        for (int k = 0; k < KINDS; k++) {
            kindStart[k] = usedCount;
            while (usedCount < walls && kindOf(d.byteWalls.get(order[usedCount])) == k) usedCount++;
            kindEnd[k] = usedCount;
        }
        this.used = usedCount;
        this.groupTicks = new int[groups][];
        this.coefWall = new double[groups][walls][];
        this.split = new int[groups][KINDS];
        this.wallAxisX = new boolean[walls];
        for (int i = 0; i < walls; i++) wallAxisX[i] = d.byteWalls.get(order[i]).mode == JumpConstraint.Mode.X;
        this.runA = new double[groups][walls];
        this.runB = new double[groups][walls];
        for (int g = 0; g < groups; g++) {
            List<Integer> list = members.get(g);
            int[] ticks = new int[list.size()];
            for (int i = 0; i < ticks.length; i++) ticks[i] = list.get(i);
            groupTicks[g] = ticks;
            boolean[] affects = new boolean[walls];
            for (int i = 0; i < walls; i++) {
                int wallTick = d.byteWalls.get(order[i]).t1;
                double[] cw = new double[ticks.length];
                for (int j = 0; j < ticks.length; j++) {
                    cw[j] = lm.coef(ticks[j], wallTick);
                    if (cw[j] != 0.0) affects[i] = true;
                }
                coefWall[g][i] = cw;
            }
            for (int k = 0; k < KINDS; k++) {
                int first = kindEnd[k];
                for (int i = kindStart[k]; i < kindEnd[k]; i++) {
                    if (affects[i]) {
                        first = i;
                        break;
                    }
                }
                split[g][k] = first;
            }
        }
        this.unitCos = new double[groups];
        this.unitSin = new double[groups];
        this.baseCos = new double[groups];
        this.baseSin = new double[groups];
        this.bestCos = new double[groups];
        this.bestSin = new double[groups];
        this.winCos = new double[groups];
        this.winSin = new double[groups];
        this.coarseCos = new double[REF + 1][groups];
        this.coarseSin = new double[REF + 1][groups];
        this.coarseViol = new double[REF + 1];
        this.bestFacing = new double[groups];
        int free = 0;
        for (int g = 0; g < groups; g++) if (g != mainGroup) free++;
        this.freeGroups = new int[free];
        free = 0;
        for (int g = 0; g < groups; g++) if (g != mainGroup) freeGroups[free++] = g;
        this.shiftJoint = new double[walls];
        this.shiftBase = new double[walls];
        this.shiftAcc = new double[walls];
        this.shiftNoU = new double[walls];
    }

    private static int kindOf(JumpConstraint wc) {
        boolean axisX = wc.mode == JumpConstraint.Mode.X;
        if (wc.cmp == JumpConstraint.Cmp.LE) return axisX ? LE_X : LE_Z;
        if (wc.cmp == JumpConstraint.Cmp.GE) return axisX ? GE_X : GE_Z;
        return KINDS;
    }

    private static int[] sortWalls(List<JumpConstraint> walls) {
        Integer[] idx = new Integer[walls.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> {
            int ka = kindOf(walls.get(a));
            int kb = kindOf(walls.get(b));
            if (ka != kb) return Integer.compare(ka, kb);
            int ta = walls.get(a).t1;
            int tb = walls.get(b).t1;
            if (ta != tb) return Integer.compare(ta, tb);
            return Integer.compare(a, b);
        });
        int[] out = new int[idx.length];
        for (int i = 0; i < out.length; i++) out[i] = idx[i];
        return out;
    }

    int groupCount() {
        return groups;
    }

    int mainGroup() {
        return mainGroup;
    }

    int[] groupOf() {
        return groupOf;
    }

    int[] freeGroups() {
        return freeGroups;
    }

    void computeCoefs(int[] combos, boolean[] sprint) {
        for (int g = 0; g < groups; g++) {
            int[] sp = split[g];
            for (int k = 0; k < KINDS; k++) {
                for (int i = sp[k]; i < kindEnd[k]; i++) {
                    boolean axisX = wallAxisX[i];
                    StructurePoolDriver.sumAB(axisX ? d.gc : d.gcz, axisX ? d.gs : d.gsz, coefWall[g][i], groupTicks[g],
                            combos, sprint, ab);
                    runA[g][i] = ab[0];
                    runB[g][i] = ab[1];
                }
            }
        }
    }

    void fillSeed(double[] seedOut) {
        for (int t = 0; t < seedOut.length; t++) {
            seedOut[t] = Angles.wrap(Angles.deg(bestFacing[groupOf[t]]));
        }
    }

    double[] screen(double centerTheta) {
        double coarse = 2.0 * Math.PI / REF;
        double refRad = Angles.rad(Angles.wrap(Double.isNaN(centerTheta) ? 0.0 : centerTheta));
        for (int g = 0; g < groups; g++) {
            unitCos[g] = StrictMath.cos(refRad);
            unitSin[g] = StrictMath.sin(refRad);
        }
        linearize();

        int count;
        if (Double.isNaN(centerTheta)) {
            count = REF;
            for (int k = 0; k < REF; k++) thetas[k] = -Math.PI + k * coarse;
        } else {
            int cells = groups > 2 ? WINDOW_CELLS_MULTI : WINDOW_CELLS;
            count = 0;
            thetas[count++] = refRad;
            for (int w = 1; w <= cells; w++) {
                thetas[count++] = refRad - w * coarse;
                thetas[count++] = refRad + w * coarse;
            }
        }
        for (int k = 0; k < count; k++) {
            coarseViol[k] = Double.POSITIVE_INFINITY;
            skip[k] = false;
        }
        for (int k = 0; k < count; k++) {
            if (skip[k]) continue;
            double theta = thetas[k];
            double best = Double.POSITIVE_INFINITY;
            int from = nearestDone(k, count);
            if (from < 0) {
                for (double off : RESTART_OFFSETS) {
                    double v = solveFrom(theta, StrictMath.cos(theta + off), StrictMath.sin(theta + off), null, null, true, false);
                    if (v < best) {
                        best = v;
                        keep(coarseCos[k], coarseSin[k]);
                    }
                }
            } else {
                best = solveFrom(theta, 0.0, 0.0, coarseCos[from], coarseSin[from], false, false);
                keep(coarseCos[k], coarseSin[k]);
                if (k % RESTART_EVERY == 0) {
                    double v = solveFrom(theta, StrictMath.cos(theta), StrictMath.sin(theta), null, null, true, false);
                    if (v < best) {
                        best = v;
                        keep(coarseCos[k], coarseSin[k]);
                    }
                }
            }
            coarseViol[k] = best;
        }

        double best = Double.POSITIVE_INFINITY;
        double bestTheta = refRad;
        boolean any = false;
        for (int k = 0; k < count; k++) done[k] = skip[k];
        for (int refined = 0; refined < REFINE_BEST; refined++) {
            int bk = -1;
            double bv = Double.POSITIVE_INFINITY;
            for (int k = 0; k < count; k++) {
                if (!done[k] && coarseViol[k] < bv) {
                    bv = coarseViol[k];
                    bk = k;
                }
            }
            if (bk < 0) break;
            done[bk] = true;
            double theta = thetas[bk];
            double v = solveFrom(theta, 0.0, 0.0, coarseCos[bk], coarseSin[bk], true, false);
            keep(bestCos, bestSin);
            double step = coarse / 2.0;
            for (int level = 0; level < THETA_LEVELS; level++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    double cand = theta + sign * step;
                    double cv = solveFrom(cand, 0.0, 0.0, bestCos, bestSin, false, false);
                    if (cv < v) {
                        v = cv;
                        theta = cand;
                        keep(bestCos, bestSin);
                        break;
                    }
                }
                step /= 2.0;
            }
            if (v < best) {
                best = v;
                bestTheta = theta;
                keep(winCos, winSin);
                any = true;
            }
        }
        if (!any) {
            solveFrom(bestTheta, StrictMath.cos(bestTheta), StrictMath.sin(bestTheta), null, null, true, false);
            keep(winCos, winSin);
        }

        System.arraycopy(winCos, 0, unitCos, 0, groups);
        System.arraycopy(winSin, 0, unitSin, 0, groups);
        double viol;
        if (best <= POLISH_MAX_DEFICIT) {
            for (int outer = 0; outer < OUTER; outer++) {
                linearize();
                bestTheta = polish(bestTheta, coarse / 32.0);
            }
            resetShift();
            viol = fullViol(true);
        } else {
            linearize();
            solveFrom(bestTheta, 0.0, 0.0, unitCos, unitSin, false, false);
            viol = fullViol(true);
        }
        for (int g = 0; g < groups; g++) bestFacing[g] = StrictMath.atan2(unitSin[g], unitCos[g]);
        double dp;
        if (d.maximize) dp = d.objAxisX ? Math.min(bestHiX, d.hiShiftX) : Math.min(bestHiZ, d.hiShiftZ);
        else dp = d.objAxisX ? Math.max(bestLoX, d.loShiftX) : Math.max(bestLoZ, d.loShiftZ);
        if (viol > 0.0) dp = 0.0;
        bestObj = objBase + dp;
        d.witnessLoX = bestLoX;
        d.witnessHiX = bestHiX;
        d.witnessLoZ = bestLoZ;
        d.witnessHiZ = bestHiZ;
        return new double[]{viol, Angles.wrap(Angles.deg(bestTheta)), bestObj};
    }

    double[] evalFacings(double[] yawsDeg, double dx, double dz) {
        for (int g = 0; g < groups; g++) {
            double rad = Angles.rad(yawsDeg[groupTicks[g][0]]);
            unitCos[g] = StrictMath.cos(rad);
            unitSin[g] = StrictMath.sin(rad);
        }
        linearize();
        resetShift();
        double viol = fullViol(true);
        ByteForward bf = d.bf;
        for (int t = 0; t < bf.n; t++) bf.wrapped[t] = yawsDeg[t];
        bf.runShifted(dx, dz);
        double bv = 0.0;
        int worst = -1;
        for (int wi = 0; wi < d.byteWalls.size(); wi++) {
            JumpConstraint wc = d.byteWalls.get(wi);
            double value = bf.wallPos(wc);
            double v = wc.cmp == JumpConstraint.Cmp.LE ? value - wc.rhs : wc.rhs - value;
            if (v > bv) {
                bv = v;
                worst = wi;
            }
        }
        return new double[]{viol, bestLoX, bestHiX, bestLoZ, bestHiZ, groups, bv, worst};
    }

    private int nearestDone(int k, int count) {
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int j = 0; j < count; j++) {
            if (j == k || skip[j] || coarseViol[j] == Double.POSITIVE_INFINITY) continue;
            double dist = Math.abs(Angles.wrapDelta(Angles.deg(thetas[j] - thetas[k])));
            if (dist < bestDist) {
                bestDist = dist;
                best = j;
            }
        }
        return best;
    }

    private void keep(double[] cos, double[] sin) {
        System.arraycopy(unitCos, 0, cos, 0, groups);
        System.arraycopy(unitSin, 0, sin, 0, groups);
    }

    private double polish(double theta, double step) {
        double v = solveFrom(theta, 0.0, 0.0, unitCos, unitSin, true, true);
        keep(bestCos, bestSin);
        if (mainGroup < 0) return theta;
        double maxStep = step;
        int trials = 0;
        while (step > THETA_FINE_RAD && trials < POLISH_MAX_TRIALS) {
            boolean moved = false;
            for (int sign = -1; sign <= 1; sign += 2) {
                double cand = theta + sign * step;
                trials++;
                double cv = solveFrom(cand, 0.0, 0.0, bestCos, bestSin, false, true);
                if (cv < v) {
                    v = cv;
                    theta = cand;
                    keep(bestCos, bestSin);
                    moved = true;
                    break;
                }
            }
            step = moved ? Math.min(maxStep, step * 2.0) : step * 0.5;
        }
        solveFrom(theta, 0.0, 0.0, bestCos, bestSin, false, true);
        return theta;
    }

    private void linearize() {
        ByteForward bf = d.bf;
        for (int g = 0; g < groups; g++) {
            baseCos[g] = unitCos[g];
            baseSin[g] = unitSin[g];
        }
        for (int t = 0; t < bf.n; t++) {
            int g = groupOf[t];
            bf.wrapped[t] = Angles.wrap(Angles.deg(StrictMath.atan2(baseSin[g], baseCos[g])));
        }
        bf.run();
        for (int i = 0; i < used; i++) {
            JumpConstraint wc = d.byteWalls.get(order[i]);
            shiftBase[i] = wc.rhs - bf.wallPos(wc);
        }
        objBase = bf.pos(d.objTick, d.objAxisX);
    }

    private double solveFrom(double theta, double startCos, double startSin, double[] fromCos, double[] fromSin,
                             boolean global, boolean fine) {
        for (int g = 0; g < groups; g++) {
            if (g == mainGroup) {
                unitCos[g] = StrictMath.cos(theta);
                unitSin[g] = StrictMath.sin(theta);
            } else if (fromCos != null) {
                unitCos[g] = fromCos[g];
                unitSin[g] = fromSin[g];
            } else {
                unitCos[g] = startCos;
                unitSin[g] = startSin;
            }
        }
        resetShift();
        sweepGroups(global, fine ? UNIT_LEVELS_FINE : UNIT_LEVELS);
        return fullViol(false);
    }

    private void resetShift() {
        System.arraycopy(shiftBase, 0, shiftAcc, 0, used);
        for (int g = 0; g < groups; g++) {
            double dc = unitCos[g] - baseCos[g];
            double ds = unitSin[g] - baseSin[g];
            double[] ua = runA[g];
            double[] ub = runB[g];
            for (int i = 0; i < used; i++) shiftAcc[i] -= ua[i] * dc + ub[i] * ds;
        }
    }

    private void sweepGroups(boolean global, int levels) {
        boolean joint = global && freeGroups.length == 2;
        if (joint) jointCoarse();
        for (int pass = 0; pass < SWEEP_PASSES; pass++) {
            if (!sweepOnce(global && !joint, levels)) break;
        }
    }

    private void jointCoarse() {
        int g1 = freeGroups[0];
        int g2 = freeGroups[1];
        double[] a1 = runA[g1];
        double[] b1 = runB[g1];
        double[] a2 = runA[g2];
        double[] b2 = runB[g2];
        double bc1 = baseCos[g1], bs1 = baseSin[g1], bc2 = baseCos[g2], bs2 = baseSin[g2];
        double dc1 = unitCos[g1] - bc1, ds1 = unitSin[g1] - bs1, dc2 = unitCos[g2] - bc2, ds2 = unitSin[g2] - bs2;
        int[] sp1 = split[g1];
        int[] sp2 = split[g2];
        int fromLeX = Math.min(sp1[LE_X], sp2[LE_X]);
        int fromLeZ = Math.min(sp1[LE_Z], sp2[LE_Z]);
        int fromGeX = Math.min(sp1[GE_X], sp2[GE_X]);
        int fromGeZ = Math.min(sp1[GE_Z], sp2[GE_Z]);
        for (int k = 0; k < KINDS; k++) {
            int from = Math.min(sp1[k], sp2[k]);
            for (int i = from; i < kindEnd[k]; i++) {
                shiftNoU[i] = shiftAcc[i] + a1[i] * dc1 + b1[i] * ds1 + a2[i] * dc2 + b2[i] * ds2;
            }
        }
        baseHiX = minPlain(shiftAcc, kindStart[LE_X], fromLeX, d.hiShiftX);
        baseHiZ = minPlain(shiftAcc, kindStart[LE_Z], fromLeZ, d.hiShiftZ);
        baseLoX = maxPlain(shiftAcc, kindStart[GE_X], fromGeX, d.loShiftX);
        baseLoZ = maxPlain(shiftAcc, kindStart[GE_Z], fromGeZ, d.loShiftZ);
        double bestV = fullViol(false);
        int bi = -1;
        int bj = -1;
        for (int i = 0; i < UNIT_STEPS && bestV > 0.0; i++) {
            double sc1 = STEP_COS[i] - bc1;
            double ss1 = STEP_SIN[i] - bs1;
            for (int k = 0; k < KINDS; k++) {
                int from = Math.min(sp1[k], sp2[k]);
                for (int w = from; w < kindEnd[k]; w++) shiftJoint[w] = shiftNoU[w] - a1[w] * sc1 - b1[w] * ss1;
            }
            for (int j = 0; j < UNIT_STEPS && bestV > 0.0; j++) {
                double v = jointViolBelow(a2, b2, STEP_COS[j] - bc2, STEP_SIN[j] - bs2,
                        fromLeX, fromLeZ, fromGeX, fromGeZ, bestV);
                if (v < bestV) {
                    bestV = v;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (bi < 0) return;
        unitCos[g1] = STEP_COS[bi];
        unitSin[g1] = STEP_SIN[bi];
        unitCos[g2] = STEP_COS[bj];
        unitSin[g2] = STEP_SIN[bj];
        double nc1 = unitCos[g1] - bc1, ns1 = unitSin[g1] - bs1, nc2 = unitCos[g2] - bc2, ns2 = unitSin[g2] - bs2;
        for (int k = 0; k < KINDS; k++) {
            int from = Math.min(sp1[k], sp2[k]);
            for (int i = from; i < kindEnd[k]; i++) {
                shiftAcc[i] = shiftNoU[i] - a1[i] * nc1 - b1[i] * ns1 - a2[i] * nc2 - b2[i] * ns2;
            }
        }
    }

    private boolean sweepOnce(boolean global, int levels) {
        boolean moved = false;
        for (int g = 0; g < groups; g++) {
            if (g == mainGroup) continue;
            double[] ua = runA[g];
            double[] ub = runB[g];
            int[] sp = split[g];
            double bc = baseCos[g];
            double bs = baseSin[g];
            double cc = unitCos[g];
            double cs = unitSin[g];
            double dc0 = cc - bc;
            double ds0 = cs - bs;
            for (int k = 0; k < KINDS; k++) {
                for (int i = sp[k]; i < kindEnd[k]; i++) shiftNoU[i] = shiftAcc[i] + ua[i] * dc0 + ub[i] * ds0;
            }
            baseLoX = maxPlain(shiftAcc, kindStart[GE_X], sp[GE_X], d.loShiftX);
            baseHiX = minPlain(shiftAcc, kindStart[LE_X], sp[LE_X], d.hiShiftX);
            baseLoZ = maxPlain(shiftAcc, kindStart[GE_Z], sp[GE_Z], d.loShiftZ);
            baseHiZ = minPlain(shiftAcc, kindStart[LE_Z], sp[LE_Z], d.hiShiftZ);
            double bestC = cc;
            double bestS = cs;
            double bestV = affectedViol(sp, ua, ub, dc0, ds0);
            if (global) {
                for (int kk = 0; kk < UNIT_STEPS && bestV > 0.0; kk++) {
                    double v = affectedViolBelow(sp, ua, ub, STEP_COS[kk] - bc, STEP_SIN[kk] - bs, bestV);
                    if (v < bestV) {
                        bestV = v;
                        bestC = STEP_COS[kk];
                        bestS = STEP_SIN[kk];
                    }
                }
            }
            int maxLevels = Math.min(levels, UNIT_LEVELS_FINE);
            for (int level = 0; level < maxLevels && bestV > 0.0; level++) {
                double centerC = bestC;
                double centerS = bestS;
                double[] rc = REFINE_COS[level];
                double[] rs = REFINE_SIN[level];
                for (int j = 0; j <= 2 * UNIT_REFINE && bestV > 0.0; j++) {
                    if (j == UNIT_REFINE) continue;
                    double c = centerC * rc[j] - centerS * rs[j];
                    double s = centerS * rc[j] + centerC * rs[j];
                    double v = affectedViolBelow(sp, ua, ub, c - bc, s - bs, bestV);
                    if (v < bestV) {
                        bestV = v;
                        bestC = c;
                        bestS = s;
                    }
                }
            }
            if (bestC != cc || bestS != cs) moved = true;
            unitCos[g] = bestC;
            unitSin[g] = bestS;
            double ndc = bestC - bc;
            double nds = bestS - bs;
            for (int k = 0; k < KINDS; k++) {
                for (int i = sp[k]; i < kindEnd[k]; i++) shiftAcc[i] = shiftNoU[i] - ua[i] * ndc - ub[i] * nds;
            }
        }
        return moved;
    }

    private double affectedViol(int[] sp, double[] ua, double[] ub, double dc, double ds) {
        double hiX = minAff(shiftNoU, ua, ub, dc, ds, sp[LE_X], kindEnd[LE_X], baseHiX);
        double hiZ = minAff(shiftNoU, ua, ub, dc, ds, sp[LE_Z], kindEnd[LE_Z], baseHiZ);
        double loX = maxAff(shiftNoU, ua, ub, dc, ds, sp[GE_X], kindEnd[GE_X], baseLoX);
        double loZ = maxAff(shiftNoU, ua, ub, dc, ds, sp[GE_Z], kindEnd[GE_Z], baseLoZ);
        return Math.max(0.0, Math.max(loX - hiX, loZ - hiZ));
    }

    private double affectedViolBelow(int[] sp, double[] ua, double[] ub, double dc, double ds, double bound) {
        double hiX = minAffBelow(shiftNoU, ua, ub, dc, ds, sp[LE_X], kindEnd[LE_X], baseHiX, baseLoX, bound);
        if (hiX != hiX) return Double.POSITIVE_INFINITY;
        double loX = maxAffBelow(shiftNoU, ua, ub, dc, ds, sp[GE_X], kindEnd[GE_X], baseLoX, hiX, bound);
        if (loX != loX) return Double.POSITIVE_INFINITY;
        double hiZ = minAffBelow(shiftNoU, ua, ub, dc, ds, sp[LE_Z], kindEnd[LE_Z], baseHiZ, baseLoZ, bound);
        if (hiZ != hiZ) return Double.POSITIVE_INFINITY;
        double loZ = maxAffBelow(shiftNoU, ua, ub, dc, ds, sp[GE_Z], kindEnd[GE_Z], baseLoZ, hiZ, bound);
        if (loZ != loZ) return Double.POSITIVE_INFINITY;
        return Math.max(0.0, Math.max(loX - hiX, loZ - hiZ));
    }

    private double jointViolBelow(double[] ua, double[] ub, double dc, double ds, int fromLeX, int fromLeZ,
                                  int fromGeX, int fromGeZ, double bound) {
        double hiX = minJointBelow(shiftJoint, ua, ub, dc, ds, fromLeX, kindEnd[LE_X], baseHiX, baseLoX, bound);
        if (hiX != hiX) return Double.POSITIVE_INFINITY;
        double loX = maxJointBelow(shiftJoint, ua, ub, dc, ds, fromGeX, kindEnd[GE_X], baseLoX, hiX, bound);
        if (loX != loX) return Double.POSITIVE_INFINITY;
        double hiZ = minJointBelow(shiftJoint, ua, ub, dc, ds, fromLeZ, kindEnd[LE_Z], baseHiZ, baseLoZ, bound);
        if (hiZ != hiZ) return Double.POSITIVE_INFINITY;
        double loZ = maxJointBelow(shiftJoint, ua, ub, dc, ds, fromGeZ, kindEnd[GE_Z], baseLoZ, hiZ, bound);
        if (loZ != loZ) return Double.POSITIVE_INFINITY;
        return Math.max(0.0, Math.max(loX - hiX, loZ - hiZ));
    }

    private double fullViol(boolean writeWitness) {
        double hiX = minPlain(shiftAcc, kindStart[LE_X], kindEnd[LE_X], d.hiShiftX);
        double hiZ = minPlain(shiftAcc, kindStart[LE_Z], kindEnd[LE_Z], d.hiShiftZ);
        double loX = maxPlain(shiftAcc, kindStart[GE_X], kindEnd[GE_X], d.loShiftX);
        double loZ = maxPlain(shiftAcc, kindStart[GE_Z], kindEnd[GE_Z], d.loShiftZ);
        double viol = Math.max(0.0, Math.max(loX - hiX, loZ - hiZ));
        if (writeWitness) {
            bestLoX = loX;
            bestHiX = hiX;
            bestLoZ = loZ;
            bestHiZ = hiZ;
        }
        return viol;
    }

    private static double minPlain(double[] shift, int from, int to, double init) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i];
            if (s < m) m = s;
        }
        return m;
    }

    private static double maxPlain(double[] shift, int from, int to, double init) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i];
            if (s > m) m = s;
        }
        return m;
    }

    private static double minAff(double[] shift, double[] ua, double[] ub, double dc, double ds, int from, int to,
                                 double init) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i] - ua[i] * dc - ub[i] * ds;
            if (s < m) m = s;
        }
        return m;
    }

    private static double maxAff(double[] shift, double[] ua, double[] ub, double dc, double ds, int from, int to,
                                 double init) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i] - ua[i] * dc - ub[i] * ds;
            if (s > m) m = s;
        }
        return m;
    }

    private static double minAffBelow(double[] shift, double[] ua, double[] ub, double dc, double ds, int from, int to,
                                      double init, double lo, double bound) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i] - ua[i] * dc - ub[i] * ds;
            if (s < m) {
                m = s;
                if (lo - m >= bound) return Double.NaN;
            }
        }
        return m;
    }

    private static double maxAffBelow(double[] shift, double[] ua, double[] ub, double dc, double ds, int from, int to,
                                      double init, double hi, double bound) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i] - ua[i] * dc - ub[i] * ds;
            if (s > m) {
                m = s;
                if (m - hi >= bound) return Double.NaN;
            }
        }
        return m;
    }

    private static double minJointBelow(double[] shift, double[] ua, double[] ub, double dc, double ds, int from,
                                        int to, double init, double lo, double bound) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i];
            s -= ua[i] * dc + ub[i] * ds;
            if (s < m) {
                m = s;
                if (lo - m >= bound) return Double.NaN;
            }
        }
        return m;
    }

    private static double maxJointBelow(double[] shift, double[] ua, double[] ub, double dc, double ds, int from,
                                        int to, double init, double hi, double bound) {
        double m = init;
        for (int i = from; i < to; i++) {
            double s = shift[i];
            s -= ua[i] * dc + ub[i] * ds;
            if (s > m) {
                m = s;
                if (m - hi >= bound) return Double.NaN;
            }
        }
        return m;
    }
}
