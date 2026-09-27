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
        int[] combos = NoTurnProblem.combosOf(sc, n - 1);
        boolean[] sprint = NoTurnProblem.sprintOf(sc, n - 1);

        double center = driver.diskFeasibleTheta(combos, sprint, null);
        double[] witness = driver.screenExact(combos, sprint, center);
        double[] warmSeed = new double[n];
        driver.fillSeed(warmSeed);

        NoTurnCertifier.Result r = new NoTurnCertifier(model)
                .certifyWarm(spec, warmSeed, Math.min(budgetNanos, WARM_CAP_NANOS), cancel);
        if (r.feasible) {
            return FastCheckVerdict.feasible(r.yaws, r.startX, r.startZ, String.format(Locale.ROOT,
                    "screenWarm seed=%s theta=%.4f", Double.isNaN(center) ? "circle" : "disk", witness[1]));
        }
        return FastCheckVerdict.unknown(String.format(Locale.ROOT, "screenWarm miss screenViol=%.3g", witness[5]));
    }

    @Override
    public String describe() {
        return "ScreenWitnessWarmStart";
    }
}
