package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothJumpModel;
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

/** Diagnostic: solve a fixture (fallback CMA-ES yields a known-feasible solution), then evaluate that
 *  same facing vector on the SmoothJumpModel to measure how faithful the smooth twin is. */
public class SmoothFidelityTest {

    @org.junit.Ignore("manual diagnostic, not an assertion test")
    @Test
    public void smoothFidelity() {
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

            engine.solve();
            long deadline = System.currentTimeMillis() + 30_000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            engine.poll();

            SolveResult r = state.getResult();
            JumpSpec spec = engine.lastSpecDebug();
            JumpPhysicsInputs sc = spec.asScenario();
            int n = sc.numTicks;
            double[] yaws = new double[n];
            for (SolveResult.YawEntry y : r.getYaws()) {
                int seg = y.tick - 1 - (r.getStartTick() - 1);
                if (seg >= 0 && seg < n) yaws[seg] = y.yaw;
            }
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
            ForwardPath exactPath = exact.forward(sc, gf);
            ForwardPath smoothPath = SmoothJumpModel.like(exact).forward(sc, gf);
            double exactViol = c.maxViolation(gf, exactPath);
            double smoothViol = c.maxViolation(gf, smoothPath);
            double maxPosDiff = 0;
            for (int t = 0; t <= n; t++) {
                maxPosDiff = Math.max(maxPosDiff, Math.abs(exactPath.posX[t] - smoothPath.posX[t]));
                maxPosDiff = Math.max(maxPosDiff, Math.abs(exactPath.posZ[t] - smoothPath.posZ[t]));
            }
            System.out.printf("FIDELITY %-12s n=%d success=%s exactViol=%.3e smoothViol=%.3e maxPosDiff=%.3e%n",
                    fx, n, r != null && r.isSuccess(), exactViol, smoothViol, maxPosDiff);

            // Run the LM solver from cold seeds and report its best maxViol + per-constraint gaps, plus the
            // known-feasible yaws, to see how far the cold solve lands from the achievable basin.
            de.legoshi.parkourcalc.core.anglesolver.solver.SmoothGradientSolver gs =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.SmoothGradientSolver(SmoothJumpModel.like(exact), sc);
            double objYaw = spec.objective.axis == JumpPhysicsInputs.Axis.X
                    ? (spec.objective.sense == de.legoshi.parkourcalc.core.anglesolver.solver.Objective.Sense.MAX ? -90 : 90)
                    : (spec.objective.sense == de.legoshi.parkourcalc.core.anglesolver.solver.Objective.Sense.MAX ? 0 : 180);
            double[] seed = new double[n];
            java.util.Arrays.fill(seed, objYaw + 40);
            double[] got = gs.solve(c.ineq, spec.objective, seed);
            ForwardPath gp = SmoothJumpModel.like(exact).forward(sc, sc.toGameFacings(got));
            System.out.printf("  LM best maxViol=%.3e%n", c.maxViolation(sc.toGameFacings(got), gp));
            StringBuilder sb = new StringBuilder("  knownYaw:");
            for (double y : yaws) sb.append(String.format(" %.1f", y));
            sb.append("\n  lmYaw:   ");
            for (double y : got) sb.append(String.format(" %.1f", y));
            System.out.println(sb);
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
        try (InputStream in = SmoothFidelityTest.class.getResourceAsStream("/anglesolver/" + name)) {
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
