package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.anglesolver.SensefinderTab;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratfinderWindow.Ending;
import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

public final class SenseSearchController implements SensefinderTab.Host {

    public static final int MIN_PERCENT = 1;
    public static final int MAX_PERCENT = 200;
    public static final int DEFAULT_FROM_PERCENT = 100;
    private static final long WINDOW_PROBE_NANOS = 1_500_000_000L;

    private final NoTurnSearchController noTurn;
    private final InputData inputData;
    private final SaveController saveController;
    private final AngleSolverEngine engine;
    private final ExactJumpModel model;
    private final IntConsumer onUserChange;
    private final ObjIntConsumer<String> pushMessage;

    private final AtomicBoolean cancel = new AtomicBoolean(false);

    private Thread thread;
    private volatile boolean done;
    private volatile Ending ending = Ending.NONE;
    private volatile String stage = "";
    private volatile double fraction;
    private volatile String outcome = "";
    private volatile List<SenseFinder.Candidate> found = Collections.emptyList();
    private volatile List<SenseFinder.Candidate> ranked = Collections.emptyList();
    private volatile SenseFinder.Candidate selected;
    private volatile SenseFinder.Mode rankMode = SenseFinder.Mode.SENSE;
    private volatile int fromPercent = DEFAULT_FROM_PERCENT;
    private volatile NoTurnResult source;
    private volatile int sourceIndex;
    private volatile SenseFinder.Turn turn;
    private volatile SenseFinder.Window window;
    private long startNanos;
    private long endNanos;
    private int startTick;
    private boolean freeStartYaw;

    public SenseSearchController(NoTurnSearchController noTurn, InputData inputData, SaveController saveController,
                                 AngleSolverEngine engine, ExactJumpModel model, IntConsumer onUserChange,
                                 ObjIntConsumer<String> pushMessage) {
        this.noTurn = noTurn;
        this.inputData = inputData;
        this.saveController = saveController;
        this.engine = engine;
        this.model = model;
        this.onUserChange = onUserChange;
        this.pushMessage = pushMessage;
    }

    @Override
    public boolean isBusy() {
        return thread != null;
    }

    @Override
    public Ending ending() {
        return ending;
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
    public List<SenseFinder.Candidate> results() {
        return ranked;
    }

    @Override
    public SenseFinder.Candidate selected() {
        return selected;
    }

    @Override
    public SenseFinder.Turn turn() {
        return turn;
    }

    @Override
    public SenseFinder.Window window() {
        return window;
    }

    @Override
    public int startTick() {
        return startTick;
    }

    @Override
    public boolean hasLine() {
        return noTurn.selected() != null && noTurn.problem() != null;
    }

    @Override
    public String lineText() {
        NoTurnResult r = source;
        if (r == null) return null;
        return "line " + sourceIndex + "  " + NoTurnKeys.describe(r.combos);
    }

    @Override
    public boolean lineStale() {
        NoTurnResult r = source;
        return r != null && r != noTurn.selected();
    }

    @Override
    public String goalWallLabel() {
        return noTurn.goalWallLabel();
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
    public int fromPercent() {
        return fromPercent;
    }

    @Override
    public void setFromPercent(int percent) {
        fromPercent = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
    }

    @Override
    public void start() {
        if (thread != null) return;
        NoTurnResult r = noTurn.selected();
        NoTurnProblem p = noTurn.problem();
        if (r == null || p == null || r.yaws == null) {
            pushMessage.accept("Sensefinder: select a line first", HudMessageStyle.COLOR_WARN);
            return;
        }
        if (noTurn.isBusy() || engine.isSolving()) {
            pushMessage.accept("Finish the current solve first", HudMessageStyle.COLOR_WARN);
            return;
        }
        JumpSpec spec = p.buildSpec(r.combos, r.sprint, r.turnCombo, r.ja);
        SenseFinder.Turn t = SenseFinder.turnOf(model, spec, r.yaws, r.startX, r.startZ);
        source = r;
        sourceIndex = noTurn.results().indexOf(r) + 1;
        startTick = noTurn.startTick();
        freeStartYaw = noTurn.freeStartYaw();
        turn = t;
        window = null;
        found = Collections.emptyList();
        ranked = Collections.emptyList();
        selected = null;
        if (t.angles() == 0) {
            ending = Ending.EMPTY;
            outcome = "the line has no turn";
            pushMessage.accept("Sensefinder: the line has no turn", HudMessageStyle.COLOR_WARN);
            return;
        }
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.minSens = fromPercent / 200f;
        cfg.maxSens = 1f;
        cancel.set(false);
        done = false;
        ending = Ending.NONE;
        outcome = "";
        stage = "starting";
        fraction = 0.0;
        startNanos = System.nanoTime();
        endNanos = startNanos;
        thread = new Thread(() -> {
            try {
                SenseFinder.Progress progress = (s, f) -> {
                    stage = s;
                    fraction = Math.max(0.0, Math.min(1.0, f));
                };
                SenseFinder.Window w = SenseFinder.solvedWindow(t, WINDOW_PROBE_NANOS, cancel, progress);
                window = w;
                List<SenseFinder.Candidate> list = SenseFinder.run(t, cfg, w, cancel, progress);
                found = Collections.unmodifiableList(new ArrayList<>(list));
                rerank();
            } catch (Throwable ex) {
                ex.printStackTrace();
                stage = "error: " + ex;
            } finally {
                done = true;
            }
        }, "sense-finder");
        thread.setDaemon(true);
        thread.start();
        pushMessage.accept("Sense search started", HudMessages.COLOR_DEFAULT);
    }

    @Override
    public void cancel() {
        if (thread == null) return;
        cancel.set(true);
        stage = "cancelling";
        pushMessage.accept("Sense search cancelled", HudMessageStyle.COLOR_WARN);
    }

    @Override
    public void select(SenseFinder.Candidate c) {
        if (c == null) return;
        NoTurnResult r = source;
        if (r == null) return;
        selected = c;
        if (r != noTurn.selected()) noTurn.select(r);
        engine.writeYaws(inputData.getRows(), startTick, c.yaws, freeStartYaw);
        saveController.markDirty();
        onUserChange.accept(startTick);
    }

    public void poll() {
        if (thread == null || !done) return;
        thread = null;
        endNanos = System.nanoTime();
        fraction = 1.0;
        List<SenseFinder.Candidate> list = ranked;
        if (cancel.get()) {
            ending = list.isEmpty() ? Ending.EMPTY : Ending.CANCELLED;
            outcome = list.isEmpty() ? "cancelled, nothing found" : "cancelled, " + countText(list.size()) + " kept";
            return;
        }
        if (list.isEmpty()) {
            ending = Ending.EMPTY;
            outcome = window == null ? "the line does not land" : "no sense lands the turn";
            pushMessage.accept("Sensefinder: none found", HudMessageStyle.COLOR_DANGER);
            return;
        }
        ending = Ending.FOUND;
        outcome = countText(list.size()) + " found";
    }

    private void rerank() {
        List<SenseFinder.Candidate> copy = new ArrayList<>(found);
        copy.sort(SenseFinder.by(rankMode));
        ranked = Collections.unmodifiableList(copy);
    }

    private static String countText(int n) {
        return n + (n == 1 ? " sense" : " senses");
    }
}
