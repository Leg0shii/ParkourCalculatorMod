package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
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

public final class TurnProfileController {

    public static final class Current {
        public final TurnProfile profile;
        public final int startTick;
        public final boolean[] jumpTicks;

        Current(TurnProfile profile, int startTick, boolean[] jumpTicks) {
            this.profile = profile;
            this.startTick = startTick;
            this.jumpTicks = jumpTicks;
        }
    }

    private final AngleSolverEngine engine;
    private final InputData inputs;
    private final BooleanSupplier enabled;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pkc-turn-profile");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private volatile AtomicBoolean cancelToken = new AtomicBoolean(false);
    private volatile Current current;
    private volatile boolean computing;

    public TurnProfileController(AngleSolverEngine engine, InputData inputs, BooleanSupplier enabled) {
        this.engine = engine;
        this.inputs = inputs;
        this.enabled = enabled;
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
        ForwardModel model = engine.forwardModel();
        cancelToken.set(true);
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancelToken = cancel;
        int gen = generation.incrementAndGet();
        computing = true;
        worker.submit(() -> {
            TurnProfile p = TurnProfile.compute(model, snap.spec, snap.yaws, cancel);
            if (gen != generation.get() || cancel.get()) return;
            current = new Current(p, snap.startTick, jumps);
            computing = false;
        });
    }

    public Current current() {
        return current;
    }

    public boolean isComputing() {
        return computing;
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
