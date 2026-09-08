package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScreenWitnessWarmStart implements FastCheck {

    public static final long WARM_CAP_NANOS = 500_000_000L;
    private static final int CIRCLE_CENTERS = 20;

    private NoTurnProblem preparedProblem;
    private StructurePoolDriver driver;

    @Override
    public void prepare(NoTurnProblem problem) {
        StructurePoolDriver d = new StructurePoolDriver(problem.model, new StructurePoolDriver.Config(),
                new AtomicBoolean(false), null);
        d.prepare(problem);
        this.driver = d;
        this.preparedProblem = problem;
    }

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        if (driver == null || preparedProblem != problem) prepare(problem);
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int se = problem.setupEnd;
        int[] combos = NoTurnProblem.combosOf(sc, se);
        boolean[] sprint = NoTurnProblem.sprintOf(sc, se);

        double center = driver.diskFeasibleTheta(combos, sprint, null);
        double[] witness = null;
        String seedNote;
        double bestCenter = center;
        if (!Double.isNaN(center)) {
            witness = driver.screenExact(combos, sprint, center);
            seedNote = "disk";
        } else {
            for (int k = 0; k < CIRCLE_CENTERS; k++) {
                double c = -180.0 + k * (360.0 / CIRCLE_CENTERS);
                double[] w = driver.screenExact(combos, sprint, c);
                if (witness == null || w[5] < witness[5]) {
                    witness = w;
                    bestCenter = c;
                }
            }
            seedNote = "circle";
        }
        double theta = witness[1];
        double phi = witness[2];
        double[] warmSeed = new double[n];
        if (driver.multiSegment()) {
            driver.screenExact(combos, sprint, bestCenter);
            driver.fillMultiSeed(warmSeed);
            seedNote = seedNote + "-multi";
        } else {
            for (int t = 0; t < n; t++) warmSeed[t] = t <= se ? theta : phi;
        }

        NoTurnCertifier.Result r = new NoTurnCertifier(model)
                .certifyWarm(spec, warmSeed, Math.min(budgetNanos, WARM_CAP_NANOS), cancel);
        if (r.feasible) {
            return FastCheckVerdict.feasible(r.yaws, r.startX, r.startZ, String.format(Locale.ROOT,
                    "screenWarm seed=%s theta=%.4f phi=%.3f", seedNote, theta, phi));
        }
        return FastCheckVerdict.unknown(String.format(Locale.ROOT, "screenWarm miss screenViol=%.3g", witness[5]));
    }

    @Override
    public String describe() {
        return "ScreenWitnessWarmStart";
    }
}
