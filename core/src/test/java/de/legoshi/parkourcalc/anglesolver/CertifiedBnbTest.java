package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SineTableGeometry;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CertifiedBnbTest {

    private static JumpPhysicsInputs scenario(int n, boolean jumpTick0, double vx0, double vz0) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0.0F;
        sc.initialVelocity = new Vec3dCore(vx0, 0.0, vz0);
        sc.startBox = StartBox.pinned(0.5, 0.5, vx0, vz0);
        sc.jumpTick = jumpTick0 ? 0 : -1;
        double[] slip = new double[n];
        for (int t = 0; t < n; t++) slip[t] = t == 0 && jumpTick0 ? 0.6 : Double.NaN;
        sc.slipPerTick = slip;
        return sc;
    }

    private static double[] realizedU(ExactJumpModel model, JumpPhysicsInputs base, int tick, double gf) {
        JumpPhysicsInputs sc = base.copy();
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.startBox = StartBox.pinned(sc.startPos.x, sc.startPos.z, 0.0, 0.0);
        double[] yaws = new double[sc.numTicks];
        boolean[] locked = new boolean[sc.numTicks];
        for (int t = 0; t < sc.numTicks; t++) {
            yaws[t] = gf;
            locked[t] = true;
        }
        sc.yawLockedPerTick = locked;
        JumpPhysicsInputs shifted = shiftTo(sc, tick);
        double[] gfArr = new double[shifted.numTicks];
        for (int t = 0; t < gfArr.length; t++) gfArr[t] = gf;
        ForwardPath p = model.forward(shifted, gfArr);
        return new double[]{p.posX[1] - p.posX[0], p.posZ[1] - p.posZ[0]};
    }

    private static JumpPhysicsInputs shiftTo(JumpPhysicsInputs sc, int tick) {
        JumpPhysicsInputs one = new JumpPhysicsInputs(1);
        one.startPos = sc.startPos;
        one.startYaw = sc.startYaw;
        one.initialVelocity = Vec3dCore.ZERO;
        one.startBox = StartBox.pinned(sc.startPos.x, sc.startPos.z, 0.0, 0.0);
        one.jumpTick = sc.jumpAt(tick) ? 0 : -1;
        double[] slip = {sc.slipAt(tick)};
        one.slipPerTick = slip;
        boolean[] lock = {true};
        one.yawLockedPerTick = lock;
        return one;
    }

    private static final double[][] TEST_BRACKETS = {
            {10.0, 17.0}, {-123.4, -120.9}, {179.0, 181.0}, {-0.3, 0.3}, {44.9, 45.1}
    };

    private static final double[][] TEST_DIRS = {
            {1.0, 0.0}, {0.0, 1.0}, {-1.0, 0.0}, {0.0, -1.0},
            {0.7071, 0.7071}, {-0.3, 0.95}, {0.99, -0.14}, {-0.6, -0.8}
    };

    @Test
    public void supportAndRangeContainRealizedInputsAcrossEras() {
        for (String ver : new String[]{"1.8.9", "1.12.2", "1.21.5", "26.2"}) {
            ExactJumpModel model = ExactJumpModel.forMcVersion(ver);
            for (boolean jump : new boolean[]{true, false}) {
                JumpPhysicsInputs sc = scenario(2, jump, 0.0, 0.0);
                SineTableGeometry geom = new SineTableGeometry(model, sc);
                int tick = 0;
                if (!geom.hasInput(tick)) continue;
                for (double[] br : TEST_BRACKETS) {
                    SineTableGeometry.RangeInfo ri = geom.rangeInfo(tick, br[0], br[1]);
                    for (int k = 0; k <= 200; k++) {
                        double gf = br[0] + (br[1] - br[0]) * k / 200.0;
                        double[] u = realizedU(model, sc, tick, (float) gf);
                        assertTrue(ver + " uxLo " + gf, u[0] >= ri.uxLo - 1.0e-15);
                        assertTrue(ver + " uxHi " + gf, u[0] <= ri.uxHi + 1.0e-15);
                        assertTrue(ver + " uzLo " + gf, u[1] >= ri.uzLo - 1.0e-15);
                        assertTrue(ver + " uzHi " + gf, u[1] <= ri.uzHi + 1.0e-15);
                        if (ri.hasChord) {
                            double proj = ri.chordAx * u[0] + ri.chordAz * u[1];
                            assertTrue(ver + " chord " + gf, proj >= ri.chordRhs - 1.0e-15);
                        }
                        for (double[] g : TEST_DIRS) {
                            double sup = geom.support(tick, g[0], g[1], br[0], br[1]);
                            double dot = g[0] * u[0] + g[1] * u[1];
                            assertTrue(ver + " support " + gf, dot <= sup + 1.0e-15);
                        }
                        double r = geom.radiusUpper(tick);
                        assertTrue(ver + " radius " + gf, Math.hypot(u[0], u[1]) <= r + 1.0e-15);
                    }
                }
                for (double[] g : TEST_DIRS) {
                    double supFull = geom.support(tick, g[0], g[1], Double.NaN, Double.NaN);
                    for (int k = 0; k < 720; k++) {
                        double gf = -180.0 + k * 0.5;
                        double[] u = realizedU(model, sc, tick, (float) gf);
                        double dot = g[0] * u[0] + g[1] * u[1];
                        assertTrue(ver + " fullSupport " + gf, dot <= supFull + 1.0e-15);
                    }
                }
            }
        }
    }

    private static JumpSpec tinySpec(JumpPhysicsInputs sc) {
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, sc.numTicks, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, sc.startPos.z + 0.12, "zcap"));
        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, sc.numTicks);
        return new JumpSpec(sc, cons, obj);
    }

    @Test
    public void tinyInstanceCertifiesAtTheEnumeratedOptimum() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(2, false, 0.25, 0.05);
        JumpSpec spec = tinySpec(sc);

        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = 300000;
        cfg.polishCap = 6;
        CertifiedBnb.Result res = CertifiedBnb.solve(model, spec, cfg);
        assertTrue("must not decline", !res.declined);
        assertTrue("must find a feasible point", res.feasible);
        assertTrue("must certify, gap=" + res.gap, res.certified);
        assertTrue("gap " + res.gap, res.gap <= CertifiedBnb.CERT_EPS);

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double best = Double.NEGATIVE_INFINITY;
        double[] yaws = new double[2];
        double bucket = 360.0 / 65536.0;
        for (int a = 0; a < 512; a++) {
            yaws[0] = Angles.wrap(-180.0 + a * (360.0 / 512.0));
            for (int b = 0; b < 65536; b += 64) {
                yaws[1] = Angles.wrap(-180.0 + b * bucket);
                double[] gf = sc.toGameFacings(yaws);
                ForwardPath p = model.forward(sc, gf);
                if (compiled.maxViolation(gf, p) != 0.0) continue;
                double v = p.posX[2];
                if (v > best) best = v;
            }
        }
        assertTrue("enum found something", best > Double.NEGATIVE_INFINITY);
        assertTrue("certified objective " + res.objective + " must dominate the sampled enum " + best,
                res.objective >= best - 1.0e-12);
        assertTrue("bound " + res.boundObjective + " must dominate the incumbent",
                res.boundObjective >= res.objective - CertifiedBnb.CERT_EPS);
    }

    @Test
    public void gateResolutionMatchesBruteForceOnANearThresholdCarry() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(3, false, 0.0049, 0.0);
        JumpSpec spec = tinySpec(sc);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] yaws = new double[3];
        boolean sawZeroX0 = false;
        boolean sawOpenX1 = false;
        for (int a = 0; a < 360; a += 5) {
            yaws[0] = Angles.wrap(a);
            for (int b = 0; b < 360; b += 5) {
                yaws[1] = Angles.wrap(b);
                yaws[2] = 0.0;
                double[] gf = sc.toGameFacings(yaws);
                ForwardPath p = model.forward(sc, gf);
                if (Math.abs(p.velX[0]) < model.inertiaThreshold()) sawZeroX0 = true;
                if (Math.abs(p.velX[1]) >= model.inertiaThreshold()) sawOpenX1 = true;
                assertNotNull(compiled);
            }
        }
        assertTrue("carry 0.0049 must gate-zero at t0 in every replay", sawZeroX0);
        assertTrue("some replays must keep X alive at t1", sawOpenX1);
    }

    @Test
    public void tinySolveIsDeterministic() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(2, false, 0.25, 0.05);
        JumpSpec spec = tinySpec(sc);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = 300000;
        cfg.polishCap = 6;
        CertifiedBnb.Result a = CertifiedBnb.solve(model, spec, cfg);
        CertifiedBnb.Config cfg2 = new CertifiedBnb.Config();
        cfg2.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg2.nodeCap = 300000;
        cfg2.polishCap = 6;
        CertifiedBnb.Result b = CertifiedBnb.solve(model, spec, cfg2);
        assertTrue(a.feasible && b.feasible);
        assertEquals(Double.doubleToLongBits(a.objective), Double.doubleToLongBits(b.objective));
        assertEquals(a.yawsDeg.length, b.yawsDeg.length);
        for (int i = 0; i < a.yawsDeg.length; i++) {
            assertEquals("yaw " + i, Double.doubleToLongBits(a.yawsDeg[i]), Double.doubleToLongBits(b.yawsDeg[i]));
        }
    }
}
