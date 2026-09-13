package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class NoTurnOptimizePass {

    public interface Progress {
        void update(String stage, double fraction);

        void optimized(NoTurnResult before, NoTurnResult after);
    }

    private static final int THREAD_CAP = 8;

    private final ExactJumpModel model;
    private final AtomicBoolean cancel;
    private final Progress progress;
    private final int threads;

    public NoTurnOptimizePass(ExactJumpModel model, int threads, AtomicBoolean cancel, Progress progress) {
        this.model = model;
        this.cancel = cancel;
        this.progress = progress;
        this.threads = Math.max(1, Math.min(THREAD_CAP, NoTurnParallel.resolveThreads(threads)));
    }

    public int run(NoTurnProblem problem, List<NoTurnResult> lines, int budgetSeconds) {
        List<NoTurnResult> todo = new ArrayList<>();
        for (NoTurnResult r : lines) if (!r.optimized) todo.add(r);
        if (todo.isEmpty()) return 0;
        int seconds = Math.max(1, budgetSeconds);
        SolverGraph graph = BuiltinGraphs.optimize(seconds);
        long budget = seconds * 1_000_000_000L;
        NoTurnCertifier cert = new NoTurnCertifier(model);
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        int total = todo.size();
        int improved = 0;
        try {
            for (int from = 0; from < total; from += threads) {
                if (cancel.get()) break;
                int take = Math.min(threads, total - from);
                final int base = from;
                progress.update(stageText(done.get(), total, seconds), done.get() / (double) total);
                List<NoTurnResult> res = NoTurnParallel.collectAll(exec, take, cancel, (idx, tc) -> {
                    NoTurnResult before = todo.get(base + idx);
                    NoTurnResult after = cert.polish(problem, before, graph, budget, tc);
                    int k = done.incrementAndGet();
                    progress.update(stageText(k, total, seconds), k / (double) total);
                    return after;
                });
                for (int i = 0; i < res.size(); i++) {
                    NoTurnResult before = todo.get(base + i);
                    NoTurnResult after = res.get(i);
                    if (after == null) continue;
                    after.optimized = true;
                    before.optimized = true;
                    if (after != before) improved++;
                    progress.optimized(before, after);
                }
            }
        } finally {
            exec.shutdownNow();
        }
        return improved;
    }

    private String stageText(int done, int total, int seconds) {
        return "optimizing line " + Math.min(done + 1, total) + " of " + total + " (" + seconds + " s each, parallel "
                + threads + ")";
    }
}
