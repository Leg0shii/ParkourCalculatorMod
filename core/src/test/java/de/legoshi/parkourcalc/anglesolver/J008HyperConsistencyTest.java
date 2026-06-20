package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.velocity.LandingPad;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.List;

public class J008HyperConsistencyTest {

    private static VelocityFinder build(SaveFile file, ExactJumpModel model, VelocityFinder.Accuracy acc) {
        final SaveFile.Start seed = file.angleSolver.seed;
        final int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick;
        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
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
        double[] pb = LandingPad.derive(landTickCons(file), landBox(file));
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        VelocityFinder f = new VelocityFinder(problem, model, anchor, lt, pad, null, 20_000L);
        f.setAccuracy(acc);
        return f;
    }

    /** Every velocity HYPER reports as a lander must be REAL: its solved TAS, realized and re-simmed,
     *  must satisfy the problem's wall constraints and land on the pad. Regression for the false
     *  positives caused when {@code evaluateViaEngine} forward-simmed the solver's absolute wrapped
     *  facings instead of {@code toGameFacings} (a mod-360 seam crossing shifts the MC sine bucket, so
     *  the landing it reported was off a garbage trajectory unrelated to the one it checked the walls on). */
    @Test
    public void hyperLandersAreReal() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j008-hyper-fp"));
        final ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        final SaveFile.Start seed = file.angleSolver.seed;

        VelocityFinder.Grid grid = new VelocityFinder.Grid(
                seed.vel[0] - 0.25, seed.vel[0] + 0.25, 0.02,
                seed.vel[2] - 0.25, seed.vel[2] + 0.25, 0.02);

        VelocityFinder f = build(file, model, VelocityFinder.Accuracy.HYPER);
        List<VelocityFinder.Candidate> cells = f.sweep(grid);
        int landers = 0, badWalls = 0;
        double worstViol = 0.0;
        for (VelocityFinder.Candidate c : cells) {
            if (c == null || !c.lands) continue;
            landers++;
            double worst = worstWallViolation(file, model, seed, c);
            worstViol = Math.max(worstViol, worst);
            if (worst > 1e-3) badWalls++;
        }
        System.out.printf("HYPER: %d cells, %d landers, %d violate walls (worst %.5f)%n",
                cells.size(), landers, badWalls, worstViol);
        org.junit.Assert.assertTrue("sweep must report HYPER landers (non-vacuous)", landers > 0);
        org.junit.Assert.assertEquals("every HYPER lander's realized TAS must meet the walls", 0, badWalls);
    }

    /** Realize the candidate's TAS exactly as Application.applyVelocityCandidate would (force-45 keys +
     *  locked yaws), re-sim it, and return the worst violation across the problem's wall constraints. */
    private static double worstWallViolation(SaveFile file, ExactJumpModel model, SaveFile.Start seed,
                                             VelocityFinder.Candidate c) {
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
            if (c.force45Mask != null && k < c.force45Mask.length && c.force45Mask[k]) {
                w = true; s = false; spr = true;
                boolean strafeThis = c.strafeMask != null && k < c.strafeMask.length && c.strafeMask[k];
                a = strafeThis && c.strafeSign > 0;
                d = strafeThis && c.strafeSign < 0;
            }
            in.forwardInputPerTick[k] = 0.98F * ((w ? 1 : 0) - (s ? 1 : 0));
            in.strafeInputPerTick[k] = 0.98F * ((a ? 1 : 0) - (d ? 1 : 0));
            jump[k] = row.keys.contains("JUMP");
            sprint[k] = spr;
            slip[k] = slipFor(file, st + k);
            locked[k] = true;
        }
        in.jumpPerTick = jump; in.sprintPerTick = sprint; in.slipPerTick = slip; in.yawLockedPerTick = locked;
        ForwardPath p = model.forward(in, c.yawsGameFacing);

        double worst = 0.0;
        AngleSolverState s = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, s);
        for (Integer tickKey : s.populatedTicks()) {
            int seg = tickKey - st;
            if (seg < 0 || seg > n) continue;
            TickConstraints tc = s.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            for (Constraint con : tc.getConstraints()) {
                if (!con.isEnabled() || con.isRange()) continue;
                double found = con.getField() == Constraint.Field.X ? p.posX[seg]
                        : con.getField() == Constraint.Field.Z ? p.posZ[seg] : Double.NaN;
                if (Double.isNaN(found)) continue;
                double v = con.getValue();
                double viol;
                switch (con.getOp()) {
                    case GE: case GT: viol = v - found; break;
                    case LE: case LT: viol = found - v; break;
                    default: viol = 0.0;
                }
                worst = Math.max(worst, viol);
            }
        }
        return worst;
    }

    private static List<Constraint> landTickCons(SaveFile file) {
        AngleSolverState s = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, s);
        TickConstraints tc = s.tickConstraintsOrNull(file.angleSolver.landingTick);
        return tc == null ? null : tc.getConstraints();
    }

    private static double[] landBox(SaveFile file) {
        for (SaveFile.BlockSel b : file.angleSolver.selectedBlocks) {
            if ("LAND".equals(b.kind)) return new double[]{b.box[0], b.box[3], b.box[2], b.box[5]};
        }
        return new double[]{-0.5, 0.5, -0.5, 0.5};
    }

    private static double slipFor(SaveFile file, int tick) {
        if (file.angleSolver.ticks != null) {
            for (SaveFile.Tick t : file.angleSolver.ticks) {
                if (t.tick == tick && t.override != null && t.override.slipperiness != null) {
                    String sl = t.override.slipperiness;
                    if ("DEFAULT".equals(sl)) return 0.6;
                    if ("SLIME".equals(sl)) return 0.8;
                    if ("ICE".equals(sl) || "PACKED_ICE".equals(sl)) return 0.98;
                    if ("BLUE_ICE".equals(sl)) return 0.989;
                }
            }
        }
        return Double.NaN;
    }
}
