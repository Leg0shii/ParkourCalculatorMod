package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.anglesolver.SenseFinderWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

public final class SenseSearchController implements SenseFinderWindow.Host {

    private static final long WINDOW_PROBE_NANOS = 750_000_000L;
    private static final long ANCHOR_NANOS = 750_000_000L;
    private static final long MARGIN_PROBE_NANOS = 120_000_000L;
    private static final long RERANK_NANOS = 250_000_000L;

    private final InputData inputData;
    private final SaveController saveController;
    private final AngleSolverState state;
    private final AngleSolverEngine engine;
    private final ExactJumpModel model;
    private final IntConsumer onUserChange;
    private final DoubleSupplier gameSense;

    private final AtomicBoolean cancel = new AtomicBoolean(false);

    private Thread thread;
    private volatile boolean done;
    private volatile String stage = "";
    private volatile double fraction;
    private volatile String outcome = "";
    private volatile List<SenseFinder.Candidate> found = Collections.emptyList();
    private volatile List<SenseFinder.Candidate> ranked = Collections.emptyList();
    private volatile SenseFinder.Candidate gameRow;
    private volatile float gameRowSens = Float.NaN;
    private volatile SenseFinder.Candidate selected;
    private volatile SenseFinder.Mode rankMode = SenseFinder.Mode.WEAKEST;
    private volatile SenseFinder.Window window;
    private volatile SenseFinder.Turn searched;
    private volatile String applied = "";
    private float minPercent = 0f;
    private float maxPercent = 200f;
    private int firstTick;
    private int lastTick;
    private SenseFinder.Turn current;
    private int currentStartTick;
    private boolean currentFreeStartYaw;
    private volatile boolean stale = true;
    private int seenStartTick = -1;
    private int seenLandingTick = -1;
    private int searchedStartTick;
    private boolean searchedFreeStartYaw;
    private long startNanos;
    private long endNanos;
    private long lastRerank;

    public SenseSearchController(InputData inputData, SaveController saveController, AngleSolverState state,
                                 AngleSolverEngine engine, ExactJumpModel model, IntConsumer onUserChange,
                                 DoubleSupplier gameSense) {
        this.inputData = inputData;
        this.saveController = saveController;
        this.state = state;
        this.engine = engine;
        this.model = model;
        this.onUserChange = onUserChange;
        this.gameSense = gameSense;
    }

    @Override
    public SenseFinder.Turn turn() {
        if (state.getStartTick() != seenStartTick || state.getLandingTick() != seenLandingTick) {
            seenStartTick = state.getStartTick();
            seenLandingTick = state.getLandingTick();
            stale = true;
        }
        if (stale && !engine.isSolving()) {
            stale = false;
            current = null;
            AngleSolverEngine.PathSnapshot snap = model == null ? null
                    : engine.snapshotPath(state.getStartTick(), state.getLandingTick());
            if (snap != null) {
                SenseFinder.Turn t = SenseFinder.turnOf(model, snap);
                if (t.angles() > 0 && !Double.isNaN(t.referenceOffset())) {
                    current = t;
                    currentStartTick = snap.startTick;
                    currentFreeStartYaw = snap.freeStartYaw;
                }
            }
        }
        return current;
    }

    public void invalidate() {
        stale = true;
    }

    @Override
    public int startTick() {
        return currentStartTick;
    }

    @Override
    public float gameSense() {
        return (float) gameSense.getAsDouble();
    }

    @Override
    public boolean isBusy() {
        return thread != null;
    }

    @Override
    public String stage() {
        return stage;
    }

    @Override
    public double fraction() {
        return fraction;
    }

    @Override
    public double elapsedSeconds() {
        if (startNanos == 0L) return 0.0;
        long end = thread != null ? System.nanoTime() : endNanos;
        return (end - startNanos) / 1e9;
    }

    @Override
    public String outcome() {
        return outcome;
    }

    @Override
    public String applied() {
        return applied;
    }

    @Override
    public List<SenseFinder.Candidate> results() {
        return ranked;
    }

    @Override
    public SenseFinder.Candidate gameRow() {
        return gameRow;
    }

    @Override
    public boolean searchedGameSense() {
        return !Float.isNaN(gameRowSens);
    }

    @Override
    public SenseFinder.Candidate selected() {
        return selected;
    }

    @Override
    public SenseFinder.Turn searchedTurn() {
        return searched;
    }

    @Override
    public SenseFinder.Window window() {
        return window;
    }

    @Override
    public SenseFinder.Mode rankMode() {
        return rankMode;
    }

    @Override
    public void setRankMode(SenseFinder.Mode mode) {
        if (mode == null || mode == rankMode) return;
        rankMode = mode;
        rerank();
    }

    @Override
    public float minPercent() {
        return minPercent;
    }

    @Override
    public float maxPercent() {
        return maxPercent;
    }

    @Override
    public void setRange(float min, float max) {
        minPercent = Math.max(0f, Math.min(200f, min));
        maxPercent = Math.max(0f, Math.min(200f, max));
    }

