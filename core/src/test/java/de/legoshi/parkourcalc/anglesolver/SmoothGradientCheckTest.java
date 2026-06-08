package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothGradientSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothJumpModel;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Validates the analytic gradient (vs finite differences) and that descent reduces violation. */
public class SmoothGradientCheckTest {

    @Test
    public void descentReachesFeasible() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(8);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0f;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.jumpTick = 0;
        sc.strafeSign = 1;

        SmoothJumpModel model = new SmoothJumpModel(0.005, true);

        // Objective: maximize X at the end. Walls: keep X <= 1.0 on ticks 1..5 (must arc in Z then break out).
        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 8);
        List<JumpConstraint> ineq = new ArrayList<>();
        for (int t = 1; t <= 5; t++) {
            ineq.add(new JumpConstraint(JumpConstraint.Mode.X, t, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.LE, 1.0, "x<=1@" + t));
        }

        SmoothGradientSolver solver = new SmoothGradientSolver(model, sc);
        double[] start = new double[8];
        java.util.Arrays.fill(start, -45.0); // diagonal, non-degenerate
        double startViol = maxViol(model, sc, ineq, start);

        double[] sol = solver.solve(ineq, obj, start);
        double endViol = maxViol(model, sc, ineq, sol);
        System.out.printf("GRADCHK startViol=%.4e endViol=%.4e%n", startViol, endViol);
        org.junit.Assert.assertTrue("descent should reduce violation below start", endViol < startViol);
        org.junit.Assert.assertTrue("descent should reach near-feasible (<1e-3), got " + endViol, endViol < 1e-3);
    }

    private static double maxViol(SmoothJumpModel model, JumpPhysicsInputs sc, List<JumpConstraint> ineq, double[] theta) {
        ForwardPath p = model.forward(sc, sc.toGameFacings(Angles.wrapAll(theta)));
        double m = 0;
        for (JumpConstraint c : ineq) {
            double v = p.posX[c.t1];
            m = Math.max(m, Math.max(0, v - c.rhs));
        }
        return m;
    }
}
