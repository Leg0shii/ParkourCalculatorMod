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
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertTrue;

public class SnapRepairPolishTest {

    private static final int ALM_SEEDS = 16;
    private static final double OBJ_TOL = 5.0e-4;

    @Test
    public void v4ClosedFormJ004() {
        runV4("j004");
    }

    @Test
    public void v4ClosedFormJ006() {
        runV4("j006");
    }

    @Test
    public void v4ClosedFormJ011() {
        runV4("j011-1.875x1bmdoublecross");
    }

    @Test
    public void translationOffByteIdentical() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec spec = pf.specFor(null, null);
        ExactJumpModel model = pf.model;
        double[] seed = almSeed(model, spec, true);
        assertTrue("no smooth-feasible ALM seed produced", seed != null);

        SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
        SnapRepairPolish.Result base = SnapRepairPolish.run(model, spec, seed, cfg, 0L, null);
        SnapRepairPolish.Result nullDom = SnapRepairPolish.run(model, spec, seed, cfg, 0L, null, null);
        SnapRepairPolish.Result zeroDom =
                SnapRepairPolish.run(model, spec, seed, cfg, 0L, null, new double[]{0.0, 0.0, 0.0, 0.0});

        assertTrue("tx/tz not zero on pinned domain",
                base.tx == 0.0 && base.tz == 0.0 && zeroDom.tx == 0.0 && zeroDom.tz == 0.0);
        assertTrue("null domain viol drift", base.exactViol == nullDom.exactViol);
        assertTrue("zero domain viol drift", base.exactViol == zeroDom.exactViol);
        assertTrue("null domain obj drift", base.exactObjective == nullDom.exactObjective);
        assertTrue("zero domain obj drift", base.exactObjective == zeroDom.exactObjective);
        assertTrue("null domain feasible drift", base.feasible == nullDom.feasible);
        assertTrue("zero domain feasible drift", base.feasible == zeroDom.feasible);
        for (int k = 0; k < base.absYawsDeg.length; k++) {
            assertTrue("null domain yaw[" + k + "] drift", base.absYawsDeg[k] == nullDom.absYawsDeg[k]);
            assertTrue("zero domain yaw[" + k + "] drift", base.absYawsDeg[k] == zeroDom.absYawsDeg[k]);
        }
        System.out.printf(Locale.ROOT, "BYTEID snap off byte-identical: viol=%.6e obj=%.9f feasible=%b%n",
                base.exactViol, base.exactObjective, base.feasible);
    }

    @Test
    public void razorDiagnosticJ005() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j005");
        JumpSpec spec = pf.specFor(null, null);
        ExactJumpModel model = pf.model;
        JumpPhysicsInputs sc = spec.asScenario();
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double refObj = pf.expect.refObjective;

        double[] seed = almSeed(model, spec, false);
        SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
        SnapRepairPolish.Result r = SnapRepairPolish.run(model, spec, seed, cfg, 0L, null);
        SnapRepairPolish.Counters c = r.counters;

        double gap = max ? refObj - r.exactObjective : r.exactObjective - refObj;
        System.out.printf(Locale.ROOT, "J005 RAZOR DIAGNOSTIC (not asserted):%n");
        System.out.printf(Locale.ROOT, "  repairClosed(feasible)=%b  finalViol=%.6e  objective=%.9f  ref=%.9f  gap=%.3e%n",
                r.feasible, r.exactViol, r.exactObjective, refObj, gap);
        System.out.printf(Locale.ROOT, "  rounds: 1opt=%d 2opt=%d  accepts=%d  exactChecks=%d%n",
                c.oneOptRounds, c.twoOptRounds, c.accepts, c.exactChecks);
        System.out.printf(Locale.ROOT, "  srp2: snap_degradation=%.6e fastexact_disagree=%d disagree_cands=%d cell_miss=%d "
                        + "reconstruct_fail=%d resim_drift=%d down_hills=%d gate_pattern_mismatch=%d exact_only=%b%n",
                c.snapDegradation, c.fastExactDisagree, c.disagreeCandidates, c.cellMiss, c.reconstructFail,
                c.resimDrift, c.downHills, c.gatePatternMismatch, c.exactOnly);
        System.out.printf(Locale.ROOT,
                "  fix:  pattern_recompiles=%d probe_checks=%d exact_checks=%d exactonly_2opt_skipped=%d%n",
                c.patternRecompiles, c.probeChecks, c.exactChecks, c.exactonly2optSkipped);
    }

    private void runV4(String name) {
        ProblemFixture pf = ProblemFixture.load("closedform", name);
        JumpSpec spec = pf.specFor(null, null);
        ExactJumpModel model = pf.model;
        JumpPhysicsInputs sc = spec.asScenario();
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double refObj = pf.expect.refObjective;

        double[] seed = almSeed(model, spec, true);
        assertTrue(name + ": no smooth-feasible ALM seed produced", seed != null);

        SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
        SnapRepairPolish.Result r = SnapRepairPolish.run(model, spec, seed, cfg, 0L, null);

        double gap = max ? refObj - r.exactObjective : r.exactObjective - refObj;
        System.out.printf(Locale.ROOT, "V4 %-5s viol=%.3e obj=%.9f ref=%.9f gap=%.3e feasible=%b "
                        + "1opt=%d 2opt=%d accepts=%d%n",
                name, r.exactViol, r.exactObjective, refObj, gap, r.feasible,
                r.counters.oneOptRounds, r.counters.twoOptRounds, r.counters.accepts);

        assertTrue(name + ": exact viol not feasible (viol=" + r.exactViol + ")", r.exactViol <= 0.0);
        assertTrue(name + ": objective " + r.exactObjective + " short of reference " + refObj + " by " + gap,
                gap <= OBJ_TOL);

        double[] gf = sc.toGameFacings(Angles.wrapAll(r.absYawsDeg));
        ForwardPath path = model.forward(sc, gf);
        double reViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        double reObj = path.getPos(spec.objective.tick, spec.objective.axis);
        assertTrue(name + ": re-verify from scratch viol=" + reViol, reViol <= 0.0);
        assertTrue(name + ": re-verify objective " + reObj + " != reported " + r.exactObjective,
                reObj == r.exactObjective);
    }

    private static double[] almSeed(ExactJumpModel model, JumpSpec spec, boolean requireFeasible) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        AlmBfgsCore.Config cfg = new AlmBfgsCore.Config();

        AlmBfgsCore.Result bestFeas = null;
        double bestFeasObj = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        AlmBfgsCore.Result bestAny = null;
        double bestAnyViol = Double.POSITIVE_INFINITY;

        for (int i = 0; i < ALM_SEEDS; i++) {
            double angle = 2.0 * Math.PI * i / ALM_SEEDS;
            double[] seed = new double[n];
            for (int k = 0; k < n; k++) seed[k] = angle;
            AlmBfgsCore.Result res = AlmBfgsCore.solve(model, spec, seed, cfg, 0L, null);
            if (res.smoothViol <= cfg.feasTol) {
                boolean better = max ? res.smoothObjective > bestFeasObj : res.smoothObjective < bestFeasObj;
                if (bestFeas == null || better) {
                    bestFeas = res;
                    bestFeasObj = res.smoothObjective;
                }
            }
            if (bestAny == null || res.smoothViol < bestAnyViol) {
                bestAny = res;
                bestAnyViol = res.smoothViol;
            }
        }

        AlmBfgsCore.Result chosen = bestFeas != null ? bestFeas : (requireFeasible ? null : bestAny);
        if (chosen == null) return null;
        return Angles.wrapAll(toDeg(chosen.thetaRad));
    }

    private static double[] toDeg(double[] rad) {
        double[] d = new double[rad.length];
        for (int i = 0; i < rad.length; i++) d[i] = Math.toDegrees(rad[i]);
        return d;
    }
}
