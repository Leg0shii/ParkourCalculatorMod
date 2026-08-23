package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksControls;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksFilter;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksRows;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksSearch;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksSettings;
import de.legoshi.parkourcalc.core.anglesolver.runticks.StepTimeouts;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ObjIntConsumer;

public final class RunTicksController implements RunTicksControls {

    private static final InputRow.Key[] INHERITED_KEYS = {
            InputRow.Key.W, InputRow.Key.A, InputRow.Key.S, InputRow.Key.D, InputRow.Key.SPRINT
    };

    private enum Phase { IDLE, STEP, FINAL }

    private final AngleSolverState state;
    private final AngleSolverEngine engine;
    private final InputData inputs;
    private final ConstraintSelection constraintSelection;
    private final HudMessages hud;
    private final Runnable runSimulation;
    private final Runnable markDirty;
    private final ObjIntConsumer<String> pushMessage;

    private Phase phase = Phase.IDLE;
    private boolean cancelRequested;

    private DocumentSnapshot originalDocument;
    private DocumentSnapshot searchBase;
    private List<Integer> jumpTicks = new ArrayList<Integer>();
    private RunTicksSearch<List<InputRow>> search;
    private StepTimeouts timeouts;

    private long searchStartMs;
    private long searchElapsedMs;
    private long stepStartMs;
    private int stepTimeoutMs = RunTicksSettings.DEFAULT_TIMEOUT_MS;

    private int[] bestCombo;
    private List<InputRow> bestRows;
    private SolveResult bestResult;
    private double bestObjective;

    private int furthestDepth;

    public RunTicksController(AngleSolverState state, AngleSolverEngine engine, InputData inputs,
                              ConstraintSelection constraintSelection, HudMessages hud,
                              Runnable runSimulation, Runnable markDirty, ObjIntConsumer<String> pushMessage) {
        this.state = state;
        this.engine = engine;
        this.inputs = inputs;
        this.constraintSelection = constraintSelection;
        this.hud = hud;
        this.runSimulation = runSimulation;
        this.markDirty = markDirty;
        this.pushMessage = pushMessage;
    }

    @Override
    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    @Override
    public int liveTimeoutMs() {
        return timeouts != null ? timeouts.live() : settings().getTimeoutMs();
    }

    @Override
    public void cancel() {
        if (phase != Phase.IDLE) cancelRequested = true;
    }

    @Override
    public void start() {
        if (phase != Phase.IDLE) return;
        RunTicksSettings cfg = settings();
        if (!cfg.isEnabled()) {
            engine.solve();
            return;
        }

        cancelRequested = false;
        originalDocument = DocumentSnapshot.capture(inputs, state);
        boolean removedRows = dropPreviousRunTicks();
        jumpTicks = findJumpTicks();
        if (jumpTicks.isEmpty()) {
            if (removedRows) {
                originalDocument.restoreInto(inputs, state);
                runSimulation.run();
            }
            engine.solve();
            return;
        }

        searchBase = DocumentSnapshot.capture(inputs, state);
        search = new RunTicksSearch<List<InputRow>>(jumpTicks.size(), cfg.getMaxTicks(), cfg.isMinimize(),
                this::allowsExtraTicks);
        timeouts = new StepTimeouts(cfg, jumpTicks.size());
        bestCombo = null;
        bestRows = null;
        bestResult = null;
        bestObjective = state.getGoal() == AngleSolverState.Goal.MAX
                ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        furthestDepth = 0;
        searchStartMs = now();
        phase = Phase.STEP;
        advance();
    }

    public void poll() {
        if (phase == Phase.STEP) pollStep();
        else if (phase == Phase.FINAL) pollFinalSolve();
    }

    public void reset() {
        phase = Phase.IDLE;
        cancelRequested = false;
        search = null;
        timeouts = null;
        originalDocument = null;
        searchBase = null;
        jumpTicks = new ArrayList<Integer>();
        bestCombo = null;
        bestRows = null;
        bestResult = null;
        furthestDepth = 0;
        hud.clearStatus();
    }

    private RunTicksSettings settings() {
        return state.getRunTicks();
    }

