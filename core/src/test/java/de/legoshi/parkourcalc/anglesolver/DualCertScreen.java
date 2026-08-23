package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * gh-418 screen: why does the closed form fail to certify? At the dual's own answer this prints the
 * linear model's predicted position against the byte-exact forward for every tick and every
 * constraint, plus the per-tick velocity against the inertia threshold, so the divergence is
 * localised rather than guessed at. Skipped unless PKC_SCREENS is set; PKC_DC_FILE names the save.
 */
public class DualCertScreen {

    private static final double RAD = Math.PI / 180.0;

    static double uAxis(JumpLinearModel lin, int axis, int s, double gfDeg) {
        double phi = lin.baseArg(s) + gfDeg * RAD;
        return lin.mMag(s) * (axis == 0 ? Math.cos(phi) : Math.sin(phi));
    }

    static double linPos(JumpLinearModel lin, int axis, int k, double[] gf) {
        double p = lin.constPos(k, axis);
        for (int s = 0; s < k; s++) {
            double c = lin.coefAxis(axis, s, k);
            if (c == 0.0) continue;
            p += c * uAxis(lin, axis, s, gf[s]);
        }
        return p;
    }

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_SCREENS=1", "1".equals(System.getenv("PKC_SCREENS")));
        String path = System.getenv("PKC_DC_FILE");
        Assume.assumeTrue("set PKC_DC_FILE", path != null && !path.isEmpty());

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()),
                StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        AtomicBoolean cancel = new AtomicBoolean(false);

        System.out.println("=== DC file=" + new File(path).getName() + " mc=" + file.mcVersion
                + " n=" + n + " cons=" + spec.constraints.size()
                + " thr=" + model.inertiaThreshold() + " perAxis=" + model.perAxisInertia()
                + " startTick=" + state.getStartTick() + " ===");
        System.out.printf("DC start pos=(%.9f, %.9f) vel=(%.9f, %.9f) startYaw=%.6f%n",
                sc.startPos.x, sc.startPos.z, sc.initialVelocity.x, sc.initialVelocity.z, sc.startYaw);

        ClosedFormSolve.Result r = ClosedFormSolve.optimizeRobustGraded(model, spec, 0.0, cancel);
        if (r == null || r.yaws == null) {
            System.out.println("DC dual returned null");
            return;
        }
        System.out.printf("DC dual viol=%.9e feasible=%s%n", r.violation, r.feasible);

        double[] y = Angles.wrapAll(r.yaws);
        double[] gf = sc.toGameFacings(y);
        ForwardPath p = model.forward(sc, gf);
        JumpLinearModel lin = new JumpLinearModel(sc);

        System.out.println("DC --- per-tick: linear model vs byte-exact ---");
        System.out.printf("DC %-4s %9s %9s %14s %14s %11s %14s %14s %11s %11s %-5s%n",
                "tick", "yawAbs", "gameFace", "linX", "exactX", "dX", "linZ", "exactZ", "dZ", "dist", "grnd");
        double worst = 0.0;
        int worstTick = -1;
        double prevD = 0.0;
        for (int k = 0; k <= n; k++) {
            double lx = linPos(lin, 0, k, gf);
            double lz = linPos(lin, 1, k, gf);
            double ex = p.getPos(k, JumpPhysicsInputs.Axis.X);
            double ez = p.getPos(k, JumpPhysicsInputs.Axis.Z);
            double dx = lx - ex;
            double dz = lz - ez;
            double d = Math.hypot(dx, dz);
            boolean grounded = k < n && !Double.isNaN(sc.slipAt(k));
            String jump = k < n && sc.jumpAt(k) && grounded ? " JUMP" : "";
            String step = d - prevD > 1.0e-4 ? "  <-STEP" : "";
            System.out.printf("DC %-4d %9.4f %9.4f %14.9f %14.9f %11.3e %14.9f %14.9f %11.3e %11.3e %-5s%s%s%n",
                    k, k < n ? y[k] : Double.NaN, k < n ? gf[k] : Double.NaN,
                    lx, ex, dx, lz, ez, dz, d, grounded ? "yes" : "air", jump, step);
            if (d > worst) {
                worst = d;
                worstTick = k;
            }
            prevD = d;
        }
        System.out.printf("DC worst per-tick divergence %.9e at tick %d%n", worst, worstTick);

        System.out.println("DC --- per-tick velocity against the inertia gate ---");
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        lin.zeroingPattern(y, model.inertiaThreshold(), model.perAxisInertia(), zx, zz);
        double thr = model.inertiaThreshold();
        for (int k = 0; k < n; k++) {
            double vx = p.velX == null ? Double.NaN : p.velX[k];
            double vz = p.velZ == null ? Double.NaN : p.velZ[k];
            boolean nearX = Math.abs(vx) < 2.0 * thr;
            boolean nearZ = Math.abs(vz) < 2.0 * thr;
            if (zx[k] || zz[k] || nearX || nearZ) {
                System.out.printf("DC   t=%-4d vx=%14.9f vz=%14.9f linZeroX=%-5s linZeroZ=%-5s %s%s%n",
                        k, vx, vz, zx[k], zz[k], nearX ? "vxUNDER2xTHR " : "", nearZ ? "vzUNDER2xTHR" : "");
            }
        }

        System.out.println("DC --- per-constraint: linear prediction vs exact ---");
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        for (JumpConstraint c : spec.constraints) {
            if (c.t2 != null) continue;
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) continue;
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : 1;
            double lv = linPos(lin, axis, c.t1, gf);
            double ev = p.getPos(c.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double slackLin = c.cmp == JumpConstraint.Cmp.GE ? lv - c.rhs : c.rhs - lv;
            double slackEx = c.cmp == JumpConstraint.Cmp.GE ? ev - c.rhs : c.rhs - ev;
            System.out.printf("DC   %s t=%-4d %-2s rhs=%14.7f lin=%14.7f exact=%14.7f linSlack=%11.3e exactSlack=%11.3e %s%n",
                    c.mode, c.t1, c.cmp, c.rhs, lv, ev, slackLin, slackEx, slackEx < 0 ? "VIOLATED" : "");
        }
        System.out.printf("DC exact maxViolation=%.9e%n", comp.maxViolation(gf, p));
    }
}
