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

public class NixColdT1 {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;

    private static final class Act {
        final double yaw; final float fwd; final float strafe; final boolean sprint; final String label;
        Act(double yaw, float fwd, float strafe, boolean sprint, String label) {
            this.yaw = yaw; this.fwd = fwd; this.strafe = strafe; this.sprint = sprint; this.label = label;
        }
    }
    private static final Act[] AL = {
        new Act(45, 1f, 1f, false, "W+A@45"), new Act(45, 1f, 1f, true, "W+A@45+spr"),
        new Act(45, -1f, -1f, false, "S+D@45"), new Act(45, -1f, -1f, true, "S+D@45+spr"),
        new Act(225, 1f, 1f, false, "W+A@225"),
        new Act(0, 1f, 0f, false, "W@0"), new Act(0, 1f, 0f, true, "W@0+spr"),
        new Act(180, 1f, 0f, false, "W@180"), new Act(180, 1f, 0f, true, "W@180+spr"),
        new Act(0, -1f, 0f, false, "S@0"), new Act(0, 0f, 0f, false, "coast"),
        new Act(20, 1f, 1f, false, "W+A@20"), new Act(20, 1f, 1f, true, "W+A@20+spr"),
        new Act(70, 1f, 0f, false, "W@70"),
        new Act(110, 1f, 0f, false, "W@110"),
    };

    private static final class Node {
        final long a0, a1; final double vx, vz, px, pz;
        Node(long a0, long a1, double vx, double vz, double px, double pz) {
            this.a0 = a0; this.a1 = a1; this.vx = vx; this.vz = vz; this.px = px; this.pz = pz;
        }
    }

    private static int[] actsOf(Node n, int len) {
        int[] a = new int[len];
        for (int t = 0; t < len; t++) a[t] = (int) ((t < 16 ? n.a0 >>> (4 * t) : n.a1 >>> (4 * (t - 16))) & 0xF);
        return a;
    }