    private void pollStep() {
        if (cancelRequested) {
            engine.cancel();
            finishSearch();
            return;
        }
        engine.poll();
        if (engine.isSolving()) {
            if (elapsedMs(stepStartMs) <= stepTimeoutMs) return;
            engine.cancel();
        }

        RunTicksSearch.Node<List<InputRow>> node = search.current();
        SolveResult result = state.getResult();
        if (result != null && result.isSuccess()) {
            engine.apply();
            timeouts.recordSuccess(node.depth(), elapsedMs(stepStartMs));
            List<InputRow> solvedRows = copyRows();
            furthestDepth = Math.max(furthestDepth, node.depth());
            if (node.depth() == search.jumpCount() && result.hasObjective()
                    && improvesBest(result.getObjectiveValue())) {
                bestObjective = result.getObjectiveValue();
                bestCombo = node.combo().clone();
                bestRows = solvedRows;
                bestResult = result;
            }
            search.recordSuccess(solvedRows);
        } else {
            search.recordFailure();
        }
        advance();
    }

    private boolean improvesBest(double value) {
        if (bestCombo == null) return true;
        return state.getGoal() == AngleSolverState.Goal.MAX ? value > bestObjective : value < bestObjective;
    }

    private void advance() {
        while (!search.hasNext()) {
            if (cancelRequested || bestCombo != null || !search.nextRung()) {
                finishSearch();
                return;
            }
        }
        RunTicksSearch.Node<List<InputRow>> node = search.take();
        stepTimeoutMs = timeouts.forDepth(node.depth());
        publishProgress();
        applyStep(node);
        runSimulation.run();
        stepStartMs = now();
        engine.solve(AngleSolverState.Effort.FAST);
        phase = Phase.STEP;
    }

    private void finishSearch() {
        searchElapsedMs = elapsedMs(searchStartMs);
        hud.clearStatus();
        state.setApplyDeviation(null, null);

        if (bestCombo != null) {
            applyFinal(bestCombo, bestRows);
            runSimulation.run();
            if (cancelRequested) {
                phase = Phase.IDLE;
                publishSolved(bestResult);
                return;
            }
            stepTimeoutMs = Math.max(settings().getTimeoutMs(), timeouts.forDepth(search.jumpCount()));
            stepStartMs = now();
            engine.solve(AngleSolverState.Effort.FAST);
            phase = Phase.FINAL;
            return;
        }

        phase = Phase.IDLE;
        originalDocument.restoreInto(inputs, state);
        runSimulation.run();

        SolveResult failed = engine.diagnoseCurrentPath();
        if (failed == null) {
            failed = new SolveResult(false, 0, enabledConstraintCount(),
                    state.getStartTick() + 1, state.getLandingTick() + 1);
        }
        failed.setSolver(cancelRequested ? "Run ticks (cancelled)" : "Run ticks");
        failed.addDetail("Run ticks", cancelRequested
                ? "Cancelled, path restored" : "No solution, path restored");
        failed.addDetail("Jumps solved", furthestDepth + "/" + search.jumpCount());
        addSearchDetails(failed);
        state.setResult(failed);
        pushMessage.accept((cancelRequested ? "Cancelled · " : "No solution · ")
                + "path restored", HudMessageStyle.COLOR_DANGER);
    }

    private void pollFinalSolve() {
        engine.poll();
        if (engine.isSolving()) {
            if (!cancelRequested && elapsedMs(stepStartMs) <= stepTimeoutMs) return;
            engine.cancel();
        }
        phase = Phase.IDLE;
        hud.clearStatus();

        SolveResult result = state.getResult();
        if (result != null && result.isSuccess() && !result.getYaws().isEmpty()) {
            engine.apply();
            runSimulation.run();
        } else {
            result = bestResult;
            if (result != null) state.setResult(result);
        }
        publishSolved(result);
    }

    private void publishSolved(SolveResult result) {
        markDirty.run();
        if (result == null) {
            pushMessage.accept("Run ticks · no result", HudMessageStyle.COLOR_DANGER);
            return;
        }
        result.addDetail("Run ticks", cancelRequested
                ? "Cancelled (showing best found)" : "Finished (found solution)");
        addSearchDetails(result);
        int extraTicks = 0;
        for (int extra : bestCombo) extraTicks += extra;
        pushMessage.accept((cancelRequested ? "Solved (cancelled) · +" : "Solved · +")
                + extraTicks + " run ticks", HudMessageStyle.COLOR_OK);
    }

