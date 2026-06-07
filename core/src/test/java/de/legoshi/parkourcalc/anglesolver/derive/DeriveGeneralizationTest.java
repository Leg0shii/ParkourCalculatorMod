package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.anglesolver.solver.BlockSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/** Generalization gate for the PRODUCTION {@link BlockSolver}: it must wrap not just the exact 3-block
 *  j154 layout but every obstacle subset of that arc (head-only, cubes-only, none, ...). Each result is
 *  asserted ok() AND independently re-validated against the swept oracle, so this proves the algorithm is
 *  not overfit to one layout and never reports a clipping/missing solution as solved. */
public class DeriveGeneralizationTest {

    @Test
    public void blockSolverGeneralizesOverObstacleSubsets() {
        DeriveFixtures.Loaded f = DeriveFixtures.load("j154-fails-nr3.json");
        DeriveProblem base = f.problem;
        List<AABB> all = base.obstacles; // [cube1, cube2, head]

        Object[][] cases = {
                {"none", new ArrayList<AABB>()},
                {"cubes-only", new ArrayList<>(Arrays.asList(all.get(0), all.get(1)))},
                {"head-only", new ArrayList<>(Arrays.asList(all.get(2)))},
                {"cube-top+head", new ArrayList<>(Arrays.asList(all.get(1), all.get(2)))},
                {"full", new ArrayList<>(all)},
        };

        StringBuilder report = new StringBuilder("\n=== BlockSolver generalization (j154 arc, obstacle subsets) ===\n");
        boolean allOk = true;
        for (Object[] c : cases) {
            String nm = (String) c[0];
            @SuppressWarnings("unchecked")
            List<AABB> obs = (List<AABB>) c[1];

            List<JumpConstraint> footprints = DeriveSupport.landFootprint(base.numTicks, base.land, base.half);
            double[] landFp = DeriveSupport.expand(base.land, base.half);
            List<BlockSolver.Obstacle> obstacles = new ArrayList<>();
            for (AABB b : obs) obstacles.add(new BlockSolver.Obstacle(b));
            List<Objective> objectives = DeriveSupport.endpointObjectives(base.scenario, base.land, base.numTicks);

            long t0 = System.nanoTime();
            BlockSolver.Result r = new BlockSolver().solve(base.model, base.scenario, footprints, landFp, obstacles,
                    base.heights, objectives, base.budget, base.sigmaDeg, base.feasTol, 40, new AtomicBoolean(false));
            long ms = (System.nanoTime() - t0) / 1_000_000L;

            // Independent oracle re-check of the returned solution (not trusting BlockSolver's own verdict).
            DeriveOracle oracle = new DeriveOracle(base.scenario, obs, base.land, base.heights, base.half,
                    base.model, base.budget, base.sigmaDeg, base.feasTol, new AtomicBoolean(false));
            Validation v = (r != null && r.yaws != null)
                    ? oracle.validateGameFacings(base.scenario.toGameFacings(r.yaws)) : Validation.cancelled();

            boolean ok = r != null && r.ok() && v.valid;
            report.append(String.format("%-16s obs=%d ok=%-5s faces=%-3d oracle:%s (%d ms)%n",
                    nm, obs.size(), r != null && r.ok(), r != null ? r.faces.size() : -1, v.describe(), ms));
            allOk &= ok;
        }
        System.out.println(report);
        assertTrue("BlockSolver failed a generalization subset case" + report, allOk);
    }
}
