package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnFinder;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnOptimizePass;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnRanking;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratfinderWindow;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratfinderWindow.Ending;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratfinderWindow.Phase;
import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

public final class NoTurnSearchController implements StratfinderWindow.Host {

    public static final int MIN_BUDGET_SECONDS = 1;
    public static final int MAX_BUDGET_SECONDS = 120;
    public static final int DEFAULT_BUDGET_SECONDS = 10;

    private final InputData inputData;
    private final SimulationRunner runner;
    private final BoxController boxController;
    private final SaveController saveController;
    private final AngleSolverState state;
    private final AngleSolverEngine engine;
    private final ExactJumpModel model;
    private final MinecraftAccess mc;
    private final IntConsumer onUserChange;
    private final ObjIntConsumer<String> pushMessage;
    private final BooleanSupplier otherSolveRunning;

    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private final List<NoTurnResult> results = new ArrayList<>();

    private Thread thread;
    private volatile boolean done;
    private volatile Phase phase = Phase.IDLE;
    private volatile Ending ending = Ending.NONE;
    private volatile String stage = "";
    private volatile double fraction;
    private volatile String outcome = "";
    private volatile List<NoTurnResult> ranked = Collections.emptyList();
    private volatile boolean reapplySelected;
    private volatile boolean optimizeQueued;
    private volatile NoTurnResult selected;
    private volatile NoTurnProblem problem;
    private volatile JumpConstraint goalWall;
    private volatile NoTurnRanking.Mode rankMode = NoTurnRanking.Mode.EASIEST;
    private volatile int budgetSeconds = DEFAULT_BUDGET_SECONDS;
    private volatile int optimizedInPass;
    private volatile boolean playable = true;
    private long startNanos;
    private long endNanos;
    private int startTick;
    private boolean freeStartYaw;

    public NoTurnSearchController(InputData inputData, SimulationRunner runner, BoxController boxController,
                                  SaveController saveController, AngleSolverState state, AngleSolverEngine engine,
                                  ExactJumpModel model, MinecraftAccess mc, IntConsumer onUserChange,
                                  ObjIntConsumer<String> pushMessage, BooleanSupplier otherSolveRunning) {
        this.inputData = inputData;
        this.runner = runner;
        this.boxController = boxController;
        this.saveController = saveController;
        this.state = state;
        this.engine = engine;
        this.model = model;
        this.mc = mc;
        this.onUserChange = onUserChange;
        this.pushMessage = pushMessage;
        this.otherSolveRunning = otherSolveRunning;
    }

    @Override
    public boolean isBusy() {
        return thread != null;
    }

