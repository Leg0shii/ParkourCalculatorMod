package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Byte-exact replay of the capture's saved rows through the solver model: the rows' yaw chain (locked rows
 *  absolute, the rest accumulated in float the way the game does) is forwarded from the launch state and
 *  scored against the capture's own constraints. The sidecar says what the replay must show: the recorded
 *  debug positions ({@code debugPositions}), feasibility or a pinned violation ({@code feasible},
 *  {@code refViolation}), the objective ({@code refObjective}), the legal goal wall ({@code goalWall},
 *  {@code goalWallRhs}) and, with {@code legal}, the hard walls feasible and the shortfall to that wall at its
 *  recorded value ({@code refShortfall}). {@code bindingTick} pins which tick's wall binds and
 *  {@code feasibleTicks} which ticks' walls must hold on an otherwise infeasible replay. */
final class ReplayCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        String name = pf.name;
        ProblemFixture.Rig rig = pf.rig();
        JumpSpec spec = rig.spec();
        assertNotNull(name + ": no spec", spec);
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (e.numTicks != null) assertEquals(name + ": window length", e.numTicks.intValue(), n);

        double[] gf = rowFacings(rig.inputs.getRows(), rig.state.getStartTick(), sc);
        ForwardPath path = pf.model.forward(sc, gf);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double viol = compiled.maxViolation(gf, path);
        double obj = path.getPos(spec.objective.tick, spec.objective.axis);

        if (e.flag(e.debugPositions, false)) {
            double posDiff = debugPositionDiff(pf.file, rig.state.getStartTick(), path, n);
            assertTrue(name + ": replay leaves the recorded positions, posDiff=" + posDiff, posDiff < 1.0e-12);
        }

        JumpConstraint goal = null;
        if (e.goalWall != null || e.goalWallRhs != null || e.flag(e.legal, false)) {
            String[] whyNot = new String[1];
            goal = AngleSolverEngine.selectLegalGoalWall(spec.constraints, spec.objective, whyNot);
            assertNotNull(name + ": no legal goal wall: " + whyNot[0], goal);
            if (e.goalWall != null) assertEquals(name + ": goal wall", e.goalWall, goal.name);
            if (e.goalWallRhs != null) assertEquals(name + ": goal wall rhs", e.goalWallRhs, goal.rhs, 0.0);
        }

        if (e.flag(e.legal, false)) {
            List<JumpConstraint> hard = new ArrayList<JumpConstraint>(spec.constraints);
            hard.remove(goal);
            double hardViol = JumpConstraintCompiler.compile(new JumpSpec(sc, hard, spec.objective)).maxViolation(gf, path);
            double shortfall = Paths.shortfall(spec.objective.sense, obj, goal.rhs);
            System.out.printf(Locale.ROOT, "REPLAY %-36s hardViol=%.9e shortfall=%.12e%s%n", name, hardViol, shortfall,
                    e.refShortfall != null ? String.format(Locale.ROOT, " recorded=%.12e drift=%+.3e",
                            e.refShortfall, shortfall - e.refShortfall) : "");
            assertTrue(name + ": hard walls must replay feasible, viol=" + hardViol, hardViol <= 0.0);
            if (e.refShortfall != null) {
                assertTrue(name + ": shortfall drifted from the recorded value: " + shortfall,
                        Math.abs(shortfall - e.refShortfall) <= e.or(e.shortfallTol, 1.0e-9));
            }
        } else {
            System.out.printf(Locale.ROOT, "REPLAY %-36s viol=%.9e obj=%.9f%n", name, viol, obj);
            if (e.flag(e.feasible, true)) assertTrue(name + ": replay not feasible, viol=" + viol, viol <= 0.0);
            if (e.refViolation != null) assertEquals(name + ": violation", e.refViolation, viol, e.or(e.violationTol, 1.0e-6));
        }
        if (e.refObjective != null) assertEquals(name + ": objective", e.refObjective, obj, e.or(e.objectiveTol, 1.0e-6));

        if (e.bindingTick != null) {
            double maxSlack = -1.0;
            int bindTick = Integer.MIN_VALUE;
            for (JumpConstraint jc : spec.constraints) {
                double s = JumpConstraintCompiler.slack(jc, gf, path);
                if (s > maxSlack) {
                    maxSlack = s;
                    bindTick = jc.t1;
                }
            }
            assertEquals(name + ": binding tick", e.bindingTick.intValue(), bindTick);
            assertEquals(name + ": binding slack is the violation", viol, maxSlack, 1.0e-12);
        }
        if (e.feasibleTicks != null) {
            boolean saw = false;
            for (JumpConstraint jc : spec.constraints) {
                boolean touches = e.feasibleTicks.contains(jc.t1) || (jc.t2 != null && e.feasibleTicks.contains(jc.t2));
                if (!touches) continue;
                saw = true;
                double s = JumpConstraintCompiler.slack(jc, gf, path);
                assertTrue(name + ": constraint " + jc.name + " t" + jc.t1 + " slack " + s, s <= 0.0);
            }
            assertTrue(name + ": no constraint touches the ticks " + e.feasibleTicks, saw);
        }
    }

    static double[] rowFacings(List<InputRow> rows, int startTick, JumpPhysicsInputs sc) {
        int n = sc.numTicks;
        double[] gf = new double[n];
        float entity = sc.startYaw;
        for (int k = 0; k < n; k++) {
            InputRow row = rows.get(startTick + k);
            Float yaw = row.getYaw();
            if (yaw != null) entity = row.isYawLocked() ? yaw : entity + yaw;
            gf[k] = entity;
        }
        return gf;
    }

    private static double debugPositionDiff(SaveFile file, int startTick, ForwardPath path, int n) {
        assertNotNull("capture carries no debug ticks", file.debug);
        double posDiff = 0.0;
        for (int k = 0; k <= n; k++) {
            SaveFile.DebugTick d = file.debug.get(startTick + k);
            posDiff = Math.max(posDiff, Math.abs(path.posX[k] - d.pos[0]));
            posDiff = Math.max(posDiff, Math.abs(path.posZ[k] - d.pos[2]));
        }
        return posDiff;
    }
}
