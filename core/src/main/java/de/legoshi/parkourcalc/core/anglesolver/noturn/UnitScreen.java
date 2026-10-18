package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;

import java.util.ArrayList;
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
    private final double[][][] coefWall;
    private final int[][] affected;
    private final int[][] unaffected;
    private final double[][] runA;
    private final double[][] runB;
    private final double[] shiftBase;
    private final double[] shiftAcc;
    private final double[] shiftNoU;
    private final boolean[] wallAxisX;
    private final int[] wallKind;
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
        java.util.Arrays.fill(groupOfUnit, -1);
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
        this.groupTicks = new int[groups][];
        this.coefWall = new double[groups][walls][];
        this.affected = new int[groups][];
        this.unaffected = new int[groups][];
        this.runA = new double[groups][walls];
        this.runB = new double[groups][walls];
        for (int g = 0; g < groups; g++) {
            List<Integer> list = members.get(g);
            int[] ticks = new int[list.size()];
            for (int i = 0; i < ticks.length; i++) ticks[i] = list.get(i);
            groupTicks[g] = ticks;
            List<Integer> aff = new ArrayList<>();
            List<Integer> unaff = new ArrayList<>();
            for (int wi = 0; wi < walls; wi++) {
                int wallTick = d.byteWalls.get(wi).t1;
                double[] cw = new double[ticks.length];
                boolean any = false;
                for (int i = 0; i < ticks.length; i++) {
                    cw[i] = lm.coef(ticks[i], wallTick);
                    if (cw[i] != 0.0) any = true;
                }
                coefWall[g][wi] = cw;
                (any ? aff : unaff).add(wi);
            }
            affected[g] = toArray(aff);
            unaffected[g] = toArray(unaff);
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
        this.wallAxisX = new boolean[walls];
        this.wallKind = new int[walls];
        for (int wi = 0; wi < walls; wi++) {
            JumpConstraint wc = d.byteWalls.get(wi);
            wallAxisX[wi] = wc.mode == JumpConstraint.Mode.X;
            wallKind[wi] = wc.cmp == JumpConstraint.Cmp.LE ? 0 : wc.cmp == JumpConstraint.Cmp.GE ? 1 : 2;
        }
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    void computeCoefs(int[] combos, boolean[] sprint) {
        for (int g = 0; g < groups; g++) {
            int[] aff = affected[g];
            for (int ai = 0; ai < aff.length; ai++) {
                int wi = aff[ai];
                boolean axisX = wallAxisX[wi];
                StructurePoolDriver.sumAB(axisX ? d.gc : d.gcz, axisX ? d.gs : d.gsz, coefWall[g][wi], groupTicks[g],
                        combos, sprint, ab);
                runA[g][wi] = ab[0];
                runB[g][wi] = ab[1];
            }
            int[] unaff = unaffected[g];
            for (int ui = 0; ui < unaff.length; ui++) {
                runA[g][unaff[ui]] = 0.0;
                runB[g][unaff[ui]] = 0.0;
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
            viol = descentViol(shiftAcc, null, null, 0.0, 0.0, true);
        } else {
            linearize();
            solveFrom(bestTheta, 0.0, 0.0, unitCos, unitSin, false, false);
            viol = descentViol(shiftAcc, null, null, 0.0, 0.0, true);
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
        double viol = descentViol(shiftAcc, null, null, 0.0, 0.0, true);
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
        int walls = d.byteWalls.size();
        for (int g = 0; g < groups; g++) {
            baseCos[g] = unitCos[g];
            baseSin[g] = unitSin[g];
        }
        for (int t = 0; t < bf.n; t++) {
            int g = groupOf[t];
            bf.wrapped[t] = Angles.wrap(Angles.deg(StrictMath.atan2(baseSin[g], baseCos[g])));
        }
        bf.run();
        for (int wi = 0; wi < walls; wi++) {
            JumpConstraint wc = d.byteWalls.get(wi);
            shiftBase[wi] = wc.rhs - bf.wallPos(wc);
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
        return descentViol(shiftAcc, null, null, 0.0, 0.0, false);
    }

    private void resetShift() {
        int walls = d.byteWalls.size();
        for (int wi = 0; wi < walls; wi++) {
            double sh = shiftBase[wi];
            for (int g = 0; g < groups; g++) {
                sh -= runA[g][wi] * (unitCos[g] - baseCos[g]) + runB[g][wi] * (unitSin[g] - baseSin[g]);
            }
            shiftAcc[wi] = sh;
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
        int walls = d.byteWalls.size();
        for (int wi = 0; wi < walls; wi++) {
            shiftNoU[wi] = shiftAcc[wi] + a1[wi] * (unitCos[g1] - bc1) + b1[wi] * (unitSin[g1] - bs1)
                    + a2[wi] * (unitCos[g2] - bc2) + b2[wi] * (unitSin[g2] - bs2);
        }
        double bestV = descentViol(shiftAcc, null, null, 0.0, 0.0, false);
        int bi = -1;
        int bj = -1;
        for (int i = 0; i < UNIT_STEPS; i++) {
            double dc1 = STEP_COS[i] - bc1;
            double ds1 = STEP_SIN[i] - bs1;
            for (int wi = 0; wi < walls; wi++) shiftJoint[wi] = shiftNoU[wi] - a1[wi] * dc1 - b1[wi] * ds1;
            for (int j = 0; j < UNIT_STEPS; j++) {
                double v = descentViol(shiftJoint, a2, b2, STEP_COS[j] - bc2, STEP_SIN[j] - bs2, false);
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
        for (int wi = 0; wi < walls; wi++) {
            shiftAcc[wi] = shiftNoU[wi] - a1[wi] * (unitCos[g1] - bc1) - b1[wi] * (unitSin[g1] - bs1)
                    - a2[wi] * (unitCos[g2] - bc2) - b2[wi] * (unitSin[g2] - bs2);
        }
    }

    private boolean sweepOnce(boolean global, int levels) {
        boolean moved = false;
        int walls = d.byteWalls.size();
        for (int g = 0; g < groups; g++) {
            if (g == mainGroup) continue;
            double[] ua = runA[g];
            double[] ub = runB[g];
            double bc = baseCos[g];
            double bs = baseSin[g];
            double cc = unitCos[g];
            double cs = unitSin[g];
            for (int wi = 0; wi < walls; wi++) {
                shiftNoU[wi] = shiftAcc[wi] + ua[wi] * (cc - bc) + ub[wi] * (cs - bs);
            }
            baseNeeds(g);
            int[] aff = affected[g];
            double bestC = cc;
            double bestS = cs;
            double bestV = affectedViol(aff, ua, ub, cc - bc, cs - bs);
            if (global) {
                for (int kk = 0; kk < UNIT_STEPS; kk++) {
                    double c = STEP_COS[kk];
                    double s = STEP_SIN[kk];
                    double v = affectedViol(aff, ua, ub, c - bc, s - bs);
                    if (v < bestV) {
                        bestV = v;
                        bestC = c;
                        bestS = s;
                    }
                }
            }
            int maxLevels = Math.min(levels, UNIT_LEVELS_FINE);
            for (int level = 0; level < maxLevels; level++) {
                double centerC = bestC;
                double centerS = bestS;
                double[] rc = REFINE_COS[level];
                double[] rs = REFINE_SIN[level];
                for (int j = 0; j <= 2 * UNIT_REFINE; j++) {
                    if (j == UNIT_REFINE) continue;
                    double c = centerC * rc[j] - centerS * rs[j];
                    double s = centerS * rc[j] + centerC * rs[j];
                    double v = affectedViol(aff, ua, ub, c - bc, s - bs);
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
            for (int wi = 0; wi < walls; wi++) {
                shiftAcc[wi] = shiftNoU[wi] - ua[wi] * (bestC - bc) - ub[wi] * (bestS - bs);
            }
        }
        return moved;
    }

    private void baseNeeds(int g) {
        double needLoX = d.loShiftX, needHiX = d.hiShiftX;
        double needLoZ = d.loShiftZ, needHiZ = d.hiShiftZ;
        int[] unaff = unaffected[g];
        for (int ui = 0; ui < unaff.length; ui++) {
            int wi = unaff[ui];
            double shift = shiftNoU[wi];
            int kind = wallKind[wi];
            if (kind == 0) {
                if (wallAxisX[wi]) {
                    if (shift < needHiX) needHiX = shift;
                } else {
                    if (shift < needHiZ) needHiZ = shift;
                }
            } else if (kind == 1) {
                if (wallAxisX[wi]) {
                    if (shift > needLoX) needLoX = shift;
                } else {
                    if (shift > needLoZ) needLoZ = shift;
                }
            }
        }
        baseLoX = needLoX;
        baseHiX = needHiX;
        baseLoZ = needLoZ;
        baseHiZ = needHiZ;
    }

    private double affectedViol(int[] aff, double[] ua, double[] ub, double dc, double ds) {
        double needLoX = baseLoX, needHiX = baseHiX;
        double needLoZ = baseLoZ, needHiZ = baseHiZ;
        for (int ai = 0; ai < aff.length; ai++) {
            int wi = aff[ai];
            double shift = shiftNoU[wi] - ua[wi] * dc - ub[wi] * ds;
            int kind = wallKind[wi];
            if (kind == 0) {
                if (wallAxisX[wi]) {
                    if (shift < needHiX) needHiX = shift;
                } else {
                    if (shift < needHiZ) needHiZ = shift;
                }
            } else if (kind == 1) {
                if (wallAxisX[wi]) {
                    if (shift > needLoX) needLoX = shift;
                } else {
                    if (shift > needLoZ) needLoZ = shift;
                }
            }
        }
        double vx = needLoX - needHiX;
        double vz = needLoZ - needHiZ;
        return Math.max(0.0, Math.max(vx, vz));
    }

    private double descentViol(double[] shiftNoU, double[] ua, double[] ub, double dc, double ds, boolean writeWitness) {
        double needLoX = d.loShiftX, needHiX = d.hiShiftX;
        double needLoZ = d.loShiftZ, needHiZ = d.hiShiftZ;
        int walls = d.byteWalls.size();
        for (int wi = 0; wi < walls; wi++) {
            double shift = shiftNoU[wi];
            if (ua != null) shift -= ua[wi] * dc + ub[wi] * ds;
            int kind = wallKind[wi];
            if (kind == 0) {
                if (wallAxisX[wi]) {
                    if (shift < needHiX) needHiX = shift;
                } else {
                    if (shift < needHiZ) needHiZ = shift;
                }
            } else if (kind == 1) {
                if (wallAxisX[wi]) {
                    if (shift > needLoX) needLoX = shift;
                } else {
                    if (shift > needLoZ) needLoZ = shift;
                }
            }
        }
        double vx = needLoX - needHiX;
        double vz = needLoZ - needHiZ;
        double viol = Math.max(0.0, Math.max(vx, vz));
        if (writeWitness) {
            bestLoX = needLoX;
            bestHiX = needHiX;
            bestLoZ = needLoZ;
            bestHiZ = needHiZ;
        }
        return viol;
    }
}
