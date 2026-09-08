package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class MultiFacingScreen {

    private static final int OUTER = 3;
    private static final int REF = 6;
    private static final int ROUNDS = 1;
    private static final int UNIT_STEPS = 12;
    private static final int UNIT_REFINE = 4;
    private static final int UNIT_LEVELS = 2;
    private static final int REFINE_BEST = 2;

    private final StructurePoolDriver d;
    private final int groupCount;
    private final int[][] groupTicks;
    private final double[][][] coefWall;
    private final double[][] runA;
    private final double[][] runB;
    private final double[] shiftBase;
    private final double[] shiftAcc;
    private final double[] shiftNoU;
    private final double[] unitCos;
    private final double[] unitSin;
    private final double[] facing;
    private final double[] baseCos;
    private final double[] baseSin;
    private final double[] bestFacing;
    private final int[] groupOfTick;
    private final double[] ab = new double[2];
    private double bestObj;
    private double bestPhi;
    private double bestLoX;
    private double bestHiX;
    private double bestLoZ;
    private double bestHiZ;

    MultiFacingScreen(StructurePoolDriver d, JumpLinearModel lm) {
        this.d = d;
        NoTurnProblem problem = d.problem;
        int setupEnd = d.setupEnd;
        LinkedHashMap<Integer, List<Integer>> byRoot = new LinkedHashMap<>();
        List<Integer> main = new ArrayList<>();
        for (int t = 0; t <= setupEnd; t++) {
            if (problem.freeDirection(t)) {
                byRoot.computeIfAbsent(problem.segment[t], k -> new ArrayList<>()).add(t);
            } else {
                main.add(t);
            }
        }
        List<List<Integer>> ordered = new ArrayList<>();
        if (!main.isEmpty()) ordered.add(main);
        ordered.addAll(byRoot.values());
        this.groupCount = ordered.size();
        int walls = d.byteWalls.size();
        this.groupTicks = new int[groupCount][];
        this.coefWall = new double[groupCount][][];
        this.runA = new double[groupCount][walls];
        this.runB = new double[groupCount][walls];
        int gi = 0;
        for (List<Integer> ticks : ordered) {
            int m = ticks.size();
            int[] arr = new int[m];
            for (int i = 0; i < m; i++) arr[i] = ticks.get(i);
            groupTicks[gi] = arr;
            double[][] cw = new double[walls][m];
            for (int wi = 0; wi < walls; wi++) {
                int wallTick = d.byteWalls.get(wi).t1;
                for (int i = 0; i < m; i++) cw[wi][i] = lm.coef(arr[i], wallTick);
            }
            coefWall[gi] = cw;
            gi++;
        }
        int units = groupCount + 1;
        this.unitCos = new double[units];
        this.unitSin = new double[units];
        this.facing = new double[units];
        this.bestFacing = new double[units];
        this.baseCos = new double[units];
        this.baseSin = new double[units];
        this.shiftBase = new double[walls];
        this.shiftAcc = new double[walls];
        this.shiftNoU = new double[walls];
        this.groupOfTick = new int[setupEnd + 1];
        for (int g = 0; g < groupCount; g++) {
            for (int s : groupTicks[g]) groupOfTick[s] = g;
        }
    }

    void computeCoefs(int[] combos, boolean[] sprint) {
        int walls = d.byteWalls.size();
        for (int g = 0; g < groupCount; g++) {
            double[][] cw = coefWall[g];
            for (int wi = 0; wi < walls; wi++) {
                boolean axisX = d.byteWalls.get(wi).mode == JumpConstraint.Mode.X;
                StructurePoolDriver.sumAB(axisX ? d.gc : d.gcz, axisX ? d.gs : d.gsz, cw[wi], groupTicks[g],
                        combos, sprint, ab);
                runA[g][wi] = ab[0];
                runB[g][wi] = ab[1];
            }
        }
    }

    void fillSeed(double[] seedOut) {
        int airUnit = groupCount;
        for (int t = 0; t < seedOut.length; t++) {
            double rad = t <= d.setupEnd ? bestFacing[groupOfTick[t]] : bestFacing[airUnit];
            seedOut[t] = Angles.wrap(Math.toDegrees(rad));
        }
    }

    double[] screen() {
        double coarse = 360.0 / REF;
        double best = Double.POSITIVE_INFINITY;
        double bestTheta = 0.0;
        double screenObj = Double.NaN;
        double bPhi = 0.0, bLoX = 0.0, bHiX = 0.0, bLoZ = 0.0, bHiZ = 0.0;
        double[] coarseViol = new double[REF];
        boolean[] done = new boolean[REF];
        for (int k = 0; k < REF; k++) {
            coarseViol[k] = evalAt(-180.0 + k * coarse, 1);
        }
        int refined = 0;
        while (refined < REFINE_BEST) {
            int bk = -1;
            double bv = Double.POSITIVE_INFINITY;
            for (int k = 0; k < REF; k++) {
                if (done[k]) continue;
                if (coarseViol[k] < bv) {
                    bv = coarseViol[k];
                    bk = k;
                }
            }
            if (bk < 0) break;
            done[bk] = true;
            refined++;
            double theta = -180.0 + bk * coarse;
            double v = evalAt(theta, OUTER);
            if (v < best) {
                best = v;
                bestTheta = theta;
                screenObj = bestObj;
                bPhi = bestPhi;
                bLoX = bestLoX;
                bHiX = bestHiX;
                bLoZ = bestLoZ;
                bHiZ = bestHiZ;
                System.arraycopy(facing, 0, bestFacing, 0, facing.length);
            }
        }
        d.byteViolBuf[0] = best;
        d.byteObjBuf[0] = screenObj;
        d.bytePhiBuf[0] = Math.toRadians(Angles.wrap(bPhi));
        d.byteLoXBuf[0] = bLoX;
        d.byteHiXBuf[0] = bHiX;
        d.byteLoZBuf[0] = bLoZ;
        d.byteHiZBuf[0] = bHiZ;
        return new double[]{best, Angles.wrap(bestTheta), screenObj};
    }

    private double evalAt(double theta, int outerCount) {
        int units = groupCount + 1;
        int airUnit = groupCount;
        int walls = d.byteWalls.size();
        ByteForward bf = d.bf;
        double refRad = Math.toRadians(Angles.wrap(theta));
        for (int u = 0; u < units; u++) facing[u] = refRad;
        double viol = Double.POSITIVE_INFINITY;
        double objBase = 0.0;
        for (int outer = 0; outer < outerCount; outer++) {
            for (int t = 0; t <= d.setupEnd; t++) bf.wrapped[t] = Angles.wrap(Math.toDegrees(facing[groupOfTick[t]]));
            double airDeg = Angles.wrap(Math.toDegrees(facing[airUnit]));
            for (int t = d.setupEnd + 1; t < bf.n; t++) bf.wrapped[t] = airDeg;
            bf.run();
            for (int wi = 0; wi < walls; wi++) {
                JumpConstraint wc = d.byteWalls.get(wi);
                shiftBase[wi] = wc.rhs - bf.wallPos(wc);
            }
            objBase = bf.pos(d.objTick, d.objAxisX);
            for (int u = 0; u < units; u++) {
                baseCos[u] = Math.cos(facing[u]);
                baseSin[u] = Math.sin(facing[u]);
                unitCos[u] = baseCos[u];
                unitSin[u] = baseSin[u];
            }
            System.arraycopy(shiftBase, 0, shiftAcc, 0, walls);
            for (int round = 0; round < ROUNDS; round++) {
                for (int u = 0; u < units; u++) {
                    double[] ua = unitA(u);
                    double[] ub = unitB(u);
                    double bc = baseCos[u];
                    double bs = baseSin[u];
                    for (int wi = 0; wi < walls; wi++) {
                        shiftNoU[wi] = shiftAcc[wi] + ua[wi] * (unitCos[u] - bc) + ub[wi] * (unitSin[u] - bs);
                    }
                    double bestPsi = Math.atan2(unitSin[u], unitCos[u]);
                    double bestV = descentViol(shiftNoU, ua, ub, unitCos[u] - bc, unitSin[u] - bs, false);
                    for (int kk = 0; kk < UNIT_STEPS; kk++) {
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
                    unitCos[u] = Math.cos(bestPsi);
                    unitSin[u] = Math.sin(bestPsi);
                    for (int wi = 0; wi < walls; wi++) {
                        shiftAcc[wi] = shiftNoU[wi] - ua[wi] * (unitCos[u] - bc) - ub[wi] * (unitSin[u] - bs);
                    }
                }
            }
            for (int u = 0; u < units; u++) facing[u] = Math.atan2(unitSin[u], unitCos[u]);
            viol = descentViol(shiftAcc, null, null, 0.0, 0.0, true);
        }
        double dp;
        if (d.maximize) dp = d.objAxisX ? Math.min(bestHiX, d.hiShiftX) : Math.min(bestHiZ, d.hiShiftZ);
        else dp = d.objAxisX ? Math.max(bestLoX, d.loShiftX) : Math.max(bestLoZ, d.loShiftZ);
        if (viol > 0.0) dp = 0.0;
        bestObj = objBase + dp;
        bestPhi = Math.toDegrees(facing[airUnit]);
        return viol;
    }

    private double[] unitA(int u) {
        return u < groupCount ? runA[u] : d.byteWallAirA;
    }

    private double[] unitB(int u) {
        return u < groupCount ? runB[u] : d.byteWallAirB;
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
