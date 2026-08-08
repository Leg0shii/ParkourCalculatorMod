package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StratFinder {

    public static final class Item {
        public final String label;
        public final int edits;
        public final boolean feasible;
        public final double margin;
        public final long elapsedMs;
        public final String appliedSnapshotJson;
        public final boolean original;

        Item(String label, int edits, boolean feasible, double margin, long elapsedMs,
             String appliedSnapshotJson, boolean original) {
            this.label = label;
            this.edits = edits;
            this.feasible = feasible;
            this.margin = margin;
            this.elapsedMs = elapsedMs;
            this.appliedSnapshotJson = appliedSnapshotJson;
            this.original = original;
        }
    }

    private static final Comparator<Item> RANK = new Comparator<Item>() {
        @Override
        public int compare(Item a, Item b) {
            if (a.original != b.original) return a.original ? -1 : 1;
            if (a.feasible != b.feasible) return a.feasible ? -1 : 1;
            if (a.edits != b.edits) return Integer.compare(a.edits, b.edits);
            if (a.feasible && a.elapsedMs != b.elapsedMs) return Long.compare(a.elapsedMs, b.elapsedMs);
            return a.label.compareTo(b.label);
        }
    };

    private final SaveFile witness;
    private final ExactJumpModel model;
    private final FileSystemSaveStore store;
    private final long budgetMs;
    private final double objectiveEdge;
    private final boolean objectiveMax;
    private final StratVariants.Filter filter;

    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final List<Item> items = new CopyOnWriteArrayList<Item>();
    private volatile int total = -1;
    private volatile boolean running;

    public StratFinder(SaveFile witness, ExactJumpModel model, FileSystemSaveStore store,
                       long budgetMs, double objectiveEdge, boolean objectiveMax) {
        this(witness, model, store, budgetMs, objectiveEdge, objectiveMax, StratVariants.Filter.ALL);
    }

    public StratFinder(SaveFile witness, ExactJumpModel model, FileSystemSaveStore store,
                       long budgetMs, double objectiveEdge, boolean objectiveMax,
                       StratVariants.Filter filter) {
        this.witness = witness;
        this.model = model;
        this.store = store;
        this.budgetMs = budgetMs;
        this.objectiveEdge = objectiveEdge;
        this.objectiveMax = objectiveMax;
        this.filter = filter == null ? StratVariants.Filter.ALL : filter;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread worker = new Thread(this::run, "strat-finder");
        worker.setDaemon(true);
        worker.start();
    }

    public void cancel() {
        cancelToken.set(true);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isCancelled() {
        return cancelToken.get();
    }

    public int total() {
        return total;
    }

    public int done() {
        return items.size();
    }

    public boolean canaryFailed() {
        for (Item it : items) {
            if (it.original) return !it.feasible;
        }
        return false;
    }

    public List<Item> ranked() {
        List<Item> out = new ArrayList<Item>(items);
        Collections.sort(out, RANK);
        return out;
    }

    private void run() {
        try {
            List<StratVariants.Variant> variants = StratVariants.variants(witness, model, filter);
            total = variants.size();
            for (StratVariants.Variant v : variants) {
                if (cancelToken.get()) break;
                items.add(solveVariant(v));
            }
        } finally {
            running = false;
        }
    }

    private Item solveVariant(StratVariants.Variant v) {
        long t0 = System.nanoTime();
        boolean original = "self".equals(v.label);
        try {
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(v.save, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(v.save, state);
            state.clearResult();
            AngleSolverEngine engine = new AngleSolverEngine(state, SaveBoxes.buildBoxes(v.save), inputs, t -> {
            }, model);
            final Vec3dCore[] moved = new Vec3dCore[1];
            engine.setOnStartMoved(p -> moved[0] = p);
            engine.solve();
            long deadline = System.currentTimeMillis() + budgetMs;
            while (engine.isSolving() && !cancelToken.get() && System.currentTimeMillis() < deadline) {
                engine.poll();
                sleep(2);
            }
            if (engine.isSolving()) engine.cancel();
            engine.poll();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            SolveResult result = state.getResult();
            if (result == null || !result.isSuccess()) {
                return new Item(v.label, v.edits, false, Double.NaN, ms, null, original);
            }
            engine.apply();
            state.setApplyDeviation(null, null);
            Vec3dCore startPos = moved[0] != null ? moved[0] : SaveIO.posOf(witness.start);
            SaveFile applied = SaveIO.buildSaveFile(store, inputs, startPos, SaveIO.velOf(witness.start),
                    witness.start.yaw, pitchOf(witness.start), state, null, false);
            return new Item(v.label, v.edits, true, marginOf(result), ms, SaveIO.saveJson(applied), original);
        } catch (RuntimeException ex) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            return new Item(v.label, v.edits, false, Double.NaN, ms, null, original);
        }
    }

    private double marginOf(SolveResult result) {
        if (!result.hasObjective() || Double.isNaN(objectiveEdge)) return Double.NaN;
        return objectiveMax ? result.getObjectiveValue() - objectiveEdge
                : objectiveEdge - result.getObjectiveValue();
    }

    private static float pitchOf(SaveFile.Start start) {
        return start.pitch != null ? start.pitch : PlaybackController.DEFAULT_PITCH;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
