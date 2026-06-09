package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import org.junit.Test;

import java.util.Arrays;

/** Not an assertion test: times the live engine on every problem in {@code problems/solve/} under its
 *  effort and prints median/min/max ms + feasibility, so we can compare the solver before/after a change. */
public class SolveBenchmark {

    private static final int WARMUP = 2;
    private static final int RUNS = 7;

    @org.junit.Ignore("manual benchmark, not an assertion test")
    @Test
    public void benchmark() {
        for (String fx : ProblemCatalog.problemNames("solve")) {
            ProblemFixture pf = ProblemFixture.load("solve", fx);
            for (int i = 0; i < WARMUP; i++) pf.solve(30_000L);
            long[] times = new long[RUNS];
            boolean allOk = true;
            int met = 0, total = 0;
            for (int i = 0; i < RUNS; i++) {
                ProblemFixture.Run run = pf.solve(30_000L);
                times[i] = run.elapsedMs;
                SolveResult r = run.result;
                allOk &= (r != null && r.isSuccess());
                if (r != null) { met = r.getMet(); total = r.getTotal(); }
            }
            Arrays.sort(times);
            System.out.printf("BENCH %-14s median=%4dms min=%4dms max=%4dms  feasible=%s (%d/%d)%n",
                    fx, times[RUNS / 2], times[0], times[RUNS - 1], allOk, met, total);
        }
    }
}
