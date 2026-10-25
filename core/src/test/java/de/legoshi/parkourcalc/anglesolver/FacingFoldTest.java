package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
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
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FacingFoldTest {

    private static final double COR = 1.0e-4;
    private static final double THETA = 30.0;
    private static final double SLACK = 0.05;

    private static final class Fx {
        final ExactJumpModel exact;
        final JumpPhysicsInputs sc;
        final int n;

        Fx() {
            ProblemFixture pf = ProblemFixture.load("closedform", "j004");
            exact = pf.model;
            sc = pf.specFor(null, null).asScenario();
            n = sc.numTicks;
        }
    }

    private static JumpConstraint wall(JumpConstraint.Mode m, int t, JumpConstraint.Cmp cmp, double rhs) {
        return new JumpConstraint(m, t, null, JumpConstraint.Op.PLUS, cmp, rhs, m + "@" + t);
    }

    private static void pin(List<JumpConstraint> cons, int t, double yaw) {
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, yaw - COR, "pin@" + t));
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.LE, yaw + COR, "pin@" + t));
    }

    private static void chain(List<JumpConstraint> cons, int from, int to) {
        for (int t = from; t <= to; t++) {
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.GE, -COR, "df@" + t));
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.LE, COR, "df@" + t));
        }
    }

    private static List<JumpConstraint> padAroundFlatYaw(Fx fx, boolean capObjectiveAxis) {
        double[] flat = new double[fx.n];
        Arrays.fill(flat, THETA);
        return padAroundYaws(fx, flat, capObjectiveAxis);
    }

    private static List<JumpConstraint> padAroundYaws(Fx fx, double[] yaws, boolean capObjectiveAxis) {
        double[] gf = fx.sc.toGameFacings(yaws);
        ForwardPath path = fx.exact.forward(fx.sc, gf);
        double lx = path.getPos(fx.n, JumpPhysicsInputs.Axis.X);
        double lz = path.getPos(fx.n, JumpPhysicsInputs.Axis.Z);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(wall(JumpConstraint.Mode.X, fx.n, JumpConstraint.Cmp.GE, lx - SLACK));
        cons.add(wall(JumpConstraint.Mode.X, fx.n, JumpConstraint.Cmp.LE, lx + SLACK));
        cons.add(wall(JumpConstraint.Mode.Z, fx.n, JumpConstraint.Cmp.GE, lz - SLACK));
        if (capObjectiveAxis) {
            cons.add(wall(JumpConstraint.Mode.Z, fx.n, JumpConstraint.Cmp.LE, lz + SLACK));
        }
        return cons;
    }

    private static JumpSpec spec(Fx fx, List<JumpConstraint> cons) {
        return new JumpSpec(fx.sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, fx.n));
    }

    private static double viol(Fx fx, JumpSpec s, double[] yaws) {
        double[] gf = fx.sc.toGameFacings(Angles.wrapAll(yaws));
        return JumpConstraintCompiler.compile(s).maxViolation(gf, fx.exact.forward(fx.sc, gf));
    }

    @Test
    public void pinnedChainSolvesOnTheClosedForm() {
        Fx fx = new Fx();
        List<JumpConstraint> cons = padAroundFlatYaw(fx, false);
        pin(cons, 2, THETA);
        chain(cons, 3, 7);
        JumpSpec s = spec(fx, cons);
        double[] yaws = ClosedFormSolve.optimize(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("closed form must solve a pinned dF chain", yaws);
        for (int t = 2; t <= 7; t++) {
            assertEquals("tick " + t + " must hold the pinned facing", THETA, yaws[t], 1.0e-9);
        }
        assertTrue(viol(fx, s, yaws) <= 0.0);
    }

    @Test
    public void freeChainMergesOnTheClosedForm() {
        Fx fx = new Fx();
        List<JumpConstraint> cons = padAroundFlatYaw(fx, false);
        chain(cons, 3, 7);
        JumpSpec s = spec(fx, cons);
        double[] yaws = ClosedFormSolve.optimize(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("closed form must solve a free dF chain", yaws);
        for (int t = 3; t <= 7; t++) {
            assertEquals("chain ticks must share one facing", yaws[2], yaws[t], 0.0);
        }
        assertTrue(viol(fx, s, yaws) <= 0.0);
    }

    @Test
    public void fullyPinnedSpecDegeneratesToAFeasibilityCheck() {
        Fx fx = new Fx();
        List<JumpConstraint> cons = padAroundFlatYaw(fx, true);
        pin(cons, 0, THETA);
        chain(cons, 1, fx.n - 1);
        JumpSpec s = spec(fx, cons);
        double[] yaws = ClosedFormSolve.optimize(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("a fully pinned spec must certify without free variables", yaws);
        for (int t = 0; t < fx.n; t++) {
            assertEquals(THETA, yaws[t], 1.0e-9);
        }
        assertTrue(viol(fx, s, yaws) <= 0.0);
    }

    @Test
    public void openChainScanSolvesAcrossKeyChange() {
        Fx fx = new Fx();
        List<JumpConstraint> cons = padAroundFlatYaw(fx, false);
        chain(cons, 1, 5);
        JumpSpec s = spec(fx, cons);
        double[] yaws = ClosedFormSolve.optimize(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("the anchor scan must solve a chain crossing a key-combo change", yaws);
        for (int t = 1; t <= 5; t++) {
            assertEquals("chain ticks must hold one facing", yaws[0], yaws[t], 0.0);
        }
        assertTrue(viol(fx, s, yaws) <= 0.0);
    }

    @Test
    public void constantTurnChainSolvesOnTheClosedForm() {
        Fx fx = new Fx();
        double turn = 2.0;
        double[] witness = new double[fx.n];
        Arrays.fill(witness, THETA);
        for (int t = 2; t < fx.n; t++) {
            witness[t] = t <= 6 ? THETA + turn * (t - 2) : THETA + turn * 4;
        }
        List<JumpConstraint> cons = padAroundYaws(fx, witness, false);
        for (int t = 3; t <= 6; t++) {
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.GE, turn - COR, "dfc@" + t));
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.LE, turn + COR, "dfc@" + t));
        }
        JumpSpec s = spec(fx, cons);
        double[] yaws = ClosedFormSolve.optimize(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("the anchor scan must solve a constant-turn chain", yaws);
        for (int t = 3; t <= 6; t++) {
            assertEquals("turn per tick must hold", turn, yaws[t] - yaws[t - 1], 1.0e-9);
        }
        assertTrue(viol(fx, s, yaws) <= 0.0);
    }

    @Test
    public void pinnedChainWithTurnOffsetsEliminates() {
        Fx fx = new Fx();
        double turn = 2.0;
        double[] witness = new double[fx.n];
        Arrays.fill(witness, THETA);
        witness[3] = THETA + turn;
        for (int t = 4; t < fx.n; t++) witness[t] = THETA + turn * 2;
        List<JumpConstraint> cons = padAroundYaws(fx, witness, false);
        pin(cons, 2, THETA);
        for (int t = 3; t <= 4; t++) {
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.GE, turn - COR, "dfc@" + t));
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.LE, turn + COR, "dfc@" + t));
        }
        JumpSpec s = spec(fx, cons);
        double[] yaws = ClosedFormSolve.optimize(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("a pinned chain with turn offsets must eliminate", yaws);
        assertEquals(THETA, yaws[2], 1.0e-9);
        assertEquals(THETA + turn, yaws[3], 1.0e-9);
        assertEquals(THETA + turn * 2, yaws[4], 1.0e-9);
        assertTrue(viol(fx, s, yaws) <= 0.0);
    }

    @Test
    public void freeStartTranslatesOpenChainSpecs() {
        Fx fx = new Fx();
        double seedX = fx.sc.startPos.x;
        double seedZ = fx.sc.startPos.z;
        double vx = fx.sc.initialVelocity.x;
        double vz = fx.sc.initialVelocity.z;
        double tgtX = seedX - 0.4;
        double tgtZ = seedZ + 0.3;

        JumpPhysicsInputs tgt = withStart(fx.sc, tgtX, tgtZ,
                de.legoshi.parkourcalc.core.anglesolver.solver.StartBox.pinned(tgtX, tgtZ, vx, vz));
        double[] flat = new double[fx.n];
        Arrays.fill(flat, THETA);
        double[] gf = tgt.toGameFacings(flat);
        ForwardPath path = fx.exact.forward(tgt, gf);
        double lx = path.getPos(fx.n, JumpPhysicsInputs.Axis.X);
        double lz = path.getPos(fx.n, JumpPhysicsInputs.Axis.Z);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(wall(JumpConstraint.Mode.X, fx.n, JumpConstraint.Cmp.GE, lx - SLACK));
        cons.add(wall(JumpConstraint.Mode.X, fx.n, JumpConstraint.Cmp.LE, lx + SLACK));
        cons.add(wall(JumpConstraint.Mode.Z, fx.n, JumpConstraint.Cmp.GE, lz - SLACK));
        chain(cons, 1, 5);

        de.legoshi.parkourcalc.core.anglesolver.solver.StartBox box =
                new de.legoshi.parkourcalc.core.anglesolver.solver.StartBox(seedX, seedZ, vx, vz,
                        tgtX - 0.05, seedX + 0.05, seedZ - 0.05, tgtZ + 0.05, vx, vx, vz, vz);
        JumpSpec s = new JumpSpec(withStart(fx.sc, seedX, seedZ, box), cons,
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, fx.n));

        FreeStartSolve.Result r = FreeStartSolve.solveJoint(fx.exact, s, 0.0, new AtomicBoolean(false));
        assertNotNull("free start must translate an open-chain spec off a bad seed", r);
        assertTrue(r.feasible);
        for (int t = 1; t <= 5; t++) {
            assertEquals("chain ticks must hold one facing", r.yaws[0], r.yaws[t], 0.0);
        }
    }

    private static JumpPhysicsInputs withStart(JumpPhysicsInputs b, double px, double pz,
                                               de.legoshi.parkourcalc.core.anglesolver.solver.StartBox box) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new de.legoshi.parkourcalc.core.sim.Vec3dCore(px, b.startPos.y, pz);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = box;
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafeSignPerTick = b.strafeSignPerTick;
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
    public void oneSidedDeltaStillBailsToTheFallback() {
        Fx fx = new Fx();
        List<JumpConstraint> cons = padAroundFlatYaw(fx, true);
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, 3, 2, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.LE, 0.0, "df<=0"));
        assertNull(ClosedFormSolve.optimize(fx.exact, spec(fx, cons), 0.0, new AtomicBoolean(false)));
    }

    private static final String DF_CHAIN_CAPTURE = "f2f-dfchain-multijump";

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
        ProblemFixture pf = ProblemFixture.load("solve", DF_CHAIN_CAPTURE);
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
        pin(cons, 4, 60.0);
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
}
