package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        public final double difficulty;
        public final StratMeasurements measurements;
        public final int corpusEntries;
        public final String corpusExample;
        public final ForwardPath path;
        public final int startTick;

        Item(String label, int edits, boolean feasible, double margin, long elapsedMs,
             String appliedSnapshotJson, boolean original, double difficulty,
             StratMeasurements measurements, int corpusEntries, String corpusExample,
             ForwardPath path, int startTick) {
            this.label = label;
            this.edits = edits;
            this.feasible = feasible;
            this.margin = margin;
            this.elapsedMs = elapsedMs;
            this.appliedSnapshotJson = appliedSnapshotJson;
            this.original = original;
            this.difficulty = difficulty;
            this.measurements = measurements;
            this.corpusEntries = corpusEntries;
            this.corpusExample = corpusExample;
            this.path = path;
            this.startTick = startTick;
        }
    }

    private static final Gson GSON = new Gson();

    private static final Comparator<Item> RANK = new Comparator<Item>() {
        @Override
        public int compare(Item a, Item b) {
            if (a.original != b.original) return a.original ? -1 : 1;
            if (a.feasible != b.feasible) return a.feasible ? -1 : 1;
            boolean an = Double.isNaN(a.difficulty);
            boolean bn = Double.isNaN(b.difficulty);
            if (an != bn) return an ? 1 : -1;
            if (!an) {
                int c = Double.compare(a.difficulty, b.difficulty);
                if (c != 0) return c;
            }
            if (a.edits != b.edits) return Integer.compare(a.edits, b.edits);
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
    private final int threads;

    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final List<Item> items = new CopyOnWriteArrayList<Item>();
    private volatile int total = -1;
    private volatile boolean running;

    public StratFinder(SaveFile witness, ExactJumpModel model, FileSystemSaveStore store,
                       long budgetMs, double objectiveEdge, boolean objectiveMax) {
        this(witness, model, store, budgetMs, objectiveEdge, objectiveMax, StratVariants.Filter.ALL, 1);
    }

    public StratFinder(SaveFile witness, ExactJumpModel model, FileSystemSaveStore store,
                       long budgetMs, double objectiveEdge, boolean objectiveMax,
                       StratVariants.Filter filter) {
        this(witness, model, store, budgetMs, objectiveEdge, objectiveMax, filter, 1);
    }

    public StratFinder(SaveFile witness, ExactJumpModel model, FileSystemSaveStore store,
                       long budgetMs, double objectiveEdge, boolean objectiveMax,
                       StratVariants.Filter filter, int threads) {
        this.witness = witness;
        this.model = model;
        this.store = store;
        this.budgetMs = budgetMs;
        this.objectiveEdge = objectiveEdge;
        this.objectiveMax = objectiveMax;
        this.filter = filter == null ? StratVariants.Filter.ALL : filter;
        this.threads = Math.max(1, threads);
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

    public int feasibleCount() {
        int n = 0;
        for (Item it : items) {
            if (it.feasible) n++;
        }
        return n;
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
        ExecutorService pool = null;
        try {
            List<StratVariants.Variant> variants = StratVariants.variants(witness, model, filter);
            total = variants.size();
            if (threads <= 1) {
                for (StratVariants.Variant v : variants) {
                    if (cancelToken.get()) break;
                    items.add(solveVariant(v));
                }
                return;
            }
            pool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "strat-finder-worker");
                t.setDaemon(true);
                return t;
            });
            for (StratVariants.Variant v : variants) {
                final StratVariants.Variant fv = v;
                pool.submit(() -> {
                    if (!cancelToken.get()) {
                        items.add(solveVariant(fv));
                    }
                });
            }
            pool.shutdown();
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            running = false;
        }
    }

    private Item solveVariant(StratVariants.Variant v) {
        long t0 = System.nanoTime();
        boolean original = "self".equals(v.label);
        int startTick = v.save.angleSolver.startTick;
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
                return new Item(v.label, v.edits, false, Double.NaN, ms, null, original,
                        Double.NaN, null, 0, null, null, startTick);
            }
            engine.apply();
            state.setApplyDeviation(null, null);
            Vec3dCore startPos = moved[0] != null ? moved[0] : SaveIO.posOf(witness.start);
            SaveFile applied = SaveIO.buildSaveFile(store, inputs, startPos, SaveIO.velOf(witness.start),
                    witness.start.yaw, pitchOf(witness.start), state, null, false);
            graftDebug(applied, v.save, moved[0]);

            SaveFile smoothed = null;
            if (!cancelToken.get()) {
                try {
                    smoothed = StratMeasure.withSmoothedLine(applied, v.label);
                } catch (RuntimeException ignored) {
                }
            }
            SaveFile use = smoothed != null ? smoothed : applied;

            StratMeasurements meas = null;
            double difficulty = Double.NaN;
            if (!cancelToken.get()) {
                try {
                    meas = StratMeasure.measure(use, v.label);
                    difficulty = StratDifficulty.combinedV4(meas);
                } catch (RuntimeException ignored) {
                }
            }

            ForwardPath path = pathOf(use);
            CorpusIndex.Provenance prov = CorpusIndex.get().lookup(v.label);
            ms = (System.nanoTime() - t0) / 1_000_000L;
            return new Item(v.label, v.edits, true, marginOf(result), ms, SaveIO.saveJson(use), original,
                    difficulty, meas, prov != null ? prov.entries : 0,
                    prov != null ? prov.example : null, path, use.angleSolver.startTick);
        } catch (RuntimeException ex) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            return new Item(v.label, v.edits, false, Double.NaN, ms, null, original,
                    Double.NaN, null, 0, null, null, startTick);
        }
    }

    private void graftDebug(SaveFile applied, SaveFile variant, Vec3dCore moved) {
        SaveFile src = GSON.fromJson(GSON.toJson(variant), SaveFile.class);
        applied.debug = src.debug;
        if (applied.angleSolver != null && src.angleSolver != null) {
            applied.angleSolver.seed = src.angleSolver.seed;
            if (moved != null && applied.angleSolver.seed != null && applied.angleSolver.startTick == 0) {
                applied.angleSolver.seed.pos = new double[]{moved.x, moved.y, moved.z};
            }
        }
        try {
            JumpPhysicsInputs sc = StratVariants.scenario(variant, model);
            if (sc != null) {
                StratVariants.deriveDebugSamples(applied, sc);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private ForwardPath pathOf(SaveFile use) {
        try {
            JumpSpec spec = StratMeasure.buildSpec(use, model);
            if (spec == null) {
                return null;
            }
            JumpPhysicsInputs sc = spec.asScenario();
            double[] yaws = StratMeasure.recordedYaws(use, "path", sc.numTicks);
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            return model.forward(sc, gf);
        } catch (RuntimeException ex) {
            return null;
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
