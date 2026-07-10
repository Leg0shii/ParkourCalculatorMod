package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.AlmBfgsCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class AlmBfgsCoreTest {

    @Test
    public void v2Rosenbrock2D() {
        AlmBfgsCore.ValueGradient f = rosenbrock();
        AlmBfgsCore.BfgsResult r = AlmBfgsCore.bfgs(f, new double[]{-1.2, 1.0}, 1000, 1.0e-9, false, 0L, null);
        double fv = f.eval(r.x.clone(), new double[2]);
        System.out.printf("V2 rosenbrock2D    f=%.3e iters=%d exit=%s%n", fv, r.iters, r.exit);
        assertTrue("rosenbrock2D not converged: f=" + fv, fv < 1.0e-12);
    }

    @Test
    public void v2Rosenbrock10D() {
        AlmBfgsCore.ValueGradient f = rosenbrock();
        double[] x0 = new double[10];
        for (int i = 0; i < 10; i++) x0[i] = (i % 2 == 0) ? -1.2 : 1.0;
        AlmBfgsCore.BfgsResult r = AlmBfgsCore.bfgs(f, x0, 5000, 1.0e-9, false, 0L, null);
        double fv = f.eval(r.x.clone(), new double[10]);
        System.out.printf("V2 rosenbrock10D   f=%.3e iters=%d exit=%s%n", fv, r.iters, r.exit);
        assertTrue("rosenbrock10D not converged: f=" + fv, fv < 1.0e-12);
    }

    @Test
    public void v2ConvexQuadratic() {
        int n = 50;
        double[][] a = randomSpd(n, 1.0e4, 20240708L);
        double[] b = new double[n];
        Random rng = new Random(42L);
        for (int i = 0; i < n; i++) b[i] = rng.nextDouble() * 2.0 - 1.0;
        AlmBfgsCore.ValueGradient f = quadratic(a, b);
        AlmBfgsCore.BfgsResult r = AlmBfgsCore.bfgs(f, new double[n], 80, 1.0e-6, false, 0L, null);
        double gradNorm = Math.sqrt(r.gradNormSq);
        System.out.printf("V2 convexQuadratic gradNorm=%.3e iters=%d exit=%s%n", gradNorm, r.iters, r.exit);
        assertTrue("quadratic did not reach gradTol within 80 iters (gradNorm=" + gradNorm + ", exit=" + r.exit + ")",
                "gradTol".equals(r.exit) && gradNorm <= 1.0e-6);
    }

    @Test
    public void translationOffByteIdentical() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec spec = pf.specFor(null, null);
        ExactJumpModel model = pf.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        AlmBfgsCore.Config cfg = new AlmBfgsCore.Config();
        double[] seed = new double[n];
        for (int k = 0; k < n; k++) seed[k] = 1.0;

        AlmBfgsCore.Result base = AlmBfgsCore.solve(model, spec, seed, cfg, 0L, null);
        AlmBfgsCore.Result nullDom = AlmBfgsCore.solve(model, spec, seed, cfg, 0L, null, null);
        AlmBfgsCore.Result zeroDom = AlmBfgsCore.solve(model, spec, seed, cfg, 0L, null, new double[]{0.0, 0.0, 0.0, 0.0});

        assertTrue("tx/tz not zero on pinned domain", base.tx == 0.0 && base.tz == 0.0
                && zeroDom.tx == 0.0 && zeroDom.tz == 0.0);
        assertTrue("null domain smoothViol drift", base.smoothViol == nullDom.smoothViol);
        assertTrue("zero domain smoothViol drift", base.smoothViol == zeroDom.smoothViol);
        assertTrue("null domain smoothObjective drift", base.smoothObjective == nullDom.smoothObjective);
        assertTrue("zero domain smoothObjective drift", base.smoothObjective == zeroDom.smoothObjective);
        for (int k = 0; k < n; k++) {
            assertTrue("null domain theta[" + k + "] drift", base.thetaRad[k] == nullDom.thetaRad[k]);
            assertTrue("zero domain theta[" + k + "] drift", base.thetaRad[k] == zeroDom.thetaRad[k]);
        }
        System.out.printf("BYTEID alm off byte-identical: smoothViol=%.9e smoothObj=%.9f%n",
                base.smoothViol, base.smoothObjective);
    }

    @Test
    public void v3ClosedFormJ004() {
        runV3("j004");
    }

    @Test
    public void v3ClosedFormJ011() {
        runV3("j011-1.875x1bmdoublecross");
    }

    @Test
    public void v3ClosedFormJ006() {
        runV3("j006");
    }

    private void runV3(String name) {
        ProblemFixture pf = ProblemFixture.load("closedform", name);
        JumpSpec spec = pf.specFor(null, null);
        ExactJumpModel model = pf.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double refObj = pf.expect.refObjective;

        AlmBfgsCore.Config cfg = new AlmBfgsCore.Config();
        int seeds = 16;
        AlmBfgsCore.Result best = null;
        double bestObj = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int i = 0; i < seeds; i++) {
            double angle = 2.0 * Math.PI * i / seeds;
            double[] seed = new double[n];
            for (int k = 0; k < n; k++) seed[k] = angle;
            AlmBfgsCore.Result res = AlmBfgsCore.solve(model, spec, seed, cfg, 0L, null);
            if (res.smoothViol > cfg.feasTol) continue;
            boolean better = max ? res.smoothObjective > bestObj : res.smoothObjective < bestObj;
            if (best == null || better) {
                best = res;
                bestObj = res.smoothObjective;
            }
        }
        assertTrue(name + ": no seed produced a smooth-feasible solution", best != null);

        double[] gf = sc.toGameFacings(Angles.wrapAll(toDeg(best.thetaRad)));
        ForwardPath path = model.forward(sc, gf);
        double exactObj = path.getPos(spec.objective.tick, spec.objective.axis);
        double exactViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        double gap = max ? refObj - bestObj : bestObj - refObj;

        System.out.printf("V3 %-5s smoothObj=%.9f ref=%.9f gap=%.3e smoothViol=%.3e exactObj=%.9f "
                        + "exactViol=%.3e smooth_exact_gap=%.3e%n",
                name, bestObj, refObj, gap, best.smoothViol, exactObj, exactViol, best.counters.smoothExactGap);

        assertTrue(name + ": smooth solution not feasible (viol=" + best.smoothViol + ")",
                best.smoothViol <= cfg.feasTol);
        assertTrue(name + ": objective " + bestObj + " short of reference " + refObj + " by " + gap,
                gap <= 1.0e-6);
        assertTrue(name + ": smooth_exact_gap " + best.counters.smoothExactGap + " >= 1e-2",
                best.counters.smoothExactGap < 1.0e-2);
    }

    private static double[] toDeg(double[] rad) {
        double[] d = new double[rad.length];
        for (int i = 0; i < rad.length; i++) d[i] = Math.toDegrees(rad[i]);
        return d;
    }

    private static AlmBfgsCore.ValueGradient rosenbrock() {
        return (x, g) -> {
            int n = x.length;
            for (int i = 0; i < n; i++) g[i] = 0.0;
            double f = 0.0;
            for (int i = 0; i < n - 1; i++) {
                double t1 = x[i + 1] - x[i] * x[i];
                double t2 = 1.0 - x[i];
                f += 100.0 * t1 * t1 + t2 * t2;
                g[i] += -400.0 * t1 * x[i] - 2.0 * t2;
                g[i + 1] += 200.0 * t1;
            }
            return f;
        };
    }

    private static AlmBfgsCore.ValueGradient quadratic(double[][] a, double[] b) {
        return (x, g) -> {
            int n = x.length;
            double f = 0.0;
            for (int i = 0; i < n; i++) {
                double ax = 0.0;
                double[] row = a[i];
                for (int j = 0; j < n; j++) ax += row[j] * x[j];
                g[i] = ax - b[i];
                f += 0.5 * x[i] * ax - b[i] * x[i];
            }
            return f;
        };
    }

    private static double[][] randomSpd(int n, double condition, long seed) {
        Random rng = new Random(seed);
        double[][] q = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) q[i][j] = rng.nextGaussian();
        }
        gramSchmidt(q);
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = Math.pow(condition, (double) i / (n - 1));
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double s = 0.0;
                for (int k = 0; k < n; k++) s += q[k][i] * d[k] * q[k][j];
                a[i][j] = s;
            }
        }
        return a;
    }

    private static void gramSchmidt(double[][] q) {
        int n = q.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double dot = 0.0;
                for (int k = 0; k < n; k++) dot += q[i][k] * q[j][k];
                for (int k = 0; k < n; k++) q[i][k] -= dot * q[j][k];
            }
            double norm = 0.0;
            for (int k = 0; k < n; k++) norm += q[i][k] * q[i][k];
            norm = Math.sqrt(norm);
            for (int k = 0; k < n; k++) q[i][k] /= norm;
        }
    }
}
