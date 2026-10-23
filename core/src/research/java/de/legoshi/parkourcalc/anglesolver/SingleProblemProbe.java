package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import org.junit.Assume;
import org.junit.Test;

public class SingleProblemProbe {

    @Test
    public void probe() {
        String target = System.getProperty("pkc.probe");
        Assume.assumeTrue("set -Dpkc.probe=<category>/<name>", target != null);
        String[] parts = target.split("/", 2);
        ProblemFixture pf = ProblemFixture.load(parts[0], parts[1]);
        ProblemFixture.Run run = pf.solve(60_000L);
        SolveResult r = run.result;
        System.out.printf("PROBE %s success=%s met=%d/%d %d ms%s%n",
                target, r != null && r.isSuccess(), r == null ? 0 : r.getMet(), r == null ? 0 : r.getTotal(),
                run.elapsedMs, r != null && r.hasObjective() ? String.format("  obj=%.7f", r.getObjectiveValue()) : "");
        if (r != null) {
            System.out.println("PROBE solver=" + r.getSolver());
            if (r.getNotice() != null) System.out.println("PROBE notice=" + r.getNotice());
            for (SolveResult.Detail d : r.getDetails()) System.out.println("PROBE detail  " + d.label + " = " + d.value);
        }
    }
}
