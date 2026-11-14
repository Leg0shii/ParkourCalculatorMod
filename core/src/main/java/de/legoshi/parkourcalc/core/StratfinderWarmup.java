package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.DebugBoxes;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class StratfinderWarmup {

    public static final String JUMP_RESOURCE = "/strats/warmup-jump.json";
    public static final long DEFAULT_NANOS = 8_000_000_000L;

    private StratfinderWarmup() {
    }

    public static boolean enabled() {
        return !"false".equals(System.getProperty("pkc.stratfinder.warmup"));
    }

    public static int threads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }

    public static NoTurnProblem problem(ExactJumpModel model) throws IOException {
        SaveFile file = SaveIO.parseSafe(read(JUMP_RESOURCE));
        if (file == null) throw new IOException(JUMP_RESOURCE + ": failed to parse");
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        JumpSpec spec = new AngleSolverEngine(state, DebugBoxes.of(file), inputs, t -> { }, model).debugBuildSpec();
        if (spec == null) throw new IOException(JUMP_RESOURCE + ": no spec");
        JumpPhysicsInputs wiped = spec.asScenario().copy();
        wiped.forwardInputPerTick = new float[wiped.numTicks];
        wiped.strafeInputPerTick = new float[wiped.numTicks];
        wiped.sprintPerTick = new boolean[wiped.numTicks];
        wiped.strafePerTick = null;
        return NoTurnProblem.from(new JumpSpec(wiped, spec.constraints, spec.objective), model);
    }

    public static int run(ExactJumpModel model, StructurePoolDriver.Config searchConfig, long budgetNanos, int threads,
                          AtomicBoolean cancel) throws IOException {
        NoTurnProblem problem = problem(model);
        if (problem.issue != null) return 0;
        StructurePoolDriver.Config cfg = searchConfig.copy();
        cfg.totalBudgetNanos = budgetNanos;
        cfg.extraCertify = Integer.MAX_VALUE;
        cfg.extraCertifyNanos = budgetNanos;
        cfg.threads = threads;
        final AtomicInteger found = new AtomicInteger();
        new StructurePoolDriver(model, cfg, cancel, new StructurePoolDriver.Progress() {
            @Override
            public void update(String stage, double fraction) {
            }

            @Override
            public void found(NoTurnResult result) {
                found.incrementAndGet();
            }
        }).run(problem, BuiltinGraphs.optimize(6));
        return found.get();
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = StratfinderWarmup.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing resource: " + resource);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
