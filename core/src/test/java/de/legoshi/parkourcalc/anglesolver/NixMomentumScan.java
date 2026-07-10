package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
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

public class NixMomentumScan {

    private ExactJumpModel model;
    private JumpPhysicsInputs full;
    private JumpSpec fullSpec;

    private static final class Act {
        final double yaw; final float fwd; final float strafe; final boolean sprint; final String label;
        Act(double yaw, float fwd, float strafe, boolean sprint, String label) {
            this.yaw = yaw; this.fwd = fwd; this.strafe = strafe; this.sprint = sprint; this.label = label;
        }
    }

    private static Act[] buildActs() {
        List<Act> a = new ArrayList<>();
        double[] faces = {0, 45, 90, 135, 180, 225, 270, 315, 20, 70, 110, 160, 200, 250, 290, 340};
        for (double f : faces) {
            a.add(new Act(f, 0.98f, 0f, true, "W@" + f));
            a.add(new Act(f, 0.98f, 0.98f, true, "WA@" + f));
        }
        for (double f : new double[]{0, 90, 180, 270}) {
            a.add(new Act(f, -0.98f, 0f, false, "S@" + f));
            a.add(new Act(f, 0f, 0.98f, false, "A@" + f));
            a.add(new Act(f, 0.98f, 0f, false, "Wns@" + f));
            a.add(new Act(f, 0f, 0f, false, "idle@" + f));
        }
        return a.toArray(new Act[0]);
    }

    private static final Act[] ACTS = buildActs();

    private static final class Node {
        final long[] acts;
        final double px, pz, vx, vz;
        final double sx, sz;
        Node(long[] acts, double sx, double sz, double px, double pz, double vx, double vz) {
            this.acts = acts; this.sx = sx; this.sz = sz;
            this.px = px; this.pz = pz; this.vx = vx; this.vz = vz;
        }
    }

    private static long[] withAct(long[] acts, int t, int ai) {
        long[] out = acts.clone();
        int bit = t * 6;
        out[bit >> 6] |= ((long) ai) << (bit & 63);
        if ((bit & 63) > 58) out[(bit >> 6) + 1] |= ((long) ai) >>> (64 - (bit & 63));
        return out;
    }

    private static int actAt(long[] acts, int t) {
        int bit = t * 6;
        long v = acts[bit >> 6] >>> (bit & 63);
        if ((bit & 63) > 58) v |= acts[(bit >> 6) + 1] << (64 - (bit & 63));
        return (int) (v & 63);
    }

    @Test
    public void scan() throws Exception {
        String path = System.getenv("PKC_MS_FILE");
        org.junit.Assume.assumeTrue("set PKC_MS_FILE", path != null && !path.isEmpty());
        int horizon = Integer.parseInt(System.getenv().getOrDefault("PKC_MS_HORIZON", "50"));
        int cap = Integer.parseInt(System.getenv().getOrDefault("PKC_MS_CAP", "500000"));
        long t0 = System.nanoTime();
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setStartTick(0);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        fullSpec = engine.debugBuildSpec();
        full = fullSpec.asScenario();
        System.out.printf(Locale.ROOT, "=== NixMomentumScan %s horizon=%d acts=%d cap=%d ===%n",
                new File(path).getName(), horizon, ACTS.length, cap);

        List<Node> frontier = new ArrayList<>();
        for (double sx = 9.8; sx <= 11.3 + 1e-9; sx += 0.15) {
            for (double sz = 1.6; sz <= 7.2 + 1e-9; sz += 0.2) {
                frontier.add(new Node(new long[5], sx, sz, sx, sz, 0.0, 0.0));
            }
        }
        System.out.printf(Locale.ROOT, "seeded %d start positions%n", frontier.size());

        for (int t = 0; t < horizon; t++) {
            final int tt = t;
            if (tt >= 12) {
                frontier.sort(Comparator.comparingDouble((Node nd) -> -score(nd, tt)));
            }
            HashMap<Long, Node> grid = new HashMap<>();
            List<Node> prev = frontier;
            for (int pi = 0; pi < prev.size(); pi++) {
                Node nd = prev.get(pi);
                for (int ai = 0; ai < ACTS.length; ai++) {
                    Node c = step(nd, ai, tt);
                    if (c == null) continue;
                    if (violatesBox(tt + 1, c.px, c.pz)) continue;
                    if (!viable(tt + 1, c.px, c.pz)) continue;
                    long key = ((Math.round(c.vz / 3e-4) * 250007L + Math.round(c.vx / 3e-4)) * 250007L
                            + Math.round(c.pz / 2.5e-2)) * 1021L + Math.round(c.px / 2.5e-2);
                    Node old = grid.get(key);
                    if (old == null) {
                        if (grid.size() < cap) grid.put(key, c);
                    } else if (score(c, tt + 1) > score(old, tt + 1)) {
                        grid.put(key, c);
                    }
                }
            }
            frontier = new ArrayList<>(grid.values());
            if (t >= 40 || t % 10 == 9 || t == horizon - 1) {
                double bestVz = Double.NEGATIVE_INFINITY;
                for (Node nd : frontier) bestVz = Math.max(bestVz, nd.vz);
                System.out.printf(Locale.ROOT, "t=%d frontier=%d maxVz=%.4f (%.0fs)%n", t + 1, frontier.size(), bestVz, sec(t0));
            }
            if (frontier.isEmpty()) return;
        }

        System.out.println("t50 envelope near arc entry (x 9.3-9.8, z 6.3-7.2):");
        List<Node> cands = new ArrayList<>();
        for (Node nd : frontier) {
            if (nd.px >= 9.3 && nd.px <= 9.8 && nd.pz >= 6.3 && nd.pz <= 7.2) cands.add(nd);
        }
        cands.sort(Comparator.comparingDouble((Node nd) -> -(nd.vz - 0.4 * nd.vx)));
        for (int i = 0; i < Math.min(25, cands.size()); i++) {
            Node nd = cands.get(i);
            System.out.printf(Locale.ROOT, "CAND pos=(%.4f,%.4f) vel=(%+.4f,%+.4f) score=%.4f%n",
                    nd.px, nd.pz, nd.vx, nd.vz, nd.vz - 0.4 * nd.vx);
        }
        if (!cands.isEmpty()) {
            Node best = cands.get(0);
            System.out.printf(Locale.ROOT, "BEST start=(%.3f, %.3f)%n", best.sx, best.sz);
            for (int t = 0; t < horizon; t++) {
                System.out.printf(Locale.ROOT, "ACT %d %s%n", t, ACTS[actAt(best.acts, t)].label);
            }
        }
    }