    @Test
    public void solve() throws Exception {
        String path = System.getenv("PKC_COLD_FILE");
        org.junit.Assume.assumeTrue("set PKC_COLD_FILE", path != null && !path.isEmpty());
        int seam = Integer.parseInt(System.getenv().getOrDefault("PKC_COLD_SEAM", "30"));
        int topK = Integer.parseInt(System.getenv().getOrDefault("PKC_COLD_TOPK", "40"));
        int maxClose = Integer.parseInt(System.getenv().getOrDefault("PKC_COLD_MAXCLOSE", "8"));
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
        System.out.printf(Locale.ROOT, "=== NixColdT1 %s seam=%d n=%d cons=%d (COLD: no debug data used) ===%n",
                new File(path).getName(), seam, n, fullSpec.constraints.size());

        List<Node> seams = keySearch(seam);
        System.out.printf(Locale.ROOT, "key search: %d corridor-compliant seam states (%.1fs)%n", seams.size(), sec(t0));
        List<Node> cands = bandedCandidates(seams, topK);
        System.out.printf(Locale.ROOT, "trying %d banded candidates (pz bands, vz desc within), maxClose=%d%n", cands.size(), maxClose);

        int closed = 0;
        for (int ci = 0; ci < cands.size() && closed < maxClose; ci++) {
            Node node = cands.get(ci);
            int[] acts = actsOf(node, seam);
            JumpPhysicsInputs asm = assembledScenario(acts, seam, n);
            double[] gfSetup = new double[n];
            for (int t = 0; t < seam; t++) gfSetup[t] = AL[acts[t]].yaw;
            for (int t = seam; t < n; t++) gfSetup[t] = 45.0;
            ForwardPath sp = model.forward(asm, gfSetup);
            double seamPx = sp.posX[seam], seamPz = sp.posZ[seam];
            double seamVx = sp.velX[seam], seamVz = sp.velZ[seam];

            double dxLo = -1e18, dxHi = 1e18, dzLo = -1e18, dzHi = 1e18;
            boolean setupOk = true;
            for (JumpConstraint c : fullSpec.constraints) {
                if (c.t2 != null || c.mode == JumpConstraint.Mode.F || c.t1 >= seam) continue;
                boolean isX = c.mode == JumpConstraint.Mode.X;
                double pos = isX ? sp.posX[c.t1] : sp.posZ[c.t1];
                double b = c.rhs - pos;
                if (c.cmp == JumpConstraint.Cmp.GE) { if (isX) dxLo = Math.max(dxLo, b); else dzLo = Math.max(dzLo, b); }
                else if (c.cmp == JumpConstraint.Cmp.LE) { if (isX) dxHi = Math.min(dxHi, b); else dzHi = Math.min(dzHi, b); }
            }
            if (dxLo > dxHi || dzLo > dzHi) setupOk = false;
            if (!setupOk) continue;

            StartBox box = new StartBox(seamPx, seamPz, seamVx, seamVz,
                    seamPx + dxLo, seamPx + dxHi, seamPz + dzLo, seamPz + dzHi,
                    seamVx, seamVx, seamVz, seamVz);
            boolean seamIncomingSprint = AL[acts[seam - 1]].sprint;
            JumpSpec tail = sliced(seam, n, new Vec3dCore(seamPx, sp.posY[seam], seamPz),
                    new Vec3dCore(seamVx, 0.0, seamVz), (float) AL[acts[seam - 1]].yaw, box, seamIncomingSprint);

            System.out.printf(Locale.ROOT, "cand %d/%d seam=(%.4f,%.4f) vel=(%.5f,%.5f) dz[%.3f,%.3f] (%.1fs)%n",
                    ci + 1, cands.size(), seamPx, seamPz, seamVx, seamVz, dzLo, dzHi, sec(t0));
            closed++;

            double[] y = homotopy(tail, null, 1.0e-3, t0);
            if (y == null) {
                System.out.printf(Locale.ROOT, "  no relaxed-feasible entry (%.1fs)%n", sec(t0));
                continue;
            }
            double freeViol = violFree(tail, y);
            System.out.printf(Locale.ROOT, "  ladder done: freeViol=%.3e (%.1fs)%n", freeViol, sec(t0));
            if (freeViol > 5.0e-5) continue;

            double[] p0 = FreeStartSolve.recoverStart(model, tail, y);
            if (p0 == null) continue;
            JumpSpec pinned = sliced(seam, n, new Vec3dCore(p0[0], sp.posY[seam], p0[1]),
                    new Vec3dCore(seamVx, 0.0, seamVz), (float) AL[acts[seam - 1]].yaw, null, seamIncomingSprint);
            double pv = viol(pinned, y);
            if (pv > 0.0) {
                double[] rep = bucketRepair(pinned, y);
                double rv = viol(pinned, rep);
                System.out.printf(Locale.ROOT, "  pinned at (%.5f,%.5f): viol %.3e -> repair %.3e%n", p0[0], p0[1], pv, rv);
                if (rv > 0.0) continue;
                y = rep;
            } else {
                System.out.printf(Locale.ROOT, "  pinned at (%.5f,%.5f): viol %.3e%n", p0[0], p0[1], pv);
            }

            double ddx = p0[0] - seamPx, ddz = p0[1] - seamPz;
            double[] tailGf = pinned.asScenario().toGameFacings(Angles.wrapAll(y));
            double[] gfAll = new double[n];
            for (int t = 0; t < seam; t++) gfAll[t] = AL[acts[t]].yaw;
            for (int t = seam; t < n; t++) gfAll[t] = tailGf[t - seam];
            JumpPhysicsInputs asmT = assembledScenario(acts, seam, n);
            asmT.startPos = new Vec3dCore(full.startPos.x + ddx, full.startPos.y, full.startPos.z + ddz);
            ForwardPath fp = model.forward(asmT, gfAll);
            double fullViol = JumpConstraintCompiler.compile(fullSpec).maxViolation(gfAll, fp);
            System.out.printf(Locale.ROOT, "  FULL ROUTE verify: viol=%.3e objX=%.6f start=(%.5f,%.5f)%n",
                    fullViol, fp.getPos(fullSpec.objective.tick, fullSpec.objective.axis), asmT.startPos.x, asmT.startPos.z);
            if (fullViol <= 0.0) {
                System.out.printf(Locale.ROOT, "%n*** NIX SOLVED COLD FROM t1 *** (%.1fs)%n", sec(t0));
                System.out.printf(Locale.ROOT, "start=(%.7f,%.7f) objX=%.7f%n", asmT.startPos.x, asmT.startPos.z,
                        fp.getPos(fullSpec.objective.tick, fullSpec.objective.axis));
                for (int t = 0; t < seam; t++) System.out.printf(Locale.ROOT, "t%02d %s gf=%.4f%n", t, AL[acts[t]].label, gfAll[t]);
                for (int t = seam; t < n; t++) System.out.printf(Locale.ROOT, "t%02d tail gf=%.6f%n", t, gfAll[t]);
                return;
            }
        }
        System.out.printf(Locale.ROOT, "no cold solve assembled (%.1fs)%n", sec(t0));
    }

