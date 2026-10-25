package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/** The velocity map enforces only the angle solver's own constraints, never a synthetic landing pad: a
 *  velocity counts as a hit iff every constraint is met, wherever the jump lands. bfsetup2 is a pure
 *  constraint + objective jump (no land block) whose solution travels ~2 blocks from the start; the old
 *  pad-walls rejected every such velocity. */
public class VelocityFinderConstraintTest {

    private static VelocityFinder build(SaveFile file, VelocityFinder.Accuracy acc) {
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
        VelocityFinder f = new VelocityFinder(problem, ExactJumpModel.forMcVersion(file.mcVersion), anchor, lt, null, 20_000L);
        f.setAccuracy(acc);
        return f;
    }

    @Test
    public void feasibleVelocitiesAreAcceptedFarFromStart() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("bfsetup2"));
        final double startZ = file.angleSolver.seed.pos[2];

        VelocityFinder f = build(file, VelocityFinder.Accuracy.ACCURATE);
        VelocityFinder.Grid grid = new VelocityFinder.Grid(0.40, 0.62, 0.02, -0.12, 0.06, 0.02);
        List<VelocityFinder.Candidate> cells = f.sweep(grid);

        int feasible = 0, farFeasible = 0;
        for (VelocityFinder.Candidate c : cells) {
            if (c == null || !c.lands) continue;
            feasible++;
            if (Math.abs(c.landZ - startZ) > 1.0) farFeasible++;
        }
        System.out.printf("bfsetup2: %d cells, %d feasible, %d feasible far (>1 block) from start%n",
                cells.size(), feasible, farFeasible);
        assertTrue("constraint-only feasibility must accept velocities", feasible > 0);
        assertTrue("a feasible landing far from the start proves no synthetic pad is enforced", farFeasible > 0);
    }

    @Test
    public void offsetIsMeasuredAtTheConstraintsTick() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("bfsetup2"));
        final double edge = -1603.7;
        final int st = file.angleSolver.startTick;
        // bfsetup2: objective X MIN, last X<= constraint is at tick 12; offset is read there, not at the landing.
        VelocityFinder f = build(file, VelocityFinder.Accuracy.ACCURATE);
        f.setObjectiveConstraint(edge, 12 - st);

        VelocityFinder.Candidate hit = null;
        for (VelocityFinder.Candidate c : f.sweep(new VelocityFinder.Grid(0.40, 0.62, 0.02, -0.12, 0.06, 0.02))) {
            if (c != null && c.lands) { hit = c; break; }
        }
        assertTrue("need a feasible cell", hit != null);

        double offset = f.offsetOf(hit);
        double landingBased = Math.abs(hit.landX - edge);
        System.out.printf("offset@tick12=%.6f  landing-based=%.6f  landX=%.5f%n", offset, landingBased, hit.landX);
        assertTrue("offset is the hug margin at the constraint's tick, not the landing displacement",
                Math.abs(offset) < 0.1);
        assertTrue("landing-based offset would be much larger, proving they differ", landingBased > 0.3);
    }
}