    @Override
    public Phase phase() {
        return phase;
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
    public List<NoTurnResult> results() {
        return ranked;
    }

    @Override
    public NoTurnResult selected() {
        return selected;
    }

    @Override
    public int startTick() {
        return startTick;
    }

    @Override
    public NoTurnRanking.Mode rankMode() {
        return rankMode;
    }

    @Override
    public void setRankMode(NoTurnRanking.Mode mode) {
        if (mode == null || mode == rankMode) return;
        rankMode = mode;
        synchronized (results) {
            rerankLocked();
        }
    }

    @Override
    public int budgetSeconds() {
        return budgetSeconds;
    }

    @Override
    public void setBudgetSeconds(int seconds) {
        budgetSeconds = Math.max(MIN_BUDGET_SECONDS, Math.min(MAX_BUDGET_SECONDS, seconds));
    }

    @Override
    public boolean playable() {
        return playable;
    }

    @Override
    public void setPlayable(boolean value) {
        playable = value;
    }

    @Override
    public int unoptimizedCount() {
        int n = 0;
        for (NoTurnResult r : ranked) if (!r.optimized) n++;
        return n;
    }

    @Override
    public double offsetOf(NoTurnResult r) {
        NoTurnProblem p = problem;
        if (p == null || r == null) return Double.NaN;
        return NoTurnRanking.offset(goalWall, p.objective, r.objective);
    }

    @Override
    public String goalWallLabel() {
        JumpConstraint w = goalWall;
        return w != null ? w.name : null;
    }

    @Override
    public void start() {
        if (thread != null) return;
        if (!canRun()) return;
        JumpSpec spec = engine.debugBuildSpec();
        NoTurnProblem p = NoTurnProblem.from(spec, model);
        if (p.issue != null) {
            outcome = p.issue;
            ending = Ending.EMPTY;
            pushMessage.accept("No-turn: " + p.issue, HudMessageStyle.COLOR_WARN);
            return;
        }
        synchronized (results) {
            results.clear();
            ranked = Collections.emptyList();
        }
        selected = null;
        reapplySelected = false;
        problem = p;
        goalWall = NoTurnRanking.goalWall(p.baseSpec.constraints, p.objective);
        startTick = state.getStartTick();
        freeStartYaw = engine.debugFreeStartYaw();
        beginRun(Phase.SEARCH, "starting");
        SolverGraph graph = BuiltinGraphs.optimize(6);
        StructurePoolDriver.Config poolCfg = new StructurePoolDriver.Config();
        poolCfg.allowJa = true;
        poolCfg.certifyBudgetNanos = 0L;
        poolCfg.extraCertify = 1000;
        poolCfg.extraCertifyNanos = 45_000_000_000L;
        poolCfg.playable = playable;
        StructurePoolDriver driver = new StructurePoolDriver(model, poolCfg, cancel, new StructurePoolDriver.Progress() {
            @Override
            public void update(String s, double f) {
                onStage(s, f);
            }

            @Override
            public void found(NoTurnResult r) {
                onFound(r);
            }
        });
        NoTurnFinder.Config beamCfg = new NoTurnFinder.Config();
        beamCfg.certifyBudgetNanos = 0L;
        beamCfg.playable = playable;
        NoTurnFinder finder = new NoTurnFinder(model, beamCfg, cancel, new NoTurnFinder.Progress() {
            @Override
            public void update(String s, double f) {
                stage = "beam: " + s;
                fraction = Math.max(0.0, Math.min(1.0, f));
            }

            @Override
            public void found(NoTurnResult r) {
                onFound(r);
            }
        });
        launch(() -> {
            NoTurnResult r = driver.run(p, graph);
            if (r == null && !cancel.get()) finder.run(p, graph);
            if (optimizeQueued && !ranked.isEmpty()) {
                cancel.set(false);
                runOptimizePass();
            }
        });
        pushMessage.accept("No-turn search started", HudMessages.COLOR_DEFAULT);
    }

    @Override
    public void optimize() {
        if (problem == null || ranked.isEmpty() || unoptimizedCount() == 0) return;
        if (thread != null) {
            if (phase != Phase.SEARCH || optimizeQueued) return;
            optimizeQueued = true;
            cancel.set(true);
            stage = "stopping the search";
            pushMessage.accept("No-turn: optimizing the lines found so far", HudMessages.COLOR_DEFAULT);
            return;
        }
        if (!canRun()) return;
        beginRun(Phase.OPTIMIZE, "starting");
        launch(this::runOptimizePass);
        pushMessage.accept("No-turn: optimizing " + unoptimizedCount() + " lines", HudMessages.COLOR_DEFAULT);
    }

    @Override
    public void cancel() {
        if (thread == null) return;
        cancel.set(true);
        stage = "cancelling";
        pushMessage.accept("No-turn " + (phase == Phase.OPTIMIZE ? "optimize" : "search") + " cancelled",
                HudMessageStyle.COLOR_WARN);
    }

    @Override
    public void select(NoTurnResult r) {
        if (r == null) return;
        selected = r;
        reapplySelected = false;
        apply(r);
    }

    public void poll() {
        if (thread == null) return;
        if (reapplySelected) {
            reapplySelected = false;
            NoTurnResult s = selected;
            if (s != null) apply(s);
        }
        if (!done) return;
        thread = null;
        endNanos = System.nanoTime();
        fraction = 1.0;
        Phase finished = phase;
        phase = Phase.IDLE;
        List<NoTurnResult> list = ranked;
        if (cancel.get()) {
            ending = list.isEmpty() ? Ending.EMPTY : Ending.CANCELLED;
            outcome = list.isEmpty() ? "cancelled, nothing found"
                    : "cancelled, " + list.size() + (list.size() == 1 ? " line" : " lines") + " kept";
            return;
        }
        if (list.isEmpty()) {
            ending = Ending.EMPTY;
            outcome = "no line found";
            pushMessage.accept("No-turn: none found", HudMessageStyle.COLOR_DANGER);
            return;
        }
        ending = Ending.FOUND;
        if (finished == Phase.OPTIMIZE) {
            outcome = optimizedInPass + " of " + list.size() + " lines improved";
        } else {
            outcome = list.size() + (list.size() == 1 ? " line" : " lines") + " found";
        }
        if (selected == null) {
            select(list.get(0));
            pushMessage.accept("No-turn applied: " + list.get(0).describe(), HudMessageStyle.COLOR_OK);
        }
    }

    private boolean canRun() {
        if (engine.isSolving() || otherSolveRunning.getAsBoolean()) {
            pushMessage.accept("Finish the current solve first", HudMessageStyle.COLOR_WARN);
            return false;
        }
        return mc.isReady();
    }

    private void beginRun(Phase p, String firstStage) {
        cancel.set(false);
        optimizeQueued = false;
        done = false;
        phase = p;
        ending = Ending.NONE;
        outcome = "";
        stage = firstStage;
        fraction = 0.0;
        optimizedInPass = 0;
        startNanos = System.nanoTime();
        endNanos = startNanos;
    }

    private void launch(Runnable body) {
        thread = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                t.printStackTrace();
                stage = "error: " + t;
            } finally {
                done = true;
            }
        }, "noturn-finder");
        thread.setDaemon(true);
        thread.start();
    }

    private void runOptimizePass() {
        NoTurnProblem p = problem;
        if (p == null) return;
        phase = Phase.OPTIMIZE;
        fraction = 0.0;
        NoTurnOptimizePass pass = new NoTurnOptimizePass(model, 0, cancel, new NoTurnOptimizePass.Progress() {
            @Override
            public void update(String s, double f) {
                stage = s;
                fraction = Math.max(0.0, Math.min(1.0, f));
            }

            @Override
            public void optimized(NoTurnResult before, NoTurnResult after) {
                onOptimized(before, after);
            }
        });
        optimizedInPass = pass.run(p, ranked, budgetSeconds);
    }

    private void onStage(String s, double f) {
        int cut = s.indexOf(" best=");
        stage = cut > 0 ? s.substring(0, cut) : s;
        if (f > fraction || f >= 1.0) fraction = Math.min(1.0, f);
    }

    private Comparator<NoTurnResult> order() {
        NoTurnProblem p = problem;
        return NoTurnRanking.by(rankMode, goalWall, p.objective);
    }

    private void onFound(NoTurnResult r) {
        if (r == null) return;
        synchronized (results) {
            int same = -1;
            for (int i = 0; i < results.size(); i++) {
                if (sameKeys(results.get(i), r)) {
                    same = i;
                    break;
                }
            }
            if (same >= 0) {
                NoTurnResult old = results.get(same);
                if (order().compare(r, old) >= 0) return;
                results.remove(same);
                if (selected == old) {
                    selected = r;
                    reapplySelected = true;
                }
            }
            results.add(r);
            rerankLocked();
        }
    }

    private void onOptimized(NoTurnResult before, NoTurnResult after) {
        synchronized (results) {
            int at = -1;
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i) == before) {
                    at = i;
                    break;
                }
            }
            if (at < 0) return;
            if (after != before) {
                results.set(at, after);
                if (selected == before) {
                    selected = after;
                    reapplySelected = true;
                }
            }
            rerankLocked();
        }
    }

    private void rerankLocked() {
        List<NoTurnResult> copy = new ArrayList<>(results);
        if (problem != null) copy.sort(order());
        ranked = Collections.unmodifiableList(copy);
    }

    private static boolean sameKeys(NoTurnResult a, NoTurnResult b) {
        return a.ja == b.ja && a.warm == b.warm && Arrays.equals(a.combos, b.combos) && Arrays.equals(a.sprint, b.sprint);
    }

    private void apply(NoTurnResult r) {
        int start = startTick;
        if (!r.warm) {
            for (int t = 0; t < r.combos.length; t++) {
                int idx = start + t;
                if (idx < 0 || idx >= inputData.size()) continue;
                InputRow row = inputData.get(idx);
                int c = r.combos[t];
                row.setKeyActive(InputRow.Key.W, NoTurnKeys.forwardSign(c) > 0);
                row.setKeyActive(InputRow.Key.S, NoTurnKeys.forwardSign(c) < 0);
                row.setKeyActive(InputRow.Key.A, NoTurnKeys.strafeSign(c) > 0);
                row.setKeyActive(InputRow.Key.D, NoTurnKeys.strafeSign(c) < 0);
                row.setKeyActive(InputRow.Key.SPRINT, r.sprint[t]);
            }
        }
        if (r.yaws != null) engine.writeYaws(inputData.getRows(), start, r.yaws, freeStartYaw);
        Vec3dCore cur = runner.getStartPosition();
        runner.setStartPosition(new Vec3dCore(r.startX, cur.y, r.startZ));
        saveController.markDirty();
        onUserChange.accept(start);
    }
}
