package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DeltaFacingConstraintTest {

    private static final String PROBLEM = "j004";
    private static final double MET_TOL = 1.0e-4;

    private static final class Ctx {
        final AngleSolverState state;
        final AngleSolverEngine engine;

        Ctx(AngleSolverState state, AngleSolverEngine engine) {
            this.state = state;
            this.engine = engine;
        }
    }

    private static Ctx build(Consumer<AngleSolverState> mutate) {
        ProblemFixture pf = ProblemFixture.load("closedform", PROBLEM);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(pf.file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(pf.file, state);
        state.setEffort(pf.expect.effort());
        state.clearResult();
        mutate.accept(state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(pf.file), inputs, t -> { }, pf.model);
        return new Ctx(state, engine);
    }

    private static void addScalar(AngleSolverState state, int tick, Constraint.Op op, double value) {
        state.tickConstraints(tick).getConstraints().add(Constraint.scalar(Constraint.Field.DF, op, value));
    }

    private static List<JumpConstraint> byPrefix(JumpSpec spec, String prefix) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.name.startsWith(prefix)) out.add(c);
        }
        return out;
    }

    private static void assertNameAbsent(JumpSpec spec, String name) {
        for (JumpConstraint c : spec.constraints) {
            if (c.name.equals(name)) throw new AssertionError("expected " + name + " to be dropped");
        }
    }

    @Test
    public void mapsToTwoTickFacingDeltas() {
        int[] range = new int[2];
        Ctx ctx = build(state -> {
            int start = state.getStartTick();
            int landing = state.getLandingTick();
            range[0] = start;
            range[1] = landing;
            assertTrue("fixture too short for the mapping cases", landing - start >= 5);
            addScalar(state, start + 2, Constraint.Op.LE, 0.0);
            addScalar(state, start + 3, Constraint.Op.EQ, 0.0);
            state.tickConstraints(start + 4).getConstraints()
                    .add(Constraint.range(Constraint.Field.DF, -1.0, 1.0, true, true));
            addScalar(state, start, Constraint.Op.GT, 0.0);
            addScalar(state, landing, Constraint.Op.LT, 5.0);
        });
        JumpSpec spec = ctx.engine.debugBuildSpec();
        assertNotNull(spec);
        int start = range[0];
        int landing = range[1];

        List<JumpConstraint> le = byPrefix(spec, "dF@" + (start + 2));
        assertEquals(1, le.size());
        JumpConstraint c = le.get(0);
        assertEquals(JumpConstraint.Mode.F, c.mode);
        assertEquals(2, c.t1);
        assertEquals(Integer.valueOf(1), c.t2);
        assertEquals(JumpConstraint.Op.MINUS, c.op);
        assertEquals(JumpConstraint.Cmp.LE, c.cmp);
        assertEquals(0.0, c.rhs, 0.0);

        List<JumpConstraint> eq = byPrefix(spec, "dF@" + (start + 3));
        assertEquals(2, eq.size());
        for (JumpConstraint w : eq) {
            assertEquals(JumpConstraint.Mode.F, w.mode);
            assertEquals(3, w.t1);
            assertEquals(Integer.valueOf(2), w.t2);
            assertEquals(JumpConstraint.Op.MINUS, w.op);
            if (w.cmp == JumpConstraint.Cmp.GE) assertEquals(-MET_TOL, w.rhs, 0.0);
            else {
                assertEquals(JumpConstraint.Cmp.LE, w.cmp);
                assertEquals(MET_TOL, w.rhs, 0.0);
            }
        }

        List<JumpConstraint> in = byPrefix(spec, "dF@" + (start + 4));
        assertEquals(2, in.size());
        for (JumpConstraint w : in) {
            assertEquals(JumpConstraint.Mode.F, w.mode);
            assertEquals(4, w.t1);
            assertEquals(Integer.valueOf(3), w.t2);
            if (w.cmp == JumpConstraint.Cmp.GE) assertEquals(-1.0, w.rhs, 0.0);
            else assertEquals(1.0, w.rhs, 0.0);
        }

        assertNameAbsent(spec, "dF@" + start);
        assertNameAbsent(spec, "dF@" + landing);
    }

    private static SolveResult solve(Ctx ctx) {
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
        return ctx.state.getResult();
    }

    @Test
    public void zeroTurnEqualitySolvesAndReports() {
        Ctx ctx = build(state ->
                addScalar(state, state.getStartTick() + 2, Constraint.Op.EQ, 0.0));
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        boolean reported = false;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if ("dF".equals(o.field)) reported = true;
        }
        assertTrue("dF outcome row missing from the result panel", reported);
        assertTrue("j004 must solve with a zero-turn tick", r.isSuccess());
        assertNotNull("solver label missing", r.getSolver());
        assertTrue("dF=0 must stay on the closed form, got: " + r.getSolver(),
                r.getSolver().contains("closed form"));
    }

    @Test
    public void pinnedFacingStaysOnClosedForm() {
        SolveResult plain = solve(build(state -> { }));
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        double pinYaw = plain.getYaws().get(2).yaw;

        Ctx ctx = build(state -> state.tickConstraints(state.getStartTick() + 2).getConstraints()
                .add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, pinYaw)));
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with the facing pinned at the baseline optimum", r.isSuccess());
        assertNotNull("solver label missing", r.getSolver());
        assertTrue("a pinned facing must stay on the closed form, got: " + r.getSolver(),
                r.getSolver().contains("closed form"));
        assertEquals(pinYaw, r.getYaws().get(2).yaw, 1.0e-9);
    }
}
