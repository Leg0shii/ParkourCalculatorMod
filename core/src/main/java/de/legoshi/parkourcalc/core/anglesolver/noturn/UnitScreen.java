package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;

import java.util.ArrayList;
import java.util.List;

final class UnitScreen {

    private static final int OUTER = 2;
    private static final int REF = 36;
    private static final int UNIT_STEPS = 12;
    private static final int UNIT_REFINE = 4;
    private static final int UNIT_LEVELS = 2;
    private static final int REFINE_BEST = 2;
    private static final int THETA_LEVELS = 4;
    private static final int RESTART_EVERY = 6;
    private static final double[] RESTART_OFFSETS = {0.0, 0.5 * Math.PI, -0.5 * Math.PI, Math.PI};

    private final StructurePoolDriver d;
    private final int groups;
    private final int mainGroup;
    private final int[] groupOf;
    private final int[][] groupTicks;
    private final double[][][] coefWall;
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
    private final double[][] coarseCos;
    private final double[][] coarseSin;
    private final double[] coarseViol;
    private final double[] bestFacing;
    private final double[] ab = new double[2];
    private double objBase;
    private double bestObj;
    private double bestLoX;
    private double bestHiX;
    private double bestLoZ;
    private double bestHiZ;

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
        this.runA = new double[groups][walls];
        this.runB = new double[groups][walls];
        for (int g = 0; g < groups; g++) {
            List<Integer> list = members.get(g);
            int[] ticks = new int[list.size()];
            for (int i = 0; i < ticks.length; i++) ticks[i] = list.get(i);
            groupTicks[g] = ticks;
            for (int wi = 0; wi < walls; wi++) {
                int wallTick = d.byteWalls.get(wi).t1;
                double[] cw = new double[ticks.length];
                for (int i = 0; i < ticks.length; i++) cw[i] = lm.coef(ticks[i], wallTick);
                coefWall[g][wi] = cw;
            }
        }
        this.unitCos = new double[groups];
        this.unitSin = new double[groups];
        this.baseCos = new double[groups];
        this.baseSin = new double[groups];
        this.bestCos = new double[groups];
        this.bestSin = new double[groups];
        this.coarseCos = new double[REF + 1][groups];
        this.coarseSin = new double[REF + 1][groups];
        this.coarseViol = new double[REF + 1];
        this.bestFacing = new double[groups];
        this.shiftBase = new double[walls];
        this.shiftAcc = new double[walls];
        this.shiftNoU = new double[walls];
    }

    void computeCoefs(int[] combos, boolean[] sprint) {
        int walls = d.byteWalls.size();
        for (int g = 0; g < groups; g++) {
            for (int wi = 0; wi < walls; wi++) {
                boolean axisX = d.byteWalls.get(wi).mode == JumpConstraint.Mode.X;
                StructurePoolDriver.sumAB(axisX ? d.gc : d.gcz, axisX ? d.gs : d.gsz, coefWall[g][wi], groupTicks[g],
                        combos, sprint, ab);
                runA[g][wi] = ab[0];
                runB[g][wi] = ab[1];
            }
        }
    }

    void fillSeed(double[] seedOut) {
        for (int t = 0; t < seedOut.length; t++) {
            seedOut[t] = Angles.wrap(Math.toDegrees(bestFacing[groupOf[t]]));
        }
    }

    double[] screen(double centerTheta) {
        double coarse = 2.0 * Math.PI / REF;
        double refRad = Math.toRadians(Angles.wrap(Double.isNaN(centerTheta) ? 0.0 : centerTheta));
        for (int g = 0; g < groups; g++) {
            unitCos[g] = Math.cos(refRad);
            unitSin[g] = Math.sin(refRad);
        }
        linearize();

        int count = Double.isNaN(centerTheta) ? REF : REF + 1;
        double[] thetas = new double[count];
        for (int k = 0; k < REF; k++) thetas[k] = -Math.PI + k * coarse;
        if (count > REF) thetas[REF] = refRad;
        for (int k = 0; k < count; k++) {
            double theta = thetas[k];
            double best = Double.POSITIVE_INFINITY;
            if (k == 0) {
                for (double off : RESTART_OFFSETS) {
                    double v = solveFrom(theta, Math.cos(theta + off), Math.sin(theta + off), null, null, true);
                    if (v < best) {
                        best = v;
                        keep(coarseCos[k], coarseSin[k]);
                    }
                }
            } else {
                int from = k < REF ? k - 1 : nearestCoarse(theta, coarse);
                best = solveFrom(theta, 0.0, 0.0, coarseCos[from], coarseSin[from], false);
                keep(coarseCos[k], coarseSin[k]);
                if (k % RESTART_EVERY == 0) {
                    double v = solveFrom(theta, Math.cos(theta), Math.sin(theta), null, null, true);
                    if (v < best) {
                        best = v;
                        keep(coarseCos[k], coarseSin[k]);
                    }
                }
            }
            coarseViol[k] = best;
        }

        double best = Double.POSITIVE_INFINITY;
        double bestTheta = 0.0;
        boolean[] done = new boolean[count];
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
            double v = solveFrom(theta, 0.0, 0.0, coarseCos[bk], coarseSin[bk], true);
            keep(bestCos, bestSin);
            double step = coarse / 2.0;
            for (int level = 0; level < THETA_LEVELS; level++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    double cand = theta + sign * step;
                    double cv = solveFrom(cand, 0.0, 0.0, bestCos, bestSin, false);
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
                System.arraycopy(bestCos, 0, coarseCos[REF], 0, groups);
                System.arraycopy(bestSin, 0, coarseSin[REF], 0, groups);
            }
        }

        System.arraycopy(coarseCos[REF], 0, unitCos, 0, groups);
        System.arraycopy(coarseSin[REF], 0, unitSin, 0, groups);
        for (int outer = 0; outer < OUTER; outer++) {
            linearize();
            solveFrom(bestTheta, 0.0, 0.0, unitCos, unitSin, false);
        }
        resetShift();
        double viol = descentViol(shiftAcc, null, null, 0.0, 0.0, true);
        for (int g = 0; g < groups; g++) bestFacing[g] = Math.atan2(unitSin[g], unitCos[g]);
        double dp;
        if (d.maximize) dp = d.objAxisX ? Math.min(bestHiX, d.hiShiftX) : Math.min(bestHiZ, d.hiShiftZ);
        else dp = d.objAxisX ? Math.max(bestLoX, d.loShiftX) : Math.max(bestLoZ, d.loShiftZ);
        if (viol > 0.0) dp = 0.0;
        bestObj = objBase + dp;
        d.witnessLoX = bestLoX;
        d.witnessHiX = bestHiX;
        d.witnessLoZ = bestLoZ;
        d.witnessHiZ = bestHiZ;
        return new double[]{viol, Angles.wrap(Math.toDegrees(bestTheta)), bestObj};
    }

    private int nearestCoarse(double theta, double coarse) {
        int k = (int) Math.round((theta + Math.PI) / coarse);
        return ((k % REF) + REF) % REF;
    }

    private void keep(double[] cos, double[] sin) {
        System.arraycopy(unitCos, 0, cos, 0, groups);
        System.arraycopy(unitSin, 0, sin, 0, groups);
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
            bf.wrapped[t] = Angles.wrap(Math.toDegrees(Math.atan2(baseSin[g], baseCos[g])));
        }
        bf.run();
        for (int wi = 0; wi < walls; wi++) {
            JumpConstraint wc = d.byteWalls.get(wi);
            shiftBase[wi] = wc.rhs - bf.wallPos(wc);
        }
        objBase = bf.pos(d.objTick, d.objAxisX);
    }

    private double solveFrom(double theta, double startCos, double startSin, double[] fromCos, double[] fromSin,
                             boolean global) {
        for (int g = 0; g < groups; g++) {
            if (g == mainGroup) {
                unitCos[g] = Math.cos(theta);
                unitSin[g] = Math.sin(theta);
            } else if (fromCos != null) {
                unitCos[g] = fromCos[g];
                unitSin[g] = fromSin[g];
            } else {
                unitCos[g] = startCos;
                unitSin[g] = startSin;
            }
        }
        resetShift();
        sweepGroups(global);
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

    private void sweepGroups(boolean global) {
        int walls = d.byteWalls.size();
        for (int g = 0; g < groups; g++) {
            if (g == mainGroup) continue;
            double[] ua = runA[g];
            double[] ub = runB[g];
            double bc = baseCos[g];
            double bs = baseSin[g];
            for (int wi = 0; wi < walls; wi++) {
                shiftNoU[wi] = shiftAcc[wi] + ua[wi] * (unitCos[g] - bc) + ub[wi] * (unitSin[g] - bs);
            }
            double bestPsi = Math.atan2(unitSin[g], unitCos[g]);
            double bestV = descentViol(shiftNoU, ua, ub, unitCos[g] - bc, unitSin[g] - bs, false);
            for (int kk = 0; global && kk < UNIT_STEPS; kk++) {
                double psi = -Math.PI + kk * (2.0 * Math.PI / UNIT_STEPS);
                double v = descentViol(shiftNoU, ua, ub, Math.cos(psi) - bc, Math.sin(psi) - bs, false);
                if (v < bestV) {
                    bestV = v;
                    bestPsi = psi;
                }
            }
            double rstep = (2.0 * Math.PI / UNIT_STEPS) / (UNIT_REFINE + 1);
            for (int level = 0; level < UNIT_LEVELS; level++) {
                double center = bestPsi;
                for (int j = -UNIT_REFINE; j <= UNIT_REFINE; j++) {
                    if (j == 0) continue;
                    double psi = center + j * rstep;
                    double v = descentViol(shiftNoU, ua, ub, Math.cos(psi) - bc, Math.sin(psi) - bs, false);
                    if (v < bestV) {
                        bestV = v;
                        bestPsi = psi;
                    }
                }
                rstep /= (UNIT_REFINE + 1);
            }
            unitCos[g] = Math.cos(bestPsi);
            unitSin[g] = Math.sin(bestPsi);
            for (int wi = 0; wi < walls; wi++) {
                shiftAcc[wi] = shiftNoU[wi] - ua[wi] * (unitCos[g] - bc) - ub[wi] * (unitSin[g] - bs);
            }
        }
    }

    private double descentViol(double[] shiftNoU, double[] ua, double[] ub, double dc, double ds, boolean writeWitness) {
        double needLoX = d.loShiftX, needHiX = d.hiShiftX;
        double needLoZ = d.loShiftZ, needHiZ = d.hiShiftZ;
        int walls = d.byteWalls.size();
        for (int wi = 0; wi < walls; wi++) {
            JumpConstraint wc = d.byteWalls.get(wi);
            double shift = shiftNoU[wi];
            if (ua != null) shift -= ua[wi] * dc + ub[wi] * ds;
            boolean axisX = wc.mode == JumpConstraint.Mode.X;
            if (wc.cmp == JumpConstraint.Cmp.LE) {
                if (axisX) {
                    if (shift < needHiX) needHiX = shift;
                } else {
                    if (shift < needHiZ) needHiZ = shift;
                }
            } else if (wc.cmp == JumpConstraint.Cmp.GE) {
                if (axisX) {
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
