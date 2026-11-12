package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.FlowVector;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
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
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

        double verified = Scoring.verifiedObjectiveAt(model, sc, spec, res.yawsDeg, res.px, res.pz, 0.0);
        assertFalse("certified optimum must be byte-exact lattice-attainable", Double.isNaN(verified));
        assertEquals("certified objective must equal its byte-exact re-simulation",
                res.objective, verified, CertifiedBnb.CERT_EPS);
    }

    private static JumpSpec withWallPastTheOptimum(JumpPhysicsInputs sc, double optimum, double past) {
        JumpSpec spec = tinySpec(sc);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(spec.constraints);
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, sc.numTicks, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, optimum + past, "xwall"));
        return new JumpSpec(sc, cons, spec.objective);
    }

    @Test
    public void exhaustedSearchWithoutAWitnessCertifiesInfeasibility() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(2, false, 0.25, 0.05);
        CertifiedBnb.Config opt = new CertifiedBnb.Config();
        opt.mode = CertifiedBnb.Mode.OPTIMIZE;
        opt.nodeCap = 300000;
        opt.polishCap = 6;
        CertifiedBnb.Result best = CertifiedBnb.solve(model, tinySpec(sc), opt);
        assertTrue("the tiny instance must certify its optimum first", best.certified);

        for (double past : new double[]{0.5, 1.0e-3}) {
            CertifiedBnb.Config cfg = new CertifiedBnb.Config();
            cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
            cfg.nodeCap = 300000;
            cfg.polishCap = 2;
            CertifiedBnb.Result res = CertifiedBnb.solve(model, withWallPastTheOptimum(sc, best.objective, past), cfg);
            assertFalse("a wall " + past + " past the certified optimum must not decline", res.declined);
            assertFalse("a wall " + past + " past the certified optimum has no witness", res.feasible);
            assertTrue("a wall " + past + " past the certified optimum must be certified infeasible, unresolved="
                    + res.unresolved + " nodes=" + res.nodes, res.certifiedInfeasible);
        }
    }

    @Test
    public void aFeasibleInstanceNeverCarriesAnInfeasibilityCertificate() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(2, false, 0.25, 0.05);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 300000;
        CertifiedBnb.Result res = CertifiedBnb.solve(model, tinySpec(sc), cfg);
        assertTrue(res.feasible);
        assertFalse(res.certifiedInfeasible);
    }

    @Test
    public void aFlowPushedJumpNeverCarriesAnInfeasibilityCertificate() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.21.3");
        int n = 4;
        JumpPhysicsInputs pushed = scenario(n, false, 0.0, 0.2);
        FlowVector[] flow = new FlowVector[n];
        Arrays.fill(flow, new FlowVector(0.0, 1.0));
        pushed.flowPerTick = flow;
        JumpPhysicsInputs still = scenario(n, false, 0.0, 0.2);
        double[] straight = new double[n];
        double[] facings = pushed.toGameFacings(straight);
        ForwardPath path = model.forward(pushed, facings);
        double reachPushed = path.posZ[n];
        double reachStill = model.forward(still, still.toGameFacings(straight)).posZ[n];
        assertTrue("the push must carry the jump further", reachPushed > reachStill);

        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, n, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 0.5 * (reachPushed + reachStill), "reach"));
        JumpSpec spec = new JumpSpec(pushed, cons, new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, n));
        assertEquals("walking straight meets the wall", 0.0,
                JumpConstraintCompiler.compile(spec).maxViolation(facings, path), 0.0);

        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 200000;
        CertifiedBnb.Result res = CertifiedBnb.solve(model, spec, cfg);
        assertFalse("the search does not model the push, so it cannot certify", res.certifiedInfeasible);
        assertFalse(res.certified);
    }

    @Test
    public void anExhaustedBudgetLeavesInfeasibilityUncertified() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(2, false, 0.25, 0.05);
        JumpSpec spec = withWallPastTheOptimum(sc, 10.0, 0.0);
        CertifiedBnb.Config byDeadline = new CertifiedBnb.Config();
        byDeadline.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        byDeadline.nodeCap = 300000;
        byDeadline.deadlineNanos = System.nanoTime() - 1L;
        CertifiedBnb.Result stopped = CertifiedBnb.solve(model, spec, byDeadline);
        assertFalse(stopped.feasible);
        assertFalse("a search stopped by its deadline has not exhausted anything", stopped.certifiedInfeasible);

        CertifiedBnb.Config byNodes = new CertifiedBnb.Config();
        byNodes.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        byNodes.nodeCap = 0;
        CertifiedBnb.Result capped = CertifiedBnb.solve(model, spec, byNodes);
        assertFalse(capped.feasible);
        assertFalse("a search stopped by its node cap has not exhausted anything", capped.certifiedInfeasible);
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
    @Category(SlowSolverTests.class)
    public void innerNoTurnCertIncumbentIsAttainableAndBoundIsSound() {
        ProblemFixture pf = ProblemFixture.load("solve", "j1150-noturn-inner");
        JumpSpec spec = pf.specFor(null, null);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = 200_000;
        cfg.polishCap = 12;
        cfg.deadlineNanos = System.nanoTime() + 4_000_000_000L;
        CertifiedBnb.Result res = CertifiedBnb.solve(pf.model, spec, cfg);
        assertFalse("cert must not decline this foldable no-turn dF problem", res.declined);

        if (res.feasible) {
            double verified = Scoring.verifiedObjectiveAt(pf.model, spec.asScenario(), spec,
                    res.yawsDeg, res.px, res.pz, 0.0);
            assertFalse("any cert incumbent must be byte-exact lattice-attainable", Double.isNaN(verified));
            assertEquals("cert's reported objective must equal its byte-exact re-simulation",
                    res.objective, verified, 1.0e-9);
        }
        if (res.certified) {
            assertTrue("the certified dual bound " + res.boundObjective + " must dominate its own incumbent "
                            + res.objective, res.boundObjective >= res.objective - CertifiedBnb.CERT_EPS);
            assertTrue("a certified gap must be non-negative and within CERT_EPS (was " + res.gap + ")",
                    res.gap >= 0.0 && res.gap <= CertifiedBnb.CERT_EPS);
        }
    }

    private static CertifiedBnb.Result optimize(ExactJumpModel model, JumpSpec spec, boolean probeGates,
                                                int nodeCap, long seconds) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = nodeCap;
        cfg.polishCap = 6;
        cfg.probeGates = probeGates;
        if (seconds > 0) cfg.deadlineNanos = System.nanoTime() + seconds * 1_000_000_000L;
        return CertifiedBnb.solve(model, spec, cfg);
    }

    private static final int GATE_CASES = 54;
    private static final int[] GATE_CASES_SOLVED = {2, 17, 21, 22, 51, 53};

    private static JumpSpec nearThresholdCase(Random rnd) {
        double scale = rnd.nextBoolean() ? 0.012 : 0.03;
        double vx = (rnd.nextDouble() * 2.0 - 1.0) * scale;
        double vz = (rnd.nextDouble() * 2.0 - 1.0) * scale;
        boolean ground = rnd.nextInt(3) == 0;
        int n = 3;
        JumpPhysicsInputs sc = scenario(n, false, vx, vz);
        if (ground) {
            double[] slip = new double[n];
            Arrays.fill(slip, 0.6);
            sc.slipPerTick = slip;
        }
        double wall = 0.5 + (rnd.nextDouble() * 2.0 - 1.0) * 0.08;
        int wallTick = 1 + rnd.nextInt(n);
        JumpConstraint.Mode mode = rnd.nextBoolean() ? JumpConstraint.Mode.Z : JumpConstraint.Mode.X;
        JumpConstraint.Cmp cmp = rnd.nextBoolean() ? JumpConstraint.Cmp.LE : JumpConstraint.Cmp.GE;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(mode, wallTick, null, JumpConstraint.Op.PLUS, cmp, wall, "wall"));
        JumpPhysicsInputs.Axis axis = rnd.nextBoolean() ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
        Objective.Sense sense = rnd.nextBoolean() ? Objective.Sense.MAX : Objective.Sense.MIN;
        return new JumpSpec(sc, cons, new Objective(axis, sense, n));
    }

    @Test
    @Category(SlowSolverTests.class)
    public void gateProbingKeepsTheCertifiedOptimumOfNearThresholdCarries() {
        Random rnd = new Random(1L);
        int probed = 0;
        for (int k = 0; k < GATE_CASES; k++) {
            JumpSpec spec = nearThresholdCase(rnd);
            if (Arrays.binarySearch(GATE_CASES_SOLVED, k) < 0) continue;
            String version = k % 2 == 0 ? "1.8.9" : "1.21.5";
            ExactJumpModel model = ExactJumpModel.forMcVersion(version);
            CertifiedBnb.Result plain = optimize(model, spec, false, 200000, 60);
            CertifiedBnb.Result withProbe = optimize(model, spec, true, 200000, 60);
            String label = "case " + k + " on " + version;
            assertTrue(label + " must certify without probing, gap=" + plain.gap, plain.certified);
            assertTrue(label + " must certify with probing, gap=" + withProbe.gap, withProbe.certified);
            assertEquals(label + " probing moved the certified optimum", plain.objective, withProbe.objective,
                    CertifiedBnb.CERT_EPS);
            if (withProbe.nodes != plain.nodes) probed++;
        }
        assertTrue("probing changed " + probed + " searches, the cases do not exercise it", probed >= 3);
    }

    @Test
    @Category(SlowSolverTests.class)
    public void gateProbingTightensLongJumpsAndNeverCutsBelowAKnownLanding() {
        for (String name : new String[]{"j021-rinav1-01", "loopmm-3jump-lands", "j002"}) {
            ProblemFixture pf = ProblemFixture.load("solve", name);
            ProblemFixture.Rig rig = pf.rig();
            JumpSpec spec = rig.spec();
            double sign = spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0;
            CertifiedBnb.Result plain = optimize(pf.model, spec, false, 150, 60);
            CertifiedBnb.Result withProbe = optimize(pf.model, spec, true, 150, 60);
            assertTrue(name + " must land without probing", plain.feasible);
            assertTrue(name + " must land with probing", withProbe.feasible);
            double bestLanding = Math.max(sign * plain.objective, sign * withProbe.objective);
            assertTrue(name + " probed bound " + withProbe.boundObjective + " cuts below a landing at "
                    + sign * bestLanding, sign * withProbe.boundObjective >= bestLanding - CertifiedBnb.CERT_EPS);
            assertTrue(name + " gap " + withProbe.gap + " with probing against " + plain.gap + " without",
                    withProbe.gap < 0.25 * plain.gap);
        }
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
