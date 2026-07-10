package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixSolve {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;

    private static final class Act {
        final double yaw; final float fwd; final float strafe; final boolean sprint;
        Act(double yaw, float fwd, float strafe, boolean sprint) { this.yaw = yaw; this.fwd = fwd; this.strafe = strafe; this.sprint = sprint; }
    }
    private static final Act[] AL = {
        new Act(45, 1f, 1f, false), new Act(45, 1f, 1f, true),      // W+A @45 forward (+Z), 45-strafe
        new Act(45, -1f, -1f, false), new Act(45, -1f, -1f, true),  // S+D @45 backward (-Z), 45-strafe
        new Act(225, 1f, 1f, false),                                 // W+A @225 backward (-Z), 45-strafe (idea #1)
        new Act(0, 1f, 0f, false), new Act(0, 1f, 0f, true),         // W @0 forward, sprint variant = +0.2 jump boost
        new Act(180, 1f, 0f, false), new Act(180, 1f, 0f, true),     // W @180 backward, sprint = -0.2 jump boost (idea #1)
        new Act(0, -1f, 0f, false), new Act(0, 0f, 0f, false),       // S @0, coast
        new Act(20, 1f, 1f, false), new Act(20, 1f, 1f, true),       // W+A @20 curve LEFT while +Z (idea #2)
        new Act(70, 1f, 0f, false),                                  // W @70 mostly -X (build vx)
        new Act(110, 1f, 0f, false),                                 // W @110 -X and -Z
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
        String path = System.getenv("PKC_SOLVE2_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE2_FILE", path != null && !path.isEmpty());
        int seam = Integer.parseInt(System.getenv().getOrDefault("PKC_SOLVE2_SEAM", "30"));
        double tVzLo = Double.parseDouble(System.getenv().getOrDefault("PKC_SOLVE2_VZLO", "0.213"));
        double tVzHi = Double.parseDouble(System.getenv().getOrDefault("PKC_SOLVE2_VZHI", "0.222"));
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
        double provPz = dp.posZ[seam];

        System.out.printf("=== NixSolve %s: COLD from t1 (setup key search overshoots vz, tail SolveCore, one translation) ===%n",
                new File(path).getName());

        // 1. Cold key-search setup [0,seam): reach a curved, lower-vz seam (idea #2: vx un-sticks the tail).
        double pzTol = Double.parseDouble(System.getenv().getOrDefault("PKC_SOLVE2_PZTOL", "3.0"));
        double vxLo = Double.parseDouble(System.getenv().getOrDefault("PKC_SOLVE2_VXLO", "-0.12"));
        double vxHi = Double.parseDouble(System.getenv().getOrDefault("PKC_SOLVE2_VXHI", "-0.03"));
        List<Node> candidates = keySearch(seam, tVzLo, tVzHi, vxLo, vxHi, provPz, pzTol);
        if (candidates.isEmpty()) { System.out.println("key search found no curved corridor-compliant seam in band; widen target"); return; }
        int maxTry = Integer.parseInt(System.getenv().getOrDefault("PKC_SOLVE2_MAXTRY", "40"));
        System.out.printf("1. %d curved corridor-compliant candidate seams; trying up to %d (tail SolveCore + joint translation)%n",
                candidates.size(), maxTry);
        SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);
        int tried = 0;
        for (Node node : candidates) {
            if (tried++ >= maxTry) break;
            int[] acts = actsOf(node, seam);
            float seedYaw = seam == 0 ? full.startYaw : (float) AL[acts[seam - 1]].yaw;
            JumpSpec tail = slice(seam, n, new Vec3dCore(node.px, dp.posY[seam], node.pz), new Vec3dCore(node.vx, 0.0, node.vz), seedYaw);
            double[] tailY = SolveCore.optimize(model, tail, budget, 20.0, 0.0, new AtomicBoolean(false), null);
            if (tailY == null) continue;
            double tailViol = viol(tail, tailY);
            if (tailViol > 0.0) continue;
            double[] tailGf = tail.asScenario().toGameFacings(Angles.wrapAll(tailY));
            double[] gf = new double[n];
            for (int t = 0; t < seam; t++) gf[t] = AL[acts[t]].yaw;
            for (int t = seam; t < n; t++) gf[t] = tailGf[t - seam];
            JumpPhysicsInputs sc = fullScenario(acts, seam, n);
            ForwardPath p = model.forward(sc, gf);
            double dxLo = -1e18, dxHi = 1e18, dzLo = -1e18, dzHi = 1e18;
            for (JumpConstraint c : fullSpec.constraints) {
                boolean isX = c.mode == JumpConstraint.Mode.X;
                double bound = c.rhs - (isX ? p.posX[c.t1] : p.posZ[c.t1]);
                if (c.cmp == JumpConstraint.Cmp.GE) { if (isX) dxLo = Math.max(dxLo, bound); else dzLo = Math.max(dzLo, bound); }
                else if (c.cmp == JumpConstraint.Cmp.LE) { if (isX) dxHi = Math.min(dxHi, bound); else dzHi = Math.min(dzHi, bound); }
            }
            double sx = sc.startPos.x, sz = sc.startPos.z;
            dxLo = Math.max(dxLo, 9.7 - sx); dxHi = Math.min(dxHi, 11.3 - sx);
            dzLo = Math.max(dzLo, 1.2 - sz); dzHi = Math.min(dzHi, 4.3 - sz);
            boolean feasible = dxLo <= dxHi + 1e-12 && dzLo <= dzHi + 1e-12;
            System.out.printf("   cand %d seam=(%.4f,%.4f) vel=(%.5f,%.5f) tail byte-exact; transWindow dx[%.4f,%.4f] dz[%.4f,%.4f] %s%n",
                    tried, node.px, node.pz, node.vx, node.vz, dxLo, dxHi, dzLo, dzHi, feasible ? "FEASIBLE" : "-");
            if (feasible) {
                double dx = 0.5 * (dxLo + dxHi), dz = 0.5 * (dzLo + dzHi);
                System.out.printf("%n*** NIX SOLVED COLD FROM t1 ***  start=(%.5f,%.5f) land=(%.5f,%.5f)%n",
                        sx + dx, sz + dz, p.posX[n] + dx, p.posZ[n] + dz);
                return;
            }
        }
        System.out.printf("no candidate seam admits a single translation over all 23 constraints (tried %d)%n", Math.min(tried, maxTry));
    }

    private List<Node> keySearch(int seam, double vzLo, double vzHi, double vxLo, double vxHi, double targetPz, double pzTol) {
        double vxCap = 0.20;
        List<Node> frontier = new ArrayList<>();
        frontier.add(new Node(0L, 0L, full.initialVelocity.x, full.initialVelocity.z, full.startPos.x, full.startPos.z));
        int pruned = 0;
        for (int t = 0; t < seam; t++) {
            HashMap<Long, Node> grid = new HashMap<>();
            for (Node nd : frontier) {
                for (int ai = 0; ai < AL.length; ai++) {
                    Node c = step(nd, ai, t);
                    if (Math.abs(c.vx) > vxCap) continue;
                    if (violatesSetupCons(t + 1, seam, c.px, c.pz)) { pruned++; continue; }
                    long key = ((Math.round(c.vz / 6e-4) * 90001L + Math.round(c.pz / 6e-3)) * 90001L
                            + Math.round(c.vx / 6e-4)) * 601L + Math.round(c.px / 6e-3);
                    if (!grid.containsKey(key)) grid.put(key, c);
                    if (grid.size() >= 90000) break;
                }
            }
            frontier = new ArrayList<>(grid.values());
            if (frontier.isEmpty()) return null;
        }
        System.out.printf("   (key search pruned %d off-corridor; %d seam states)%n", pruned, frontier.size());
        HashMap<Long, Node> diverse = new HashMap<>();
        for (Node nd : frontier) {
            if (nd.vz < vzLo || nd.vz > vzHi) continue;
            if (nd.vx < vxLo || nd.vx > vxHi) continue;
            if (Math.abs(nd.pz - targetPz) > pzTol) continue;
            long k = (Math.round(nd.vx / 0.01) * 9001L + Math.round(nd.pz / 0.05)) * 9001L + Math.round(nd.vz / 0.005);
            diverse.putIfAbsent(k, nd);
        }
        return new ArrayList<>(diverse.values());
    }

    private boolean violatesSetupCons(int tick, int seam, double px, double pz) {
        if (tick >= seam) return false;
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.t1 != tick) continue;
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

    private JumpPhysicsInputs fullScenario(int[] acts, int seam, int n) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startPos = full.startPos;
        sc.initialVelocity = full.initialVelocity;
        sc.startYaw = full.startYaw;
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
            boolean jmpGround = full.jumpPerTick != null && full.jumpPerTick[t]
                    && full.slipPerTick != null && !Double.isNaN(full.slipPerTick[t]);
            strafeMask[t] = !jmpGround; sprint[t] = true; fwd[t] = 0.98f; str[t] = 0.98f;
        }
        sc.strafePerTick = strafeMask;
        sc.sprintPerTick = sprint;
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = str;
        return sc;
    }

    private JumpSpec slice(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        JumpPhysicsInputs win = sliceScenario(full, a, c, pos, vel, yaw);
        List<JumpConstraint> cons = sliceConstraints(fullSpec, a, c);
        return new JumpSpec(win, cons, new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a));
    }

    private double viol(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
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
