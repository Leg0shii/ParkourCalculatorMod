package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.CmaesJumpHarness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverRunResult;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixYawAssemble {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;

    private static final double[] MENU = {45, 0, 90, 135, 180, 225, 270, 315, 25, 65, 20, 70};

    private static final class Node {
        final long a0, a1; final double vx, vz, px, pz, dip;
        Node(long a0, long a1, double vx, double vz, double px, double pz, double dip) {
            this.a0 = a0; this.a1 = a1; this.vx = vx; this.vz = vz; this.px = px; this.pz = pz; this.dip = dip;
        }
    }

    private static int[] actsOf(Node n, int len) {
        int[] a = new int[len];
        for (int t = 0; t < len; t++) a[t] = (int) ((t < 16 ? n.a0 >>> (4 * t) : n.a1 >>> (4 * (t - 16))) & 0xF);
        return a;
    }

    @Test
    public void solve() throws Exception {
        String path = System.getenv("PKC_YA_FILE");
        org.junit.Assume.assumeTrue("set PKC_YA_FILE", path != null && !path.isEmpty());
        int seam = Integer.parseInt(System.getenv().getOrDefault("PKC_YA_SEAM", "30"));
        int topK = Integer.parseInt(System.getenv().getOrDefault("PKC_YA_TOPK", "40"));
        int maxClose = Integer.parseInt(System.getenv().getOrDefault("PKC_YA_MAXCLOSE", "12"));
        long t0 = System.nanoTime();
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
        System.out.printf(Locale.ROOT, "=== NixYawAssemble %s seam=%d n=%d cons=%d (yaws-only over the rows' keys, COLD) ===%n",
                new File(path).getName(), seam, n, fullSpec.constraints.size());

        double[] diag = new double[n];
        java.util.Arrays.fill(diag, 45.0);
        ForwardPath dpath = model.forward(full, diag);
        System.out.printf(Locale.ROOT, "all-45 setup: seam=(%.5f,%.5f) vel=(%.6f,%.6f) z5=%.5f z17=%.5f x0=%.4f x2=%.4f x5=%.4f x17=%.4f x29=%.4f%n",
                dpath.posX[seam], dpath.posZ[seam], dpath.velX[seam], dpath.velZ[seam],
                dpath.posZ[5], dpath.posZ[17], dpath.posX[0], dpath.posX[2], dpath.posX[5], dpath.posX[17], dpath.posX[29]);

        List<double[]> setups = new ArrayList<>();
        for (int j = 1; j < seam; j++) {
            if (!full.jumpAt(j) || !full.sprintAt(j)) continue;
            double bestAxis = 0.0, bestVz = Double.NEGATIVE_INFINITY;
            for (double axisFacing : new double[]{0, 90, 180, 270}) {
                double[] tpl = new double[n];
                java.util.Arrays.fill(tpl, 45.0);
                tpl[j] = axisFacing;
                double vz = model.forward(full, tpl).velZ[seam];
                if (vz > bestVz) { bestVz = vz; bestAxis = axisFacing; }
            }
            for (double delta : new double[]{0, 8, 16, 24, 4, 12, 20, 28, -8}) {
                double[] tpl = new double[seam];
                java.util.Arrays.fill(tpl, 45.0);
                tpl[j] = bestAxis + delta;
                setups.add(tpl);
            }
        }
        System.out.printf(Locale.ROOT, "injected %d axis-boost templates%n", setups.size());

        List<Node> seams = yawSearch(seam);
        System.out.printf(Locale.ROOT, "yaw-menu search: %d corridor-compliant seam states (%.1fs)%n", seams.size(), sec(t0));
        List<Node> cands = bandedCandidates(seams, topK);
        for (Node node : cands) {
            int[] acts = actsOf(node, seam);
            double[] s = new double[seam];
            for (int t = 0; t < seam; t++) s[t] = MENU[acts[t]];
            setups.add(s);
        }
        System.out.printf(Locale.ROOT, "trying %d candidates (%d injected + %d banded), maxClose=%d%n",
                setups.size(), setups.size() - cands.size(), cands.size(), maxClose);

        int attempts = 0;
        for (int ci = 0; ci < setups.size() && attempts < maxClose; ci++) {
            double[] setup = setups.get(ci);
            double[] gfAll = new double[n];
            System.arraycopy(setup, 0, gfAll, 0, seam);
            for (int t = seam; t < n; t++) gfAll[t] = 45.0;
            ForwardPath sp = model.forward(full, gfAll);
            double seamPx = sp.posX[seam], seamPz = sp.posZ[seam];
            double seamVx = sp.velX[seam], seamVz = sp.velZ[seam];

            double dxLo = -1e18, dxHi = 1e18, dzLo = -1e18, dzHi = 1e18;
            for (JumpConstraint c : fullSpec.constraints) {
                if (c.t2 != null || c.mode == JumpConstraint.Mode.F || c.t1 >= seam) continue;
                boolean isX = c.mode == JumpConstraint.Mode.X;
                double pos = isX ? sp.posX[c.t1] : sp.posZ[c.t1];
                double b = c.rhs - pos;
                if (c.cmp == JumpConstraint.Cmp.GE) { if (isX) dxLo = Math.max(dxLo, b); else dzLo = Math.max(dzLo, b); }
                else if (c.cmp == JumpConstraint.Cmp.LE) { if (isX) dxHi = Math.min(dxHi, b); else dzHi = Math.min(dzHi, b); }
            }
            if (dxLo > dxHi || dzLo > dzHi) continue;

            if (dxLo > 1.0e-12 || dxHi < -1.0e-12 || dzLo > 1.0e-12 || dzHi < -1.0e-12) continue;
            JumpSpec pinned = sliced(seam, n, new Vec3dCore(seamPx, sp.posY[seam], seamPz),
                    new Vec3dCore(seamVx, 0.0, seamVz), (float) setup[seam - 1], null);

            System.out.printf(Locale.ROOT, "cand %d/%d seam=(%.4f,%.4f) vel=(%.5f,%.5f) dx[%.3f,%.3f] dz[%.3f,%.3f] (%.1fs)%n",
                    ci + 1, setups.size(), seamPx, seamPz, seamVx, seamVz, dxLo, dxHi, dzLo, dzHi, sec(t0));
            attempts++;

            double[] y = homotopy(pinned, 1.0e-3, t0);
            if (y == null) {
                System.out.printf(Locale.ROOT, "  no relaxed-feasible entry (%.1fs)%n", sec(t0));
                continue;
            }
            double pv = viol(pinned, y);
            System.out.printf(Locale.ROOT, "  ladder done: viol=%.3e (%.1fs)%n", pv, sec(t0));
            if (pv > 0.0) continue;

            double ddx = 0.0, ddz = 0.0;
            double[] tailGf = pinned.asScenario().toGameFacings(Angles.wrapAll(y));
            for (int t = seam; t < n; t++) gfAll[t] = tailGf[t - seam];
            JumpPhysicsInputs fullT = fullAt(full.startPos.x + ddx, full.startPos.z + ddz);
            ForwardPath fp = model.forward(fullT, gfAll);
            double fullViol = JumpConstraintCompiler.compile(fullSpec).maxViolation(gfAll, fp);
            System.out.printf(Locale.ROOT, "  FULL ROUTE verify: viol=%.3e objX=%.7f start=(%.6f,%.6f)%n",
                    fullViol, fp.getPos(fullSpec.objective.tick, fullSpec.objective.axis), fullT.startPos.x, fullT.startPos.z);
            if (fullViol > 0.0) {
                JumpSpec fullPinned = new JumpSpec(fullT, fullSpec.constraints, fullSpec.objective);
                double[] gfRep = bucketDescentGf(fullPinned, gfAll);
                double rv = JumpConstraintCompiler.compile(fullSpec).maxViolation(gfRep, model.forward(fullT, gfRep));
                System.out.printf(Locale.ROOT, "  full-route gf repair: %.3e -> %.3e%n", fullViol, rv);
                if (rv <= 0.0) { gfAll = gfRep; fullViol = rv; fp = model.forward(fullT, gfAll); }
            }
            if (fullViol <= 0.0) {
                System.out.printf(Locale.ROOT, "%n*** NIX SOLVED COLD FROM t1 (yaws-only) *** (%.1fs)%n", sec(t0));
                System.out.printf(Locale.ROOT, "start=(%.7f,%.7f) objX=%.7f%n", fullT.startPos.x, fullT.startPos.z,
                        fp.getPos(fullSpec.objective.tick, fullSpec.objective.axis));
                for (int t = 0; t < n; t++) System.out.printf(Locale.ROOT, "t%02d gf=%.6f%n", t, gfAll[t]);
                return;
            }
        }
        System.out.printf(Locale.ROOT, "no cold solve assembled (%.1fs)%n", sec(t0));
    }

    private JumpPhysicsInputs fullAt(double p0x, double p0z) {
        JumpPhysicsInputs p = new JumpPhysicsInputs(full.numTicks);
        p.startPos = new Vec3dCore(p0x, full.startPos.y, p0z);
        p.initialVelocity = full.initialVelocity;
        p.startYaw = full.startYaw;
        p.incomingSprint = full.incomingSprint;
        p.incomingAmp = full.incomingAmp;
        p.strafeSign = full.strafeSign;
        p.jumpPerTick = full.jumpPerTick;
        p.strafePerTick = full.strafePerTick;
        p.yawLockedPerTick = full.yawLockedPerTick;
        p.speedAmplifier = full.speedAmplifier;
        p.slipPerTick = full.slipPerTick;
        p.sprintPerTick = full.sprintPerTick;
        p.forwardInputPerTick = full.forwardInputPerTick;
        p.strafeInputPerTick = full.strafeInputPerTick;
        p.startBox = StartBox.pinned(p0x, p0z, full.initialVelocity.x, full.initialVelocity.z);
        return p;
    }

    private List<Node> yawSearch(int seam) {
        double vxCap = 0.13;
        double slack = 0.35;
        List<Node> frontier = new ArrayList<>();
        frontier.add(new Node(0L, 0L, full.initialVelocity.x, full.initialVelocity.z, full.startPos.x, full.startPos.z, full.startPos.z));
        for (int t = 0; t < seam; t++) {
            frontier.sort(Comparator.comparingDouble((Node nd) -> -nd.vz));
            HashMap<Long, Node> grid = new HashMap<>();
            for (Node nd : frontier) {
                for (int ai = 0; ai < MENU.length; ai++) {
                    Node c = step(nd, ai, t);
                    if (Math.abs(c.vx) > vxCap) continue;
                    if (violatesSetupCons(t + 1, seam, c.px, c.pz, slack)) continue;
                    long key = ((Math.round(c.vz / 2e-4) * 270001L + Math.round((c.pz - c.dip) / 2e-2)) * 270001L
                            + Math.round(c.vx / 1e-3)) * 161L + Math.round(c.px / 5e-2);
                    Node prev = grid.get(key);
                    if (prev == null) {
                        if (grid.size() < 400000) grid.put(key, c);
                    } else if (c.vz > prev.vz) {
                        grid.put(key, c);
                    }
                }
            }
            frontier = new ArrayList<>(grid.values());
            if (frontier.isEmpty()) return frontier;
        }
        return frontier;
    }

    private Node step(Node parent, int ai, int t) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(parent.px, full.startPos.y, parent.pz);
        sc.initialVelocity = new Vec3dCore(parent.vx, 0.0, parent.vz);
        sc.startYaw = (float) MENU[ai];
        sc.incomingSprint = t == 0 ? full.incomingSprint : full.sprintAt(t - 1);
        sc.incomingAmp = t == 0 ? full.incomingAmp : full.speedAmplifierAt(t - 1);
        sc.strafeSign = full.strafeSign;
        sc.jumpPerTick = new boolean[]{full.jumpAt(t)};
        sc.slipPerTick = new double[]{full.slipPerTick != null && t < full.slipPerTick.length ? full.slipPerTick[t] : Double.NaN};
        sc.strafePerTick = new boolean[]{full.strafeAt(t)};
        sc.sprintPerTick = new boolean[]{full.sprintAt(t)};
        sc.forwardInputPerTick = new float[]{full.forwardAt(t)};
        sc.strafeInputPerTick = new float[]{full.strafeInputAt(t)};
        sc.speedAmplifier = new int[]{full.speedAmplifierAt(t)};
        sc.yawLockedPerTick = new boolean[]{false};
        ForwardPath p = model.forward(sc, new double[]{MENU[ai]});
        long na0 = parent.a0, na1 = parent.a1;
        if (t < 16) na0 |= (long) ai << (4 * t); else na1 |= (long) ai << (4 * (t - 16));
        return new Node(na0, na1, p.velX[1], p.velZ[1], p.posX[1], p.posZ[1], Math.min(parent.dip, p.posZ[1]));
    }

    private List<Node> bandedCandidates(List<Node> seamStates, int topK) {
        HashMap<Long, Node> exact = new HashMap<>();
        for (Node nd : seamStates) {
            long k = (Math.round(nd.vz / 1e-7) * 1000003L + Math.round(nd.pz / 1e-6)) * 1000003L
                    + Math.round(nd.vx / 1e-7) * 31L + Math.round(nd.px / 1e-6);
            exact.putIfAbsent(k, nd);
        }
        HashMap<Integer, List<Node>> bands = new HashMap<>();
        for (Node nd : exact.values()) {
            int band = (int) Math.floor((nd.pz - nd.dip) / 0.5);
            bands.computeIfAbsent(band, b -> new ArrayList<>()).add(nd);
        }
        List<Integer> bandKeys = new ArrayList<>(bands.keySet());
        for (List<Node> in : bands.values()) in.sort(Comparator.comparingDouble((Node nd) -> -nd.vz));
        bandKeys.sort(Comparator.comparingDouble((Integer b) -> -bands.get(b).get(0).vz));
        List<List<Node>> picks = new ArrayList<>();
        for (int b : bandKeys) {
            List<Node> in = bands.get(b);
            List<Node> sel = new ArrayList<>();
            for (Node nd : in) {
                if (sel.size() >= 2) break;
                sel.add(nd);
            }
            for (Node nd : in) {
                if (nd.vx <= -0.02) { if (!sel.contains(nd)) sel.add(nd); break; }
            }
            picks.add(sel);
        }
        List<Node> out = new ArrayList<>();
        for (int round = 0; out.size() < topK; round++) {
            boolean any = false;
            for (List<Node> sel : picks) {
                if (round < sel.size()) {
                    out.add(sel.get(round));
                    any = true;
                    if (out.size() >= topK) break;
                }
            }
            if (!any) break;
        }
        return out;
    }

    private boolean violatesSetupCons(int tick, int seam, double px, double pz, double slack) {
        if (tick >= seam) return false;
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.t1 != tick || c.t2 != null) continue;
            double val = c.mode == JumpConstraint.Mode.X ? px : (c.mode == JumpConstraint.Mode.Z ? pz : Double.NaN);
            if (Double.isNaN(val)) continue;
            if (c.cmp == JumpConstraint.Cmp.GE && val < c.rhs - slack) return true;
            if (c.cmp == JumpConstraint.Cmp.LE && val > c.rhs + slack) return true;
        }
        return false;
    }

    private double[] homotopy(JumpSpec spec, double eps0, long t0) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        SolveCore.Budget entryBudget = new SolveCore.Budget(128, 100000, 8, BucketAscentPolish.FAST);
        StartBox box = spec.asScenario().startBox;
        double eps = eps0;
        double[] y = null;
        for (int i = 0; i < 2 && y == null; i++, eps *= 4.0) {
            JumpSpec relaxed = relax(spec, eps);
            double[] cand = SolveCore.optimize(model, relaxed, entryBudget, 20.0, 0.0, cancel, null);
            if (cand != null && violFree(relaxed, cand) > 0.0) cand = bucketDescent(relaxed, cand, true);
            if (cand != null && violFree(relaxed, cand) <= 0.0) { y = cand; break; }
            if (box != null && box.startFree()) {
                JumpSpec corner = pinnedVariant(spec, box.pxHi - 1.0e-9, box.pzLo + 1.0e-9);
                JumpSpec cornerRelaxed = relax(corner, eps);
                double[] c2 = SolveCore.optimize(model, cornerRelaxed, entryBudget, 20.0, 0.0, cancel,
                        cand != null ? Angles.wrapAll(cand) : null);
                if (c2 != null && violFree(relaxed, c2) > 0.0) c2 = bucketDescent(cornerRelaxed, c2, false);
                if (c2 != null && violFree(relaxed, c2) <= 0.0) { y = c2; break; }
            }
        }
        if (y == null) return null;
        JumpSpec cur = spec;
        boolean pinnedNow = false;
        double epsGood = eps;
        int rung = 0;
        while (epsGood > 0.0 && rung < 120) {
            double epsNext = epsGood <= 2.0e-6 ? 0.0 : epsGood * 0.5;
            int refine = 0;
            while (true) {
                rung++;
                JumpSpec sp = relax(cur, epsNext);
                double before = violFree(sp, y);
                if (before <= 0.0) break;
                double[] fixed = repair(sp, y, cancel);
                double after = fixed == null ? before : violFree(sp, fixed);
                if (after <= 0.0) { y = fixed; break; }
                if (fixed != null && after < before) y = fixed;
                refine++;
                if (refine > 6) {
                    double[] rep = bucketDescent(sp, y, !pinnedNow);
                    if (violFree(sp, rep) <= 0.0) { y = rep; break; }
                    if (!pinnedNow) {
                        double[] p0 = minViolationPin(spec, y);
                        if (p0 != null) {
                            cur = pinnedVariant(spec, p0[0], p0[1]);
                            pinnedNow = true;
                            refine = 0;
                            System.out.printf(Locale.ROOT, "  free stall at eps=%.3e -> pinned at (%.5f,%.5f) (%.1fs)%n",
                                    epsNext, p0[0], p0[1], sec(t0));
                            epsNext = epsGood;
                            continue;
                        }
                    }
                    double[] kicked = kickRepair(sp, y, cancel);
                    if (kicked != null && violFree(sp, kicked) <= 0.0) { y = kicked; break; }
                    if (kicked != null && violFree(sp, kicked) < violFree(sp, y)) y = kicked;
                    System.out.printf(Locale.ROOT, "  stall at eps=%.3e (%.1fs)%n", epsNext, sec(t0));
                    printBinding(cur, y);
                    return y;
                }
                epsNext = 0.5 * (epsGood + epsNext);
            }
            epsGood = epsNext;
        }
        double fv = violFree(cur, y);
        if (fv > 0.0) {
            double[] rep = bucketDescent(cur, y, !pinnedNow);
            if (violFree(cur, rep) < fv) y = rep;
        }
        return y;
    }

    private void printBinding(JumpSpec spec, double[] y) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        ForwardPath pr = model.forward(sc, gf);
        double[] d = sc.startBox != null && sc.startBox.startFree()
                ? FreeStartSolve.bestTranslate(spec, gf, pr, sc.startBox) : new double[]{0, 0};
        for (JumpConstraint cc : c.ineq) {
            double s = JumpConstraintCompiler.translatedSlack(cc, gf, pr, d[0], d[1]);
            if (s > -5.0e-4) System.out.printf(Locale.ROOT, "    %s t%d %s rhs=%.4f slack=%+.3e%n",
                    cc.mode, cc.t1, cc.cmp, cc.rhs, s);
        }
    }

    private double[] kickRepair(JumpSpec sp, double[] y, AtomicBoolean cancel) {
        java.util.Random rng = new java.util.Random(0xC0FFEE ^ y.length);
        double[] best = null;
        double bestV = Double.POSITIVE_INFINITY;
        for (int k = 0; k < 10; k++) {
            double[] cand = Angles.wrapAll(y.clone());
            int ticks = 2 + rng.nextInt(6);
            double mag = 0.5 + rng.nextDouble() * 6.0;
            for (int q = 0; q < ticks; q++) cand[rng.nextInt(cand.length)] += (rng.nextDouble() * 2.0 - 1.0) * mag;
            SolverRunResult rr = new CmaesJumpHarness(1.0e7, 1.0e7, 1.0, 60000, true).solve(model, sp, cand, cancel);
            double vv = violFree(sp, rr.yawAbsDeg);
            if (vv < bestV) { bestV = vv; best = rr.yawAbsDeg; }
            if (vv <= 0.0) return rr.yawAbsDeg;
        }
        if (best != null) {
            double[] desc = bucketDescent(sp, best, sp.asScenario().startBox != null && sp.asScenario().startBox.startFree());
            if (violFree(sp, desc) < bestV) return desc;
        }
        return best;
    }

    private double[] repair(JumpSpec sp, double[] warm, AtomicBoolean cancel) {
        double[] best = null;
        double bestV = Double.POSITIVE_INFINITY;
        for (double sigma : new double[]{0.3, 1.0, 3.0}) {
            SolverRunResult rr = new CmaesJumpHarness(1.0e7, 1.0e7, sigma, 60000, true).solve(model, sp, Angles.wrapAll(warm.clone()), cancel);
            double vv = violFree(sp, rr.yawAbsDeg);
            if (vv < bestV) { bestV = vv; best = rr.yawAbsDeg; }
            if (vv <= 0.0) return rr.yawAbsDeg;
        }
        return best;
    }

    private double[] bucketDescent(JumpSpec spec, double[] start, boolean free) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y = Angles.wrapAll(start.clone());
        double best = slackAbs(spec, c, sc, y, free);
        int n = y.length;
        double[][] b1 = {{0.05, 0.001}, {0.012, 0.0002}, {0.003, 0.00005}, {0.0008, 0.00001}};
        double[][] b2 = {{0.02, 0.0008}, {0.006, 0.0002}, {0.0015, 0.00004}};
        for (int round = 0; round < 24 && best > 0.0; round++) {
            boolean moved = false;
            for (double[] r : b1) {
                for (int t = 0; t < n && best > 0.0; t++) {
                    double orig = y[t], by = orig, bo = best;
                    for (double d = -r[0]; d <= r[0] + 1e-12; d += r[1]) {
                        y[t] = orig + d;
                        double s = slackAbs(spec, c, sc, y, free);
                        if (s < bo) { bo = s; by = y[t]; }
                    }
                    y[t] = by;
                    if (bo < best) { best = bo; moved = true; }
                }
            }
            if (best <= 0.0) break;
            for (double[] r : b2) {
                for (int i = 0; i < n && best > 0.0; i++) {
                    for (int j = i + 1; j <= Math.min(n - 1, i + 3); j++) {
                        double oi = y[i], oj = y[j], bi = oi, bj = oj, bo = best;
                        for (double di = -r[0]; di <= r[0] + 1e-12; di += r[1]) {
                            y[i] = oi + di;
                            for (double dj = -r[0]; dj <= r[0] + 1e-12; dj += r[1]) {
                                y[j] = oj + dj;
                                double s = slackAbs(spec, c, sc, y, free);
                                if (s < bo) { bo = s; bi = y[i]; bj = y[j]; }
                            }
                        }
                        y[i] = bi; y[j] = bj;
                        if (bo < best) { best = bo; moved = true; }
                    }
                }
            }
            if (!moved) break;
        }
        return y;
    }

    private double[] bucketDescentGf(JumpSpec spec, double[] gfStart) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y = gfStart.clone();
        double best = gfSlack(c, sc, y);
        int n = y.length;
        double[][] b1 = {{0.012, 0.0002}, {0.003, 0.00005}, {0.0008, 0.00001}};
        double[][] b2 = {{0.006, 0.0002}, {0.0015, 0.00004}};
        for (int round = 0; round < 12 && best > 0.0; round++) {
            boolean moved = false;
            for (double[] r : b1) {
                for (int t = 0; t < n && best > 0.0; t++) {
                    double orig = y[t], by = orig, bo = best;
                    for (double d = -r[0]; d <= r[0] + 1e-12; d += r[1]) {
                        y[t] = orig + d;
                        double s = gfSlack(c, sc, y);
                        if (s < bo) { bo = s; by = y[t]; }
                    }
                    y[t] = by;
                    if (bo < best) { best = bo; moved = true; }
                }
            }
            if (best <= 0.0) break;
            for (double[] r : b2) {
                for (int i = 0; i < n && best > 0.0; i++) {
                    for (int j = i + 1; j <= Math.min(n - 1, i + 3); j++) {
                        double oi = y[i], oj = y[j], bi = oi, bj = oj, bo = best;
                        for (double di = -r[0]; di <= r[0] + 1e-12; di += r[1]) {
                            y[i] = oi + di;
                            for (double dj = -r[0]; dj <= r[0] + 1e-12; dj += r[1]) {
                                y[j] = oj + dj;
                                double s = gfSlack(c, sc, y);
                                if (s < bo) { bo = s; bi = y[i]; bj = y[j]; }
                            }
                        }
                        y[i] = bi; y[j] = bj;
                        if (bo < best) { best = bo; moved = true; }
                    }
                }
            }
            if (!moved) break;
        }
        return y;
    }

    private double gfSlack(JumpConstraintCompiler.Compiled c, JumpPhysicsInputs sc, double[] gf) {
        ForwardPath pr = model.forward(sc, gf);
        double m = Double.NEGATIVE_INFINITY;
        for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.slack(cc, gf, pr));
        for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.evaluate(cc, gf, pr)));
        return m;
    }

    private double slackAbs(JumpSpec spec, JumpConstraintCompiler.Compiled c, JumpPhysicsInputs sc, double[] absYaws, boolean free) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath pr = model.forward(sc, gf);
        double m = Double.NEGATIVE_INFINITY;
        if (free && sc.startBox != null && sc.startBox.startFree()) {
            double[] d = FreeStartSolve.bestTranslate(spec, gf, pr, sc.startBox);
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.translatedSlack(cc, gf, pr, d[0], d[1]));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.translatedEvaluate(cc, gf, pr, d[0], d[1])));
        } else {
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.slack(cc, gf, pr));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.evaluate(cc, gf, pr)));
        }
        return m;
    }

    private JumpSpec relax(JumpSpec spec, double eps) {
        if (eps <= 0.0) return spec;
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.cmp == JumpConstraint.Cmp.GE) out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, c.rhs - eps, c.name));
            else if (c.cmp == JumpConstraint.Cmp.LE) out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, c.rhs + eps, c.name));
            else out.add(c);
        }
        return new JumpSpec(spec.asScenario(), out, spec.objective);
    }

    private double viol(JumpSpec spec, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private double violFree(JumpSpec spec, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        return Math.max(0.0, slackAbs(spec, c, sc, yawsAbs, true));
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }

    private double[] minViolationPin(JumpSpec freeSpec, double[] y) {
        JumpPhysicsInputs sc = freeSpec.asScenario();
        StartBox box = sc.startBox;
        if (box == null || !box.startFree()) return null;
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        ForwardPath pr = model.forward(sc, gf);
        double loX = Double.NEGATIVE_INFINITY, hiX = Double.POSITIVE_INFINITY;
        double loZ = Double.NEGATIVE_INFINITY, hiZ = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : freeSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (tc == 0) continue;
            double e0 = JumpConstraintCompiler.evaluate(c, gf, pr);
            boolean isX = c.mode == JumpConstraint.Mode.X;
            if (c.cmp == JumpConstraint.Cmp.LE) {
                double b = -e0 / tc;
                if (isX) hiX = Math.min(hiX, b); else hiZ = Math.min(hiZ, b);
            } else if (c.cmp == JumpConstraint.Cmp.GE) {
                double b = -e0 / tc;
                if (isX) loX = Math.max(loX, b); else loZ = Math.max(loZ, b);
            }
        }
        double dx = mid(loX, hiX);
        double dz = mid(loZ, hiZ);
        dx = Math.max(box.pxLo - box.px, Math.min(box.pxHi - box.px, dx));
        dz = Math.max(box.pzLo - box.pz, Math.min(box.pzHi - box.pz, dz));
        return new double[]{box.px + dx, box.pz + dz};
    }

    private static double mid(double lo, double hi) {
        boolean fLo = lo != Double.NEGATIVE_INFINITY, fHi = hi != Double.POSITIVE_INFINITY;
        if (fLo && fHi) return 0.5 * (lo + hi);
        if (fLo) return Math.max(0.0, lo);
        if (fHi) return Math.min(0.0, hi);
        return 0.0;
    }

    private JumpSpec pinnedVariant(JumpSpec spec, double px, double pz) {
        JumpPhysicsInputs s = spec.asScenario();
        JumpPhysicsInputs p = new JumpPhysicsInputs(s.numTicks);
        p.startPos = new Vec3dCore(px, s.startPos.y, pz);
        p.initialVelocity = s.initialVelocity;
        p.startYaw = s.startYaw;
        p.incomingSprint = s.incomingSprint;
        p.incomingAmp = s.incomingAmp;
        p.strafeSign = s.strafeSign;
        p.jumpPerTick = s.jumpPerTick;
        p.strafePerTick = s.strafePerTick;
        p.yawLockedPerTick = s.yawLockedPerTick;
        p.speedAmplifier = s.speedAmplifier;
        p.slipPerTick = s.slipPerTick;
        p.sprintPerTick = s.sprintPerTick;
        p.forwardInputPerTick = s.forwardInputPerTick;
        p.strafeInputPerTick = s.strafeInputPerTick;
        p.startBox = StartBox.pinned(px, pz, s.initialVelocity.x, s.initialVelocity.z);
        return new JumpSpec(p, spec.constraints, spec.objective);
    }

    private JumpSpec sliced(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw, StartBox box) {
        JumpPhysicsInputs win = sliceScenario(full, a, c, pos, vel, yaw);
        win.startBox = box;
        win.incomingSprint = full.sprintAt(a - 1);
        win.incomingAmp = full.speedAmplifierAt(a - 1);
        List<JumpConstraint> cons = sliceConstraints(fullSpec, a, c);
        Objective obj = new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a);
        return new JumpSpec(win, cons, obj);
    }

    private static JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos;
        p.initialVelocity = vel;
        p.startYaw = yaw;
        p.strafeSign = sc.strafeSign;
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
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                out.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return out;
    }

    private static boolean[] sliceBool(boolean[] x, int f, int len) { if (x == null) return null; boolean[] o = new boolean[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i]; return o; }
    private static int[] sliceInt(int[] x, int f, int len) { if (x == null) return null; int[] o = new int[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0; return o; }
    private static double[] sliceDouble(double[] x, int f, int len) { if (x == null) return null; double[] o = new double[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN; return o; }
    private static float[] sliceFloat(float[] x, int f, int len, float d) { if (x == null) return null; float[] o = new float[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d; return o; }
}
