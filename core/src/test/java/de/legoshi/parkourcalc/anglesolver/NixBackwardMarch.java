package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixBackwardMarch {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;
    private final SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);

    private double[] dfsMaxSpeed, dfsGf, dfsYaws, dfsColdYaws;
    private double dfsTtol, dfsKstep, dfsThr, dfsStartPx, dfsStartPz, dfsBestSlack, dfsLambda, dfsVfac;
    private int dfsKn, dfsA, dfsN, dfsOrder;
    private boolean dfsCold;
    private long dfsCap, dfsNodes;
    private double[] dfsCurVx, dfsCurVz, dfsCurPx, dfsCurPz;
    private double[] dfsDeepVx, dfsDeepVz, dfsDeepPx, dfsDeepPz, dfsDeepYaw, dfsDeepWin;
    private int dfsDeepest;
    private double[] dfsXLoAt, dfsXHiAt, dfsMaxDispX;
    private int[] dfsNextXt;
    private boolean dfsLookahead;
    private boolean[][] dfsReach;
    private boolean dfsReachOn;
    private int dfsGN;
    private double dfsGRes, dfsGVMax;
    private double dtvVx, dtvVz;

    private void doTickVel(int t, double gfYaw, double vx, double vz) {
        if (Math.abs(vx) < dfsThr) vx = 0.0;
        if (Math.abs(vz) < dfsThr) vz = 0.0;
        float yawF = (float) gfYaw;
        int amp = full.factorAmpAt(t);
        double slipOv = full.slipAt(t);
        boolean contact = !Double.isNaN(slipOv);
        float slipF = contact ? (float) slipOv : Constants.SLIP_F;
        boolean isJumpTick = full.jumpAt(t) && contact;
        boolean sprint = full.sprintAt(t);
        boolean factorSprint = full.factorSprintAt(t);
        float f4, accelSpeed;
        if (contact) {
            f4 = slipF * 0.91F;
            float ground = 0.16277136F / (f4 * f4 * f4);
            accelSpeed = Constants.attrValueF(amp, sprint) * ground;
        } else {
            f4 = 0.91F;
            accelSpeed = factorSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        }
        if (isJumpTick && sprint) {
            float fj = yawF * (float) (Math.PI / 180.0);
            vx -= McSineTable.sinStep(fj) * 0.2F;
            vz += McSineTable.cosStep(fj) * 0.2F;
        }
        float forward = full.forwardAt(t);
        float strafe;
        if (full.strafeAt(t) && !isJumpTick) strafe = full.strafeSign * 1.0F * 0.98F;
        else strafe = full.strafeInputAt(t);
        float fm = strafe * strafe + forward * forward;
        if (fm >= 1.0E-4F) {
            fm = (float) Math.sqrt((double) fm);
            if (fm < 1.0F) fm = 1.0F;
            fm = accelSpeed / fm;
            float s = strafe * fm, fwd = forward * fm;
            float rad = yawF * (float) Math.PI / 180.0F;
            float sinD = McSineTable.sinStep(rad), cosD = McSineTable.cosStep(rad);
            vx += (double) (s * cosD - fwd * sinD);
            vz += (double) (fwd * cosD + s * sinD);
        }
        dtvVx = vx * (double) f4;
        dtvVz = vz * (double) f4;
    }

    private int gridIdx(double vx, double vz) {
        int i = (int) ((vx + dfsGVMax) / dfsGRes);
        int j = (int) ((vz + dfsGVMax) / dfsGRes);
        if (i < 0 || i >= dfsGN || j < 0 || j >= dfsGN) return -1;
        return i * dfsGN + j;
    }

    private boolean reachable(int t, double vx, double vz) {
        if (!dfsReachOn || dfsReach[t] == null) return true;
        int c = gridIdx(vx, vz);
        return c >= 0 && dfsReach[t][c];
    }

    @Test
    public void step0() throws Exception {
        String path = System.getenv("PKC_BM_FILE");
        org.junit.Assume.assumeTrue("set PKC_BM_FILE", path != null && !path.isEmpty());
        int a = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_TAKEOFF", "42"));
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        fullSpec = engine.debugBuildSpec();
        full = fullSpec.asScenario();
        int n = full.numTicks;
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        ForwardPath dp = model.forward(full, full.toGameFacings(Angles.wrapAll(dyaw)));
        double px = dp.posX[a], pz = dp.posZ[a], py = dp.posY[a];
        float seedYaw = (float) full.toGameFacings(Angles.wrapAll(dyaw))[a - 1];

        System.out.printf("=== NixBackwardMarch STEP 0 (%s): minimal landable takeoff velocity at t%d (vx,vz both free, position-free) ===%n",
                new File(path).getName(), a);
        System.out.printf("proven takeoff t%d: pos=(%.4f,%.4f) vel=(%.5f,%.5f)  |v|=%.5f%n",
                a, px, pz, dp.velX[a], dp.velZ[a], Math.hypot(dp.velX[a], dp.velZ[a]));
        System.out.println("  F=lands byte-exact (position-free), .=no.  rows=vz, cols=vx");

        double[] vxs = {0.02, 0.00, -0.03, -0.06, -0.10, -0.14, -0.18};
        double[] vzs = {0.220, 0.205, 0.190, 0.175, 0.160, 0.145, 0.130};
        System.out.print("           ");
        for (double vx : vxs) System.out.printf("vx%+.2f ", vx);
        System.out.println();
        double bestMag = Double.MAX_VALUE, bmvx = 0, bmvz = 0;
        for (double vz : vzs) {
            System.out.printf("  vz%+.3f : ", vz);
            for (double vx : vxs) {
                boolean lands = freeLands(a, n, px, py, pz, vx, vz, seedYaw);
                System.out.print(lands ? "  F    " : "  .    ");
                if (lands) {
                    double mag = Math.hypot(vx, vz);
                    if (mag < bestMag) { bestMag = mag; bmvx = vx; bmvz = vz; }
                }
            }
            System.out.println();
        }
        if (bestMag < Double.MAX_VALUE) {
            System.out.printf("%n=> minimal-|v| landable takeoff (on this grid): vel=(%.5f,%.5f) |v|=%.5f  (proven |v|=%.5f)%n",
                    bmvx, bmvz, bestMag, Math.hypot(dp.velX[a], dp.velZ[a]));
            System.out.println("   (grid is coarse; Step 1 backward march starts from this anchor state)");
        } else {
            System.out.println("no landable velocity found on the grid; widen the sweep");
        }
    }

    @Test
    public void step1() throws Exception {
        String path = System.getenv("PKC_BM_FILE");
        org.junit.Assume.assumeTrue("set PKC_BM_FILE", path != null && !path.isEmpty());
        org.junit.Assume.assumeTrue("set PKC_BM_STEP1", "1".equals(System.getenv("PKC_BM_STEP1")));
        int a = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_TAKEOFF", "42"));
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        fullSpec = engine.debugBuildSpec();
        full = fullSpec.asScenario();
        int n = full.numTicks;
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] gf = full.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(full, gf);
        double thr = model.inertiaThreshold();

        System.out.printf("=== NixBackwardMarch STEP 1 PART A (%s): backward-inversion self-check, anchor t%d -> 0 ===%n",
                new File(path).getName(), a);
        System.out.printf("anchor t%d (proven): pos=(%.6f,%.6f) vel=(%.6f,%.6f)%n",
                a, dp.posX[a], dp.posZ[a], dp.velX[a], dp.velZ[a]);
        System.out.println("  chaining the inverse from the true anchor state; drift = |inverted - gate(true)| accumulated backward");

        double cpx = dp.posX[a], cpz = dp.posZ[a], cvx = dp.velX[a], cvz = dp.velZ[a];
        double maxPosDrift = 0.0, maxVelDrift = 0.0, maxRoundTrip = 0.0;
        int maxPosT = a, maxVelT = a, maxRtT = a;
        for (int t = a - 1; t >= 0; t--) {
            double nPx = cpx, nPz = cpz, nVx = cvx, nVz = cvz;
            double[] prev = invertTick(t, gf[t], cpx, cpz, cvx, cvz);
            double[] rt = forwardOneTick(t, prev[0], prev[1], prev[2], prev[3], gf[t]);
            double rtErr = Math.max(Math.max(Math.abs(rt[0] - nPx), Math.abs(rt[1] - nPz)),
                    Math.max(Math.abs(rt[2] - nVx), Math.abs(rt[3] - nVz)));
            if (rtErr > maxRoundTrip) { maxRoundTrip = rtErr; maxRtT = t; }
            cpx = prev[0]; cpz = prev[1]; cvx = prev[2]; cvz = prev[3];
            boolean gate = "1".equals(System.getenv("PKC_BM_GATE"));
            if (gate) {
                if (Math.abs(cvx) < thr) cvx = 0.0;
                if (Math.abs(cvz) < thr) cvz = 0.0;
            }
            double truePx = dp.posX[t], truePz = dp.posZ[t];
            double trueVxG = Math.abs(dp.velX[t]) < thr ? 0.0 : dp.velX[t];
            double trueVzG = Math.abs(dp.velZ[t]) < thr ? 0.0 : dp.velZ[t];
            double posD = Math.max(Math.abs(cpx - truePx), Math.abs(cpz - truePz));
            double velD = Math.max(Math.abs(cvx - trueVxG), Math.abs(cvz - trueVzG));
            if (posD > maxPosDrift) { maxPosDrift = posD; maxPosT = t; }
            if (velD > maxVelDrift) { maxVelDrift = velD; maxVelT = t; }
            if ((t >= 27 && t <= 32) || t <= 2 || velD > 1e-6 || posD > 1e-6) {
                System.out.printf("  t%02d inv=(%.6f,%.6f | %.6f,%.6f) true=(%.6f,%.6f | %.6f,%.6f) posD=%.2e velD=%.2e%n",
                        t, cpx, cpz, cvx, cvz, truePx, truePz, dp.velX[t], dp.velZ[t], posD, velD);
            }
        }
        System.out.printf("%n=> max accumulated drift over t%d->0:  pos=%.3e (t%d)   vel=%.3e (t%d)   [tail razor ~2e-5]%n",
                a, maxPosDrift, maxPosT, maxVelDrift, maxVelT);
        System.out.printf("=> max per-tick round-trip error forward(invert(state))-state: %.3e (t%d)  [~0 => inverse is exact, blowup is the gate]%n",
                maxRoundTrip, maxRtT);
        boolean rest = Math.abs(cvx) < thr && Math.abs(cvz) < thr;
        System.out.printf("=> inverted t0 carried vel=(%.6f,%.6f)  |  reaches gate band (rest): %s%n",
                cvx, cvz, rest ? "YES" : "NO");
        System.out.printf("   inverted t0 pos=(%.6f,%.6f)  true t0 pos=(%.6f,%.6f)%n",
                cpx, cpz, dp.posX[0], dp.posZ[0]);
    }

    private static final class BNode {
        final double px, pz, vx, vz;
        final double txLo, txHi, tzLo, tzHi;
        final BNode parent;
        final double yaw;
        final int tick;
        BNode(double px, double pz, double vx, double vz, double txLo, double txHi, double tzLo, double tzHi,
              BNode parent, double yaw, int tick) {
            this.px = px; this.pz = pz; this.vx = vx; this.vz = vz;
            this.txLo = txLo; this.txHi = txHi; this.tzLo = tzLo; this.tzHi = tzHi;
            this.parent = parent; this.yaw = yaw; this.tick = tick;
        }
    }

    private double[] intersectTWindow(double txLo, double txHi, double tzLo, double tzHi, int t, double px, double pz, double tol) {
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.t1 != t || c.t2 != null) continue;
            boolean isX = c.mode == JumpConstraint.Mode.X;
            if (c.mode == JumpConstraint.Mode.F) continue;
            double pos = isX ? px : pz;
            double bound = c.rhs - pos;
            if (c.cmp == JumpConstraint.Cmp.GE) {
                if (isX) txLo = Math.max(txLo, bound - tol); else tzLo = Math.max(tzLo, bound - tol);
            } else if (c.cmp == JumpConstraint.Cmp.LE) {
                if (isX) txHi = Math.min(txHi, bound + tol); else tzHi = Math.min(tzHi, bound + tol);
            }
        }
        if (txLo > txHi || tzLo > tzHi) return null;
        return new double[]{txLo, txHi, tzLo, tzHi};
    }

    @Test
    public void step2() throws Exception {
        String path = System.getenv("PKC_BM_FILE");
        org.junit.Assume.assumeTrue("set PKC_BM_FILE", path != null && !path.isEmpty());
        org.junit.Assume.assumeTrue("set PKC_BM_STEP2", "1".equals(System.getenv("PKC_BM_STEP2")));
        int a = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_TAKEOFF", "42"));
        int kn = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_KN", "0"));
        double kstep = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_KSTEP", "5"));
        int cap = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_CAP", "300000"));
        double ctol = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_CTOL", "0.1"));
        double ddisp = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_DDISP", "0"));
        double ttol = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_TTOL", "0.005"));
        double vres = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_VRES", "6e-4"));
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        fullSpec = engine.debugBuildSpec();
        full = fullSpec.asScenario();
        int n = full.numTicks;
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] gf = full.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(full, gf);
        double thr = model.inertiaThreshold();

        System.out.printf("=== NixBackwardMarch STEP 2 (%s): PURE v=0 velocity march from t%d anchor (WARM anchor) ===%n",
                new File(path).getName(), a);
        System.out.printf("anchor t%d: vel=(%.5f,%.5f)   actionSet=proven+/-%d*%.1fdeg  2-D vel dedup + reachability prune  cap=%d%n",
                a, dp.velX[a], dp.velZ[a], kn, kstep, cap);

        double[] maxSpeed = new double[n + 1];
        double vAcc = 0.0;
        for (int t = 0; t < n; t++) {
            double[] af = accelF4(t);
            vAcc = (vAcc + af[0] + af[2]) * af[1];
            maxSpeed[t + 1] = vAcc;
        }

        List<BNode> frontier = new ArrayList<>();
        double[] aw = intersectTWindow(-1e18, 1e18, -1e18, 1e18, a, dp.posX[a], dp.posZ[a], ttol);
        if (aw == null) aw = new double[]{-1e18, 1e18, -1e18, 1e18};
        frontier.add(new BNode(dp.posX[a], dp.posZ[a], dp.velX[a], dp.velZ[a], aw[0], aw[1], aw[2], aw[3], null, Double.NaN, a));
        List<BNode> restNodes = new ArrayList<>();
        long prunedReach = 0, prunedTwin = 0;
        for (int t = a - 1; t >= 0; t--) {
            java.util.HashMap<String, BNode> grid = new java.util.HashMap<>();
            outer:
            for (BNode nd : frontier) {
                for (double y : actionSet(gf[t], kn, kstep)) {
                    double[] prev = invertTick(t, y, nd.px, nd.pz, nd.vx, nd.vz);
                    double px = prev[0], pz = prev[1], vx = prev[2], vz = prev[3];
                    if (Math.abs(vx) < thr) vx = 0.0;
                    if (Math.abs(vz) < thr) vz = 0.0;
                    if (Math.hypot(vx, vz) > maxSpeed[t] + 1e-6) { prunedReach++; continue; }
                    double[] win = intersectTWindow(nd.txLo, nd.txHi, nd.tzLo, nd.tzHi, t, px, pz, ttol);
                    if (win == null) { prunedTwin++; continue; }
                    String key;
                    if (vres <= 0.0) {
                        key = Double.doubleToLongBits(vx) + "," + Double.doubleToLongBits(vz)
                                + "," + Double.doubleToLongBits(px) + "," + Double.doubleToLongBits(pz);
                    } else if (ddisp > 0.0) {
                        key = Math.round(vx / vres) + "," + Math.round(vz / vres)
                                + "," + Math.round(win[0] / ddisp) + "," + Math.round(win[1] / ddisp)
                                + "," + Math.round(win[2] / ddisp) + "," + Math.round(win[3] / ddisp);
                    } else {
                        key = Math.round(vx / vres) + "," + Math.round(vz / vres);
                    }
                    BNode ex = grid.get(key);
                    double candW = Math.min(win[1] - win[0], win[3] - win[2]);
                    if (ex == null || candW > Math.min(ex.txHi - ex.txLo, ex.tzHi - ex.tzLo)) {
                        grid.put(key, new BNode(px, pz, vx, vz, win[0], win[1], win[2], win[3], nd, y, t));
                    }
                    if (grid.size() >= cap) break outer;
                }
            }
            frontier = new ArrayList<>(grid.values());
            if (t % 5 == 0 || t >= a - 2 || t <= 2) {
                System.out.printf("  t%02d frontier=%d (maxSpeed[t]=%.4f)%n", t, frontier.size(), maxSpeed[t]);
            }
            if (frontier.isEmpty()) { System.out.printf("  frontier EMPTY at t%d%n", t); break; }
            if (t == 0) {
                for (BNode nd : frontier) if (Math.abs(nd.vx) < thr && Math.abs(nd.vz) < thr) restNodes.add(nd);
            }
        }
        System.out.printf("  (reachability-pruned %d, translation-window-pruned %d)%n", prunedReach, prunedTwin);

        System.out.printf("%n=> rest-reaching (both-band at t0) nodes: %d%n", restNodes.size());
        int feasibleCount = 0, shown = 0;
        double bestWidth = -1;
        for (BNode r : restNodes) {
            double[] yseq = new double[n];
            BNode cur = r;
            while (cur != null && cur.tick < a) { yseq[cur.tick] = cur.yaw; cur = cur.parent; }
            for (int t = a; t < n; t++) yseq[t] = gf[t];
            JumpPhysicsInputs sc = cloneStart(full, r.px, r.pz);
            ForwardPath p = model.forward(sc, yseq);
            double dxLo = -1e18, dxHi = 1e18, dzLo = -1e18, dzHi = 1e18;
            for (JumpConstraint c : fullSpec.constraints) {
                boolean isX = c.mode == JumpConstraint.Mode.X;
                if (c.mode == JumpConstraint.Mode.F) continue;
                double bound = c.rhs - (isX ? p.posX[c.t1] : p.posZ[c.t1]);
                if (c.cmp == JumpConstraint.Cmp.GE) { if (isX) dxLo = Math.max(dxLo, bound); else dzLo = Math.max(dzLo, bound); }
                else if (c.cmp == JumpConstraint.Cmp.LE) { if (isX) dxHi = Math.min(dxHi, bound); else dzHi = Math.min(dzHi, bound); }
            }
            boolean feasible = dxLo <= dxHi + 1e-9 && dzLo <= dzHi + 1e-9;
            double closest = Math.min(dxHi - dxLo, dzHi - dzLo);
            if (closest > bestWidth) bestWidth = closest;
            if (feasible) feasibleCount++;
            if (feasible || shown < 6) {
                shown++;
                System.out.printf("  rest start=(%.5f,%.5f) transWindow dx[%.5f,%.5f] dz[%.5f,%.5f]  %s%n",
                        r.px, r.pz, dxLo, dxHi, dzLo, dzHi, feasible ? "FEASIBLE (viol=0 under one translation)" : "-");
                if (feasible) {
                    double dx = 0.5 * (dxLo + dxHi), dz = 0.5 * (dzLo + dzHi);
                    System.out.printf("     *** rest->land closes: start=(%.5f,%.5f) land@%d=(%.5f,%.5f) ***%n",
                            r.px + dx, r.pz + dz, n, p.posX[n] + dx, p.posZ[n] + dz);
                }
            }
        }
        System.out.printf("=> feasible-translation rest nodes: %d / %d   (best window slack over infeasible: %.2e)%n",
                feasibleCount, restNodes.size(), bestWidth);
        if (restNodes.isEmpty()) System.out.println("   (no rest node; widen action set or check prune)");
    }

    @Test
    public void step3() throws Exception {
        String path = System.getenv("PKC_BM_FILE");
        org.junit.Assume.assumeTrue("set PKC_BM_FILE", path != null && !path.isEmpty());
        org.junit.Assume.assumeTrue("set PKC_BM_STEP3", "1".equals(System.getenv("PKC_BM_STEP3")));
        int a = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_TAKEOFF", "54"));
        dfsKn = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_KN", "8"));
        dfsKstep = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_KSTEP", "0.5"));
        dfsCap = Long.parseLong(System.getenv().getOrDefault("PKC_BM_CAP", "5000000"));
        dfsTtol = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_TTOL", "0.005"));
        dfsOrder = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_ORDER", "1"));
        dfsLambda = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_LAMBDA", "1.0"));
        dfsVfac = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_VFAC", "1.0"));
        dfsCold = "1".equals(System.getenv("PKC_BM_COLD"));
        int nyaw = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_NYAW", "360"));
        if (dfsCold) {
            dfsColdYaws = new double[nyaw];
            for (int i = 0; i < nyaw; i++) dfsColdYaws[i] = i * 360.0 / nyaw;
        }
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        fullSpec = engine.debugBuildSpec();
        full = fullSpec.asScenario();
        int n = full.numTicks;
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] gf = full.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(full, gf);
        dfsThr = model.inertiaThreshold();
        dfsN = n; dfsA = a; dfsGf = gf; dfsYaws = new double[n];
        dfsCurVx = new double[n + 1]; dfsCurVz = new double[n + 1]; dfsCurPx = new double[n + 1]; dfsCurPz = new double[n + 1];
        dfsDeepVx = new double[n + 1]; dfsDeepVz = new double[n + 1]; dfsDeepPx = new double[n + 1]; dfsDeepPz = new double[n + 1];
        dfsDeepYaw = new double[n]; dfsDeepWin = new double[4]; dfsDeepest = a + 1;
        dfsMaxSpeed = new double[n + 1];
        double vAcc = 0.0;
        for (int t = 0; t < n; t++) { double[] af = accelF4(t); vAcc = (vAcc + af[0] + af[2]) * af[1]; dfsMaxSpeed[t + 1] = vAcc; }
        double maxRatio = 0; int maxRatioT = 0;
        for (int t = 0; t <= a; t++) {
            double r = Math.hypot(dp.velX[t], dp.velZ[t]) / Math.max(dfsMaxSpeed[t], 1e-9);
            if (r > maxRatio) { maxRatio = r; maxRatioT = t; }
        }
        System.out.printf("proven |v|/maxSpeed: max ratio=%.4f at t%d  (VFAC must be >= this to keep the proven path)%n", maxRatio, maxRatioT);

        dfsReachOn = "1".equals(System.getenv("PKC_BM_REACH"));
        dfsGRes = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_GRES", "0.002"));
        dfsGVMax = Double.parseDouble(System.getenv().getOrDefault("PKC_BM_GVMAX", "0.45"));
        int reachNyaw = Integer.parseInt(System.getenv().getOrDefault("PKC_BM_RYAW", "360"));
        boolean dilate = !"0".equals(System.getenv().getOrDefault("PKC_BM_DILATE", "1"));
        if (dfsReachOn) {
            dfsGN = (int) Math.round(2 * dfsGVMax / dfsGRes);
            dfsReach = new boolean[a + 1][];
            double[] ry = new double[reachNyaw];
            for (int i = 0; i < reachNyaw; i++) ry[i] = i * 360.0 / reachNyaw;
            dfsReach[0] = new boolean[dfsGN * dfsGN];
            java.util.List<Integer> cur = new java.util.ArrayList<>();
            for (int i = 0; i < dfsGN; i++) for (int j = 0; j < dfsGN; j++) {
                double vx = -dfsGVMax + (i + 0.5) * dfsGRes, vz = -dfsGVMax + (j + 0.5) * dfsGRes;
                if (Math.abs(vx) < dfsThr && Math.abs(vz) < dfsThr) { dfsReach[0][i * dfsGN + j] = true; cur.add(i * dfsGN + j); }
            }
            long tR = System.nanoTime();
            for (int t = 0; t < a; t++) {
                dfsReach[t + 1] = new boolean[dfsGN * dfsGN];
                java.util.List<Integer> next = new java.util.ArrayList<>();
                for (int c : cur) {
                    double vx0 = -dfsGVMax + (c / dfsGN + 0.5) * dfsGRes, vz0 = -dfsGVMax + (c % dfsGN + 0.5) * dfsGRes;
                    for (double y : ry) {
                        doTickVel(t, y, vx0, vz0);
                        int i = (int) ((dtvVx + dfsGVMax) / dfsGRes), j = (int) ((dtvVz + dfsGVMax) / dfsGRes);
                        int r = dilate ? 1 : 0;
                        for (int di = -r; di <= r; di++) for (int dj = -r; dj <= r; dj++) {
                            int ii = i + di, jj = j + dj;
                            if (ii < 0 || ii >= dfsGN || jj < 0 || jj >= dfsGN) continue;
                            int cc = ii * dfsGN + jj;
                            if (!dfsReach[t + 1][cc]) { dfsReach[t + 1][cc] = true; next.add(cc); }
                        }
                    }
                }
                cur = next;
            }
            int miss = 0;
            double worstProven = 0;
            for (int t = 0; t <= a; t++) {
                if (!reachable(t, dp.velX[t], dp.velZ[t])) { miss++; worstProven = Math.max(worstProven, Math.hypot(dp.velX[t], dp.velZ[t])); }
            }
            System.out.printf("reachability precompute: GN=%d res=%.4f ryaw=%d dilate=%s last-cells=%d  %.1fs%n",
                    dfsGN, dfsGRes, reachNyaw, dilate, cur.size(), (System.nanoTime() - tR) / 1e9);
            System.out.printf("  SOUNDNESS: proven trajectory velocities in reachable set: %d/%d MISSES (must be 0; else bound false-prunes truth)%n", miss, a + 1);
        }

        dfsLookahead = "1".equals(System.getenv("PKC_BM_LOOKAHEAD"));
        dfsXLoAt = new double[n + 1]; dfsXHiAt = new double[n + 1];
        java.util.Arrays.fill(dfsXLoAt, Double.NaN); java.util.Arrays.fill(dfsXHiAt, Double.NaN);
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.X && c.t2 == null && c.t1 <= n) {
                if (c.cmp == JumpConstraint.Cmp.GE) dfsXLoAt[c.t1] = c.rhs;
                else if (c.cmp == JumpConstraint.Cmp.LE) dfsXHiAt[c.t1] = c.rhs;
            }
        }
        dfsNextXt = new int[n + 1]; dfsMaxDispX = new double[n + 1];
        for (int cur = 0; cur <= a; cur++) {
            int tp = -1;
            for (int t = cur - 1; t >= 0; t--) if (!Double.isNaN(dfsXLoAt[t]) || !Double.isNaN(dfsXHiAt[t])) { tp = t; break; }
            dfsNextXt[cur] = tp;
            double d = 0; if (tp >= 0) for (int k = tp; k < cur; k++) d += dfsMaxSpeed[k];
            dfsMaxDispX[cur] = d;
        }

        double[] aw = intersectTWindow(-1e18, 1e18, -1e18, 1e18, a, dp.posX[a], dp.posZ[a], dfsTtol);
        if (aw == null) aw = new double[]{-1e18, 1e18, -1e18, 1e18};

        System.out.printf("=== NixBackwardMarch STEP 3 (%s): byte-exact guided DFS backward from t%d ===%n",
                new File(path).getName(), a);
        System.out.printf("anchor vel=(%.5f,%.5f)  actionSet=%s  ttol=%.4f  order=%s  nodeCap=%d  (no dedup)%n",
                dp.velX[a], dp.velZ[a],
                dfsCold ? ("COLD uniform " + (dfsColdYaws == null ? 0 : dfsColdYaws.length) + " yaws") : ("proven+/-" + dfsKn + "*" + dfsKstep + "deg"),
                dfsTtol, dfsOrder == 0 ? "proven-close" : dfsOrder == 2 ? "min-vel" : dfsOrder == 3 ? ("window-" + dfsLambda + "*vel") : "widest-window", dfsCap);

        dfsNodes = 0; dfsBestSlack = -1e18;
        long tStart = System.nanoTime();
        boolean found = dfs(a, dp.posX[a], dp.posZ[a], dp.velX[a], dp.velZ[a], aw[0], aw[1], aw[2], aw[3]);
        double secs = (System.nanoTime() - tStart) / 1e9;
        System.out.printf("nodes=%d  time=%.1fs  found=%s%n", dfsNodes, secs, found);
        if (found) {
            double[] yseq = new double[n];
            System.arraycopy(dfsYaws, 0, yseq, 0, a);
            for (int t = a; t < n; t++) yseq[t] = gf[t];
            ForwardPath p = model.forward(cloneStart(full, dfsStartPx, dfsStartPz), yseq);
            double dxLo = -1e18, dxHi = 1e18, dzLo = -1e18, dzHi = 1e18;
            for (JumpConstraint c : fullSpec.constraints) {
                if (c.mode == JumpConstraint.Mode.F) continue;
                boolean isX = c.mode == JumpConstraint.Mode.X;
                double bound = c.rhs - (isX ? p.posX[c.t1] : p.posZ[c.t1]);
                if (c.cmp == JumpConstraint.Cmp.GE) { if (isX) dxLo = Math.max(dxLo, bound); else dzLo = Math.max(dzLo, bound); }
                else if (c.cmp == JumpConstraint.Cmp.LE) { if (isX) dxHi = Math.min(dxHi, bound); else dzHi = Math.min(dzHi, bound); }
            }
            double dx = 0.5 * (dxLo + dxHi), dz = 0.5 * (dzLo + dzHi);
            System.out.printf("*** SOLVED byte-exact: start=(%.5f,%.5f) land@%d=(%.5f,%.5f) transWindow dx[%.5f,%.5f] dz[%.5f,%.5f] ***%n",
                    dfsStartPx + dx, dfsStartPz + dz, n, p.posX[n] + dx, p.posZ[n] + dz, dxLo, dxHi, dzLo, dzHi);
        } else {
            System.out.printf("no exactly-feasible rest path within budget; best exact window slack seen=%.3e%n", dfsBestSlack);
            System.out.printf("%n--- deepest descent: reached t%d (=%d ticks back from t%d); rest needs t0 ---%n",
                    dfsDeepest, a - dfsDeepest, a);
            int fromT = Math.min(a, dfsDeepest + 16);
            for (int t = fromT; t >= dfsDeepest; t--) {
                String yaw = t < a ? String.format("yaw=%.3f", dfsDeepYaw[t]) : "(anchor)";
                double sp = Math.hypot(dfsDeepVx[t], dfsDeepVz[t]);
                System.out.printf("  t%02d vel=(%+.5f,%+.5f) |v|=%.5f<=max%.5f pos=(%.4f,%.4f) %s%n",
                        t, dfsDeepVx[t], dfsDeepVz[t], sp, dfsMaxSpeed[t], dfsDeepPx[t], dfsDeepPz[t], yaw);
            }
            if (dfsDeepest > 0) {
                int t = dfsDeepest - 1;
                double px = dfsDeepPx[dfsDeepest], pz = dfsDeepPz[dfsDeepest], vx = dfsDeepVx[dfsDeepest], vz = dfsDeepVz[dfsDeepest];
                double[] ys = dfsCold ? dfsColdYaws : actionSet(dfsGf[t], dfsKn, dfsKstep);
                int reach = 0, winp = 0, surv = 0;
                double bestW = -1e18, bestSp = Double.NaN, actualMinV = 1e18, actualMaxV = 0, argMinYaw = Double.NaN;
                for (double y : ys) {
                    double[] prev = invertTick(t, y, px, pz, vx, vz);
                    double pvx = prev[2], pvz = prev[3];
                    if (Math.abs(pvx) < dfsThr) pvx = 0.0;
                    if (Math.abs(pvz) < dfsThr) pvz = 0.0;
                    double sp = Math.hypot(pvx, pvz);
                    if (sp < actualMinV) { actualMinV = sp; argMinYaw = y; }
                    if (sp > actualMaxV) actualMaxV = sp;
                    if (sp > dfsMaxSpeed[t] + 1e-6) { reach++; continue; }
                    double[] w = intersectTWindow(dfsDeepWin[0], dfsDeepWin[1], dfsDeepWin[2], dfsDeepWin[3], t, prev[0], prev[1], dfsTtol);
                    if (w == null) { winp++; continue; }
                    surv++;
                    double ww = Math.min(w[1] - w[0], w[3] - w[2]);
                    if (ww > bestW) { bestW = ww; bestSp = Math.hypot(pvx, pvz); }
                }
                System.out.printf("why stuck at t%d: of %d yaws -> %d reachability-pruned (|v|>maxSpeed=%.5f), %d window-pruned, %d survivors%n",
                        t, ys.length, reach, dfsMaxSpeed[t], winp, surv);
                if (surv > 0) System.out.printf("   (survivors exist; deepest is budget-limited, best-survivor |v|=%.5f) -> node cap, not a dead end%n", bestSp);
                else System.out.printf("   (0 survivors -> genuine dead end here: no yaw keeps |v| reachable AND the block window non-empty)%n");

                double[] af = accelF4(t);
                double vIn = Math.hypot(vx, vz);
                double diskMin = Math.max(0.0, vIn / af[1] - af[0] - af[2]);
                boolean isJump = full.jumpAt(t) && !Double.isNaN(full.slipAt(t));
                System.out.printf("%n=== CONVEX-DUAL COLLAPSE TRACE at t%d (jumpTick=%s, f4=%.5f, accelSpeed=%.5f, jumpBoost=%.3f) ===%n",
                        t, isJump, af[1], af[0], af[2]);
                System.out.printf("  incoming |v[%d]|=%.6f  (maxSpeed[%d]=%.6f, ratio %.4f)%n", dfsDeepest, vIn, dfsDeepest, dfsMaxSpeed[dfsDeepest], vIn / dfsMaxSpeed[dfsDeepest]);
                System.out.printf("  ACTUAL model (accel+jump BOTH tied to one yaw): min |v[%d]|=%.6f (at yaw=%.2f), max=%.6f  -> %s vs maxSpeed[%d]=%.6f%n",
                        t, actualMinV, argMinYaw, actualMaxV, actualMinV <= dfsMaxSpeed[t] + 1e-6 ? "PASSES" : "DEAD END", t, dfsMaxSpeed[t]);
                System.out.printf("  CONVEX disk relax (accel,jump free INDEPENDENT dirs): min |v[%d]|=|v_in|/f4 - accelSpeed - jumpBoost = %.6f -> %s%n",
                        t, diskMin, diskMin <= dfsMaxSpeed[t] + 1e-6 ? "FEASIBLE (would NOT prune)" : "infeasible");
                System.out.printf("  NON-CONVEXITY GAP = actualMin - diskMin = %.6f  (disk under-estimates by this; that gap is why the convex bound can't see the wall)%n",
                        actualMinV - diskMin);
            }
        }
    }

    private boolean dfs(int cur, double px, double pz, double vx, double vz,
                        double txLo, double txHi, double tzLo, double tzHi) {
        if (++dfsNodes > dfsCap) return false;
        dfsCurVx[cur] = vx; dfsCurVz[cur] = vz; dfsCurPx[cur] = px; dfsCurPz[cur] = pz;
        if (cur < dfsDeepest) {
            dfsDeepest = cur;
            for (int k = cur; k <= dfsA; k++) { dfsDeepVx[k] = dfsCurVx[k]; dfsDeepVz[k] = dfsCurVz[k]; dfsDeepPx[k] = dfsCurPx[k]; dfsDeepPz[k] = dfsCurPz[k]; }
            for (int k = cur; k < dfsA; k++) dfsDeepYaw[k] = dfsYaws[k];
            dfsDeepWin[0] = txLo; dfsDeepWin[1] = txHi; dfsDeepWin[2] = tzLo; dfsDeepWin[3] = tzHi;
        }
        if (cur == 0) {
            if (Math.abs(vx) < dfsThr && Math.abs(vz) < dfsThr) {
                dfsStartPx = px; dfsStartPz = pz;
                double slack = exactVerify(px, pz);
                if (slack > dfsBestSlack) dfsBestSlack = slack;
                return slack >= -1e-9;
            }
            return false;
        }
        int t = cur - 1;
        double[] ys = dfsCold ? dfsColdYaws : actionSet(dfsGf[t], dfsKn, dfsKstep);
        List<double[]> surv = new ArrayList<>();
        for (double y : ys) {
            double[] prev = invertTick(t, y, px, pz, vx, vz);
            double ppx = prev[0], ppz = prev[1], pvx = prev[2], pvz = prev[3];
            if (Math.abs(pvx) < dfsThr) pvx = 0.0;
            if (Math.abs(pvz) < dfsThr) pvz = 0.0;
            if (Math.hypot(pvx, pvz) > dfsMaxSpeed[t] * dfsVfac + 1e-6) continue;
            if (!reachable(t, pvx, pvz)) continue;
            double[] win = intersectTWindow(txLo, txHi, tzLo, tzHi, t, ppx, ppz, dfsTtol);
            if (win == null) continue;
            if (dfsLookahead) {
                int tp = dfsNextXt[t];
                if (tp >= 0) {
                    double lo = Double.isNaN(dfsXLoAt[tp]) ? -1e18 : dfsXLoAt[tp];
                    double hi = Double.isNaN(dfsXHiAt[tp]) ? 1e18 : dfsXHiAt[tp];
                    double d = dfsMaxDispX[t];
                    double ftxLo = lo - (ppx + d), ftxHi = hi - (ppx - d);
                    if (Math.max(win[0], ftxLo) > Math.min(win[1], ftxHi) + 1e-12) continue;
                }
            }
            double rank;
            if (dfsOrder == 0) rank = -Math.abs(y - dfsGf[t]);
            else if (dfsOrder == 2) rank = -Math.hypot(pvx, pvz);
            else if (dfsOrder == 3) rank = Math.min(win[1] - win[0], win[3] - win[2]) - dfsLambda * Math.hypot(pvx, pvz);
            else if (dfsOrder == 4) rank = -Math.abs(pvx);
            else if (dfsOrder == 5) rank = Math.min(win[1] - win[0], win[3] - win[2]) - dfsLambda * Math.abs(pvx);
            else rank = Math.min(win[1] - win[0], win[3] - win[2]);
            surv.add(new double[]{rank, ppx, ppz, pvx, pvz, win[0], win[1], win[2], win[3], y});
        }
        surv.sort((p, q) -> Double.compare(q[0], p[0]));
        for (double[] s : surv) {
            dfsYaws[t] = s[9];
            if (dfs(t, s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8])) return true;
            if (dfsNodes > dfsCap) return false;
        }
        return false;
    }

    private double exactVerify(double startPx, double startPz) {
        double[] yseq = new double[dfsN];
        System.arraycopy(dfsYaws, 0, yseq, 0, dfsA);
        for (int t = dfsA; t < dfsN; t++) yseq[t] = dfsGf[t];
        ForwardPath p = model.forward(cloneStart(full, startPx, startPz), yseq);
        double dxLo = -1e18, dxHi = 1e18, dzLo = -1e18, dzHi = 1e18;
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            boolean isX = c.mode == JumpConstraint.Mode.X;
            double bound = c.rhs - (isX ? p.posX[c.t1] : p.posZ[c.t1]);
            if (c.cmp == JumpConstraint.Cmp.GE) { if (isX) dxLo = Math.max(dxLo, bound); else dzLo = Math.max(dzLo, bound); }
            else if (c.cmp == JumpConstraint.Cmp.LE) { if (isX) dxHi = Math.min(dxHi, bound); else dzHi = Math.min(dzHi, bound); }
        }
        return Math.min(dxHi - dxLo, dzHi - dzLo);
    }

    private static double[] actionSet(double provenYaw, int kn, double kstep) {
        double[] out = new double[2 * kn + 1];
        out[0] = provenYaw;
        for (int i = 1; i <= kn; i++) { out[2 * i - 1] = provenYaw + i * kstep; out[2 * i] = provenYaw - i * kstep; }
        return out;
    }

    private double[] accelF4(int t) {
        int amp = full.factorAmpAt(t);
        double slipOv = full.slipAt(t);
        boolean contact = !Double.isNaN(slipOv);
        float slipF = contact ? (float) slipOv : Constants.SLIP_F;
        boolean sprint = full.sprintAt(t);
        boolean factorSprint = full.factorSprintAt(t);
        float f4, accelSpeed;
        if (contact) {
            f4 = slipF * 0.91F;
            float ground = 0.16277136F / (f4 * f4 * f4);
            accelSpeed = Constants.attrValueF(amp, sprint) * ground;
        } else {
            f4 = 0.91F;
            accelSpeed = factorSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        }
        double jumpBoost = (full.jumpAt(t) && contact && sprint) ? 0.2 : 0.0;
        return new double[]{accelSpeed, f4, jumpBoost};
    }

    private boolean violatesConsAt(int t, double px, double pz, double tol) {
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.t1 != t || c.t2 != null) continue;
            double val = c.mode == JumpConstraint.Mode.X ? px : (c.mode == JumpConstraint.Mode.Z ? pz : Double.NaN);
            if (Double.isNaN(val)) continue;
            if (c.cmp == JumpConstraint.Cmp.GE && val < c.rhs - tol) return true;
            if (c.cmp == JumpConstraint.Cmp.LE && val > c.rhs + tol) return true;
        }
        return false;
    }

    private static JumpPhysicsInputs cloneStart(JumpPhysicsInputs f, double px, double pz) {
        JumpPhysicsInputs p = new JumpPhysicsInputs(f.numTicks);
        p.startPos = new Vec3dCore(px, f.startPos.y, pz);
        p.initialVelocity = Vec3dCore.ZERO;
        p.startYaw = f.startYaw;
        p.strafeSign = f.strafeSign;
        p.jumpPerTick = f.jumpPerTick;
        p.strafePerTick = f.strafePerTick;
        p.yawLockedPerTick = f.yawLockedPerTick;
        p.speedAmplifier = f.speedAmplifier;
        p.slipPerTick = f.slipPerTick;
        p.sprintPerTick = f.sprintPerTick;
        p.forwardInputPerTick = f.forwardInputPerTick;
        p.strafeInputPerTick = f.strafeInputPerTick;
        return p;
    }

    private double[] forwardOneTick(int t, double px, double pz, double vx, double vz, double gfYaw) {
        JumpPhysicsInputs sc = sliceScenario(full, t, t + 1, new Vec3dCore(px, 0.0, pz), new Vec3dCore(vx, 0.0, vz), (float) gfYaw);
        ForwardPath p = model.forward(sc, new double[]{gfYaw});
        return new double[]{p.posX[1], p.posZ[1], p.velX[1], p.velZ[1]};
    }

    private double[] invertTick(int t, double gfYaw, double nextPx, double nextPz, double nextVx, double nextVz) {
        float yawF = (float) gfYaw;
        int amp = full.factorAmpAt(t);
        double slipOv = full.slipAt(t);
        boolean contact = !Double.isNaN(slipOv);
        float slipF = contact ? (float) slipOv : Constants.SLIP_F;
        boolean isJumpTick = full.jumpAt(t) && contact;
        boolean sprint = full.sprintAt(t);
        boolean factorSprint = full.factorSprintAt(t);
        float f4;
        float accelSpeed;
        if (contact) {
            f4 = slipF * 0.91F;
            float ground = 0.16277136F / (f4 * f4 * f4);
            accelSpeed = Constants.attrValueF(amp, sprint) * ground;
        } else {
            f4 = 0.91F;
            accelSpeed = factorSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        }
        double vxa = nextVx / (double) f4;
        double vza = nextVz / (double) f4;
        double prevPx = nextPx - vxa;
        double prevPz = nextPz - vza;
        float forward = full.forwardAt(t);
        float strafe;
        if (full.strafeAt(t) && !isJumpTick) {
            strafe = full.strafeSign * 1.0F * 0.98F;
        } else {
            strafe = full.strafeInputAt(t);
        }
        double accelX = 0.0, accelZ = 0.0;
        float fm = strafe * strafe + forward * forward;
        if (fm >= 1.0E-4F) {
            fm = (float) Math.sqrt((double) fm);
            if (fm < 1.0F) fm = 1.0F;
            fm = accelSpeed / fm;
            float s = strafe * fm;
            float fwd = forward * fm;
            float rad = yawF * (float) Math.PI / 180.0F;
            float sinD = McSineTable.sinStep(rad);
            float cosD = McSineTable.cosStep(rad);
            accelX = (double) (s * cosD - fwd * sinD);
            accelZ = (double) (fwd * cosD + s * sinD);
        }
        double vxg = vxa - accelX;
        double vzg = vza - accelZ;
        if (isJumpTick && sprint) {
            float fj = yawF * (float) (Math.PI / 180.0);
            vxg += (double) (McSineTable.sinStep(fj) * 0.2F);
            vzg -= (double) (McSineTable.cosStep(fj) * 0.2F);
        }
        return new double[]{prevPx, prevPz, vxg, vzg};
    }

    private boolean freeLands(int a, int n, double px, double py, double pz, double vx, double vz, float seedYaw) {
        double refX = px, refZ = pz;
        for (int iter = 0; iter < 3; iter++) {
            JumpPhysicsInputs win = sliceScenario(full, a, n, new Vec3dCore(refX, py, refZ), new Vec3dCore(vx, 0.0, vz), seedYaw);
            win.startBox = new StartBox(refX, refZ, vx, vz, refX - 20, refX + 20, refZ - 20, refZ + 20, vx, vx, vz, vz);
            List<JumpConstraint> cons = sliceConstraints(fullSpec, a, n);
            JumpSpec spec = new JumpSpec(win, cons, new Objective(fullSpec.objective.axis, fullSpec.objective.sense, n - a));
            double[] y = SolveCore.optimize(model, spec, budget, 20.0, 0.0, new AtomicBoolean(false), null);
            if (y == null) continue;
            double[] rs = FreeStartSolve.recoverStart(model, spec, y);
            double v;
            if (rs == null) {
                double[] gf = win.toGameFacings(Angles.wrapAll(y));
                v = JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(win, gf));
            } else {
                v = FreeStartSolve.violationAt(model, spec, y, rs[0], rs[1]);
                refX = rs[0]; refZ = rs[1];
            }
            if (v <= 0.0) return true;
        }
        return false;
    }

    private static JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos; p.initialVelocity = vel; p.startYaw = yaw; p.strafeSign = sc.strafeSign;
        p.jumpPerTick = sliceBool(sc.jumpPerTick, a, len);
        p.strafePerTick = sliceBool(sc.strafePerTick, a, len);
        p.yawLockedPerTick = sliceBool(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceDouble(sc.slipPerTick, a, len);
        p.sprintPerTick = sliceBool(sc.sprintPerTick, a, len);
        p.forwardInputPerTick = sliceFloat(sc.forwardInputPerTick, a, len, 0.98F);
        p.strafeInputPerTick = sliceFloat(sc.strafeInputPerTick, a, len, 0.0F);
        return p;
    }

    private static List<JumpConstraint> sliceConstraints(JumpSpec fullSpec, int a, int c) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint jc : fullSpec.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) out.add(new JumpConstraint(jc.mode, jc.t1 - a, jc.t2 == null ? null : (jc.t2 - a), jc.op, jc.cmp, jc.rhs, jc.name));
        }
        return out;
    }

    private static boolean[] sliceBool(boolean[] x, int f, int len) { if (x == null) return null; boolean[] o = new boolean[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i]; return o; }
    private static int[] sliceInt(int[] x, int f, int len) { if (x == null) return null; int[] o = new int[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0; return o; }
    private static double[] sliceDouble(double[] x, int f, int len) { if (x == null) return null; double[] o = new double[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN; return o; }
    private static float[] sliceFloat(float[] x, int f, int len, float d) { if (x == null) return null; float[] o = new float[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d; return o; }
}
