package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class DiskSocpKernelProbe {

    private static final double MATCH = 2.0e-5;

    @Test
    public void j005MatchesCoptDisk() {
        check("j005", -41.294958);
    }

    @Test
    public void j016MatchesCoptDisk() {
        check("j016-X2jmmp2p", -4.857906);
    }

    @Test
    public void j019MatchesCoptDisk() {
        check("j019-3jmmtruenix", -13.303208);
    }

    @Test
    public void j022MatchesCoptDisk() {
        check("j022-1bmhbfly", -531.700145);
    }

    @Test
    public void j008bMatchesCoptDisk() {
        check("j008b-2jump", -0.195409);
    }

    @Test
    public void j021MatchesCoptDisk() {
        check("j021-rinav1-01", 1067.865480);
    }

    @Test
    public void deserthardScaleConvergesAtN353() {
        Case c = build("j001", 40);
        report(c, Double.NaN);
        assertTrue("j001 (n=353) must exercise the large-n conditioning, was n=" + c.n, c.n >= 300);
        assertNotNull("j001 feasible 40-wall subproblem must not return null at n=353", c.result);
        assertTrue("j001 kernel must converge at n=353, gap=" + c.gap, c.converged);
        assertTrue("j001 value must be finite at n=353", !Double.isNaN(c.value) && !Double.isInfinite(c.value));
        assertTrue("j001 disk primal must be feasible at n=353 (max |u|-m = " + c.maxDiskViol + ")",
                c.maxDiskViol <= 1.0e-9);
    }

    @Test
    public void deserthardFullSpecInfeasibleHandledRobustly() {
        Case c = build("j001", Integer.MAX_VALUE);
        System.out.printf("IPM j001-full        n=%d m=%d iters=%d nullResult=%b (dual unbounded => disk infeasible as one spec)%n",
                c.n, c.m, c.iters, c.result == null);
        assertTrue("j001 full spec must exercise n=353 with all walls, was n=" + c.n + " m=" + c.m,
                c.n >= 300 && c.m >= 60);
        assertNull("j001 monolithic spec is dual-unbounded; kernel must detect it (null), not diverge", c.result);
    }

    private void check(String cap, double coptTarget) {
        Case c = build(cap, Integer.MAX_VALUE);
        report(c, coptTarget);
        assertNotNull(cap + ": kernel returned null (unbounded/breakdown)", c.result);
        assertTrue(cap + " kernel must converge, gap=" + c.gap, c.converged);
        assertTrue(cap + " disk primal must be feasible (max |u|-m = " + c.maxDiskViol + ")",
                c.maxDiskViol <= 1.0e-9);
        assertTrue(cap + " bound must match COPT disk " + coptTarget + ", was " + c.bound
                + " (delta " + Math.abs(c.bound - coptTarget) + ")", Math.abs(c.bound - coptTarget) <= MATCH);
    }

    private static final class Case {
        String name;
        int n;
        int m;
        double value;
        double bound;
        double shippedBound;
        boolean converged;
        int iters;
        double gap;
        double maxDiskViol;
        long ms;
        DiskSocpKernel.Result result;
    }

    private Case build(String cap, int wallLimit) {
        String raw = Fixtures.rawPool(cap);
        SaveFile file = SaveIO.parseSafe(raw);
        assertNotNull(cap + ": parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();

        assertTrue(cap + ": facing walls are out of P3 scope",
                !JumpLinearModel.hasFacingWall(spec.constraints));

        JumpLinearModel lin = new JumpLinearModel(sc);
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        assertTrue(cap + ": trivially infeasible", !trivial[0]);
        if (wallLimit < walls.size()) walls = walls.subList(0, wallLimit);
        double[] mMag = lin.mMagAll();

        long t0 = System.nanoTime();
        DiskSocpKernel.Result r = DiskSocpKernel.solve(n, cx, cz, mMag, walls);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        CostateDualSolver.Result d = new CostateDualSolver(n, cx, cz, mMag, walls).solve(0.0, null);

        int axis = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        double constPos = lin.constPos(spec.objective.tick, axis);
        boolean max = spec.objective.sense == Objective.Sense.MAX;

        Case c = new Case();
        c.name = cap;
        c.n = n;
        c.m = walls.size();
        c.result = r;
        c.ms = ms;
        c.shippedBound = d == null ? Double.NaN : (max ? constPos + d.value : constPos - d.value);
        if (r != null) {
            c.value = r.value;
            c.bound = max ? constPos + r.value : constPos - r.value;
            c.converged = r.converged;
            c.iters = r.iters;
            c.gap = r.gap;
            double mv = 0.0;
            for (int t = 0; t < n; t++) {
                double un = Math.hypot(r.ux[t], r.uz[t]);
                mv = Math.max(mv, un - mMag[t]);
            }
            c.maxDiskViol = mv;
        }
        return c;
    }

    private static void report(Case c, double coptTarget) {
        System.out.printf(
                "IPM %-18s n=%d m=%d iters=%d conv=%b ms=%d%n"
                + "    value=%.9f bound=%.9f  shipped(loose)=%.9f  copt=%.9f  toCopt=%.3e%n"
                + "    gap=%.3e maxDiskViol=%.3e%n",
                c.name, c.n, c.m, c.iters, c.converged, c.ms,
                c.value, c.bound, c.shippedBound, coptTarget, coptTarget - c.bound,
                c.gap, c.maxDiskViol);
    }
}
