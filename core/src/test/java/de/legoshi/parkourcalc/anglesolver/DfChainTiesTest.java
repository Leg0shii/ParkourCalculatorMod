package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DfChainTiesTest {

    private static final String CAPTURE = "f2f-dfchain-multijump";
    private static final double COR = 1.0e-4;

    private static final class Ctx {
        final ProblemFixture pf;
        final AngleSolverState state;
        final AngleSolverEngine engine;

        Ctx(ProblemFixture pf, AngleSolverState state, AngleSolverEngine engine) {
            this.pf = pf;
            this.state = state;
            this.engine = engine;
        }
    }

    private static Ctx build() {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(pf.file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(pf.file, state);
        state.setEffort(pf.expect.effort());
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(pf.file), inputs, t -> { }, pf.model);
        return new Ctx(pf, state, engine);
    }

    private static double[] handGameFacings(Ctx ctx, int n) {
        double cur = ctx.pf.file.angleSolver.seed.yaw;
        int start = ctx.state.getStartTick();
        List<SaveFile.Row> rows = ctx.pf.file.rows;
        double[] gf = new double[n];
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = rows.get(start + k);
            if (row.yaw != null) cur = (float) (cur + row.yaw);
            gf[k] = cur;
        }
        return gf;
    }

    @Test
    public void handPlayedRowsAreByteExactFeasible() {
        Ctx ctx = build();
        JumpSpec spec = ctx.engine.debugBuildSpec();
        assertNotNull(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = handGameFacings(ctx, sc.numTicks);
        ForwardPath path = ctx.pf.model.forward(sc, gf);
        for (int k = 0; k <= sc.numTicks; k++) {
            SaveFile.DebugTick d = ctx.pf.file.debug.get(ctx.state.getStartTick() + k);
            assertEquals("replay diverged from the recorded path at tick " + k, 0.0,
                    Math.hypot(path.posX[k] - d.pos[0], path.posZ[k] - d.pos[2]), 1.0e-12);
        }
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        StringBuilder worst = new StringBuilder();
        double viol = 0.0;
        for (JumpConstraint c : spec.constraints) {
            double s = JumpConstraintCompiler.slack(c, gf, path);
            if (s > 1.0e-9) worst.append(' ').append(c.name).append('=').append(String.format("%.6f", s));
            viol = Math.max(viol, s);
        }
        assertEquals(viol, compiled.maxViolation(gf, path), 1.0e-12);
        assertTrue("hand-played rows must certify byte-exactly, violations:" + worst, viol <= 0.0);
    }

    @Test
    public void tiesCollapseChainsPinsAndOffsets() {
        int n = 6;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        link(cons, 1, 0.0);
        link(cons, 2, 0.0);
        link(cons, 3, 2.0);
        pinCorridor(cons, 4, 60.0);
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, 5, 4, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, 0.0, "oneSided"));
        YawTies ties = YawTies.of(cons, n);
        assertNotNull(ties);
        assertEquals(2, ties.dims());
        double[] full = ties.expand(new double[]{10.0, 33.0});
        assertEquals(10.0, full[0], 0.0);
        assertEquals(10.0, full[1], 0.0);
        assertEquals(10.0, full[2], 0.0);
        assertEquals(12.0, full[3], 1.0e-12);
        assertEquals(60.0, full[4], 1.0e-9);
        assertEquals(33.0, full[5], 0.0);
        double[] reduced = ties.reduce(full);
        assertEquals(10.0, reduced[0], 0.0);
        assertEquals(33.0, reduced[1], 0.0);

        assertNull(YawTies.of(new ArrayList<JumpConstraint>(), n));
        List<JumpConstraint> onlyOneSided = new ArrayList<JumpConstraint>();
        onlyOneSided.add(new JumpConstraint(JumpConstraint.Mode.F, 2, 1, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, 0.0, "oneSided"));
        assertNull(YawTies.of(onlyOneSided, n));
    }

    @Test
    public void solverReproducesTheHandFeasibleSpec() {
        Ctx ctx = build();
        ctx.engine.solve();
        long deadline = System.currentTimeMillis() + 60_000L;
        while (ctx.engine.isSolving() && System.currentTimeMillis() < deadline) {
            ctx.engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ctx.engine.poll();
        SolveResult r = ctx.state.getResult();
        assertNotNull("engine returned no result", r);
        assertTrue("hand-feasible dF-chain multijump must solve, met " + r.getMet() + "/" + r.getTotal(),
                r.isSuccess());
        assertNotNull(r.getSolver());
        assertTrue("the anchor scan must solve this without the CMA-ES fallback, got: " + r.getSolver(),
                !r.getSolver().contains("CMA-ES"));
    }

    private static void link(List<JumpConstraint> cons, int t, double center) {
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.GE, center - COR, "df@" + t));
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, center + COR, "df@" + t));
    }

    private static void pinCorridor(List<JumpConstraint> cons, int t, double yaw) {
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, yaw - COR, "pin@" + t));
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.LE, yaw + COR, "pin@" + t));
    }
}
