package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class SolveNodeStatsScreen {

    @Test
    public void dump() throws Exception {
        org.junit.Assume.assumeTrue("set -Dpkc.nodestats=1 to run", System.getProperty("pkc.nodestats") != null);
        String tag = System.getProperty("pkc.nodestats.tag", "run");
        long timeoutMs = Long.parseLong(System.getProperty("pkc.nodestats.timeoutMs", "180000"));

        StringBuilder stats = new StringBuilder();
        for (String name : ProblemCatalog.problemNames("solve")) {
            ProblemFixture pf;
            try {
                pf = ProblemFixture.load("solve", name);
            } catch (Exception e) {
                System.out.println(name + " SKIP " + e);
                continue;
            }
            long budget = pf.expect.maxSolveMs != null ? Math.max(pf.expect.maxSolveMs * 2, 30_000L) : timeoutMs;
            ProblemFixture.Run run = pf.solve(budget);
            SolveResult r = run.result;
            SolveRunRecord rec = run.engine.lastRunRecord();
            String chain = r != null ? r.getSolver() : "TIMEOUT";
            System.out.printf(Locale.ROOT, "%-40s %6d ms  %s%n", name, run.elapsedMs, chain);
            stats.append(String.format(Locale.ROOT, "RUN\t%s\t-\t%d\t%s\t%s%n",
                    name, run.elapsedMs, r != null && r.isSuccess(), chain));
            if (rec == null) continue;
            if (rec.nodes != null) {
                for (SolveRunRecord.NodeRun n : rec.nodes) {
                    stats.append(String.format(Locale.ROOT, "NODE\t%s\t-\t%s\t%d\t%d\t%s\t%d%n",
                            name, n.id, n.visits, n.elapsedNanos / 1_000_000L, n.taken, n.evals));
                }
            }
            if (rec.race != null && rec.race.exploreNodes != null) {
                for (SolveRunRecord.NodeRun n : rec.race.exploreNodes) {
                    stats.append(String.format(Locale.ROOT, "NODE\t%s\t-\texplore:%s\t%d\t%d\t%s\t%d%n",
                            name, n.id, n.visits, n.elapsedNanos / 1_000_000L, n.taken, n.evals));
                }
            }
        }
        File dst = new File("build/reports/nodestats-" + tag + ".tsv");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), stats.toString().getBytes(StandardCharsets.UTF_8));
    }
}
