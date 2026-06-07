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

/** Any axis/sense the user picks must yield a clean+landed solution (feasibility can't depend on the
 *  objective). Runs every endpoint objective on jumps the user reported failing, at the engine's FAST
 *  block budget (the default effort). */
public class DeriveF2fTest {

    /** Mirror AngleSolverEngine.blockBudget(FAST) == budgetFor(FAST). */
    private static SolveCore.Budget fastBlockBudget() {
        return new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);
    }

    private static List<JumpConstraint> footprints(DeriveProblem p) {
        int n = p.numTicks;
        List<JumpConstraint> fps = new ArrayList<>(DeriveSupport.landFootprint(n, p.land, p.half));
        int jump = p.scenario.jumpTick;
        if (p.start != null && jump > 0) fps.addAll(DeriveSupport.footprintAt(jump - 1, p.start, p.half));
        return fps;
    }

    private static BlockSolver.Result solve(DeriveProblem p, Objective obj) {
        List<JumpConstraint> fps = footprints(p);
        double[] landFp = DeriveSupport.expand(p.land, p.half);
        List<BlockSolver.Obstacle> obs = new ArrayList<>();
        for (AABB b : p.obstacles) obs.add(new BlockSolver.Obstacle(b));
        List<Objective> objs = new ArrayList<>();
        objs.add(obj);
        return new BlockSolver().solve(p.model, p.scenario, fps, landFp, obs, p.heights, objs,
                p.budget, p.sigmaDeg, p.feasTol, 40, new AtomicBoolean(false));
    }

    private void allObjectivesSolve(String fixture) {
        DeriveFixtures.Loaded f = DeriveFixtures.load(fixture, fastBlockBudget());
        DeriveProblem p = f.problem;
        int n = p.numTicks;
        StringBuilder sb = new StringBuilder("\n=== " + fixture + " (seg " + f.startTick + ".." + f.landingTick + ", FAST budget) ===\n");
        boolean allOk = true;
        for (Objective obj : new Objective[]{
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, n),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, n),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, n),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n)}) {
            long t0 = System.nanoTime();
            BlockSolver.Result r = solve(p, obj);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            boolean ok = r != null && r.ok();
            sb.append(String.format("%s/%s -> ok=%-5s clean=%s landed=%s land=(%.4f,%.4f) faces=%d (%d ms)%n",
                    obj.axis, obj.sense, ok, r != null && r.clean, r != null && r.landed,
                    r == null ? 0 : r.path.posX[n], r == null ? 0 : r.path.posZ[n], r == null ? -1 : r.faces.size(), ms));
            allOk &= ok;
        }
        System.out.println(sb);
        assertTrue("an objective failed to produce a clean+landed solution:" + sb, allOk);
    }

    @Test
    public void f2fAllObjectives() {
        allObjectivesSolve("f2f-block-only-error.json");
    }

    @Test
    public void j121AllObjectives() {
        allObjectivesSolve("j121-block-only-error.json");
    }

    @Test
    public void j154AllObjectives() {
        allObjectivesSolve("j154-block-only.json");
    }

    @Test
    public void j1097AllObjectives() {
        allObjectivesSolve("j1097-block-only.json");
    }
}