    @Override
    public int firstTick() {
        return firstTick;
    }

    @Override
    public int lastTick() {
        return lastTick;
    }

    @Override
    public void setTicks(int first, int last) {
        firstTick = Math.max(0, first);
        lastTick = Math.max(0, last);
    }

    @Override
    public void start() {
        if (thread != null || engine.isSolving()) return;
        stale = true;
        SenseFinder.Turn t = turn();
        if (t == null) return;
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.minSens = Math.min(minPercent, maxPercent) / 200f;
        cfg.maxSens = Math.max(minPercent, maxPercent) / 200f;
        cfg.scopeFirst = firstTick > 0 ? firstTick - 1 - currentStartTick : -1;
        cfg.scopeLast = lastTick > 0 ? lastTick - 1 - currentStartTick : -1;
        float game = gameSense();
        searched = t;
        searchedStartTick = currentStartTick;
        searchedFreeStartYaw = currentFreeStartYaw;
        window = null;
        found = Collections.emptyList();
        ranked = Collections.emptyList();
        gameRow = null;
        gameRowSens = Float.NaN;
        selected = null;
        applied = "";
        cancel.set(false);
        done = false;
        outcome = "";
        stage = "starting";
        fraction = 0.0;
        startNanos = System.nanoTime();
        endNanos = startNanos;
        thread = new Thread(() -> {
            try {
                search(t, cfg, game);
            } catch (Throwable ex) {
                ex.printStackTrace();
                stage = "error: " + ex;
            } finally {
                done = true;
            }
        }, "sense-finder");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void search(SenseFinder.Turn t, SenseFinder.Config cfg, float game) throws Exception {
        int threads = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
        SenseFinder.Window w = SenseFinder.solvedWindow(t, WINDOW_PROBE_NANOS, cancel, (s, f) -> stage = s);
        window = w;
        if (w == null || cancel.get()) return;
        List<SenseFinder.Anchor> anchors = SenseFinder.anchors(t, w, cfg.anchorStepDeg, ANCHOR_NANOS, threads, cancel,
                (s, f) -> {
                    stage = s;
                    fraction = 0.3 * f;
                });
        if (cancel.get()) return;
        List<SenseFinder.Candidate> list = SenseFinder.run(t, cfg, w, anchors, cancel, (s, f) -> {
            stage = s;
            fraction = 0.3 + 0.2 * f;
        });
        SenseFinder.Candidate g = game >= 0f && game <= 1f ? SenseFinder.at(t, game, w, anchors, cfg) : null;
        gameRow = g;
        gameRowSens = game;
        found = Collections.unmodifiableList(new ArrayList<>(list));
        rerank();
        if (cancel.get()) return;
        List<SenseFinder.Candidate> order = SenseFinder.ranked(list, SenseFinder.Mode.WEAKEST);
        List<SenseFinder.Candidate> work = new ArrayList<>();
        if (g != null) work.add(g);
        work.addAll(order);
        int total = work.size();
        int[] finished = new int[1];
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread th = new Thread(r, "sense-finder-margins");
            th.setDaemon(true);
            th.setPriority(Thread.MIN_PRIORITY);
            return th;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (SenseFinder.Candidate c : work) {
                futures.add(pool.submit(() -> {
                    if (cancel.get()) return;
                    SenseFinder.fillMargins(t, c, MARGIN_PROBE_NANOS, cancel);
                    synchronized (finished) {
                        finished[0]++;
                        stage = "margins " + finished[0] + " of " + total;
                        fraction = 0.5 + 0.5 * finished[0] / Math.max(1, total);
                    }
                }));
            }
            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }
        rerank();
    }

    @Override
    public void cancel() {
        if (thread == null) return;
        cancel.set(true);
        stage = "cancelling";
    }

    @Override
    public void select(SenseFinder.Candidate c) {
        SenseFinder.Turn t = searched;
        if (c == null || t == null || engine.isSolving()) return;
        selected = c;
        engine.writeYaws(inputData.getRows(), searchedStartTick, c.yaws, searchedFreeStartYaw);
        saveController.markDirty();
        onUserChange.accept(searchedStartTick);
        applied = String.format(java.util.Locale.ROOT, "Applied %.2f%%, lands %+.5f", c.percent(), c.offset);
    }

    public void poll() {
        if (thread == null) return;
        long now = System.nanoTime();
        if (now - lastRerank > RERANK_NANOS) {
            lastRerank = now;
            rerank();
        }
        if (!done) return;
        thread = null;
        endNanos = now;
        fraction = 1.0;
        rerank();
        int n = ranked.size();
        if (cancel.get()) outcome = n == 0 ? "cancelled" : "cancelled, " + countText(n);
        else if (window == null) outcome = "the solved path does not land";
        else outcome = n == 0 ? "no sense lands the turn" : countText(n);
        stage = "";
    }

    private void rerank() {
        ranked = SenseFinder.ranked(found, rankMode);
    }

    private static String countText(int n) {
        return n + (n == 1 ? " row" : " rows");
    }
}
