package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
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
import java.util.HashMap;
import java.util.List;

public class NixSetupSearch {

    private static final class Act {
        final double yaw;
        final float fwd;
        final float strafe;
        final boolean sprint;
        final String tag;
        Act(double yaw, float fwd, float strafe, boolean sprint, String tag) {
            this.yaw = yaw; this.fwd = fwd; this.strafe = strafe; this.sprint = sprint; this.tag = tag;
        }
    }

    private static final Act[] ALPHABET = {
        new Act(45, 1.0f, 1.0f, false, "WA "),
        new Act(45, 1.0f, 1.0f, true, "WA+"),
        new Act(45, -1.0f, -1.0f, false, "SD "),
        new Act(45, -1.0f, -1.0f, true, "SD+"),
        new Act(0, 1.0f, 0.0f, false, "W0 "),
        new Act(0, 1.0f, 0.0f, true, "W0+"),
        new Act(0, -1.0f, 0.0f, false, "S0 "),
        new Act(0, 0.0f, 0.0f, false, "-- "),
    };

    private static final class Node {
        final int[] acts;
        final double vx, vz, px, pz;
        Node(int[] acts, double vx, double vz, double px, double pz) {
            this.acts = acts; this.vx = vx; this.vz = vz; this.px = px; this.pz = pz;
        }
    }

    @Test
    public void search() throws Exception {
        String path = System.getenv("PKC_SETUP_FILE");
        org.junit.Assume.assumeTrue("set PKC_SETUP_FILE", path != null && !path.isEmpty());
        int seam = Integer.parseInt(System.getenv().getOrDefault("PKC_SETUP_SEAM", "30"));
        double bandLo = Double.parseDouble(System.getenv().getOrDefault("PKC_SETUP_BAND_LO", "0.20"));
        double bandHi = Double.parseDouble(System.getenv().getOrDefault("PKC_SETUP_BAND_HI", "0.28"));
        double targetPz = Double.parseDouble(System.getenv().getOrDefault("PKC_SETUP_TARGET_PZ", "3.194"));
        double pzTol = Double.parseDouble(System.getenv().getOrDefault("PKC_SETUP_PZ_TOL", "0.65"));
        int cap = Integer.parseInt(System.getenv().getOrDefault("PKC_SETUP_CAP", "60000"));
        double axisEps = 0.02;

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs full = spec.asScenario();

        System.out.printf("=== NixSetupSearch %s seam=%d band=[%.2f,%.2f] seamZ~%.3f+-%.2f cap=%d ===%n",
                new File(path).getName(), seam, bandLo, bandHi, targetPz, pzTol, cap);
        mechanismCheck(model, full, seam);

        List<Node> frontier = new ArrayList<>();
        frontier.add(new Node(new int[0], full.initialVelocity.x, full.initialVelocity.z, full.startPos.x, full.startPos.z));
        for (int t = 0; t < seam; t++) {
            HashMap<Long, Node> grid = new HashMap<>();
            for (Node nd : frontier) {
                for (int ai = 0; ai < ALPHABET.length; ai++) {
                    Node c = step(model, full, nd, ai, t);
                    if (Math.abs(c.vx) > axisEps) continue;
                    long key = gridKey(c);
                    if (!grid.containsKey(key)) grid.put(key, c);
                    if (grid.size() >= cap) break;
                }
            }
            frontier = new ArrayList<>(grid.values());
            if (frontier.isEmpty()) { System.out.printf("frontier empty at t=%d%n", t); return; }
        }

        Node best = null;
        Node bestVzOnly = null;
        Node bestInBandPos = null;
        for (Node nd : frontier) {
            if (bestVzOnly == null || Math.abs(nd.vz - 0.207) < Math.abs(bestVzOnly.vz - 0.207)) bestVzOnly = nd;
            boolean inBand = nd.vz >= bandLo && nd.vz <= bandHi;
            if (!inBand) continue;
            if (bestInBandPos == null || Math.abs(nd.pz - targetPz) < Math.abs(bestInBandPos.pz - targetPz)) bestInBandPos = nd;
            if (Math.abs(nd.pz - targetPz) <= pzTol) {
                double sc = Math.abs(nd.pz - targetPz);
                if (best == null || sc < Math.abs(best.pz - targetPz)) best = nd;
            }
        }
        System.out.printf("frontier@seam size=%d%n", frontier.size());
        if (best != null) {
            System.out.printf("COLD SEAM FOUND (band vel + reconcilable pos): vel=(%.5f,%.5f) pos=(%.4f,%.4f)%n",
                    best.vx, best.vz, best.px, best.pz);
            System.out.printf("  [proven seam vel=(0.00288,0.20688) pos=(11.3023,3.1942)]%n");
            System.out.printf("  key pattern (t0..t%d): %s%n", seam - 1, patternOf(best));
        } else {
            System.out.printf("NO seam with band vel AND reconcilable pos found cold.%n");
            if (bestInBandPos != null) System.out.printf("  in-band, closest pos: vel=(%.5f,%.5f) pos=(%.4f,%.4f) [need seamZ in %.2f+-%.2f]%n",
                    bestInBandPos.vx, bestInBandPos.vz, bestInBandPos.px, bestInBandPos.pz, targetPz, pzTol);
            System.out.printf("  closest-vz overall: vel=(%.5f,%.5f) pos=(%.4f,%.4f)%n",
                    bestVzOnly.vx, bestVzOnly.vz, bestVzOnly.px, bestVzOnly.pz);
        }
    }

