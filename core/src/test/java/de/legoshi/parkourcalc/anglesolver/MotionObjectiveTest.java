package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MotionObjectiveTest {

    private static final String CAPTURE = "gh551-motion-angle";
    private static final String JUMP_TICK_CAPTURE = "gh551-motion-jumptick";

    private static final class Loaded {
        final ExactJumpModel model;
        final JumpSpec spec;
        final AngleSolverState state;

        Loaded(ExactJumpModel model, JumpSpec spec, AngleSolverState state) {
            this.model = model;
            this.spec = spec;
            this.state = state;
        }
    }

    private static Loaded load(AngleSolverState.CustomAngleType type) {
        return load(CAPTURE, type);
    }

    private static Loaded load(String capture, AngleSolverState.CustomAngleType type) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        assertNotNull(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setCustomAngleType(type);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        return new Loaded(model, spec, state);
    }

    private static ForwardPath closedFormPath(Loaded l) {
        double[] yaws = ClosedFormSolve.optimize(l.model, l.spec, 0.0, new AtomicBoolean(false));
        assertNotNull("closed form found no feasible path", yaws);
        JumpPhysicsInputs sc = l.spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = l.model.forward(sc, gf);
        assertEquals(0.0, JumpConstraintCompiler.compile(l.spec).maxViolation(gf, path), 0.0);
        return path;
    }

    @Test
    public void motionObjectiveIsTheTickVelocityAlongTheAngle() {
        Loaded l = load(AngleSolverState.CustomAngleType.MOTION);
        Objective o = l.spec.objective;
        assertTrue(o.isCustomAngle());
        assertTrue(o.isMotion());
        assertEquals(l.state.getLandingTick() - l.state.getStartTick(), o.tick);
        ForwardPath path = closedFormPath(l);
        double rad = Angles.rad(l.state.getCustomAngleDeg());
        double vx = path.posX[o.tick] - path.posX[o.tick - 1];
        double vz = path.posZ[o.tick] - path.posZ[o.tick - 1];
        double expected = -StrictMath.sin(rad) * vx + StrictMath.cos(rad) * vz;
        assertEquals(expected, o.evaluate(path), 0.0);
        assertEquals(o.evaluate(path), o.evaluate(path, 0.25, -0.25), 0.0);
        assertEquals(0.0, o.translationGradX(), 0.0);
        assertEquals(0.0, o.translationGradZ(), 0.0);
    }

    @Test
    public void positionObjectiveIsTheTickPositionAlongTheAngle() {
        Loaded l = load(AngleSolverState.CustomAngleType.POSITION);
        Objective o = l.spec.objective;
        assertTrue(o.isCustomAngle());
        ForwardPath path = closedFormPath(l);
        double rad = Angles.rad(l.state.getCustomAngleDeg());
        double dx = -StrictMath.sin(rad);
        double dz = StrictMath.cos(rad);
        assertEquals(dx * path.posX[o.tick] + dz * path.posZ[o.tick], o.evaluate(path), 0.0);
        assertEquals(o.evaluate(path) + dx * 0.25 - dz * 0.25, o.evaluate(path, 0.25, -0.25), 1e-15);
        assertEquals(dx, o.translationGradX(), 0.0);
        assertEquals(dz, o.translationGradZ(), 0.0);
    }

    @Test
    public void retickingKeepsTheAngleAndTheType() {
        Objective o = new Objective(-18.46, Objective.Type.MOTION, 21, 0.01);
        Objective moved = o.withTick(9);
        assertEquals(9, moved.tick);
        assertEquals(o.customYaw, moved.customYaw);
        assertEquals(Objective.Type.MOTION, moved.type);
        assertEquals(0.01, moved.smoothLambda, 0.0);
        Objective axis = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, 21).withTick(3);
        assertNull(axis.customYaw);
        assertEquals(JumpPhysicsInputs.Axis.Z, axis.axis);
        assertEquals(Objective.Sense.MIN, axis.sense);
        assertEquals(-1.0, axis.gradZ(), 0.0);
        assertEquals(0.0, axis.gradX(), 0.0);
    }

    @Test
    public void angleGoalHasNoLegalGoalWall() {
        Loaded l = load(AngleSolverState.CustomAngleType.POSITION);
        String[] whyNot = new String[1];
        assertNull(AngleSolverEngine.selectLegalGoalWall(l.spec.constraints, l.spec.objective, whyNot));
        assertNotNull(whyNot[0]);
        assertNull(AngleSolverEngine.tightestGoalWall(l.spec.constraints, l.spec.objective));
    }

    @Test
    public void objectiveTickSitsInsideTheSpanWhileConstraintsRunToTheGoal() {
        Loaded l = load(JUMP_TICK_CAPTURE, AngleSolverState.CustomAngleType.MOTION);
        assertTrue(l.state.hasObjectiveTick());
        assertEquals(14, l.state.getObjectiveTick());
        assertEquals(21, l.state.getLandingTick());
        Objective o = l.spec.objective;
        assertEquals(14, o.tick);
        assertEquals(21, l.spec.asScenario().numTicks);
        int lastConstraintTick = 0;
        for (JumpConstraint c : l.spec.constraints) lastConstraintTick = Math.max(lastConstraintTick, c.t1);
        assertEquals(21, lastConstraintTick);
        ForwardPath path = closedFormPath(l);
        double rad = Angles.rad(l.state.getCustomAngleDeg());
        double vx = path.posX[14] - path.posX[13];
        double vz = path.posZ[14] - path.posZ[13];
        assertEquals(-StrictMath.sin(rad) * vx + StrictMath.cos(rad) * vz, o.evaluate(path), 0.0);
    }

    @Test
    public void objectiveTickSurvivesTheSaveFileAndFollowsItsRow() {
        AngleSolverState state = new AngleSolverState();
        state.setStartTick(2);
        state.setLandingTick(20);
        assertEquals(20, state.effectiveObjectiveTick());
        state.setObjectiveTick(9);
        SaveFile file = SaveIO.buildUndoSnapshot(new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, state);
        assertEquals(Integer.valueOf(9), file.angleSolver.objectiveTick);
        AngleSolverState back = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, back);
        assertEquals(9, back.getObjectiveTick());

        back.onRowsInserted(5, 2);
        assertEquals(11, back.getObjectiveTick());
        back.onRowsRemoved(java.util.Arrays.asList(3));
        assertEquals(10, back.getObjectiveTick());
        back.clearObjectiveTick();
        assertFalse(back.hasObjectiveTick());
        SaveFile cleared = SaveIO.buildUndoSnapshot(new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, back);
        assertNull(cleared.angleSolver.objectiveTick);
    }
}
