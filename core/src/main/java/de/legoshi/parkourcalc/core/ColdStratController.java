package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdBeamSolver;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdProblem;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdStratFinder;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.LineSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.coldfinder.ColdStratWidget;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public final class ColdStratController {

    private static final InputRow.Key[] MOVE_KEYS = {
            InputRow.Key.W, InputRow.Key.A, InputRow.Key.S, InputRow.Key.D,
            InputRow.Key.SPRINT, InputRow.Key.JUMP, InputRow.Key.SNEAK};

    private final AngleSolverState angleSolverState;
    private final BoxController boxController;
    private final SimulationRunner runner;
    private final SaveController saveController;
    private final InputData inputData;
    private final IntConsumer onUserChange;
    private final ColdStratWidget widget;

    public ColdStratController(AngleSolverState angleSolverState, BoxController boxController,
                               SimulationRunner runner, SaveController saveController,
                               InputData inputData, IntConsumer onUserChange,
                               java.util.function.Consumer<Boolean> freeYawWindow) {
        this.angleSolverState = angleSolverState;
        this.boxController = boxController;
        this.runner = runner;
        this.saveController = saveController;
        this.inputData = inputData;
        this.onUserChange = onUserChange;
        this.widget = new ColdStratWidget(this::inspect, this::run, this::apply, freeYawWindow);
    }

    public ColdStratWidget widget() {
        return widget;
    }

    private SaveFile buildFile() {
        if (saveController.getSaveStore() == null || inputData.size() == 0) return null;
        return SaveIO.buildSaveFile(saveController.getSaveStore(), inputData, runner.getStartPosition(),
                runner.getStartVelocity(), runner.getStartYaw(), runner.getStartPitch(),
                angleSolverState, boxController.getStates(), true);
    }

    private Problem inspect() {
        SaveFile file = buildFile();
        if (file == null) return Problem.error("Load a recording and set the solve segment first.");
        ColdProblem p;
        try {
            p = ColdProblem.fromSave(file);
        } catch (RuntimeException ex) {
            return Problem.error(friendly(ex));
        }
        boolean hasEnd = !p.tailWalls.isEmpty() || !p.momentumWalls.isEmpty();
        return Problem.ok(file, p, hasEnd);
    }

    private static String friendly(RuntimeException ex) {
        String m = ex.getMessage();
        if (m == null) return ex.toString();
        if (m.contains("free start")) return "Set a start-position constraint at the first tick.";
        if (m.contains("no free X/Z start rect")) return "Set a start-position constraint at the first tick.";
        if (m.contains("empty segment")) return "Set the solve start and goal ticks (goal after start).";
        return m;
    }

    private ColdStratJob run(SaveFile file, ColdStratFinder.Request req) {
        if (file == null) return null;
        return new ColdStratJob(file, req);
    }

    private void apply(ColdResult r) {
        if (r == null || !r.solved()) return;
        KeyLine line = r.line;
        ColdProblem p = line.problem;
        double[] gf = LineSpec.build(line, r.yaws[0], r.startX, r.startZ).asScenario().toGameFacings(r.yaws);
        List<InputRow> rows = inputData.getRows();
        List<InputRow> src = line.toRows();
        for (int t = p.startTick; t < p.landingTick && t < rows.size() && t < src.size(); t++) {
            int seg = t - p.startTick;
            InputRow row = rows.get(t);
            InputRow s = src.get(t);
            for (InputRow.Key k : MOVE_KEYS) row.setKeyActive(k, s.isKeyActive(k));
            row.setYawLocked(true);
            row.setYaw((float) gf[seg]);
        }
        Vec3dCore cur = runner.getStartPosition();
        double startY = cur != null ? cur.y : 0.0;
        runner.setStartPosition(new Vec3dCore(r.startX, startY, r.startZ));
        runner.setStartYaw((float) gf[0]);
        onUserChange.accept(-1);
    }

    public static final class Problem {
        public final boolean ok;
        public final String error;
        public final boolean hasEndConstraint;
        public final SaveFile file;
        public final int startTick;
        public final int landingTick;
        public final int[] segStart;
        public final int[] segPress;

        private Problem(boolean ok, String error, boolean hasEndConstraint, SaveFile file,
                        int startTick, int landingTick, int[] segStart, int[] segPress) {
            this.ok = ok;
            this.error = error;
            this.hasEndConstraint = hasEndConstraint;
            this.file = file;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.segStart = segStart;
            this.segPress = segPress;
        }

        static Problem error(String msg) {
            return new Problem(false, msg, false, null, 0, 0, new int[0], new int[0]);
        }

        static Problem ok(SaveFile file, ColdProblem p, boolean hasEnd) {
            List<ColdStratFinder.Segment> segs = ColdStratFinder.segmentsOf(p);
            int[] start = new int[segs.size()];
            int[] press = new int[segs.size()];
            for (int i = 0; i < segs.size(); i++) {
                start[i] = segs.get(i).startTick;
                press[i] = segs.get(i).pressTick;
            }
            return new Problem(true, null, hasEnd, file, p.startTick, p.landingTick, start, press);
        }

        public int segmentCount() {
            return segPress.length;
        }
    }

    public static final class ColdStratJob implements ColdBeamSolver.ProgressSink {
        private final Thread thread;
        private final AtomicBoolean cancel = new AtomicBoolean(false);
        private final long startNanos = System.nanoTime();

        private volatile boolean running = true;
        private volatile boolean finished;
        private volatile ColdStratFinder.Result result;
        private volatile String status = "running";
        private volatile int built = -1;
        private volatile int done;
        private volatile int total;
        private volatile int feasible;

        ColdStratJob(final SaveFile file, final ColdStratFinder.Request req) {
            this.thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        result = ColdStratFinder.find(file, req, ColdStratJob.this, cancel);
                        status = cancel.get() ? "cancelled" : "done";
                    } catch (Throwable ex) {
                        String m = ex.getMessage();
                        status = "error: " + (m != null ? m : ex.getClass().getSimpleName());
                    } finally {
                        running = false;
                        finished = true;
                    }
                }
            }, "cold-strat");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        public void cancel() {
            cancel.set(true);
        }

        public boolean isRunning() {
            return running;
        }

        public boolean isFinished() {
            return finished;
        }

        public ColdStratFinder.Result result() {
            return result;
        }

        public String status() {
            return status;
        }

        public int built() {
            return built;
        }

        public int done() {
            return done;
        }

        public int total() {
            return total;
        }

        public int feasible() {
            return feasible;
        }

        public double elapsedSeconds() {
            return (System.nanoTime() - startNanos) / 1e9;
        }

        @Override
        public void onBuildCycle(int cycleIndex, int cycleCount, long extensions, int survivors, long tailCut) {
        }

        @Override
        public void onBuilt(int candidates, long tailCut) {
            built = candidates;
            total = candidates;
        }

        @Override
        public void onCertify(int done, int total, int certified, long elapsedMs) {
            this.done = done;
            this.total = total;
            this.feasible = certified;
        }

        @Override
        public void onSolved(String sig, int idx, int certified, long elapsedMs) {
        }
    }
}
