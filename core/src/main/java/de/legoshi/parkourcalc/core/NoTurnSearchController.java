package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnFinder;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratfinderWindow;
import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

public final class NoTurnSearchController implements StratfinderWindow.Host {

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
    private volatile String stage = "";
    private volatile double fraction;
    private volatile String outcome = "";
    private volatile List<NoTurnResult> ranked = Collections.emptyList();
    private volatile boolean reapplySelected;
    private volatile NoTurnResult selected;
    private volatile NoTurnProblem problem;
    private long startNanos;
    private long endNanos;
    private int startTick;
    private boolean maximize;

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
    public boolean isSearching() {
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
    public void start() {
        if (thread != null) return;
        if (engine.isSolving() || otherSolveRunning.getAsBoolean()) {
            pushMessage.accept("Finish the current solve first", HudMessageStyle.COLOR_WARN);
            return;
        }
        if (!mc.isReady()) return;
        JumpSpec spec = engine.debugBuildSpec();
        NoTurnProblem p = NoTurnProblem.from(spec, model);
        if (p.issue != null) {
            outcome = p.issue;
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
        startTick = state.getStartTick();
        maximize = p.objective.sense == Objective.Sense.MAX;
        cancel.set(false);
        done = false;
        outcome = "";
        stage = "starting";
        fraction = 0.0;
        startNanos = System.nanoTime();
        endNanos = startNanos;
        SolverGraph graph = BuiltinGraphs.optimize(6);
        StructurePoolDriver.Config poolCfg = new StructurePoolDriver.Config();
        poolCfg.allowJa = true;
        poolCfg.certifyBudgetNanos = 4_000_000_000L;
        poolCfg.extraCertify = 200;
        poolCfg.extraCertifyNanos = 45_000_000_000L;
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
        NoTurnFinder finder = new NoTurnFinder(model, new NoTurnFinder.Config(), cancel, new NoTurnFinder.Progress() {
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
        thread = new Thread(() -> {
            try {
                NoTurnResult r = driver.run(p, graph);
                if (r == null && !cancel.get()) finder.run(p, graph);
            } catch (Throwable t) {
                t.printStackTrace();
                stage = "error: " + t;
            } finally {
                done = true;
            }
        }, "noturn-finder");
        thread.setDaemon(true);
        thread.start();
        pushMessage.accept("No-turn search started", HudMessages.COLOR_DEFAULT);
    }

    @Override
    public void cancel() {
        if (thread == null) return;
        cancel.set(true);
        stage = "cancelling";
        pushMessage.accept("No-turn search cancelled", HudMessageStyle.COLOR_WARN);
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
        List<NoTurnResult> list = ranked;
        if (cancel.get()) {
            outcome = list.isEmpty() ? "cancelled, nothing found" : "cancelled, " + list.size() + " kept";
            return;
        }
        if (list.isEmpty()) {
            outcome = "none found";
            pushMessage.accept("No-turn: none found", HudMessageStyle.COLOR_DANGER);
            return;
        }
        outcome = list.size() + " found";
        if (selected == null) {
            select(list.get(0));
            pushMessage.accept("No-turn applied · " + list.get(0).describe(), HudMessageStyle.COLOR_OK);
        }
    }

    private void onStage(String s, double f) {
        stage = s;
        if (f > fraction || f >= 1.0) fraction = Math.min(1.0, f);
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
                if (!StructurePoolDriver.betterResult(maximize, r, old)) return;
                results.remove(same);
                if (selected == old) {
                    selected = r;
                    reapplySelected = true;
                }
            }
            int at = results.size();
            for (int i = 0; i < results.size(); i++) {
                if (StructurePoolDriver.betterResult(maximize, r, results.get(i))) {
                    at = i;
                    break;
                }
            }
            results.add(at, r);
            ranked = Collections.unmodifiableList(new ArrayList<>(results));
        }
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
            NoTurnProblem p = problem;
            if (p != null && p.base.forwardInputPerTick != null && p.base.strafeInputPerTick != null) {
                boolean holdSprint = r.airCombo >= 0 && r.sprint.length > 0 && r.sprint[r.sprint.length - 1];
                for (int t = r.combos.length; t < p.n; t++) {
                    int idx = start + t;
                    if (idx < 0 || idx >= inputData.size()) continue;
                    InputRow row = inputData.get(idx);
                    float fwd = r.airCombo >= 0 ? NoTurnKeys.forwardInput(r.airCombo) : p.base.forwardInputPerTick[t];
                    float strafe = r.airCombo >= 0 ? NoTurnKeys.strafeInput(r.airCombo) : p.base.strafeInputPerTick[t];
                    row.setKeyActive(InputRow.Key.W, fwd > 1.0e-4f);
                    row.setKeyActive(InputRow.Key.S, fwd < -1.0e-4f);
                    row.setKeyActive(InputRow.Key.A, strafe > 1.0e-4f);
                    row.setKeyActive(InputRow.Key.D, strafe < -1.0e-4f);
                    row.setKeyActive(InputRow.Key.SPRINT, r.airCombo >= 0 ? holdSprint : p.base.sprintAt(t));
                }
            }
        }
        if (r.yaws != null) {
            AngleSolverEngine.writeYawRows(inputData.getRows(), start, r.yaws, (float) boxController.getYaw(start));
        }
        Vec3dCore cur = runner.getStartPosition();
        runner.setStartPosition(new Vec3dCore(r.startX, cur.y, r.startZ));
        saveController.markDirty();
        onUserChange.accept(start);
    }
}
