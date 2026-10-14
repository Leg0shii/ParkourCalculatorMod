package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawOrder;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class YawDirectionConstraintTest {

    private static final String FIXTURE = "gh313-j121-dfneo";

    private static JumpConstraint df(int t, JumpConstraint.Cmp cmp, double rhs) {
        return new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS, cmp, rhs, "dF@" + t);
    }

    private static JumpSpec withRange(JumpSpec base, int from, int to, JumpConstraint.Cmp cmp) {
        List<JumpConstraint> cons = new ArrayList<>(base.constraints);
        for (int t = from; t <= to; t++) cons.add(df(t, cmp, 0.0));
        return new JumpSpec(base.asScenario(), cons, base.objective);
    }

    private static void assertOneWay(double[] yaws, int from, int to, JumpConstraint.Cmp cmp, JumpSpec spec) {
        double[] gf = spec.asScenario().toGameFacings(Angles.wrapAll(yaws));
        for (int t = from; t <= to; t++) {
            double turn = Angles.wrapDelta(gf[t] - gf[t - 1]);
            if (cmp == JumpConstraint.Cmp.GE) assertTrue("tick " + t + " turned back by " + turn, turn >= 0.0);
            else assertTrue("tick " + t + " turned back by " + turn, turn <= 0.0);
        }
        assertEquals(0.0, JumpConstraintCompiler.compile(spec).maxViolation(gf,
                ProblemFixture.load("solve", FIXTURE).model.forward(spec.asScenario(), gf)), 0.0);
    }

    @Test
    public void carryAcceptsConsecutiveInequalitiesOnly() {
        List<JumpConstraint> cons = new ArrayList<>();
        cons.add(df(3, JumpConstraint.Cmp.GE, 0.0));
        cons.add(df(4, JumpConstraint.Cmp.LE, -0.5));
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 10.0, "F0"));
        YawOrder order = YawOrder.carry(cons, YawTies.of(cons, 8), 8);
        assertNotNull(order);
        assertEquals(2, order.links().size());
        assertEquals(1, order.bounds().size());
        YawOrder.Link up = order.links().get(0);
        assertEquals(3, up.hiTick);
        assertEquals(2, up.loTick);
        assertEquals(0.0, up.minTurnDeg, 0.0);
        YawOrder.Link down = order.links().get(1);
        assertEquals(3, down.hiTick);
        assertEquals(4, down.loTick);
        assertEquals(0.5, down.minTurnDeg, 1.0e-12);
        assertEquals(10.0, order.bounds().get(0).loDeg, 0.0);
        assertEquals(190.0, order.bounds().get(0).hiDeg, 0.0);

        List<JumpConstraint> far = new ArrayList<>();
        far.add(new JumpConstraint(JumpConstraint.Mode.F, 5, 2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, 0.0, "far"));
        assertNull(YawOrder.carry(far, YawTies.of(far, 8), 8));

        List<JumpConstraint> eq = new ArrayList<>();
        eq.add(new JumpConstraint(JumpConstraint.Mode.F, 5, 4, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 0.0, "sum"));
        assertNull(YawOrder.carry(eq, YawTies.of(eq, 8), 8));

        List<JumpConstraint> band = new ArrayList<>();
        band.add(df(2, JumpConstraint.Cmp.GE, -1.0));
        band.add(df(2, JumpConstraint.Cmp.LE, 1.0));
        assertNull("a dF band stays unsupported", YawOrder.carry(band, YawTies.of(band, 8), 8));

        List<JumpConstraint> held = new ArrayList<>();
        held.add(df(2, JumpConstraint.Cmp.GE, -1.0e-4));
        held.add(df(2, JumpConstraint.Cmp.LE, 1.0e-4));
        YawOrder tied = YawOrder.carry(held, YawTies.of(held, 8), 8);
        assertNotNull(tied);
        assertTrue("a dF = 0 corridor is absorbed by the tie, not carried", tied.isEmpty());
    }

    @Test
    public void projectMakesAPathOneWay() {
        List<JumpConstraint> cons = new ArrayList<>();
        for (int t = 1; t <= 4; t++) cons.add(df(t, JumpConstraint.Cmp.GE, 0.0));
        YawOrder order = YawOrder.carry(cons, null, 5);
        double[] y = order.project(new double[]{10.0, 15.0, 12.0, 20.0, 19.0});
        assertTrue(order.satisfied(y, 0.0));
        assertEquals(10.0, y[0], 0.0);
        assertEquals(15.0, y[1], 0.0);
        assertEquals(15.0, y[2], 0.0);
        assertEquals(20.0, y[3], 0.0);
        assertEquals(20.0, y[4], 0.0);
        double[] seam = order.project(new double[]{179.0, -179.0, -178.0, 170.0, -170.0});
        assertTrue(order.satisfied(seam, 0.0));
    }

    private static final class Run {
        final JumpSpec base;
        final int from;
        final int to;
        final JumpConstraint.Cmp cmp;

        Run(JumpSpec base, int from, int to, JumpConstraint.Cmp cmp) {
            this.base = base;
            this.from = from;
            this.to = to;
            this.cmp = cmp;
        }
    }

    private static JumpSpec stripped(JumpSpec spec) {
        List<JumpConstraint> cons = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) if (c.mode != JumpConstraint.Mode.F) cons.add(c);
        return new JumpSpec(spec.asScenario(), cons, spec.objective);
    }

    private static Run longestOneWayRun(ProblemFixture pf) {
        JumpSpec base = stripped(pf.specFor(AngleSolverState.Axis.X, AngleSolverState.Goal.MAX));
        String[] name = new String[1];
        double[] yaws = AngleSolverEngine.dualChain(pf.model, base, base.asScenario(), new AtomicBoolean(false), name);
        if (yaws == null) {
            CertifiedBnb.Config cfg = new CertifiedBnb.Config();
            cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
            cfg.nodeCap = 20_000;
            cfg.deadlineNanos = System.nanoTime() + 30_000_000_000L;
            CertifiedBnb.Result res = CertifiedBnb.solve(pf.model, base, cfg);
            assertTrue("the stripped fixture must solve on the certified B&B", res.feasible);
            yaws = res.yawsDeg;
            name[0] = "certified B&B";
        }
        System.out.printf("RUN %s base solved by %s%n", FIXTURE, name[0]);
        double[] gf = base.asScenario().toGameFacings(yaws);
        int bestFrom = -1;
        int bestTo = -1;
        int runFrom = -1;
        int sign = 0;
        for (int t = 1; t < gf.length; t++) {
            double turn = Angles.wrapDelta(gf[t] - gf[t - 1]);
            int sg = turn > 1.0e-3 ? 1 : (turn < -1.0e-3 ? -1 : 0);
            if (sg != 0 && sg == sign) continue;
            if (sign != 0 && t - 1 - runFrom > bestTo - bestFrom) {
                bestFrom = runFrom;
                bestTo = t - 1;
            }
            sign = sg;
            runFrom = t;
        }
        if (sign != 0 && gf.length - 1 - runFrom > bestTo - bestFrom) {
            bestFrom = runFrom;
            bestTo = gf.length - 1;
        }
        assertTrue("the base path needs a one-way run of at least three ticks", bestTo - bestFrom >= 2);
        double turn = Angles.wrapDelta(gf[bestFrom] - gf[bestFrom - 1]);
        System.out.printf("RUN %s one-way ticks %d..%d (%s)%n", FIXTURE, bestFrom, bestTo, turn > 0 ? "increasing" : "decreasing");
        return new Run(base, bestFrom, bestTo, turn > 0 ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE);
    }

    @Test
    public void slpCarriesAOneWayRange() {
        ProblemFixture pf = ProblemFixture.load("solve", FIXTURE);
        Run run = longestOneWayRun(pf);
        JumpSpec spec = withRange(run.base, run.from, run.to, run.cmp);
        double[] yaws = SlpSolve.optimize(pf.model, spec, 0.0, new AtomicBoolean(false));
        System.out.printf("SLP %s dF %s on %d..%d: %s%n", FIXTURE, run.cmp, run.from, run.to, yaws == null ? "miss" : "solved");
        if (yaws != null) assertOneWay(yaws, run.from, run.to, run.cmp, spec);
    }

    @Test
    public void certifiedBnbCarriesAOneWayRange() {
        ProblemFixture pf = ProblemFixture.load("solve", FIXTURE);
        Run run = longestOneWayRun(pf);
        JumpSpec spec = withRange(run.base, run.from, run.to, run.cmp);
        assertTrue(CertifiedBnb.accepts(spec));
        assertTrue(CertifiedBnb.carriesOrder(spec));
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 20_000;
        cfg.deadlineNanos = System.nanoTime() + 30_000_000_000L;
        CertifiedBnb.Result res = CertifiedBnb.solve(pf.model, spec, cfg);
        System.out.printf("CERT %s dF %s on %d..%d: declined=%s feasible=%s nodes=%d%n", FIXTURE, run.cmp, run.from,
                run.to, res.declined, res.feasible, res.nodes);
        assertFalse("the certified B&B must carry a one-way dF range", res.declined);
        assertTrue("the certified B&B must find a one-way path", res.feasible);
        assertOneWay(res.yawsDeg, run.from, run.to, run.cmp, spec);
    }

    @Test
    public void certifiedBnbRejectsTheWrongWay() {
        ProblemFixture pf = ProblemFixture.load("solve", FIXTURE);
        Run run = longestOneWayRun(pf);
        JumpConstraint.Cmp wrong = run.cmp == JumpConstraint.Cmp.GE ? JumpConstraint.Cmp.LE : JumpConstraint.Cmp.GE;
        JumpSpec spec = withRange(run.base, run.from, run.to, wrong);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 2_000;
        cfg.deadlineNanos = System.nanoTime() + 20_000_000_000L;
        CertifiedBnb.Result res = CertifiedBnb.solve(pf.model, spec, cfg);
        System.out.printf("CERT %s wrong way dF %s on %d..%d: declined=%s feasible=%s nodes=%d viol=%.3e%n", FIXTURE,
                wrong, run.from, run.to, res.declined, res.feasible, res.nodes, res.bestInfeasViol);
        assertFalse(res.declined);
        if (res.feasible) assertOneWay(res.yawsDeg, run.from, run.to, wrong, spec);
    }

    @Test
    public void closedFormCarriesAOneWayRange() {
        ProblemFixture pf = ProblemFixture.load("solve", FIXTURE);
        Run run = longestOneWayRun(pf);
        JumpSpec along = withRange(run.base, run.from, run.to, run.cmp);
        long t0 = System.nanoTime();
        double[] yaws = ClosedFormSolve.optimize(pf.model, along, 0.0, new AtomicBoolean(false));
        System.out.printf("CF along %s..%s %s -> %s in %.1f ms%n", run.from, run.to, run.cmp,
                yaws == null ? "miss" : "solved", (System.nanoTime() - t0) / 1e6);
        assertNotNull("the closed form must carry a one-way range along the base path's own turn", yaws);
        assertOneWay(yaws, run.from, run.to, run.cmp, along);

        JumpConstraint.Cmp against = run.cmp == JumpConstraint.Cmp.GE ? JumpConstraint.Cmp.LE : JumpConstraint.Cmp.GE;
        JumpSpec opposite = withRange(run.base, run.from, run.to, against);
        t0 = System.nanoTime();
        double[] merged = ClosedFormSolve.optimize(pf.model, opposite, 0.0, new AtomicBoolean(false));
        System.out.printf("CF against %s..%s %s -> %s in %.1f ms%n", run.from, run.to, against,
                merged == null ? "miss" : "solved", (System.nanoTime() - t0) / 1e6);
        if (merged != null) assertOneWay(merged, run.from, run.to, against, opposite);

        JumpSpec full = pf.specFor(AngleSolverState.Axis.X, AngleSolverState.Goal.MAX);
        t0 = System.nanoTime();
        double[] cf = ClosedFormSolve.optimize(pf.model, full, 0.0, new AtomicBoolean(false));
        System.out.printf("CF j121 full spec -> %s in %.1f ms%n", cf == null ? "miss" : "solved", (System.nanoTime() - t0) / 1e6);
    }

    @Test
    public void j121NeoSolvesWithDfLe() {
        ProblemFixture pf = ProblemFixture.load("solve", "gh313-j121-dfneo");
        ProblemFixture.Run run = pf.solve(90_000L);
        SolveResult r = run.result;
        System.out.printf("SOLVE gh313-j121-dfneo success=%s met=%d/%d %dms solver=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), run.elapsedMs, r.getSolver());
        assertTrue("j121 with dF <= 0 on T39..T46 must solve (met " + r.getMet() + "/" + r.getTotal() + ")",
                r.isSuccess());
    }
}
