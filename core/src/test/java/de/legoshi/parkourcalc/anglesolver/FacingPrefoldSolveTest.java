package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FacingPrefoldSolveTest {

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
}