    private double[] homotopy(JumpSpec spec, double[] entry, double eps0, long t0) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        SolveCore.Budget entryBudget = new SolveCore.Budget(192, 100000, 8, BucketAscentPolish.FAST);
        double eps = eps0;
        double[] y = null;
        for (int i = 0; i < 2 && y == null; i++, eps *= 4.0) {
            JumpSpec relaxed = relax(spec, eps);
            double[] cand = entry != null && violFree(relaxed, entry) <= 0.0 ? entry
                    : SolveCore.optimize(model, relaxed, entryBudget, 20.0, 0.0, cancel, entry);
            if (cand != null && violFree(relaxed, cand) <= 0.0) y = cand;
        }
        if (y == null) return null;
        eps0 = eps / 4.0;
        double epsGood = eps0;
        int rung = 0;
        while (epsGood > 0.0 && rung < 90) {
            double epsNext = epsGood <= 2.0e-6 ? 0.0 : epsGood * 0.5;
            int refine = 0;
            while (true) {
                rung++;
                JumpSpec sp = relax(spec, epsNext);
                double before = violFree(sp, y);
                if (before <= 0.0) break;
                double[] fixed = repair(sp, y, cancel);
                double after = fixed == null ? before : violFree(sp, fixed);
                if (after <= 0.0) { y = fixed; break; }
                if (fixed != null && after < before) y = fixed;
                refine++;
                if (refine > 6) {
                    double[] rep = bucketRepairFree(sp, y);
                    if (violFree(sp, rep) <= 0.0) { y = rep; break; }
                    System.out.printf(Locale.ROOT, "  stall at eps=%.3e (%.1fs)%n", epsNext, sec(t0));
                    return y;
                }
                epsNext = 0.5 * (epsGood + epsNext);
            }
            epsGood = epsNext;
        }
        double fv = violFree(spec, y);
        if (fv > 0.0) {
            double[] rep = bucketRepairFree(spec, y);
            if (violFree(spec, rep) < fv) y = rep;
        }
        return y;
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

    private double[] bucketRepair(JumpSpec spec, double[] start) {
        return bucketDescent(spec, start, false);
    }

    private double[] bucketRepairFree(JumpSpec spec, double[] start) {
        return bucketDescent(spec, start, true);
    }