    private void addSearchDetails(SolveResult result) {
        result.addDetail("Run ticks time", String.format(Locale.ROOT, "%.2f s", searchElapsedMs / 1000.0));
        result.addDetail("Successful solves", search.successes() + "/" + search.steps()
                + " (" + search.fullSolutions() + " full)");
    }

    private void publishProgress() {
        int percent = (int) Math.min(99, Math.max(0, search.progress() * 100));
        String elapsed = String.format(Locale.ROOT, " (%.1fs)", elapsedMs(searchStartMs) / 1000.0);
        String text = search.isMinimizing()
                ? "Run ticks (min " + search.target() + "/" + search.maxTicks() + "t) · " + percent + "%" + elapsed
                : "Run ticks · " + percent + "%" + elapsed;
        hud.setStatus(text, HudMessages.COLOR_DEFAULT);
    }

    private boolean dropPreviousRunTicks() {
        List<Integer> removed = new ArrayList<Integer>();
        for (int tick = state.getLandingTick(); tick >= state.getStartTick(); tick--) {
            if (tick < 0 || tick >= inputs.size()) continue;
            if (RunTicksRows.isRunTick(inputs.get(tick), state.tickConstraintsOrNull(tick))) removed.add(tick);
        }
        if (removed.isEmpty()) return false;
        inputs.removeRows(removed);
        state.onRowsRemoved(removed);
        return true;
    }

    private List<Integer> findJumpTicks() {
        List<Integer> ticks = new ArrayList<Integer>();
        int limit = Math.min(inputs.size() - 1, state.getLandingTick());
        for (int tick = Math.max(0, state.getStartTick()); tick <= limit; tick++) {
            if (!inputs.get(tick).isKeyActive(InputRow.Key.JUMP)) continue;
            if (tick == 0 || !inputs.get(tick - 1).isKeyActive(InputRow.Key.JUMP)) ticks.add(tick);
        }
        return ticks;
    }

    private void applyStep(RunTicksSearch.Node<List<InputRow>> node) {
        int totalExtra = rebuild(node.combo(), node.depth());
        List<InputRow> parentRows = node.payload();
        if (parentRows != null) {
            int shift = 0;
            for (int k = 0; k < node.depth() - 1; k++) shift += node.combo()[k];
            int insertedAt = jumpTicks.get(node.depth() - 1) + shift;
            int inserted = node.combo()[node.depth() - 1];
            for (int i = 0; i < parentRows.size(); i++) {
                copyChoices(parentRows.get(i), i < insertedAt ? i : i + inserted);
            }
        }
        state.setStartTick(searchBase.startTick);
        if (node.depth() < jumpTicks.size()) {
            int nextJumpTick = jumpTicks.get(node.depth()) + node.sum();
            state.setLandingTick(Math.max(searchBase.startTick, nextJumpTick - 1));
        } else {
            state.setLandingTick(searchBase.landingTick + totalExtra);
        }
    }

    private void applyFinal(int[] combo, List<InputRow> solvedRows) {
        int totalExtra = rebuild(combo, jumpTicks.size());
        if (solvedRows != null) {
            for (int i = 0; i < solvedRows.size(); i++) copyChoices(solvedRows.get(i), i);
        }
        state.setStartTick(searchBase.startTick);
        state.setLandingTick(searchBase.landingTick + totalExtra);
    }

    private void copyChoices(InputRow source, int targetIndex) {
        if (targetIndex < 0 || targetIndex >= inputs.size()) return;
        InputRow target = inputs.get(targetIndex);
        target.setYaw(source.getYaw());
        target.setYawLocked(source.isYawLocked());
        for (InputRow.Key key : INHERITED_KEYS) target.setKeyActive(key, source.isKeyActive(key));
    }

