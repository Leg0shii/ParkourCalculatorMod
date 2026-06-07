package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.anglesolver.solver.BlockSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/** Reproduce the exact engine sequence the user saw ("Solve from blocks" then "Solve") and assert parity:
 *  the block solve must land for X MIN at the FAST budget, which now equals the normal Solve budget so
 *  "Solve from blocks" can never search weaker than a follow-up "Solve" on the same derived constraints. */
public class DeriveError2Test {

    /** AngleSolverEngine.blockBudget(FAST) == budgetFor(FAST) after the unification. */
    private static SolveCore.Budget blockFast() {
        return new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);
    }

    private static SolveCore.Budget normalFast() {
        return new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);
    }

    @Test
    public void repro() {
        DeriveFixtures.Loaded f = DeriveFixtures.load("f2f-block-only-error2.json", blockFast());
        DeriveProblem p = f.problem;
        int n = p.numTicks;
        Objective xmin = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, n);

        List<JumpConstraint> footprints = new ArrayList<>(DeriveSupport.landFootprint(n, p.land, p.half));
        double[] landFp = DeriveSupport.expand(p.land, p.half);
        List<BlockSolver.Obstacle> obs = new ArrayList<>();
        for (AABB b : p.obstacles) obs.add(new BlockSolver.Obstacle(b));
        List<Objective> objs = new ArrayList<>();
        objs.add(xmin);

        // 1) Block solve at the block budget (what "Solve from blocks" runs).
        BlockSolver.Result r = new BlockSolver().solve(p.model, p.scenario, footprints, landFp, obs, p.heights,
                objs, blockFast(), p.sigmaDeg, p.feasTol, 40, new AtomicBoolean(false));
        System.out.println("[block solve, blockFast] ok=" + r.ok() + " clean=" + r.clean + " landed=" + r.landed
                + " faces=" + r.faces.size() + " land=(" + r.path.posX[n] + "," + r.path.posZ[n] + ")");

        // 2) "Click Solve after": normal solve at the normal budget on (footprints + the faces just written).
        List<JumpConstraint> tableCons = new ArrayList<>(footprints);
        for (BlockSolver.Face fc : r.faces) {
            tableCons.add(new JumpConstraint(fc.axisX ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z, fc.segTick,
                    null, JumpConstraint.Op.PLUS, fc.upper ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, fc.value, "face"));
        }
        Validation vNormal = solveAndCheck(p, tableCons, xmin, normalFast());
        System.out.println("[normal solve on written faces, normalFast] -> " + vNormal.describe());

        // 3) Block solve at the normal budget (would bumping the block budget fix it?).
        BlockSolver.Result r2 = new BlockSolver().solve(p.model, p.scenario, footprints, landFp, obs, p.heights,
                objs, normalFast(), p.sigmaDeg, p.feasTol, 40, new AtomicBoolean(false));
        System.out.println("[block solve, normalFast budget] ok=" + r2.ok() + " clean=" + r2.clean + " landed=" + r2.landed
                + " faces=" + r2.faces.size());

        assertTrue("Solve from blocks must land X MIN at the FAST budget", r.ok());
        assertTrue("follow-up Solve on the derived constraints must also land", vNormal.valid);
    }

    private static Validation solveAndCheck(DeriveProblem p, List<JumpConstraint> cons, Objective obj, SolveCore.Budget budget) {
        DeriveOracle oracle = new DeriveOracle(p.scenario, p.obstacles, p.land, p.heights, p.half, p.model,
                budget, p.sigmaDeg, p.feasTol, new AtomicBoolean(false));
        return oracle.validate(cons, obj);
    }
}