    private void mechanismCheck(ExactJumpModel model, JumpPhysicsInputs full, int seam) {
        Node nd = new Node(new int[0], 0, 0, full.startPos.x, full.startPos.z);
        int len = Math.min(8, seam);
        double vzBuild = 0, vzMid = 0;
        double maxVx = 0;
        for (int t = 0; t < len; t++) {
            nd = step(model, full, nd, t < len / 2 ? 0 : 2, t);
            maxVx = Math.max(maxVx, Math.abs(nd.vx));
            if (t == len / 2 - 1) vzBuild = nd.vz;
            if (t == len - 1) vzMid = nd.vz;
        }
        System.out.printf("mechanism check (W+A x%d then S+D x%d @45, non-sprint): max|vx|=%.3e vz build=%.5f cancel=%.5f%n",
                len / 2, len - len / 2, maxVx, vzBuild, vzMid);
    }

    private Node step(ExactJumpModel model, JumpPhysicsInputs full, Node parent, int ai, int t) {
        Act a = ALPHABET[ai];
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(parent.px, full.startPos.y, parent.pz);
        sc.initialVelocity = new Vec3dCore(parent.vx, 0.0, parent.vz);
        sc.startYaw = (float) a.yaw;
        sc.strafeSign = full.strafeSign;
        boolean jmp = full.jumpPerTick != null && t < full.jumpPerTick.length && full.jumpPerTick[t];
        double slip = full.slipPerTick != null && t < full.slipPerTick.length ? full.slipPerTick[t] : Double.NaN;
        sc.jumpPerTick = new boolean[]{jmp};
        sc.slipPerTick = new double[]{slip};
        sc.strafePerTick = new boolean[]{false};
        sc.sprintPerTick = new boolean[]{a.sprint};
        sc.forwardInputPerTick = new float[]{a.fwd * 0.98f};
        sc.strafeInputPerTick = new float[]{a.strafe * 0.98f};
        sc.speedAmplifier = new int[]{0};
        sc.yawLockedPerTick = new boolean[]{false};
        ForwardPath p = model.forward(sc, new double[]{a.yaw});
        int[] acts = new int[t + 1];
        System.arraycopy(parent.acts, 0, acts, 0, t);
        acts[t] = ai;
        return new Node(acts, p.velX[1], p.velZ[1], p.posX[1], p.posZ[1]);
    }

    private static long gridKey(Node c) {
        long a = Math.round(c.vz / 4.0e-4);
        long b = Math.round(c.pz / 4.0e-3);
        long d = Math.round(c.vx / 4.0e-4);
        return (a * 200003L + b) * 401L + d;
    }

    private String patternOf(Node nd) {
        StringBuilder sb = new StringBuilder();
        for (int a : nd.acts) sb.append(ALPHABET[a].tag);
        return sb.toString();
    }
}
