package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.anglesolver.harness.WitnessProblem;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.WallHomotopyLadder;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class CertBnbRunSet {

    private static final String[] ALL_KINDS = {"driver", "ladder", "ff", "seed", "opt"};

    @Test
    public void run() throws Exception {
        Assume.assumeTrue("set PKC_RUNSET=1", "1".equals(System.getenv("PKC_RUNSET")));
        String out = env("PKC_RUNSET_OUT", "");
        String filter = env("PKC_RUNSET_FILTER", "");
        String category = env("PKC_RUNSET_CATEGORY", "solve");
        String[] kinds = env("PKC_RUNSET_KINDS", String.join(",", ALL_KINDS)).split(",");
        String[] efforts = env("PKC_RUNSET_EFFORTS", "FAST,THOROUGH").split(",");
        int nodes = Integer.parseInt(env("PKC_RUNSET_NODES", "1500"));
        int ffNodes = Integer.parseInt(env("PKC_RUNSET_FF_NODES", "400"));
        long watchdogMs = Long.parseLong(env("PKC_RUNSET_WATCHDOG_MS", "60000"));
        int repeats = Integer.parseInt(env("PKC_RUNSET_REPEATS", "1"));
        boolean seedBoxes = "seed".equals(env("PKC_RUNSET_BOXES", "capture"));
        int witnessTrials = Integer.parseInt(env("PKC_RUNSET_WITNESS", "0"));
        long witnessSeed = Long.parseLong(env("PKC_RUNSET_WITNESS_SEED", "5"));

        PrintWriter w = out.isEmpty() ? new PrintWriter(System.out, true) : new PrintWriter(new File(out), "UTF-8");
        w.println("problem\teffort\tkind\tn\tsignature\tcancelled\tms\tkernelMs");
        for (String name : ProblemCatalog.problemNames(category)) {
            if (!filter.isEmpty() && !name.matches(filter)) continue;
            ProblemFixture fixture;
            try {
                fixture = ProblemFixture.load(category, name);
            } catch (RuntimeException e) {
                continue;
            }
            for (String effort : efforts) {
                if ("-".equals(effort)) continue;
                ProblemFixture.Rig rig = seedBoxes ? fixture.rigWithSeedBoxes() : fixture.rig();
                rig.state.setEffort(AngleSolverState.Effort.valueOf(effort));
                runSpec(w, name, effort, fixture.model, rig.spec(), kinds, nodes, ffNodes, watchdogMs, repeats);
            }
            for (int trial = 0; trial < witnessTrials; trial++) {
                WitnessProblem p = WitnessProblem.build(name, witnessSeed, trial, WitnessProblem.ALL_MARGINS);
                if (p == null) continue;
                runSpec(w, name, "witness" + trial, p.fixture.model, p.spec, kinds, nodes, ffNodes, watchdogMs,
                        repeats);
            }
        }
        w.flush();
        if (!out.isEmpty()) w.close();
    }

    private static void runSpec(PrintWriter w, String name, String variant, ExactJumpModel model, JumpSpec spec,
                                String[] kinds, int nodes, int ffNodes, long watchdogMs, int repeats) {
        if (spec == null) return;
        int n = spec.asScenario().numTicks;
        for (String kind : kinds) {
            long bestNanos = Long.MAX_VALUE;
            Row row = null;
            for (int r = 0; r < repeats; r++) {
                Row cur = runKind(kind, model, spec, nodes, ffNodes, watchdogMs);
                if (cur == null) break;
                if (cur.nanos < bestNanos) {
                    bestNanos = cur.nanos;
                    row = cur;
                }
            }
            if (row == null) continue;
            w.println(String.format(Locale.ROOT, "%s\t%s\t%s\t%d\t%s\t%s\t%.1f\t%.1f", name, variant, kind, n,
                    row.signature, row.cancelled, row.nanos / 1.0e6, row.kernelNanos / 1.0e6));
            w.flush();
        }
    }

    private static final class Row {
        String signature;
        boolean cancelled;
        long nanos;
        long kernelNanos;
    }

    private static Row runKind(String kind, ExactJumpModel model, JumpSpec spec, int nodes, int ffNodes,
                               long watchdogMs) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            long end = System.nanoTime() + watchdogMs * 1_000_000L;
            while (!done.get() && System.nanoTime() < end) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    return;
                }
            }
            if (!done.get()) cancel.set(true);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        Row row = new Row();
        long t0 = System.nanoTime();
        try {
            if ("driver".equals(kind)) {
                if (CertifiedBnb.foldable(spec) || CertifiedBnb.carriesOrder(spec)) return null;
                FoldReplayDriver.Params p = new FoldReplayDriver.Params();
                p.cancel = cancel;
                FoldReplayDriver.Result r = FoldReplayDriver.solve(model, spec, p);
                row.signature = "fixed=" + r.fixedPoint + ";" + round(r.best);
            } else if ("ladder".equals(kind)) {
                if (CertifiedBnb.foldable(spec) || CertifiedBnb.carriesOrder(spec)) return null;
                WallHomotopyLadder.Result r = WallHomotopyLadder.solve(model, spec, cancel, 0L);
                StringBuilder sb = new StringBuilder("rungs=" + r.rungs.size());
                for (WallHomotopyLadder.Rung rung : r.rungs) {
                    sb.append(";[").append(round(rung.result.best))
                            .append(']');
                }
                row.signature = sb.append(';').append(round(r.best)).toString();
            } else {
                CertifiedBnb.Config cfg = new CertifiedBnb.Config();
                boolean ff = "ff".equals(kind);
                cfg.mode = ff ? CertifiedBnb.Mode.FIRST_FEASIBLE : CertifiedBnb.Mode.OPTIMIZE;
                cfg.nodeCap = ff ? ffNodes : ("seed".equals(kind) ? 0 : nodes);
                cfg.polishCap = ff ? 2 : 12;
                cfg.cancel = cancel;
                CertifiedBnb.Result r = CertifiedBnb.solve(model, spec, cfg);
                row.kernelNanos = r.kernelNanos;
                row.signature = r.declined ? "declined"
                        : "nodes=" + r.nodes + ";solves=" + r.kernelSolves + ";feasible=" + r.feasible
                        + ";obj=" + bits(r.objective) + ";bound=" + bits(r.boundObjective) + ";gap=" + bits(r.gap)
                        + ";certified=" + r.certified + ";infeasible=" + r.certifiedInfeasible
                        + ";unresolved=" + r.unresolved + ";px=" + bits(r.px) + ";pz=" + bits(r.pz)
                        + ";yaws=" + hash(r.yawsDeg) + ";missViol=" + bits(r.bestInfeasViol)
                        + ";miss=" + hash(r.bestInfeasYaws);
            }
        } catch (RuntimeException e) {
            row.signature = "ERR " + e;
        } finally {
            done.set(true);
        }
        row.nanos = System.nanoTime() - t0;
        row.cancelled = cancel.get();
        return row;
    }

    private static String round(FoldReplayDriver.Round r) {
        if (r == null) return "none";
        return "obj=" + bits(r.objective) + ",viol=" + bits(r.maxViolation) + ",px=" + bits(r.px) + ",pz="
                + bits(r.pz) + ",yaws=" + hash(r.yawsDeg);
    }

    private static String bits(double v) {
        return Long.toHexString(Double.doubleToLongBits(v));
    }

    private static String hash(double[] v) {
        if (v == null) return "null";
        long[] raw = new long[v.length];
        for (int i = 0; i < v.length; i++) raw[i] = Double.doubleToLongBits(v[i]);
        return Integer.toHexString(Arrays.hashCode(raw));
    }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return v != null && !v.isEmpty() ? v : dflt;
    }
}
