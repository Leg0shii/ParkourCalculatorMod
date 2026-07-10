package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothJumpProblem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SmoothJumpProblemTest {

    private static final int DRAWS = 32;
    private static final double TOL_A = 1.0e-12;
    private static final double TOL_B = 5.0e-6;
    private static final double TOL_C = 1.0e-6;

    @Test
    public void v1SingleJumpClosedForm() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec spec = pf.specFor(null, null);
        double dev = check(pf.model, spec, false, "j004");
        System.out.printf("V1 j004            max fast-vs-exact deviation = %.3e%n", dev);
    }

    @Test
    public void v1NixMultiJump() {
        ProblemFixture pf = ProblemFixture.load("solve", "nix-full-t1");
        JumpSpec spec = pf.specFor(null, null);
        spec.asScenario().startBox = null;
        double dev = check(pf.model, spec, true, "nix-full-t1");
        System.out.printf("V1 nix-full-t1     max fast-vs-exact deviation = %.3e%n", dev);
    }

    @Test
    public void v1RazorWeirdpaneAirborneLag() {
        RazorFixtures.Loaded l = RazorFixtures.loadWeirdpaneSpec();
        double dev = check(l.model, l.spec, false, "razor-weirdpane");
        System.out.printf("V1 razor-weirdpane max fast-vs-exact deviation = %.3e%n", dev);
        assertTrue("razor-weirdpane: fast-vs-exact deviation " + dev + " exceeds " + TOL_B, dev <= TOL_B);
    }

    private double check(ExactJumpModel model, JumpSpec spec, boolean requireBoost, String label) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        Objective obj = spec.objective;
        int objAxis = obj.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        Random rng = new Random(1234567L + label.hashCode());

        double maxDevB = 0.0;
        int boostChecked = 0;
        int termsCheckedB = 0;
        for (int d = 0; d < DRAWS; d++) {
            double[] yawAbs = new double[n];
            for (int k = 0; k < n; k++) yawAbs[k] = rng.nextDouble() * 360.0 - 180.0;
            double[] thetaRad = new double[n];
            for (int k = 0; k < n; k++) thetaRad[k] = Math.toRadians(yawAbs[k]);

            double[] gf = sc.toGameFacings(Angles.wrapAll(yawAbs));
            float[] gfF = new float[n];
            for (int k = 0; k < n; k++) gfF[k] = (float) gf[k];
            ForwardPath path = model.forward(sc, gf);
            boolean[] zeroX = new boolean[n];
            boolean[] zeroZ = new boolean[n];
            derivePattern(model, sc, gf, zeroX, zeroZ);

            SmoothJumpProblem problem = SmoothJumpProblem.compile(spec, zeroX, zeroZ, model.modern());
            JumpLinearModel recon = new JumpLinearModel(sc, zeroX, zeroZ);

            List<SmoothJumpProblem.Term> all = new ArrayList<>();
            all.add(problem.objective());
            all.addAll(problem.ineq());
            all.addAll(problem.eq());

            for (SmoothJumpProblem.Term term : all) {
                double smooth = problem.smoothValue(term, thetaRad);
                double reconVal = reconstruct(term, obj, objAxis, recon, sc, yawAbs);
                assertTrue(label + ": check(a) smoothValue vs reconstruction off by "
                                + Math.abs(smooth - reconVal) + " for term " + term.name,
                        Math.abs(smooth - reconVal) <= TOL_A * Math.max(1.0, Math.abs(reconVal)) + TOL_A);

                if (isFacing(term)) continue;
                double fast = problem.fastValue(term, gfF);
                double ref = exactReference(term, obj, objAxis, spec, gf, path);
                double dev = Math.abs(fast - ref);
                maxDevB = Math.max(maxDevB, dev);
                termsCheckedB++;
                if (hasBoost(term)) boostChecked++;
                assertTrue(label + ": check(b) fast vs exact off by " + dev + " for term " + term.name,
                        dev <= TOL_B);
            }

            checkGradient(problem, thetaRad, rng, label);
        }
        assertTrue(label + ": no non-facing terms checked in (b)", termsCheckedB > 0);
        if (requireBoost) {
            assertTrue(label + ": no sprint-jump (boost) term covered by check (b)", boostChecked > 0);
        }
        System.out.printf("V1 %-14s draws=%d termsB=%d boostChecked=%d%n",
                label, DRAWS, termsCheckedB, boostChecked);
        return maxDevB;
    }

    private void checkGradient(SmoothJumpProblem problem, double[] thetaRad, Random rng, String label) {
        int n = thetaRad.length;
        double[] lambda = new double[problem.ineq().size()];
        for (int i = 0; i < lambda.length; i++) lambda[i] = rng.nextDouble() * 2.0;
        double[] nu = new double[problem.eq().size()];
        for (int j = 0; j < nu.length; j++) nu[j] = rng.nextDouble() * 2.0 - 1.0;
        double pen = 0.5 + rng.nextDouble() * 2.5;

        double[] analytic = new double[n];
        problem.smoothGradient(thetaRad, lambda, nu, pen, analytic);
        double h = 1.0e-6;
        double[] scratch = new double[n];
        double[] probe = thetaRad.clone();
        for (int k = 0; k < n; k++) {
            double save = probe[k];
            probe[k] = save + h;
            double vp = problem.augLagrangian(probe, lambda, nu, pen, scratch);
            probe[k] = save - h;
            double vm = problem.augLagrangian(probe, lambda, nu, pen, scratch);
            probe[k] = save;
            double num = (vp - vm) / (2.0 * h);
            double denom = Math.max(1.0, Math.abs(num));
            double rel = Math.abs(num - analytic[k]) / denom;
            assertTrue(label + ": check(c) gradient[" + k + "] analytic=" + analytic[k] + " numeric=" + num
                    + " rel=" + rel, rel <= TOL_C);
        }
    }

    private static boolean isFacing(SmoothJumpProblem.Term term) {
        for (double c : term.thetaC) if (c != 0.0) return true;
        return false;
    }

    private static boolean hasBoost(SmoothJumpProblem.Term term) {
        for (int i = 0; i < term.boostSinC.length; i++) {
            if (term.boostSinC[i] != 0.0 || term.boostCosC[i] != 0.0) return true;
        }
        return false;
    }

    private static double reconstruct(SmoothJumpProblem.Term term, Objective obj, int objAxis,
                                      JumpLinearModel recon, JumpPhysicsInputs sc, double[] yawAbs) {
        if (term.source == null) {
            return linPos(recon, objAxis, obj.tick, yawAbs);
        }
        JumpConstraint c = term.source;
        if (c.mode == JumpConstraint.Mode.F) {
            return term.constant + facingLinear(term, yawAbs);
        }
        int axis = c.mode == JumpConstraint.Mode.X ? 0 : 1;
        double opSign = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
        double value = linPos(recon, axis, c.t1, yawAbs);
        if (c.t2 != null) value += opSign * linPos(recon, axis, c.t2, yawAbs);
        value -= c.rhs;
        return c.cmp == JumpConstraint.Cmp.GE ? -value : value;
    }

    private static double facingLinear(SmoothJumpProblem.Term term, double[] yawAbs) {
        double v = 0.0;
        for (int k = 0; k < term.thetaC.length; k++) {
            if (term.thetaC[k] != 0.0) v += term.thetaC[k] * yawAbs[k];
        }
        return v;
    }

    private static double linPos(JumpLinearModel recon, int axis, int k, double[] yawAbs) {
        double v = recon.constPos(k, axis);
        for (int s = 0; s < yawAbs.length; s++) {
            double coef = recon.coefAxis(axis, s, k);
            if (coef == 0.0) continue;
            v += coef * addInput(recon, axis, s, yawAbs[s]);
        }
        return v;
    }

    private static double addInput(JumpLinearModel recon, int axis, int s, double yawDeg) {
        double th = Math.toRadians(yawDeg);
        double sin = Math.sin(th);
        double cos = Math.cos(th);
        double sm = recon.strafeMag(s);
        double fm = recon.forwardMag(s);
        double bo = recon.boostAt(s);
        if (axis == 0) return sm * cos - fm * sin - bo * sin;
        return fm * cos + sm * sin + bo * cos;
    }

    private static double exactReference(SmoothJumpProblem.Term term, Objective obj, int objAxis,
                                         JumpSpec spec, double[] gf, ForwardPath path) {
        if (term.source == null) {
            return path.getPos(obj.tick, obj.axis);
        }
        JumpConstraint c = term.source;
        double value = JumpConstraintCompiler.evaluate(c, gf, path);
        return c.cmp == JumpConstraint.Cmp.GE ? -value : value;
    }

    private static void derivePattern(ExactJumpModel model, JumpPhysicsInputs sc, double[] gf,
                                      boolean[] outX, boolean[] outZ) {
        ForwardPath path = model.forward(sc, gf);
        boolean perAxis = model.perAxisInertia();
        double thr = model.inertiaThreshold();
        int n = sc.numTicks;
        for (int t = 0; t < n; t++) {
            if (perAxis) {
                outX[t] = Math.abs(path.velX[t]) < thr;
                outZ[t] = Math.abs(path.velZ[t]) < thr;
            } else {
                double vx = path.velX[t];
                double vz = path.velZ[t];
                boolean z = vx * vx + vz * vz < 9.0e-6;
                outX[t] = z;
                outZ[t] = z;
            }
        }
    }
}
