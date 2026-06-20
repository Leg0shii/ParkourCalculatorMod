package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.List;

public class J008VelocityApplyTest {

    @Test
    public void j008_findApplyResimLands() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j008-bfneo"));
        final SaveFile.Start seed = file.angleSolver.seed;
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick;

        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                s.setEffort(AngleSolverState.Effort.FAST);
                return s;
            }
            public InputData newInputs() {
                InputData in = new InputData();
                SaveIO.applyRowsTo(file, in);
                return in;
            }
        };
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(st,
                new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]), seed.yaw, seed.vel[1], file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(0.0, 1.0, 1.0, 2.0); // j008 LAND block footprint
        VelocityFinder finder = new VelocityFinder(problem, model, anchor, lt, pad, null, 20_000L);

        // FIND
        VelocityFinder.Grid grid = new VelocityFinder.Grid(-0.30, 0.00, 0.05, 0.02, 0.14, 0.02);
        List<VelocityFinder.Candidate> ranked = VelocityFinder.rankedLanders(finder.sweep(grid));
        System.out.println("j008 band: " + ranked.size() + " landers");
        org.junit.Assert.assertTrue("band must be non-empty", !ranked.isEmpty());

        // APPLY (rebuild TAS exactly as applyVelocityCandidate) + RE-SIM
        int checked = 0, landed = 0;
        for (int i = 0; i < Math.min(6, ranked.size()); i++) {
            VelocityFinder.Candidate c = ranked.get(i);
            JumpPhysicsInputs applied = appliedScenario(file, seed, c.vx, c.vz);
            ForwardPath p = model.forward(applied, c.yawsGameFacing);
            double rx = p.posX[lt - st], rz = p.posZ[lt - st];
            boolean onPad = rx >= pad.x0 - 0.3 && rx <= pad.x1 + 0.3 && rz >= pad.z0 - 0.3 && rz <= pad.z1 + 0.3;
            boolean matchesFinder = Math.abs(rx - c.landX) < 1e-9 && Math.abs(rz - c.landZ) < 1e-9;
            System.out.printf("  lander v0=(%+.3f, %+.3f): applied-TAS re-sim lands (%.4f, %.4f) | finder said (%.4f, %.4f) | onPad=%s match=%s%n",
                    c.vx, c.vz, rx, rz, c.landX, c.landZ, onPad, matchesFinder);
            checked++;
            if (onPad) landed++;
        }
        System.out.printf(">>> find->apply->resim: %d/%d landers' rebuilt TAS re-sim onto the pad%n", landed, checked);
        org.junit.Assert.assertEquals("every applied lander's rebuilt TAS must re-sim onto the pad", checked, landed);
    }

    @Test
    public void j008_force45RealizeMatchesFinder() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j008-bfneo"));
        final SaveFile.Start seed = file.angleSolver.seed;
        final ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        final int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick;

        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                s.setDefaultInputs(AngleSolverState.InputMode.FORCE_45);
                s.setEffort(AngleSolverState.Effort.FAST);
                return s;
            }
            public InputData newInputs() {
                InputData in = new InputData();
                SaveIO.applyRowsTo(file, in);
                for (InputRow r : in.getRows()) {
                    r.setKeyActive(InputRow.Key.A, false);
                    r.setKeyActive(InputRow.Key.D, false);
                }
                return in;
            }
        };
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(st,
                new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]), seed.yaw, seed.vel[1], file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(0.0, 1.0, 1.0, 2.0);
        VelocityFinder finder = new VelocityFinder(problem, model, anchor, lt, pad, null, 20_000L);

        VelocityFinder.Grid grid = new VelocityFinder.Grid(-0.30, 0.00, 0.05, 0.02, 0.14, 0.02);
        List<VelocityFinder.Candidate> ranked = VelocityFinder.rankedLanders(finder.sweep(grid));
        org.junit.Assert.assertFalse("band must be non-empty", ranked.isEmpty());

        int checked = 0, realizedMatch = 0, literalDiffered = 0;
        double worstRealized = 0.0, worstLiteral = 0.0;
        for (int i = 0; i < Math.min(6, ranked.size()); i++) {
            VelocityFinder.Candidate c = ranked.get(i);
            org.junit.Assert.assertNotNull("lander must carry a force-45 mask", c.force45Mask);

            ForwardPath realized = model.forward(realizedScenario(file, seed, c, true), c.yawsGameFacing);
            ForwardPath literal = model.forward(realizedScenario(file, seed, c, false), c.yawsGameFacing);
            double rDiff = Math.hypot(realized.posX[lt - st] - c.landX, realized.posZ[lt - st] - c.landZ);
            double lDiff = Math.hypot(literal.posX[lt - st] - c.landX, literal.posZ[lt - st] - c.landZ);
            worstRealized = Math.max(worstRealized, rDiff);
            worstLiteral = Math.max(worstLiteral, lDiff);

            checked++;
            if (rDiff < 1e-9) realizedMatch++;
            if (lDiff > 1e-6) literalDiffered++;
            System.out.printf("  v0=(%+.3f,%+.3f): realizedDiff=%.6f literalDiff=%.6f%n", c.vx, c.vz, rDiff, lDiff);
        }
        System.out.printf(">>> force-45 realize: %d/%d match finder (worst %.2e); literal diverged on %d (worst %.4f)%n",
                realizedMatch, checked, worstRealized, literalDiffered, worstLiteral);
        org.junit.Assert.assertEquals("realized (force-45) rebuild must reproduce the finder path byte-exact",
                checked, realizedMatch);
        org.junit.Assert.assertTrue("literal rebuild must diverge somewhere, proving the realize is needed",
                literalDiffered > 0);
    }

    private static JumpPhysicsInputs realizedScenario(SaveFile file, SaveFile.Start seed,
                                                      VelocityFinder.Candidate c, boolean realize) {
        int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick, n = lt - st;
        JumpPhysicsInputs in = new JumpPhysicsInputs(n);
        in.startPos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
        in.startYaw = seed.yaw;
        in.initialVelocity = new Vec3dCore(c.vx, seed.vel[1], c.vz);
        in.forwardInputPerTick = new float[n];
        in.strafeInputPerTick = new float[n];
        boolean[] jump = new boolean[n], sprint = new boolean[n], locked = new boolean[n];
        double[] slip = new double[n];
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = file.rows.get(st + k);
            boolean w = row.keys.contains("W"), s = row.keys.contains("S");
            boolean a = false, d = false, spr = row.keys.contains("SPRINT");
            if (realize && c.force45Mask[k]) {
                w = true;
                s = false;
                spr = true;
                boolean strafeThis = c.strafeMask != null && k < c.strafeMask.length && c.strafeMask[k];
                a = strafeThis && c.strafeSign > 0;
                d = strafeThis && c.strafeSign < 0;
            }
            in.forwardInputPerTick[k] = 0.98F * ((w ? 1 : 0) - (s ? 1 : 0));
            in.strafeInputPerTick[k] = 0.98F * ((a ? 1 : 0) - (d ? 1 : 0));
            jump[k] = row.keys.contains("JUMP");
            sprint[k] = spr;
            slip[k] = effSlip(file, st + k);
            locked[k] = true;
        }
        in.jumpPerTick = jump;
        in.sprintPerTick = sprint;
        in.slipPerTick = slip;
        in.yawLockedPerTick = locked;
        return in;
    }

    private static JumpPhysicsInputs appliedScenario(SaveFile file, SaveFile.Start seed, double vx, double vz) {
        int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick, n = lt - st;
        JumpPhysicsInputs in = new JumpPhysicsInputs(n);
        in.startPos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
        in.startYaw = seed.yaw;
        in.initialVelocity = new Vec3dCore(vx, seed.vel[1], vz);
        in.forwardInputPerTick = new float[n];
        in.strafeInputPerTick = new float[n];
        boolean[] jump = new boolean[n], sprint = new boolean[n], locked = new boolean[n];
        double[] slip = new double[n];
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = file.rows.get(st + k);
            in.forwardInputPerTick[k] = 0.98F * ((row.keys.contains("W") ? 1 : 0) - (row.keys.contains("S") ? 1 : 0));
            in.strafeInputPerTick[k] = 0.98F * ((row.keys.contains("A") ? 1 : 0) - (row.keys.contains("D") ? 1 : 0));
            jump[k] = row.keys.contains("JUMP");
            sprint[k] = row.keys.contains("SPRINT");
            slip[k] = slipFor(file, st + k);
            locked[k] = true;
        }
        in.jumpPerTick = jump; in.sprintPerTick = sprint; in.slipPerTick = slip; in.yawLockedPerTick = locked;
        return in;
    }

    private static double effSlip(SaveFile file, int tick) {
        String name = file.angleSolver.defaultSlipperiness;
        if (file.angleSolver.ticks != null) {
            for (SaveFile.Tick t : file.angleSolver.ticks) {
                if (t.tick == tick && t.override != null && t.override.slipperiness != null) {
                    name = t.override.slipperiness;
                    break;
                }
            }
        }
        if ("DEFAULT".equals(name)) return 0.6;
        if ("SLIME".equals(name)) return 0.8;
        if ("ICE".equals(name) || "PACKED_ICE".equals(name)) return 0.98;
        if ("BLUE_ICE".equals(name)) return 0.989;
        return Double.NaN;
    }

    private static double slipFor(SaveFile file, int tick) {
        if (file.angleSolver.ticks != null) {
            for (SaveFile.Tick t : file.angleSolver.ticks) {
                if (t.tick == tick && t.override != null && t.override.slipperiness != null) {
                    if ("DEFAULT".equals(t.override.slipperiness)) return 0.6;
                    if ("SLIME".equals(t.override.slipperiness)) return 0.8;
                    if ("ICE".equals(t.override.slipperiness) || "PACKED_ICE".equals(t.override.slipperiness)) return 0.98;
                    if ("BLUE_ICE".equals(t.override.slipperiness)) return 0.989;
                }
            }
        }
        return 1.0;
    }
}
