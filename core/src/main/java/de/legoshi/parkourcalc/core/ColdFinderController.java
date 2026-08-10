package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdBeamSolver;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdProblem;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.LineSpec;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.coldfinder.ColdFinderWidget;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public final class ColdFinderController {

    private static final InputRow.Key[] MOVE_KEYS = {
            InputRow.Key.W, InputRow.Key.A, InputRow.Key.S, InputRow.Key.D,
            InputRow.Key.SPRINT, InputRow.Key.JUMP, InputRow.Key.SNEAK};

    private final AngleSolverState angleSolverState;
    private final BoxController boxController;
    private final SimulationRunner runner;
    private final SaveController saveController;
    private final InputData inputData;
    private final IntConsumer onUserChange;
    private final ColdFinderWidget widget;

    public ColdFinderController(AngleSolverState angleSolverState, BoxController boxController,
                                SimulationRunner runner, SaveController saveController,
                                InputData inputData, IntConsumer onUserChange) {
        this.angleSolverState = angleSolverState;
        this.boxController = boxController;
        this.runner = runner;
        this.saveController = saveController;
        this.inputData = inputData;
        this.onUserChange = onUserChange;
        this.widget = new ColdFinderWidget(this::inspect, this::run, this::applyResult);
    }

    public ColdFinderWidget widget() {
        return widget;
    }

    private SaveFile buildFile() {
        FileSystemSaveStore store = saveController.getSaveStore();
        if (store == null || inputData.size() == 0) return null;
        return SaveIO.buildSaveFile(store, inputData, runner.getStartPosition(), runner.getStartVelocity(),
                runner.getStartYaw(), runner.getStartPitch(), angleSolverState, boxController.getStates(), true);
    }

    private ProblemView inspect() {
        SaveFile file = buildFile();
        if (file == null) return ProblemView.error("Load a recording and set the solve segment first.");
        try {
            ColdProblem p = ColdProblem.fromSave(file);
            return ProblemView.ok(p);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            return ProblemView.error(msg != null ? msg : ex.toString());
        }
    }

    private ColdFinderJob run(ColdBeamSolver.Config cfg) {
        SaveFile file = buildFile();
        if (file == null) return null;
        try {
            ColdProblem.fromSave(file);
        } catch (RuntimeException ex) {
            return null;
        }
        return new ColdFinderJob(file, cfg);
    }

    private void applyResult(ColdResult r) {
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
        Vec3dCore curStart = runner.getStartPosition();
        double startY = curStart != null ? curStart.y : 0.0;
        runner.setStartPosition(new Vec3dCore(r.startX, startY, r.startZ));
        runner.setStartYaw((float) gf[0]);
        onUserChange.accept(-1);
    }

    /** Detected problem shape for the UI, or an error message when the segment is not cold-searchable. */
    public static final class ProblemView {
        public final boolean ok;
        public final String error;
        public final int startTick;
        public final int landingTick;
        public final int numTicks;
        public final int[] cycleStartSeg;
        public final int[] cyclePressSeg;

        private ProblemView(boolean ok, String error, int startTick, int landingTick, int numTicks,
                            int[] cycleStartSeg, int[] cyclePressSeg) {
            this.ok = ok;
            this.error = error;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.cycleStartSeg = cycleStartSeg;
            this.cyclePressSeg = cyclePressSeg;
        }

        static ProblemView error(String msg) {
            return new ProblemView(false, msg, 0, 0, 0, new int[0], new int[0]);
        }

        static ProblemView ok(ColdProblem p) {
            int[] press = p.pressSegTicks;
            int[] start = new int[press.length];
            int prev = -1;
            for (int i = 0; i < press.length; i++) {
                start[i] = prev + 1;
                prev = press[i];
            }
            return new ProblemView(true, null, p.startTick, p.landingTick, p.numTicks, start, press.clone());
        }

        public int cycleCount() {
            return cyclePressSeg.length;
        }
    }

    /** A running cold solve: owns the worker thread, streams progress, and holds the solved result. */
    public static final class ColdFinderJob implements ColdBeamSolver.ProgressSink {
        private final Thread thread;
        private final AtomicBoolean cancel = new AtomicBoolean(false);
        private final long startNanos = System.nanoTime();

        private volatile boolean running = true;
        private volatile boolean finished;
        private volatile ColdResult result;
        private volatile String status = "running";
        private volatile String cycleLine = "";
        private volatile int candidates = -1;
        private volatile int done;
        private volatile int total;
        private volatile int certified;
        private volatile long certifyMs;

        ColdFinderJob(final SaveFile file, final ColdBeamSolver.Config cfg) {
            this.thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ColdResult r = ColdBeamSolver.solve(file, cfg, ColdFinderJob.this, cancel);
                        result = r;
                        if (r != null && r.solved()) {
                            status = "solved";
                        } else if (cancel.get()) {
                            status = "cancelled";
                        } else {
                            status = "no solution";
                        }
                    } catch (Throwable ex) {
                        String m = ex.getMessage();
                        status = "error: " + (m != null ? m : ex.getClass().getSimpleName());
                    } finally {
                        running = false;
                        finished = true;
                    }
                }
            }, "cold-finder");
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

        public ColdResult result() {
            return result;
        }

        public String status() {
            return status;
        }

        public String cycleLine() {
            return cycleLine;
        }

        public int candidates() {
            return candidates;
        }

        public int done() {
            return done;
        }

        public int total() {
            return total;
        }

        public int certified() {
            return certified;
        }

        public double elapsedSeconds() {
            return (System.nanoTime() - startNanos) / 1e9;
        }

        @Override
        public void onBuildCycle(int cycleIndex, int cycleCount, long extensions, int survivors, long tailCut) {
            cycleLine = "cycle " + (cycleIndex + 1) + "/" + cycleCount + " · " + survivors + " survivors";
        }

        @Override
        public void onBuilt(int candidates, long tailCut) {
            this.candidates = candidates;
            this.total = candidates;
            this.cycleLine = candidates + " candidates";
        }

        @Override
        public void onCertify(int done, int total, int certified, long elapsedMs) {
            this.done = done;
            this.total = total;
            this.certified = certified;
            this.certifyMs = elapsedMs;
        }

        @Override
        public void onSolved(String sig, int idx, int certified, long elapsedMs) {
            this.certified = certified;
            this.certifyMs = elapsedMs;
        }
    }
}