    private Node step(Node parent, int ai, int t) {
        Act a = ACTS[ai];
        boolean grounded = !Double.isNaN(full.slipAt(t));
        boolean jump = full.jumpAt(t);
        if (a.sprint && a.fwd <= 0) return null;
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(parent.px, full.startPos.y, parent.pz);
        sc.initialVelocity = new Vec3dCore(parent.vx, 0.0, parent.vz);
        sc.startYaw = (float) a.yaw;
        sc.incomingSprint = t == 0 ? Boolean.FALSE : null;
        sc.incomingAmp = 0;
        sc.strafeSign = 1;
        sc.jumpPerTick = new boolean[]{jump};
        sc.slipPerTick = new double[]{full.slipAt(t)};
        sc.strafePerTick = new boolean[]{false};
        sc.sprintPerTick = new boolean[]{a.sprint};
        sc.forwardInputPerTick = new float[]{a.fwd};
        sc.strafeInputPerTick = new float[]{a.strafe};
        sc.speedAmplifier = new int[]{0};
        sc.yawLockedPerTick = new boolean[]{false};
        ForwardPath p = model.forward(sc, new double[]{a.yaw});
        return new Node(withAct(parent.acts, t, ai), parent.sx, parent.sz, p.posX[1], p.posZ[1], p.velX[1], p.velZ[1]);
    }

    private static final int[] BOX_TICKS = {24, 25, 36, 37, 48, 49};

    private double score(Node nd, int tick) {
        double base = tick >= 25 ? nd.vz - 0.4 * nd.vx : -nd.vz;
        double debt = 0.0;
        for (int k : BOX_TICKS) {
            if (k <= tick) continue;
            double dx = nd.px < 9.7 ? 9.7 - nd.px : (nd.px > 11.3 ? nd.px - 11.3 : 0.0);
            double dz = nd.pz < 1.5125 ? 1.5125 - nd.pz : (nd.pz > 7.3 ? nd.pz - 7.3 : 0.0);
            double slack = 0.30 * (k - tick);
            debt += Math.max(0.0, dx - slack) + Math.max(0.0, dz - slack);
            break;
        }
        return base - 8.0 * debt;
    }

    private boolean viable(int tick, double px, double pz) {
        for (int k : BOX_TICKS) {
            if (k <= tick) continue;
            double budget = 0.35 * (k - tick);
            double dx = px < 9.7 ? 9.7 - px : (px > 11.3 ? px - 11.3 : 0.0);
            double dz = pz < 1.5125 ? 1.5125 - pz : (pz > 7.3 ? pz - 7.3 : 0.0);
            if (dx > budget || dz > budget) return false;
        }
        return true;
    }

    private boolean violatesBox(int tick, double px, double pz) {
        if (pz < 1.2) return true;
        for (JumpConstraint c : fullSpec.constraints) {
            if (c.t1 != tick || c.t2 != null || tick >= 50) continue;
            double val = c.mode == JumpConstraint.Mode.X ? px : (c.mode == JumpConstraint.Mode.Z ? pz : Double.NaN);
            if (Double.isNaN(val)) continue;
            if (c.cmp == JumpConstraint.Cmp.GE && val < c.rhs - 1e-9) return true;
            if (c.cmp == JumpConstraint.Cmp.LE && val > c.rhs + 1e-9) return true;
        }
        return false;
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }
}
