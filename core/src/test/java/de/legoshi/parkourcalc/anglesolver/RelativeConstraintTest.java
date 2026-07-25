package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RelativeConstraintTest {

    private static final String PROBLEM = "j004";
    private static final double MET_TOL = 1.0e-4;
    private static final double SLACK = 0.05;
    private static final double THETA = 30.0;

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
        return new Ctx(pf, state, engine);
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

    private static Constraint relative(Constraint.Field field, Constraint.Op op, double value, int refTick) {
        Constraint c = Constraint.scalar(field, op, value);
        c.setRefTick(refTick);
        return c;
    }

    private static Constraint vsDz(Constraint.Op op, double value) {
        Constraint c = Constraint.scalar(Constraint.Field.DX, op, value);
        c.setVsDz(true);
        return c;
    }

    private static List<JumpConstraint> byPrefix(JumpSpec spec, String prefix) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.name.startsWith(prefix)) out.add(c);
        }
        return out;
    }

    private static ForwardPath pathOf(ProblemFixture pf, JumpSpec spec, SolveResult r) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] yaws = new double[r.getYaws().size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = r.getYaws().get(k).yaw;
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return pf.model.forward(sc, gf);
    }

    @Test
    public void mapsRelativeAndCrossDeltaConstraints() {
        int[] range = new int[2];
        Ctx ctx = build(state -> {
            int start = state.getStartTick();
            int landing = state.getLandingTick();
            range[0] = start;
            range[1] = landing;
            assertTrue("fixture too short for the mapping cases", landing - start >= 5);
            state.tickConstraints(start + 3).getConstraints()
                    .add(relative(Constraint.Field.X, Constraint.Op.LE, 0.1, start + 1));
            state.tickConstraints(start + 4).getConstraints()
                    .add(relative(Constraint.Field.Z, Constraint.Op.EQ, 0.0, start + 2));
            state.tickConstraints(start + 2).getConstraints()
                    .add(relative(Constraint.Field.X, Constraint.Op.GE, -1.0, landing + 1));
            state.tickConstraints(start + 2).getConstraints().add(vsDz(Constraint.Op.LT, 0.0));
            state.tickConstraints(start).getConstraints().add(vsDz(Constraint.Op.LT, 0.0));
        });
        JumpSpec spec = ctx.engine.debugBuildSpec();
        assertNotNull(spec);
        int start = range[0];
        int landing = range[1];

        List<JumpConstraint> rel = byPrefix(spec, "X-X[T" + (start + 2) + "]@" + (start + 3));
        assertEquals(1, rel.size());
        JumpConstraint c = rel.get(0);
        assertEquals(JumpConstraint.Mode.X, c.mode);
        assertEquals(3, c.t1);
        assertEquals(Integer.valueOf(1), c.t2);
        assertEquals(JumpConstraint.Op.MINUS, c.op);
        assertEquals(JumpConstraint.Cmp.LE, c.cmp);
        assertEquals(0.1, c.rhs, 0.0);

        List<JumpConstraint> eq = byPrefix(spec, "Z-Z[T" + (start + 3) + "]@" + (start + 4));
        assertEquals(2, eq.size());
        for (JumpConstraint w : eq) {
            assertEquals(JumpConstraint.Mode.Z, w.mode);
            assertEquals(4, w.t1);
            assertEquals(Integer.valueOf(2), w.t2);
            assertEquals(JumpConstraint.Op.MINUS, w.op);
            if (w.cmp == JumpConstraint.Cmp.GE) assertEquals(-MET_TOL, w.rhs, 0.0);
            else {
                assertEquals(JumpConstraint.Cmp.LE, w.cmp);
                assertEquals(MET_TOL, w.rhs, 0.0);
            }
        }

        assertEquals("a reference tick outside the segment must drop the constraint",
                0, byPrefix(spec, "X-X[T" + (landing + 2) + "]@" + (start + 2)).size());

        List<JumpConstraint> cross = byPrefix(spec, "dXvsdZ@" + (start + 2));
        assertEquals(1, cross.size());
        JumpConstraint d = cross.get(0);
        assertEquals(JumpConstraint.Mode.DXZ, d.mode);
        assertEquals(2, d.t1);
        assertEquals(Integer.valueOf(1), d.t2);
        assertEquals(JumpConstraint.Cmp.LE, d.cmp);
        assertEquals(0.0, d.rhs, 0.0);

        assertEquals("a cross delta on the start tick has no previous tick and must drop",
                0, byPrefix(spec, "dXvsdZ@" + start).size());
    }

    @Test
    public void relativeCorridorStaysOnClosedFormAndBinds() {
        Ctx base = build(state -> { });
        SolveResult plain = solve(base);
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        JumpSpec baseSpec = base.engine.debugBuildSpec();
        ForwardPath basePath = pathOf(base.pf, baseSpec, plain);
        int n = baseSpec.asScenario().numTicks;
        int k = n / 2;
        double v = basePath.posX[n] - basePath.posX[k];

        double half = 2.5e-3;
        int start = base.state.getStartTick();
        Ctx ctx = build(state -> {
            Constraint c = Constraint.range(Constraint.Field.X, v - half, v + half, true, true);
            c.setRefTick(start + k);
            state.tickConstraints(start + n).getConstraints().add(c);
        });
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with a relative corridor at the baseline optimum", r.isSuccess());
        assertNotNull("solver label missing", r.getSolver());
        assertTrue("a relative X/Z corridor must stay on the closed-form chain, got: " + r.getSolver(),
                r.getSolver().contains("closed form"));

        boolean reported = false;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if (("X-X[T" + (start + k + 1) + "]").equals(o.field)) {
                reported = true;
                assertTrue("relative outcome must be met", o.met);
            }
        }
        assertTrue("relative outcome row missing from the result panel", reported);

        ForwardPath solved = pathOf(ctx.pf, ctx.engine.debugBuildSpec(), r);
        assertEquals("the solved path must hold the relative corridor",
                v, solved.posX[n] - solved.posX[k], half);
    }

    @Test
    public void closedFormHonorsAnActiveRelativeWall() {
        ProblemFixture pf = ProblemFixture.load("closedform", PROBLEM);
        JumpPhysicsInputs sc = pf.specFor(null, null).asScenario();
        int n = sc.numTicks;
        int k = n / 2;
        double[] flat = new double[n];
        Arrays.fill(flat, THETA);
        double[] gf = sc.toGameFacings(flat);
        ForwardPath witness = pf.model.forward(sc, gf);
        double lx = witness.posX[n];
        double lz = witness.posZ[n];
        double rel = witness.posZ[n] - witness.posZ[k];

        List<JumpConstraint> pad = new ArrayList<>();
        pad.add(wall(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.GE, lx - SLACK));
        pad.add(wall(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.LE, lx + SLACK));
        pad.add(wall(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.GE, lz - SLACK));
        Objective obj = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n);
        double[] yaws0 = ClosedFormSolve.optimize(pf.model, new JumpSpec(sc, pad, obj), 0.0, new AtomicBoolean(false));
        assertNotNull("the pad-only spec must closed-form-solve", yaws0);
        double[] gf0 = sc.toGameFacings(Angles.wrapAll(yaws0));
        ForwardPath p0 = pf.model.forward(sc, gf0);
        double vOpt = p0.posZ[n] - p0.posZ[k];
        org.junit.Assume.assumeTrue("witness and optimum agree, wall would be inactive",
                vOpt > rel + 1.0e-3);

        double mid = 0.5 * (rel + vOpt);
        List<JumpConstraint> cons = new ArrayList<>(pad);
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, n, k, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, mid, "relZcap"));
        JumpSpec s = new JumpSpec(sc, cons, obj);
        double[] yaws = ClosedFormSolve.optimize(pf.model, s, 0.0, new AtomicBoolean(false));
        assertNotNull("an active relative wall must stay closed-form solvable", yaws);
        assertTrue(viol(pf.model, sc, s, yaws) <= 0.0);
        double[] gfS = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath pS = pf.model.forward(sc, gfS);
        assertTrue("the active relative wall must cap the solved path",
                pS.posZ[n] - pS.posZ[k] <= mid);
    }

    @Test
    public void crossDeltaCorridorSolvesAndReports() {
        Ctx base = build(state -> { });
        SolveResult plain = solve(base);
        assertNotNull("baseline solve returned no result", plain);
        assertTrue("baseline j004 must solve", plain.isSuccess());
        JumpSpec baseSpec = base.engine.debugBuildSpec();
        ForwardPath basePath = pathOf(base.pf, baseSpec, plain);
        int t = 2;
        double v = Math.abs(basePath.posX[t] - basePath.posX[t - 1])
                - Math.abs(basePath.posZ[t] - basePath.posZ[t - 1]);

        int start = base.state.getStartTick();
        Ctx ctx = build(state -> state.tickConstraints(start + t).getConstraints()
                .add(vsDz(Constraint.Op.EQ, v)));
        SolveResult r = solve(ctx);
        assertNotNull("engine returned no result", r);
        assertTrue("j004 must solve with a cross-delta corridor at the baseline value", r.isSuccess());

        boolean reported = false;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if ("dX".equals(o.field) && o.relation.contains("dZ")) {
                reported = true;
                assertTrue("cross-delta outcome must be met", o.met);
            }
        }
        assertTrue("cross-delta outcome row missing from the result panel", reported);

        ForwardPath solved = pathOf(ctx.pf, ctx.engine.debugBuildSpec(), r);
        double got = Math.abs(solved.posX[t] - solved.posX[t - 1])
                - Math.abs(solved.posZ[t] - solved.posZ[t - 1]);
        assertEquals("the solved path must hold the cross-delta corridor", v, got, MET_TOL);
    }

    private static JumpConstraint wall(JumpConstraint.Mode m, int t, JumpConstraint.Cmp cmp, double rhs) {
        return new JumpConstraint(m, t, null, JumpConstraint.Op.PLUS, cmp, rhs, m + "@" + t);
    }

    private static double viol(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec s, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return JumpConstraintCompiler.compile(s).maxViolation(gf, exact.forward(sc, gf));
    }

    @Test
    public void closedFormNeverCertifiesAViolatedCrossWall() {
        ProblemFixture pf = ProblemFixture.load("closedform", PROBLEM);
        JumpPhysicsInputs sc = pf.specFor(null, null).asScenario();
        int n = sc.numTicks;
        double[] flat = new double[n];
        Arrays.fill(flat, THETA);
        double[] gf = sc.toGameFacings(flat);
        ForwardPath witness = pf.model.forward(sc, gf);
        double lx = witness.posX[n];
        double lz = witness.posZ[n];
        List<JumpConstraint> pad = new ArrayList<>();
        pad.add(wall(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.GE, lx - SLACK));
        pad.add(wall(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.LE, lx + SLACK));
        pad.add(wall(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.GE, lz - SLACK));
        JumpSpec free = new JumpSpec(sc, pad, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n));
        double[] yaws0 = ClosedFormSolve.optimize(pf.model, free, 0.0, new AtomicBoolean(false));
        assertNotNull("the pad-only spec must closed-form-solve", yaws0);
        double[] gf0 = sc.toGameFacings(Angles.wrapAll(yaws0));
        ForwardPath p0 = pf.model.forward(sc, gf0);
        double d0 = Math.abs(p0.posX[n] - p0.posX[n - 1]) - Math.abs(p0.posZ[n] - p0.posZ[n - 1]);

        List<JumpConstraint> withCross = new ArrayList<>(pad);
        withCross.add(new JumpConstraint(JumpConstraint.Mode.DXZ, n, n - 1, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, d0 - 0.05, "dxz@" + n));
        JumpSpec s = new JumpSpec(sc, withCross, free.objective);
        double[] yaws = ClosedFormSolve.optimize(pf.model, s, 0.0, new AtomicBoolean(false));
        if (yaws != null) {
            assertTrue("closed form may only return a cross-feasible certificate",
                    viol(pf.model, sc, s, yaws) <= 0.0);
        }
    }

    @Test
    public void freeStartTranslatesWithARelativeCorridor() {
        ProblemFixture pf = ProblemFixture.load("closedform", PROBLEM);
        JumpPhysicsInputs base = pf.specFor(null, null).asScenario();
        int n = base.numTicks;
        int k = n / 2;
        double seedX = base.startPos.x;
        double seedZ = base.startPos.z;
        double vx = base.initialVelocity.x;
        double vz = base.initialVelocity.z;
        double tgtX = seedX - 0.4;
        double tgtZ = seedZ + 0.3;

        JumpPhysicsInputs tgt = withStart(base, tgtX, tgtZ, StartBox.pinned(tgtX, tgtZ, vx, vz));
        double[] flat = new double[n];
        Arrays.fill(flat, THETA);
        double[] gf = tgt.toGameFacings(flat);
        ForwardPath witness = pf.model.forward(tgt, gf);
        double lx = witness.posX[n];
        double lz = witness.posZ[n];
        double rel = witness.posZ[n] - witness.posZ[k];
        double half = 2.5e-3;

        List<JumpConstraint> cons = new ArrayList<>();
        cons.add(wall(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.GE, lx - SLACK));
        cons.add(wall(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.LE, lx + SLACK));
        cons.add(wall(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.GE, lz - SLACK));
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, n, k, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.GE, rel - half, "relZlo"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, n, k, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, rel + half, "relZhi"));

        StartBox box = new StartBox(seedX, seedZ, vx, vz,
                tgtX - 0.05, seedX + 0.05, seedZ - 0.05, tgtZ + 0.05, vx, vx, vz, vz);
        JumpSpec s = new JumpSpec(withStart(base, seedX, seedZ, box), cons,
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n));

        FreeStartSolve.Result r = FreeStartSolve.solveJoint(pf.model, s, 0.0, new AtomicBoolean(false));
        assertNotNull("free start must solve a relative corridor off a bad seed", r);
        assertTrue(r.feasible);
        assertTrue("the realized start must certify the full spec",
                FreeStartSolve.violationAt(pf.model, s, r.yaws, r.startX, r.startZ) <= 0.0);

        JumpPhysicsInputs at = withStart(base, r.startX, r.startZ,
                StartBox.pinned(r.startX, r.startZ, vx, vz));
        double[] agf = at.toGameFacings(Angles.wrapAll(r.yaws));
        ForwardPath ap = pf.model.forward(at, agf);
        assertEquals("the relative corridor must hold at the translated start",
                rel, ap.posZ[n] - ap.posZ[k], half + 1.0e-9);
    }

    private static JumpPhysicsInputs withStart(JumpPhysicsInputs b, double px, double pz, StartBox box) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(px, b.startPos.y, pz);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = box;
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return a;
    }

    @Test
    public void crossDeltaEvaluatesAndIsTranslationInvariant() {
        ProblemFixture pf = ProblemFixture.load("closedform", PROBLEM);
        JumpPhysicsInputs sc = pf.specFor(null, null).asScenario();
        int n = sc.numTicks;
        double[] flat = new double[n];
        Arrays.fill(flat, THETA);
        double[] gf = sc.toGameFacings(flat);
        ForwardPath path = pf.model.forward(sc, gf);

        double expected = Math.abs(path.posX[n] - path.posX[n - 1])
                - Math.abs(path.posZ[n] - path.posZ[n - 1]) - 0.02;
        JumpConstraint cross = new JumpConstraint(JumpConstraint.Mode.DXZ, n, n - 1,
                JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, 0.02, "dxz@" + n);
        assertEquals(expected, JumpConstraintCompiler.evaluate(cross, gf, path), 0.0);
        assertEquals("a cross delta is translation invariant",
                JumpConstraintCompiler.evaluate(cross, gf, path),
                JumpConstraintCompiler.translatedEvaluate(cross, gf, path, 5.0, -3.0), 0.0);

        JumpConstraint rel = new JumpConstraint(JumpConstraint.Mode.X, n, n / 2,
                JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, 0.1, "rel@" + n);
        assertEquals(path.posX[n] - path.posX[n / 2] - 0.1,
                JumpConstraintCompiler.evaluate(rel, gf, path), 0.0);
        assertEquals("a same-axis relative wall is translation invariant",
                JumpConstraintCompiler.evaluate(rel, gf, path),
                JumpConstraintCompiler.translatedEvaluate(rel, gf, path, 5.0, -3.0), 0.0);

        JumpSpec bad = new JumpSpec(sc, java.util.Collections.singletonList(
                new JumpConstraint(JumpConstraint.Mode.DXZ, n, null, JumpConstraint.Op.MINUS,
                        JumpConstraint.Cmp.LE, 0.0, "badDxz")),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n));
        try {
            JumpConstraintCompiler.compile(bad);
            fail("DXZ without t2 must be rejected at compile");
        } catch (IllegalArgumentException expectedEx) {
        }
    }

    @Test
    public void refTickAndCrossFieldSurviveSaveRoundTrip() throws Exception {
        FileSystemSaveStore store = new FileSystemSaveStore(
                Files.createTempDirectory("pkc-rt-relative"), "test", "1.8.9", () -> null);

        AngleSolverState in = new AngleSolverState();
        in.tickConstraints(9).getConstraints().add(relative(Constraint.Field.X, Constraint.Op.LE, 0.5, 7));
        Constraint zRange = Constraint.range(Constraint.Field.Z, -0.2, 0.2, true, false);
        zRange.setRefTick(3);
        in.tickConstraints(9).getConstraints().add(zRange);
        in.tickConstraints(4).getConstraints().add(vsDz(Constraint.Op.LT, 0.0));
        in.tickConstraints(4).getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.GE, 12.0));

        Result<String> saved = SaveIO.save(store, "run", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, in, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);
        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);

        List<Constraint> t9 = out.tickConstraintsOrNull(9).getConstraints();
        assertEquals(2, t9.size());
        assertEquals(Constraint.Field.X, t9.get(0).getField());
        assertEquals(Integer.valueOf(7), t9.get(0).getRefTick());
        assertEquals(0.5, t9.get(0).getValue(), 0.0);
        assertEquals(Constraint.Field.Z, t9.get(1).getField());
        assertEquals(Integer.valueOf(3), t9.get(1).getRefTick());
        assertTrue(t9.get(1).isRange());
        assertEquals(-0.2, t9.get(1).getLo(), 0.0);

        List<Constraint> t4 = out.tickConstraintsOrNull(4).getConstraints();
        assertEquals(2, t4.size());
        assertEquals(Constraint.Field.DX, t4.get(0).getField());
        assertEquals(Constraint.Op.LT, t4.get(0).getOp());
        assertTrue("vsDz must survive the round trip", t4.get(0).isVsDz());
        assertTrue("vsDz never survives off the dX field", !t4.get(1).isVsDz());
        assertEquals("refTick never survives on a non-spatial field", null, t4.get(1).getRefTick());
    }
}
