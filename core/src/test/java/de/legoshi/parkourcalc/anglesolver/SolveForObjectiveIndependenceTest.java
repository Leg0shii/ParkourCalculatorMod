package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.CmaesJumpHarness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverRunResult;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Guards the invariant that whether a solution EXISTS does not depend on the "Solve For" direction.
 *
 *  <p>The CMA-ES fallback minimizes {@code sign*objective + penalty}; with a finite penalty weight and the
 *  strict {@code FEAS_TOL = 0} gate, an objective that pulls against the constraints can make every restart
 *  settle a hair infeasible (so the strict-feasible polish bails) and the engine then reports "no solution"
 *  -- even though a different Solve-For on the SAME constraints lands feasible. {@link SolveCore} now retries
 *  ignoring the objective when the objective-weighted pass finds nothing feasible, so feasibility is
 *  objective-independent again. */
public class SolveForObjectiveIndependenceTest {

    private static final SolveCore.Budget FAST = new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);

    /** Every Solve-For direction must find a byte-exact-feasible solution on a known-feasible fixture. */
    @Test
    public void feasibilityDoesNotDependOnSolveFor() {
        Fixture f = load("j154.json");
        JumpSpec base = f.spec;
        JumpPhysicsInputs sc = base.asScenario();
        int tick = base.objective.tick;
        AtomicBoolean cancel = new AtomicBoolean(false);

        for (JumpPhysicsInputs.Axis axis : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.X, JumpPhysicsInputs.Axis.Z}) {
            for (Objective.Sense sense : new Objective.Sense[]{Objective.Sense.MAX, Objective.Sense.MIN}) {
                JumpSpec spec = new JumpSpec(sc, base.constraints, new Objective(axis, sense, tick));
                double[] yaws = SolveCore.optimize(f.exact, spec, FAST, 90.0, 0.0, cancel);
                String dir = axis + "/" + sense;
                assertNotNull(dir + ": solver returned null", yaws);
                double viol = violation(f.exact, spec, sc, yaws);
                assertTrue(dir + ": reported no feasible solution (viol=" + viol + ") although the "
                        + "constraints are feasible", viol <= 0.0);
            }
        }
    }

    /** The feasibility-only harness mode must find a feasible point regardless of the objective baked into
     *  the spec -- the primitive the {@link SolveCore} fallback relies on. Mirrors the fallback's actual use
     *  (a multistart over diverse facings), since a single CMA-ES run is not guaranteed to hit the strict
     *  zero-violation gate. */
    @Test
    public void feasibilityOnlyModeIgnoresObjectiveAndFindsFeasible() {
        Fixture f = load("j154.json");
        JumpPhysicsInputs sc = f.spec.asScenario();
        int n = sc.numTicks;
        AtomicBoolean cancel = new AtomicBoolean(false);
        Random rng = new Random(0xC0FFEE);

        double bestViol = Double.POSITIVE_INFINITY;
        for (int r = 0; r < 16 && bestViol > 0.0; r++) {
            double[] start = new double[n];
            if (r == 0) Arrays.fill(start, sc.startYaw);
            else for (int i = 0; i < n; i++) start[i] = -180.0 + 360.0 * rng.nextDouble();
            SolverRunResult res = new CmaesJumpHarness(1.0e7, 1.0e7, 90.0, 4500, true)
                    .solve(f.exact, f.spec, start, cancel);
            bestViol = Math.min(bestViol, SolveCore.maxViolation(res));
        }
        assertTrue("feasibility-only multistart found no byte-exact-feasible point (bestViol=" + bestViol + ")",
                bestViol <= 0.0);
    }

    // ---- fixture loading (mirrors ClosedFormSolveTest) ----

    private static final class Fixture {
        final JumpSpec spec;
        final ExactJumpModel exact;

        Fixture(JumpSpec spec, ExactJumpModel exact) {
            this.spec = spec;
            this.exact = exact;
        }
    }

    private static Fixture load(String fx) {
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
        engine.solve();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        engine.poll();
        return new Fixture(engine.lastSpecDebug(), exact);
    }

    private static double violation(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = exact.forward(sc, gf);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
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
        try (InputStream in = SolveForObjectiveIndependenceTest.class.getResourceAsStream("/anglesolver/" + name)) {
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
