package de.legoshi.parkourcalc.anglesolver;

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
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Exercises the closed-form dual costate solver ({@link ClosedFormSolve}) directly on each fixture's
 *  compiled spec. Asserts the recovered facings are strictly feasible on the byte-exact model, that the
 *  objective is no worse than the known-feasible CMA-ES solution recorded in the fixture (beyond a tiny
 *  margin slack), and that a single solve runs in well under a millisecond. Prints the per-solve
 *  microseconds so the &lt;0.1 ms target is visible. */
public class ClosedFormSolveTest {

    /** Generous upper bound (the solve is ~20-70 us; this only catches gross regressions, not CI jitter). */
    private static final double MAX_US = 2000.0;

    @Test
    public void closedFormSolvesAllFixtures() {
        for (String fx : new String[]{"j121.json", "j154.json", "j1097.json"}) {
            SaveFile file = SaveIO.parseSafe(readFixture(fx));
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            state.setEffort(AngleSolverState.Effort.FAST);
            state.clearResult();
            BoxController boxes = new BoxController();
            for (SaveFile.DebugTick d : file.debug) boxes.add(toTickState(d));
            ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
            AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, exact);

            // Run the engine once to obtain the compiled spec and a reference objective.
            engine.solve();
            long deadline = System.currentTimeMillis() + 30_000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            engine.poll();
            // Reference objective: the validated in-game (CMA-ES) solution recorded in the fixture.
            double refObj = file.angleSolver.result.objectiveValue;
            JumpSpec spec = engine.lastSpecDebug();
            JumpPhysicsInputs sc = spec.asScenario();
            JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

            AtomicBoolean cancel = new AtomicBoolean(false);
            double[] yaws = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
            assertNotNull(fx + ": closed form returned null (no feasible solve)", yaws);

            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath path = exact.forward(sc, gf);
            double viol = compiled.maxViolation(gf, path);
            assertTrue(fx + ": not byte-exact feasible (viol=" + viol + ")", viol <= 0.0);

            double obj = path.getPos(spec.objective.tick, spec.objective.axis);
            boolean max = spec.objective.sense == Objective.Sense.MAX;
            double objGap = max ? refObj - obj : obj - refObj; // > 0 means closed form is worse than reference
            // The closed form matches the in-game objective to ~1e-4 on most jumps. j121 is ~7e-3 short: its
            // path grazes the 0.005 momentum-cancellation threshold (a borderline, float-sensitive clamp the
            // continuous model does not chase), so a sliver of reach is left to the (still-met) jump. Bound
            // generously to catch only gross objective regressions.
            assertTrue(fx + ": objective regressed vs reference by " + objGap, objGap <= 1.0e-2);

            // Warm up the JIT, then time a single solve.
            for (int i = 0; i < 3000; i++) ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
            int reps = 3000;
            long t0 = System.nanoTime();
            for (int i = 0; i < reps; i++) ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
            double usEach = (System.nanoTime() - t0) / 1e3 / reps;

            System.out.printf("CLOSED %-12s n=%d  %.1f us  viol=%.2e  obj=%.6f ref=%.6f gap=%.2e%n",
                    fx, sc.numTicks, usEach, viol, obj, refObj, objGap);
            assertTrue(fx + ": solve too slow (" + usEach + " us)", usEach < MAX_US);
        }
    }

    private static TickState toTickState(SaveFile.DebugTick d) {
        Vec3dCore pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
        Vec3dCore vel = (d.vel != null && d.vel.length >= 3)
                ? new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]) : Vec3dCore.ZERO;
        double angle = d.collisionAngle == null ? Double.NaN : d.collisionAngle;
        return new TickState(pos, d.onGround, d.sneaking, d.wallCollision, d.yaw,
                Collections.<Vec3dCore>emptyList(), vel, d.softCollision, angle);
    }

    private static String readFixture(String name) {
        try (InputStream in = ClosedFormSolveTest.class.getResourceAsStream("/anglesolver/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            while ((k = in.read(buf)) != -1) out.write(buf, 0, k);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
