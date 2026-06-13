package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.VelocityAngleSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VelocityAngleSolverTest {

    private static final ExactJumpModel MODEL = ExactJumpModel.forMcVersion("1.21.10");
    private static final double SPEED = 0.3;
    private static final Vec3dCore START = new Vec3dCore(0.5, 100.0, 0.5);

    private static JumpPhysicsInputs coastAir(int n) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startPos = START;
        sc.startYaw = 0f;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[n];
        sc.strafePerTick = new boolean[n];
        sc.speedAmplifier = new int[n];
        sc.slipPerTick = new double[n];
        sc.yawLockedPerTick = new boolean[n];
        sc.forwardInputPerTick = new float[n];
        sc.strafeInputPerTick = new float[n];
        sc.sprintPerTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            sc.slipPerTick[t] = Double.NaN;
            sc.yawLockedPerTick[t] = true;
        }
        return sc;
    }

    private static double[] facings(int n) {
        return new double[n];
    }

    private static VelocityAngleSolver.Result solveObjectiveOnly(int n, JumpPhysicsInputs.Axis axis, Objective.Sense sense) {
        JumpSpec spec = new JumpSpec(coastAir(n), new ArrayList<JumpConstraint>(), new Objective(axis, sense, n));
        return new VelocityAngleSolver(MODEL, spec, facings(n), SPEED).solve(0.0, new AtomicBoolean(false));
    }

    @Test
    public void maximizingZPointsLaunchStraightNorth() {
        VelocityAngleSolver.Result r = solveObjectiveOnly(4, JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX);
        assertNotNull(r);
        double err = Math.abs(Angles.wrapDelta(r.angle360Deg - 0.0));
        assertTrue("maximize Z -> launch +Z (theta~0, got " + r.angle360Deg + ")", err < 0.5);
        assertTrue("no constraints: trivially feasible", r.maxViolation <= 0.0);
    }

    @Test
    public void minimizingXPointsLaunchWest() {
        VelocityAngleSolver.Result r = solveObjectiveOnly(3, JumpPhysicsInputs.Axis.X, Objective.Sense.MIN);
        assertNotNull(r);
        double err = Math.abs(Angles.wrapDelta(r.angle360Deg - 90.0));
        assertTrue("minimize X -> launch -X (theta~90, got " + r.angle360Deg + ")", err < 0.5);
    }

    @Test
    public void maximizingXPointsLaunchEast() {
        VelocityAngleSolver.Result r = solveObjectiveOnly(3, JumpPhysicsInputs.Axis.X, Objective.Sense.MAX);
        assertNotNull(r);
        double err = Math.abs(Angles.wrapDelta(r.angle360Deg - 270.0));
        assertTrue("maximize X -> launch +X (theta~270, got " + r.angle360Deg + ")", err < 0.5);
    }

    @Test
    public void findsFeasibleLaunchDirectionForLandingBox() {
        int n = 4;
        double target = 210.0;
        double band = 1.0e-2;
        JumpPhysicsInputs probe = coastAir(n);
        probe.initialVelocity = VelocityAngleSolver.velocityFor(target, SPEED, 0.0);
        double landX = MODEL.forward(probe, facings(n)).posX[n];
        double landZ = MODEL.forward(probe, facings(n)).posZ[n];
        List<JumpConstraint> cons = new ArrayList<>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, n, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, landX - band, "XLo"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, n, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, landX + band, "XHi"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, n, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, landZ - band, "ZLo"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, n, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, landZ + band, "ZHi"));
        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, n);
        JumpSpec spec = new JumpSpec(coastAir(n), cons, obj);

        VelocityAngleSolver.Result r = new VelocityAngleSolver(MODEL, spec, facings(n), SPEED)
                .solve(0.0, new AtomicBoolean(false));
        assertNotNull(r);
        assertTrue("feasible inside the landing box (viol=" + r.maxViolation + ")", r.maxViolation <= 0.0);
        double mag = Math.hypot(r.initialVelocity.x, r.initialVelocity.z);
        assertEquals("launch speed ~ the target magnitude", SPEED, mag, 1.0e-3);
        double err = Math.abs(Angles.wrapDelta(r.angle360Deg - target));
        assertTrue(
                "within the feasible arc of theta=" + target + " (got " + r.angle360Deg + ", err " + err + ")",
                err < 1.0
        );
    }

    @Test
    public void velocityForMatchesMinecraftYawBasis() {
        Vec3dCore north = VelocityAngleSolver.velocityFor(0.0, SPEED, 0.0);
        assertEquals(0.0, north.x, 0.0);
        assertEquals(SPEED, north.z, 0.0);
        Vec3dCore west = VelocityAngleSolver.velocityFor(90.0, SPEED, 0.0);
        assertEquals(-SPEED, west.x, 1.0e-3);
        assertEquals(0.0, west.z, 1.0e-3);
    }

    @Test
    public void engineReportsMetConstraintsForReachableLanding() {
        int n = 2;
        double band = 1.0e-2;
        JumpPhysicsInputs probe = coastAir(n);
        probe.initialVelocity = VelocityAngleSolver.velocityFor(120.0, SPEED, 0.0);
        double landX = MODEL.forward(probe, facings(n)).posX[n];
        double landZ = MODEL.forward(probe, facings(n)).posZ[n];

        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t <= n; t++) {
            inputs.getRows().add(new InputRow());
            Vec3dCore pos = t == 0 ? START : Vec3dCore.ZERO;
            boxes.add(new TickState(
                    pos, false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN
            ));
        }
        AngleSolverState state = new AngleSolverState();
        state.setDefaultInputs(AngleSolverState.InputMode.KEEP);
        state.setStartTick(0);
        state.setLandingTick(n);
        state.setTargetSpeed(SPEED);
        state.tickConstraints(n).getConstraints().add(
                Constraint.range(Constraint.Field.X, landX - band, landX + band, true, true)
        );
        state.tickConstraints(n).getConstraints().add(
                Constraint.range(Constraint.Field.Z, landZ - band, landZ + band, true, true)
        );

        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, MODEL);

        VelocityAngleSolver.Result r = engine.debugSolveVelocityAngle();
        assertNotNull("engine built a velocity-angle problem", r);
        double err = Math.abs(Angles.wrapDelta(r.angle360Deg - 120.0));
        assertTrue("engine search lands near theta=120 (got " + r.angle360Deg + ")", err < 2.0);

        engine.solveVelocityAngle();
        long deadline = System.currentTimeMillis() + 10_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep();
        }
        engine.poll();
        SolveResult res = state.getResult();
        assertNotNull("a result was published", res);
        assertTrue("both pinned constraints met (" + res.getMet() + "/" + res.getTotal() + ")", res.isSuccess());
        assertEquals(2, res.getTotal());
        assertEquals(2, res.getMet());
    }

    private static void sleep() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
