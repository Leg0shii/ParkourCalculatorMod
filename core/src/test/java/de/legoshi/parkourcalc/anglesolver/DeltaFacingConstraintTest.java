package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

        List<JumpConstraint> seam = byPrefix(spec, "dF@" + start);
        assertEquals(1, seam.size());
        JumpConstraint s0 = seam.get(0);
        assertEquals(JumpConstraint.Mode.F, s0.mode);
        assertEquals(0, s0.t1);
        assertNull(s0.t2);
        assertEquals(JumpConstraint.Op.PLUS, s0.op);
        assertEquals(JumpConstraint.Cmp.GE, s0.cmp);
        assertEquals(spec.asScenario().startYaw, s0.rhs, 1.0e-9);

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
    public void seamTurnEqualityPinsTheFirstFacing() {
        SolveResult plain = solve(build(state -> { }));
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        double firstYaw = plain.getYaws().get(0).yaw;

        double seed = build(state -> { }).engine.debugBuildSpec().asScenario().startYaw;
        double target = Angles.wrap(firstYaw - seed);

        Ctx bound = build(state -> state.tickConstraints(state.getStartTick()).getConstraints()
                .add(Constraint.scalar(Constraint.Field.DF, Constraint.Op.EQ, target)));
        SolveResult r = solve(bound);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with the seam turn pinned at the baseline value", r.isSuccess());
        boolean reported = false;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if ("dF".equals(o.field)) reported = true;
        }
        assertTrue("seam dF outcome row missing from the result panel", reported);
        assertEquals(0.0, Angles.wrap(r.getYaws().get(0).yaw - seed - target), 2.0e-4);
    }

    @Test
    public void strictOpsBecomeTheirClosedForms() {
        assertTrue(Constraint.scalar(Constraint.Field.DF, Constraint.Op.GE, 0.5).isDirectionalDf());
        assertFalse(Constraint.scalar(Constraint.Field.DF, Constraint.Op.EQ, 0.0).isDirectionalDf());
        assertFalse(Constraint.range(Constraint.Field.DF, -1.0, 1.0, true, true).isDirectionalDf());
        assertEquals(Constraint.Op.GE, Constraint.scalar(Constraint.Field.DF, Constraint.Op.GT, 0.0).getOp());
        assertEquals(Constraint.Op.LE, Constraint.scalar(Constraint.Field.DF, Constraint.Op.LT, 0.0).getOp());
    }

    @Test
    public void switchingBetweenTheShapesKeepsSensibleValues() {
        Constraint c = Constraint.scalar(Constraint.Field.DF, Constraint.Op.GE, 2.0);
        c.setOp(Constraint.Op.IN);
        assertTrue(c.isRange());
        assertEquals(2.0, c.getLo(), 0.0);
        assertEquals(2.0, c.getHi(), 0.0);
        c.setHi(5.0);
        c.setOp(Constraint.Op.LE);
        assertEquals(2.0, c.getValue(), 0.0);
        c.setOp(Constraint.Op.IN);
        c.setHi(5.0);
        c.setOp(Constraint.Op.EQ);
        assertEquals("picking = holds the facing until a turn is typed", 0.0, c.getValue(), 0.0);
        c.setValue(3.5);
        assertEquals(3.5, c.getValue(), 0.0);
    }

    @Test
    public void oneWayDfSolvesAndReports() {
        SolveResult plain = solve(build(state -> { }));
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        double turn = Angles.wrap(plain.getYaws().get(2).yaw - plain.getYaws().get(1).yaw);
        Constraint.Op op = turn >= 0.0 ? Constraint.Op.GE : Constraint.Op.LE;

        Ctx ctx = build(state -> addScalar(state, state.getStartTick() + 2, op, 0.0));
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with the baseline's own turn direction on tick 3", r.isSuccess());
        SolveResult.Outcome df = null;
        for (SolveResult.Outcome o : r.getOutcomes()) if ("dF".equals(o.field)) df = o;
        assertNotNull("dF outcome row missing from the result panel", df);
        assertTrue("the one-way dF must report met", df.met);
        assertTrue("the result panel shows the inequality: " + df.relation, df.relation.startsWith(op.glyph));
        double solvedTurn = Angles.wrap(r.getYaws().get(2).yaw - r.getYaws().get(1).yaw);
        assertTrue("solved turn " + solvedTurn + " must follow " + op, op == Constraint.Op.GE ? solvedTurn >= 0.0 : solvedTurn <= 0.0);
    }

    @Test
    public void overWideRangeIsCutToHalfATurn() {
        int[] start = new int[1];
        Ctx ctx = build(state -> {
            start[0] = state.getStartTick();
            state.tickConstraints(start[0] + 2).getConstraints()
                    .add(Constraint.range(Constraint.Field.DF, -100.0, 120.0, true, true));
            state.tickConstraints(start[0] + 3).getConstraints()
                    .add(Constraint.range(Constraint.Field.DF, -90.0, 90.0, true, true));
        });
        JumpSpec spec = ctx.engine.debugBuildSpec();
        assertNotNull(spec);
        for (JumpConstraint w : byPrefix(spec, "dF@" + (start[0] + 2))) {
            assertEquals(w.cmp == JumpConstraint.Cmp.GE ? -60.0 : 80.0, w.rhs, 0.0);
        }
        for (JumpConstraint w : byPrefix(spec, "dF@" + (start[0] + 3))) {
            assertEquals(w.cmp == JumpConstraint.Cmp.GE ? -90.0 : 90.0, w.rhs, 0.0);
        }
    }

    @Test
    public void rangeAcrossTheHalfTurnIsJudgedAroundItsMiddle() {
        Constraint flip = Constraint.range(Constraint.Field.DF, 170.0, 190.0, true, true);
        assertTrue(AngleSolverEngine.satisfied(flip, -175.0));
        assertTrue(AngleSolverEngine.satisfied(flip, 175.0));
        assertFalse(AngleSolverEngine.satisfied(flip, 160.0));
        assertFalse(AngleSolverEngine.satisfied(flip, -160.0));
        Constraint plain = Constraint.range(Constraint.Field.DF, -1.0, 1.0, true, true);
        assertTrue(AngleSolverEngine.satisfied(plain, 0.5));
        assertFalse(AngleSolverEngine.satisfied(plain, 1.5));
    }

    private static double turnAt(SolveResult r, int index) {
        return Angles.wrap(r.getYaws().get(index).yaw - r.getYaws().get(index - 1).yaw);
    }

    private static SolveResult.Outcome dfOutcome(SolveResult r) {
        SolveResult.Outcome df = null;
        for (SolveResult.Outcome o : r.getOutcomes()) if ("dF".equals(o.field)) df = o;
        return df;
    }

    @Test
    public void dfRangeSolvesInsideTheRange() {
        SolveResult plain = solve(build(state -> { }));
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        double turn = turnAt(plain, 2);
        double lo = turn + 0.25;
        double hi = turn + 1.25;

        Ctx ctx = build(state -> state.tickConstraints(state.getStartTick() + 2).getConstraints()
                .add(Constraint.range(Constraint.Field.DF, lo, hi, true, true)));
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with the turn on tick 3 held in a range next to the baseline's", r.isSuccess());
        double solved = turnAt(r, 2);
        assertTrue("solved turn " + solved + " must lie in [" + lo + ", " + hi + "]",
                solved >= lo - MET_TOL && solved <= hi + MET_TOL);
        SolveResult.Outcome df = dfOutcome(r);
        assertNotNull("dF outcome row missing from the result panel", df);
        assertTrue("the dF range must report met", df.met);
    }

    @Test
    public void twoOneWayRowsOnOneTickSolveAsARange() {
        SolveResult plain = solve(build(state -> { }));
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        double turn = turnAt(plain, 2);
        double lo = turn - 1.25;
        double hi = turn - 0.25;

        Ctx ctx = build(state -> {
            addScalar(state, state.getStartTick() + 2, Constraint.Op.GE, lo);
            addScalar(state, state.getStartTick() + 2, Constraint.Op.LE, hi);
        });
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("a dF >= and a dF <= on the same tick must solve like the range they describe", r.isSuccess());
        double solved = turnAt(r, 2);
        assertTrue("solved turn " + solved + " must lie in [" + lo + ", " + hi + "]",
                solved >= lo - MET_TOL && solved <= hi + MET_TOL);
    }

    @Test
    public void exactTurnSolvesAtTheTypedValue() {
        SolveResult plain = solve(build(state -> { }));
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        double target = turnAt(plain, 2) + 0.5;

        Ctx ctx = build(state -> addScalar(state, state.getStartTick() + 2, Constraint.Op.EQ, target));
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with an exact turn half a degree off the baseline's on tick 3", r.isSuccess());
        assertEquals(target, turnAt(r, 2), 2.0 * MET_TOL);
        SolveResult.Outcome df = dfOutcome(r);
        assertNotNull("dF outcome row missing from the result panel", df);
        assertTrue("the exact turn must report met", df.met);
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
