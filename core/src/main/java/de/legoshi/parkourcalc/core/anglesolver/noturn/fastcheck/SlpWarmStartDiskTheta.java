package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.GateFoldFinder;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SlpWarmStartDiskTheta implements FastCheck {

    private static final long WARM_CAP_NANOS = 1_000_000_000L;

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
        if (driver == null || preparedProblem != problem) {
            prepare(problem);
        }
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (!JumpLinearModel.hasFacingWall(spec.constraints)) return FastCheckVerdict.unknown("no facing wall");
        YawTies ties = YawTies.of(spec.constraints, n);
        if (ties == null) return FastCheckVerdict.unknown("not position-linear");

        int se = problem.setupEnd;
        int[] combos = new int[se + 1];
        boolean[] sprint = new boolean[se + 1];
        for (int t = 0; t <= se; t++) {
            int fs = (int) Math.signum(sc.forwardAt(t));
            int ss = (int) Math.signum(sc.strafeInputAt(t));
            combos[t] = NoTurnKeys.comboFor(fs, ss);
            sprint[t] = sc.sprintAt(t);
        }

        double diskTheta = driver.diskFeasibleTheta(combos, sprint, null);
        if (Double.isNaN(diskTheta)) return FastCheckVerdict.unknown("disk NaN");

        double[] warmSeed = new double[n];
        for (int t = 0; t < n; t++) warmSeed[t] = diskTheta;

        long deadline = System.nanoTime() + Math.min(budgetNanos, WARM_CAP_NANOS);
        GateFoldFinder.Result gr = GateFoldFinder.solve(model, spec, ties, cancel, deadline, true, false, warmSeed);
        if (gr != null && gr.feasible()) {
            return FastCheckVerdict.feasible(gr.yawsDeg, gr.px, gr.pz, "warmDiskTheta=" + diskTheta);
        }
        return FastCheckVerdict.unknown("warm miss viol=" + (gr == null ? "null" : gr.viol));
    }

    @Override
    public String describe() {
        return "SlpWarmStartDiskTheta";
    }
}