    private double[] bucketDescent(JumpSpec spec, double[] start, boolean free) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y = Angles.wrapAll(start.clone());
        double best = slackOf(spec, c, sc, y, free);
        double[][] b1 = {{0.05, 0.001}, {0.012, 0.0002}, {0.003, 0.00005}, {0.0008, 0.00001}};
        double[][] b2 = {{0.02, 0.0008}, {0.006, 0.0002}, {0.0015, 0.00004}};
        int n = y.length;
        for (int round = 0; round < 24 && best > 0.0; round++) {
            boolean moved = false;
            for (double[] r : b1) {
                for (int t = 0; t < n && best > 0.0; t++) {
                    double orig = y[t], by = orig, bo = best;
                    for (double d = -r[0]; d <= r[0] + 1e-12; d += r[1]) {
                        y[t] = orig + d;
                        double s = slackOf(spec, c, sc, y, free);
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
                                double s = slackOf(spec, c, sc, y, free);
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

    private double slackOf(JumpSpec spec, JumpConstraintCompiler.Compiled c, JumpPhysicsInputs sc, double[] absYaws, boolean free) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath pr = model.forward(sc, gf);
        double m = Double.NEGATIVE_INFINITY;
        if (free && sc.startBox != null) {
            double[] d = FreeStartSolve.bestTranslate(spec, gf, pr, sc.startBox);
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.translatedSlack(cc, gf, pr, d[0], d[1]));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.translatedEvaluate(cc, gf, pr, d[0], d[1])));
        } else {
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.slack(cc, gf, pr));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.evaluate(cc, gf, pr)));
        }
        return m;
    }

    private List<Node> keySearch(int seam) {
        double vxCap = 0.20;
        List<Node> frontier = new ArrayList<>();
        frontier.add(new Node(0L, 0L, full.initialVelocity.x, full.initialVelocity.z, full.startPos.x, full.startPos.z));
        for (int t = 0; t < seam; t++) {
            HashMap<Long, Node> grid = new HashMap<>();
            for (Node nd : frontier) {
                for (int ai = 0; ai < AL.length; ai++) {
                    Node c = step(nd, ai, t);
                    if (Math.abs(c.vx) > vxCap) continue;
                    if (violatesSetupCons(t + 1, seam, c.px, c.pz)) continue;
                    long key = ((Math.round(c.vz / 3e-4) * 180001L + Math.round(c.pz / 5e-3)) * 180001L
                            + Math.round(c.vx / 3e-4)) * 1201L + Math.round(c.px / 5e-3);
                    Node prev = grid.get(key);
                    if (prev == null) {
                        if (grid.size() < 220000) grid.put(key, c);
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

    private List<Node> bandedCandidates(List<Node> seamStates, int topK) {
        HashMap<Long, Node> exact = new HashMap<>();
        for (Node nd : seamStates) {
            long k = (Math.round(nd.vz / 1e-7) * 1000003L + Math.round(nd.pz / 1e-6)) * 1000003L
                    + Math.round(nd.vx / 1e-7) * 31L + Math.round(nd.px / 1e-6);
            exact.putIfAbsent(k, nd);
        }
        HashMap<Integer, List<Node>> bands = new HashMap<>();
        for (Node nd : exact.values()) {
            int band = (int) Math.floor((nd.pz - full.startPos.z) / 0.75);
            bands.computeIfAbsent(band, b -> new ArrayList<>()).add(nd);
        }
        List<Integer> bandKeys = new ArrayList<>(bands.keySet());
        bandKeys.sort(Comparator.naturalOrder());
        List<List<Node>> picks = new ArrayList<>();
        for (int b : bandKeys) {
            List<Node> in = bands.get(b);
            in.sort(Comparator.comparingDouble((Node nd) -> -nd.vz));
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

    private boolean violatesSetupCons(int tick, int seam, double px, double pz) {
        if (tick >= seam) return false;
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.t1 != tick || c.t2 != null) continue;
            double val = c.mode == JumpConstraint.Mode.X ? px : (c.mode == JumpConstraint.Mode.Z ? pz : Double.NaN);
            if (Double.isNaN(val)) continue;
            if (c.cmp == JumpConstraint.Cmp.GE && val < c.rhs - 1e-9) return true;
            if (c.cmp == JumpConstraint.Cmp.LE && val > c.rhs + 1e-9) return true;
        }
        return false;
    }

    private Node step(Node parent, int ai, int t) {
        Act a = AL[ai];
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(parent.px, full.startPos.y, parent.pz);
        sc.initialVelocity = new Vec3dCore(parent.vx, 0.0, parent.vz);
        sc.startYaw = (float) a.yaw;
        if (t == 0) {
            sc.incomingSprint = full.incomingSprint;
            sc.incomingAmp = full.incomingAmp;
        } else {
            int prevAi = (int) ((t - 1 < 16 ? parent.a0 >>> (4 * (t - 1)) : parent.a1 >>> (4 * (t - 1 - 16))) & 0xF);
            sc.incomingSprint = AL[prevAi].sprint;
            sc.incomingAmp = 0;
        }
        sc.strafeSign = full.strafeSign;
        sc.jumpPerTick = new boolean[]{full.jumpPerTick != null && t < full.jumpPerTick.length && full.jumpPerTick[t]};
        sc.slipPerTick = new double[]{full.slipPerTick != null && t < full.slipPerTick.length ? full.slipPerTick[t] : Double.NaN};
        sc.strafePerTick = new boolean[]{false};
        sc.sprintPerTick = new boolean[]{a.sprint};
        sc.forwardInputPerTick = new float[]{a.fwd * 0.98f};
        sc.strafeInputPerTick = new float[]{a.strafe * 0.98f};
        sc.speedAmplifier = new int[]{0};
        sc.yawLockedPerTick = new boolean[]{false};
        ForwardPath p = model.forward(sc, new double[]{a.yaw});
        long na0 = parent.a0, na1 = parent.a1;
        if (t < 16) na0 |= (long) ai << (4 * t); else na1 |= (long) ai << (4 * (t - 16));
        return new Node(na0, na1, p.velX[1], p.velZ[1], p.posX[1], p.posZ[1]);
    }

    private JumpPhysicsInputs assembledScenario(int[] acts, int seam, int n) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startPos = full.startPos;
        sc.initialVelocity = full.initialVelocity;
        sc.startYaw = full.startYaw;
        sc.incomingSprint = full.incomingSprint;
        sc.incomingAmp = full.incomingAmp;
        sc.strafeSign = full.strafeSign;
        sc.jumpPerTick = full.jumpPerTick;
        sc.slipPerTick = full.slipPerTick;
        sc.speedAmplifier = new int[n];
        sc.yawLockedPerTick = new boolean[n];
        boolean[] strafeMask = new boolean[n];
        boolean[] sprint = new boolean[n];
        float[] fwd = new float[n];
        float[] str = new float[n];
        for (int t = 0; t < seam; t++) {
            Act a = AL[acts[t]];
            strafeMask[t] = false; sprint[t] = a.sprint; fwd[t] = a.fwd * 0.98f; str[t] = a.strafe * 0.98f;
        }
        for (int t = seam; t < n; t++) {
            strafeMask[t] = full.strafePerTick != null && full.strafePerTick[t];
            sprint[t] = full.sprintPerTick != null && full.sprintPerTick[t];
            fwd[t] = full.forwardInputPerTick != null ? full.forwardInputPerTick[t] : 0.98f;
            str[t] = full.strafeInputPerTick != null ? full.strafeInputPerTick[t] : 0.98f;
        }
        sc.strafePerTick = strafeMask;
        sc.sprintPerTick = sprint;
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = str;
        return sc;
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
        double s = slackOf(spec, c, sc, yawsAbs, true);
        return Math.max(0.0, s);
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }

    private JumpSpec sliced(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw, StartBox box, Boolean incomingSprint) {
        JumpPhysicsInputs win = sliceScenario(full, a, c, pos, vel, yaw);
        win.startBox = box;
        win.incomingSprint = incomingSprint;
        win.incomingAmp = 0;
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
