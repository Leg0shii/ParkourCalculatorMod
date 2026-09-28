package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TurnProfileController {

    public static final class Current {
        public final TurnProfile profile;
        public final int startTick;
        public final boolean[] jumpTicks;
        public final AttemptSampler.Stats attempts;
        public final double pixelDeg;

        Current(TurnProfile profile, int startTick, boolean[] jumpTicks, AttemptSampler.Stats attempts, double pixelDeg) {
            this.profile = profile;
            this.startTick = startTick;
            this.jumpTicks = jumpTicks;
            this.attempts = attempts;
            this.pixelDeg = pixelDeg;
        }
    }

    private final AngleSolverEngine engine;
    private final InputData inputs;
    private final BooleanSupplier enabled;
    private final Supplier<Float> sensitivity;
    private final Supplier<AttemptSampler.Scatter> scatter;
    private final Supplier<Integer> attemptCount;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pkc-turn-profile");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private volatile AtomicBoolean cancelToken = new AtomicBoolean(false);
    private volatile Current current;
    private volatile boolean computing;

    public TurnProfileController(AngleSolverEngine engine, InputData inputs, BooleanSupplier enabled,
                                 Supplier<Float> sensitivity, Supplier<AttemptSampler.Scatter> scatter,
                                 Supplier<Integer> attemptCount) {
        this.engine = engine;
        this.inputs = inputs;
        this.enabled = enabled;
        this.sensitivity = sensitivity;
        this.scatter = scatter;
        this.attemptCount = attemptCount;
    }

    public void refresh() {
        if (!enabled.getAsBoolean()) return;
        AngleSolverEngine.PathSnapshot snap = engine.snapshotCurrentPath();
        if (snap == null) {
            current = null;
            computing = false;
            return;
        }
        boolean[] jumps = jumpTicks(snap.startTick, snap.yaws.length);
        boolean[] edges = keyEdges(snap.startTick, snap.yaws.length);
        double pixelDeg = TurnProfile.pixelDeg(sensitivity.get());
        AttemptSampler.Scatter sc = scatter.get();
        int count = attemptCount.get();
        ForwardModel model = engine.forwardModel();
        cancelToken.set(true);
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancelToken = cancel;
        int gen = generation.incrementAndGet();
        computing = true;
        worker.submit(() -> {
            TurnProfile p = TurnProfile.compute(model, snap.spec, snap.yaws, cancel);
            if (gen != generation.get() || cancel.get()) return;
            AttemptSampler.Stats stats = p.lands
                    ? AttemptSampler.sample(model, snap.spec, p.facing, p.held, edges, pixelDeg, sc, count, cancel)
                    : null;
            if (gen != generation.get() || cancel.get()) return;
            current = new Current(p, snap.startTick, jumps, stats, pixelDeg);
            computing = false;
        });
    }

    public Current current() {
        return current;
    }

    public boolean isComputing() {
        return computing;
    }

    private boolean[] keyEdges(int startTick, int n) {
        boolean[] out = new boolean[n];
        List<InputRow> rows = inputs.getRows();
        for (int k = 0; k < n; k++) {
            int t = startTick + k;
            if (t <= 0 || t >= rows.size()) continue;
            InputRow a = rows.get(t - 1), b = rows.get(t);
            for (InputRow.Key key : InputRow.Key.values()) {
                if (a.isKeyActive(key) != b.isKeyActive(key)) { out[k] = true; break; }
            }
        }
        return out;
    }

    private boolean[] jumpTicks(int startTick, int n) {
        boolean[] out = new boolean[n];
        List<InputRow> rows = inputs.getRows();
        for (int k = 0; k < n; k++) {
            int t = startTick + k;
            if (t < rows.size()) out[k] = rows.get(t).isKeyActive(InputRow.Key.JUMP);
        }
        return out;
    }
}
