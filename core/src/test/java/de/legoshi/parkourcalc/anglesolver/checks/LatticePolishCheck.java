package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LatticePolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

final class LatticePolishCheck implements ProblemCheck {

    private static final String ENGINE_MODE = "engine";

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        String name = pf.name;
        ExactJumpModel model = pf.model;
        ProblemFixture.Rig rig = pf.rig();
        JumpSpec spec = rig.spec();
        JumpPhysicsInputs sc = spec.asScenario();

        String declined = LatticePolish.unsupported(spec);
        if (e.unsupported != null) {
            assertNotNull(name + ": the stage must decline this spec", declined);
            assertTrue(name + ": declined for another reason: " + declined, declined.contains(e.unsupported));
            double[] any = new double[sc.numTicks];
            assertNull(name + ": a declined spec must come back without a result", polish(model, spec, any, sc.startPos.x, sc.startPos.z));
            return;
        }
        assertNull(name + ": the stage declines this spec: " + declined, declined);

        double[] yaws;
        double startX = sc.startPos.x;
        double startZ = sc.startPos.z;
        if (ENGINE_MODE.equals(e.mode)) {
            SolveResult r = rig.solve(e.timeoutMs(60_000L)).result;
            assertNotNull(name + ": engine returned no result", r);
            assertTrue(name + ": the engine must solve: " + r.getSolver(), r.isSuccess());
            yaws = new double[sc.numTicks];
            Arrays.fill(yaws, Double.NaN);
            for (SolveResult.YawEntry y : r.getYaws()) {
                int index = y.tick - 1 - rig.state.getStartTick();
                if (index >= 0 && index < yaws.length) yaws[index] = y.yaw;
            }
            for (double y : yaws) assertTrue(name + ": the result leaves a tick without a facing", !Double.isNaN(y));
            Vec3dCore planned = rig.engine.lastPlanStart();
            if (planned != null) {
                startX = planned.x;
                startZ = planned.z;
            }
        } else {
            double[] seed = ClosedFormSolve.optimize(model, spec, 0.0, new AtomicBoolean(false));
            assertNotNull(name + ": closed-form seed", seed);
            yaws = Angles.wrapAll(seed);
        }
        double incumbent = replay(name + " incumbent", model, spec, sc, yaws, startX, startZ);

        LatticePolish.Result out = polish(model, spec, yaws, startX, startZ);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        if (out == null) {
            System.out.printf("POLISH %-28s incumbent=%.9f kept%n", name, incumbent);
            assertNull(name + ": expected a gain of " + e.minGain + ", the stage kept the incumbent", e.minGain);
        } else {
            double value = replay(name + " polished", model, spec, sc, out.yaws, out.startX, out.startZ);
            assertEquals(name + ": reported objective differs from the replay", value, out.objective, 0.0);
            double gain = max ? value - incumbent : incumbent - value;
            System.out.printf("POLISH %-28s incumbent=%.9f -> %.9f gain=%.3e smooth=%.9f%n", name, incumbent, value,
                    gain, out.continuous);
            assertTrue(name + ": the stage returned a result that is not strictly better: " + gain, gain > 0.0);
            if (e.minGain != null) assertTrue(name + ": gain " + gain + " under " + e.minGain, gain >= e.minGain);
            LatticePolish.Result again = polish(model, spec, yaws, startX, startZ);
            assertNotNull(name + ": the second run kept the incumbent", again);
            assertArrayEquals(name + ": the second run found other facings", out.yaws, again.yaws, 0.0);
            assertEquals(name + ": the second run found another start X", out.startX, again.startX, 0.0);
            assertEquals(name + ": the second run found another start Z", out.startZ, again.startZ, 0.0);
        }

        double[] junk = new double[sc.numTicks];
        JumpPhysicsInputs at = sc.pinnedAt(startX, startZ);
        double[] facings = at.toGameFacings(junk);
        if (JumpConstraintCompiler.compile(spec).maxViolation(facings, model.forward(at, facings)) <= 0.0) return;
        assertNull(name + ": an infeasible incumbent must come back without a result", polish(model, spec, junk, startX, startZ));
    }

    private static LatticePolish.Result polish(ExactJumpModel model, JumpSpec spec, double[] yaws, double x, double z) {
        return LatticePolish.polish(model, spec, yaws, x, z, new LatticePolish.Config(), 0L, null);
    }

    private static double replay(String label, ExactJumpModel model, JumpSpec spec, JumpPhysicsInputs sc,
                                 double[] yaws, double x, double z) {
        StartBox box = sc.startBox;
        if (box != null && box.startFree()) {
            assertTrue(label + ": start X " + x + " outside the start box", x >= box.pxLo && x <= box.pxHi);
            assertTrue(label + ": start Z " + z + " outside the start box", z >= box.pzLo && z <= box.pzHi);
        } else {
            assertEquals(label + ": a pinned start moved in X", sc.startPos.x, x, 0.0);
            assertEquals(label + ": a pinned start moved in Z", sc.startPos.z, z, 0.0);
        }
        JumpPhysicsInputs at = sc.pinnedAt(x, z);
        double[] facings = at.toGameFacings(Angles.wrapAll(yaws.clone()));
        ForwardPath path = model.forward(at, facings);
        double violation = JumpConstraintCompiler.compile(spec).maxViolation(facings, path);
        assertTrue(label + ": violates a constraint by " + violation, violation <= 0.0);
        return spec.objective.evaluate(path);
    }
}