    private int rebuild(int[] combo, int assignedJumps) {
        constraintSelection.clear();
        searchBase.restoreInto(inputs, state);

        int totalExtra = 0;
        for (int jumpIndex = Math.min(assignedJumps, jumpTicks.size()) - 1; jumpIndex >= 0; jumpIndex--) {
            int extra = combo[jumpIndex];
            if (extra <= 0) continue;
            int jumpTick = jumpTicks.get(jumpIndex);
            totalExtra += extra;
            state.onRowsInserted(jumpTick, extra);

            int templateIndex = Math.max(0, jumpTick - 1);
            TickConstraints template = state.tickConstraintsOrNull(templateIndex);
            for (int k = 0; k < extra; k++) {
                int newTick = jumpTick + k;
                InputRow row = inputs.get(templateIndex).copy();
                row.setKeyActive(InputRow.Key.JUMP, false);
                row.setKeyActive(InputRow.Key.W, true);
                row.setKeyActive(InputRow.Key.SPRINT, true);
                inputs.insertRow(newTick, row);
                if (template != null) {
                    List<Constraint> target = state.tickConstraints(newTick).getConstraints();
                    for (Constraint c : template.getConstraints()) {
                        if (c.isRange()) target.add(c.copy());
                    }
                }
                state.tickConstraints(newTick).getOverride().setSlipperiness(Slipperiness.DEFAULT);
            }
            state.tickConstraints(jumpTick + extra).getOverride().setSlipperiness(Slipperiness.DEFAULT);
        }
        return totalExtra;
    }

    private boolean allowsExtraTicks(int jumpIndex, int extraTicks) {
        TickConstraints tc = searchBase.constraintsAt(jumpTicks.get(jumpIndex));
        return RunTicksFilter.allows(tc == null ? null : tc.getConstraints(), extraTicks);
    }

    private int enabledConstraintCount() {
        int n = 0;
        for (Integer tick : state.populatedTicks()) {
            if (tick < state.getStartTick() || tick > state.getLandingTick()) continue;
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) {
                if (c.isEnabled() && c.getField() != Constraint.Field.RT) n++;
            }
        }
        return n;
    }

    private List<InputRow> copyRows() {
        List<InputRow> copy = new ArrayList<InputRow>(inputs.size());
        for (InputRow row : inputs.getRows()) copy.add(row.copy());
        return copy;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long elapsedMs(long since) {
        return System.currentTimeMillis() - since;
    }

    private static final class DocumentSnapshot {
        final List<InputRow> rows = new ArrayList<InputRow>();
        final Map<Integer, TickConstraints> ticks = new LinkedHashMap<Integer, TickConstraints>();
        int startTick;
        int landingTick;

        static DocumentSnapshot capture(InputData inputs, AngleSolverState state) {
            DocumentSnapshot snapshot = new DocumentSnapshot();
            for (InputRow row : inputs.getRows()) snapshot.rows.add(row.copy());
            for (Integer tick : state.populatedTicks()) {
                TickConstraints source = state.tickConstraintsOrNull(tick);
                if (source == null) continue;
                TickConstraints copy = new TickConstraints();
                for (Constraint c : source.getConstraints()) copy.getConstraints().add(c.copy());
                copy.getOverride().copyFrom(source.getOverride());
                snapshot.ticks.put(tick, copy);
            }
            snapshot.startTick = state.getStartTick();
            snapshot.landingTick = state.getLandingTick();
            return snapshot;
        }

        TickConstraints constraintsAt(int tick) {
            return ticks.get(tick);
        }

        void restoreInto(InputData inputs, AngleSolverState state) {
            inputs.clear();
            for (InputRow row : rows) inputs.insertRow(inputs.size(), row.copy());

            state.clearConstraintsInRange(0, Integer.MAX_VALUE);
            for (Integer tick : state.populatedTicks()) {
                TickConstraints tc = state.tickConstraintsOrNull(tick);
                if (tc == null) continue;
                tc.getOverride().clearSlipperiness();
                tc.getOverride().clearMedium();
            }
            for (Map.Entry<Integer, TickConstraints> e : ticks.entrySet()) {
                TickConstraints target = state.tickConstraints(e.getKey());
                for (Constraint c : e.getValue().getConstraints()) target.getConstraints().add(c.copy());
                StateOverride source = e.getValue().getOverride();
                if (source.overridesSlipperiness()) target.getOverride().setSlipperiness(source.getSlipperiness());
                if (source.overridesMedium()) {
                    target.getOverride().setMedium(source.getMedium());
                    target.getOverride().setSoulsandCells(source.getSoulsandCells());
                }
            }
            state.setStartTick(startTick);
            state.setLandingTick(landingTick);
        }
    }
}
