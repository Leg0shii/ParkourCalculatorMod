package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.AlmBfgsCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.AlmSnapStage;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothJumpProblem;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AlmSmoothLambdaTest {

    private static JumpSpec withLambda(JumpSpec spec, double lambda) {
        Objective o = spec.objective;
        return new JumpSpec(spec.asScenario(), spec.constraints,
                new Objective(o.axis, o.sense, o.tick, lambda));
    }

    private static double[] wigglySeed(int n, double amplitudeDeg) {
        double[] s = new double[n];
        for (int k = 0; k < n; k++) s[k] = Math.toRadians((k % 2 == 0 ? 1 : -1) * amplitudeDeg);
        return s;
    }

    @Test
    public void wiggleGradientMatchesFiniteDifference() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec spec = withLambda(pf.specFor(null, null), 1.0e-2);
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        SmoothJumpProblem p = SmoothJumpProblem.compile(spec, new boolean[n], new boolean[n], pf.model.modern());

        double[] theta = new double[n];
        for (int k = 0; k < n; k++) theta[k] = Math.toRadians(20.0 * Math.sin(1.7 * k) + 5.0);
        double[] lamb = new double[p.ineq().size()];
        double[] nu = new double[p.eq().size()];
        for (int i = 0; i < lamb.length; i++) lamb[i] = 0.1 * (i % 3);
        for (int j = 0; j < nu.length; j++) nu[j] = 0.05 * (j % 2 == 0 ? 1 : -1);
        double pen = 2.0;

        double[] analytic = new double[n];
        p.augLagrangian(theta, lamb, nu, pen, analytic);
        double h = 1.0e-6;
        double[] tmp = new double[n];
        double worst = 0.0;
        for (int k = 0; k < n; k++) {
            double save = theta[k];
            theta[k] = save + h;
            double vp = p.augLagrangian(theta, lamb, nu, pen, tmp);
            theta[k] = save - h;
            double vm = p.augLagrangian(theta, lamb, nu, pen, tmp);
            theta[k] = save;
            double num = (vp - vm) / (2.0 * h);
            double denom = Math.max(1.0, Math.abs(num));
            worst = Math.max(worst, Math.abs(num - analytic[k]) / denom);
        }
        assertTrue("augLagrangian wiggle gradient relErr=" + worst, worst < 1.0e-6);

        SmoothJumpProblem pt = p.withTranslationBox(-0.05, 0.05, -0.05, 0.05);
        double[] x = new double[n + 2];
        System.arraycopy(theta, 0, x, 0, n);
        x[n] = 0.01;
        x[n + 1] = -0.02;
        double[] lambT = new double[pt.ineq().size()];
        double[] nuT = new double[pt.eq().size()];
        for (int i = 0; i < lambT.length; i++) lambT[i] = 0.1 * (i % 3);
        for (int j = 0; j < nuT.length; j++) nuT[j] = 0.05 * (j % 2 == 0 ? 1 : -1);
        double[] analyticT = new double[n + 2];
        pt.augLagrangianT(x, x[n], x[n + 1], lambT, nuT, pen, analyticT);
        double[] tmpT = new double[n + 2];
        double worstT = 0.0;
        for (int k = 0; k < n + 2; k++) {
            double save = x[k];
            x[k] = save + h;
            double vp = pt.augLagrangianT(x, x[n], x[n + 1], lambT, nuT, pen, tmpT);
            x[k] = save - h;
            double vm = pt.augLagrangianT(x, x[n], x[n + 1], lambT, nuT, pen, tmpT);
            x[k] = save;
            double num = (vp - vm) / (2.0 * h);
            double denom = Math.max(1.0, Math.abs(num));
            worstT = Math.max(worstT, Math.abs(num - analyticT[k]) / denom);
        }
        assertTrue("augLagrangianT wiggle gradient relErr=" + worstT, worstT < 1.0e-6);
    }

    @Test
    public void lambdaZeroStageByteIdentical() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec base = pf.specFor(null, null);
        JumpSpec zero = withLambda(base, 0.0);
        ExactJumpModel model = pf.model;

        AlmSnapStage.SolveOutcome a = AlmSnapStage.solve(model, base, new ArrayList<double[]>(), 8,
                false, 16, 1.0, null, 0L, null);
        AlmSnapStage.SolveOutcome b = AlmSnapStage.solve(model, zero, new ArrayList<double[]>(), 8,
                false, 16, 1.0, null, 0L, null);

        assertEquals(a.feasible, b.feasible);
        assertTrue("objective drift", a.objective == b.objective);
        assertTrue("viol drift", a.viol == b.viol);
        assertEquals(a.yawsDeg.length, b.yawsDeg.length);
        for (int k = 0; k < a.yawsDeg.length; k++) {
            assertTrue("yaw[" + k + "] drift", a.yawsDeg[k] == b.yawsDeg[k]);
        }
    }

    @Test
    public void almFlattensWigglySeedUnderLambda() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec base = pf.specFor(null, null);
        ExactJumpModel model = pf.model;
        int n = base.asScenario().numTicks;
        double[] seed = wigglySeed(n, 30.0);
        double seedTravel = Angles.travelDeg(toDeg(seed));
        AlmBfgsCore.Config cfg = new AlmBfgsCore.Config();

        AlmBfgsCore.Result plain = AlmBfgsCore.solve(model, base, seed.clone(), cfg, 0L, null);
        AlmBfgsCore.Result smooth = AlmBfgsCore.solve(model, withLambda(base, 1.0e-2), seed.clone(), cfg, 0L, null);

        assertTrue("plain run not smooth-feasible: " + plain.smoothViol, plain.smoothViol <= cfg.feasTol);
        assertTrue("lambda run not smooth-feasible: " + smooth.smoothViol, smooth.smoothViol <= cfg.feasTol);

        double plainTravel = Angles.travelDeg(toDeg(plain.thetaRad));
        double smoothTravel = Angles.travelDeg(toDeg(smooth.thetaRad));
        System.out.printf("ALM-LAMBDA j004 seedTravel=%.1f plainTravel=%.3f smoothTravel=%.3f "
                        + "plainObj=%.9f smoothObj=%.9f%n",
                seedTravel, plainTravel, smoothTravel, plain.smoothObjective, smooth.smoothObjective);
        assertTrue("lambda did not flatten the wiggly seed: " + smoothTravel + " vs seed " + seedTravel,
                smoothTravel <= 0.25 * seedTravel);
        assertTrue("lambda run wigglier than plain: " + smoothTravel + " vs " + plainTravel,
                smoothTravel <= plainTravel + 1.0);
    }

    private static double[] toDeg(double[] rad) {
        double[] d = new double[rad.length];
        for (int i = 0; i < rad.length; i++) d[i] = Math.toDegrees(rad[i]);
        return d;
    }
}
