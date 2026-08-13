package de.legoshi.parkourcalc.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.BlockStratFinder;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.DeriveChain;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.JumpArcs;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.ProblemDeriver;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.ProblemCompiler;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratFinder;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratLabels;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratMeasure;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratMeasurements;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratProblem;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratVariants;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.coldfinder.ColdStratWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ColdStratController {

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private final AngleSolverState angleSolverState;
    private final BoxController boxController;
    private final SimulationRunner runner;
    private final SaveController saveController;
    private final InputData inputData;
    private final ExactJumpModel forwardModel;
    private final MinecraftAccess mc;
    private final ColdStratWidget widget;
    private String lastAppliedJson;
    private StratProblem problem;
    private ProblemCompiler.Compilation compiled;
    private boolean problemDirty = true;

    public ColdStratController(AngleSolverState angleSolverState, BoxController boxController,
                               SimulationRunner runner, SaveController saveController,
                               InputData inputData, ExactJumpModel forwardModel, MinecraftAccess mc) {
        this.angleSolverState = angleSolverState;
        this.boxController = boxController;
        this.runner = runner;
        this.saveController = saveController;
        this.inputData = inputData;
        this.forwardModel = forwardModel;
        this.mc = mc;
        this.widget = new ColdStratWidget(this);
    }

    public ColdStratWidget widget() {
        return widget;
    }

    public boolean isTempActive() {
        return saveController.isTempActive();
    }

    public JumpInfo inspect() {
        FileSystemSaveStore store = saveController.getSaveStore();
        if (store == null || inputData.size() == 0) {
            return JumpInfo.empty("Load a recording and set the solve segment first.");
        }
        int st = angleSolverState.getStartTick();
        int lt = angleSolverState.getLandingTick();
        if (st < 0 || st >= boxController.size() || lt <= st) {
            return JumpInfo.notReady(st, lt, 0, "Set the solve start and goal ticks (goal after start).");
        }
        SaveFile file = buildFile();
        if (file == null) {
            return JumpInfo.empty("Load a recording and set the solve segment first.");
        }
        int constraints = countConstraints(file);
        double[] edge = VelocityMapController.objectiveEdge(angleSolverState);
        if (Double.isNaN(edge[0])) {
            return JumpInfo.notReady(st, lt, constraints,
                    "Set an end constraint (a landing target) before running.");
        }
        return JumpInfo.ready(st, lt, constraints);
    }

    public StratProblem problem() {
        if (problem == null) {
            problem = new StratProblem();
            problem.mcVersion = mcVersionOrDefault();
        }
        return problem;
    }

    public void problemEdited() {
        problemDirty = true;
    }

    public ProblemCompiler.Compilation compiledProblem() {
        StratProblem p = problem();
        if (p.start == null || p.segments.isEmpty()) {
            return null;
        }
        if (problemDirty || compiled == null) {
            compiled = ProblemCompiler.compile(p);
            problemDirty = false;
        }
        return compiled;
    }

    private String mcVersionOrDefault() {
        FileSystemSaveStore store = saveController.getSaveStore();
        String v = store != null ? store.getMcVersion() : null;
        return v != null && !v.isEmpty() ? v : "1.8.9";
    }

    public String syncProblemFromConstraints() {
        int[] presses = pressTicks();
        List<ProblemDeriver.Footprint> fps = footprints();
        StratProblem.Area start = startArea();
        List<TickState> states = boxController.getStates();
        double[] recordedY = new double[states.size()];
        boolean[] recordedGround = new boolean[states.size()];
        for (int i = 0; i < states.size(); i++) {
            TickState s = states.get(i);
            recordedY[i] = s != null && s.position != null ? s.position.y : Double.NaN;
            recordedGround[i] = s != null && s.onGround;
        }
        StratProblem prev = problem;
        ProblemDeriver.Result r = ProblemDeriver.derive(presses, fps, start, recordedY, recordedGround,
                mcVersionOrDefault(), prev != null ? prev.segments : null);
        if (r.error != null) {
            return r.error;
        }
        if (prev != null && prev.start != null && start != null
                && (int) Math.floor(prev.start.xLo) == (int) Math.floor(start.xLo)
                && (int) Math.floor(prev.start.zLo) == (int) Math.floor(start.zLo)) {
            r.problem.start.slipperiness = prev.start.slipperiness;
        }
        r.problem.userWalls = collectUserWalls();
        problem = r.problem;
        problemEdited();
        return null;
    }

    private List<StratProblem.Wall> collectUserWalls() {
        List<StratProblem.Wall> out = new ArrayList<StratProblem.Wall>();
        for (int tick : angleSolverState.populatedTicks()) {
            TickConstraints tc = angleSolverState.tickConstraintsOrNull(tick);
            if (tc == null) {
                continue;
            }
            for (Constraint c : tc.getConstraints()) {
                if (c.isRange() || c.isRelative() || !c.isEnabled()) {
                    continue;
                }
                if (c.getField() != Constraint.Field.X && c.getField() != Constraint.Field.Z) {
                    continue;
                }
                Constraint.Op op = c.getOp();
                if (op != Constraint.Op.GE && op != Constraint.Op.LE
                        && op != Constraint.Op.GT && op != Constraint.Op.LT) {
                    continue;
                }
                out.add(new StratProblem.Wall(tick, c.getField().name(), op.name(), c.getValue()));
            }
        }
        return out;
    }

    public String constraintSignature() {
        StringBuilder sb = new StringBuilder();
        for (int p : pressTicks()) {
            sb.append('p').append(p).append(';');
        }
        for (ProblemDeriver.Footprint f : footprints()) {
            sb.append('f').append(f.tick).append(':').append(f.xLo).append(',').append(f.xHi)
                    .append(',').append(f.zLo).append(',').append(f.zHi).append(',')
                    .append(f.surfaceY).append(';');
        }
        for (StratProblem.Wall w : collectUserWalls()) {
            sb.append('w').append(w.tick).append(':').append(w.field).append(w.op)
                    .append(w.value).append(';');
        }
        Vec3dCore pos = runner.getStartPosition();
        if (pos != null) {
            sb.append('s').append(pos.x).append(',').append(pos.y).append(',').append(pos.z);
        }
        return sb.toString();
    }

    private int[] pressTicks() {
        List<InputRow> rows = inputData.getRows();
        List<Integer> presses = new ArrayList<Integer>();
        boolean prev = false;
        for (int t = 0; t < rows.size(); t++) {
            boolean j = rows.get(t).isKeyActive(InputRow.Key.JUMP);
            if (j && !prev) {
                presses.add(t);
            }
            prev = j;
        }
        int[] out = new int[presses.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = presses.get(i);
        }
        return out;
    }

    private List<ProblemDeriver.Footprint> footprints() {
        List<ProblemDeriver.Footprint> out = new ArrayList<ProblemDeriver.Footprint>();
        for (int tick : angleSolverState.populatedTicks()) {
            TickConstraints tc = angleSolverState.tickConstraintsOrNull(tick);
            if (tc == null) {
                continue;
            }
            Constraint cx = null;
            Constraint cz = null;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isRange() || c.isRelative() || !c.isEnabled()) {
                    continue;
                }
                if (c.getField() == Constraint.Field.X && cx == null) {
                    cx = c;
                }
                if (c.getField() == Constraint.Field.Z && cz == null) {
                    cz = c;
                }
            }
            if (cx == null || cz == null) {
                continue;
            }
            Double sy = cx.getSurfaceY() != null ? cx.getSurfaceY() : cz.getSurfaceY();
            out.add(new ProblemDeriver.Footprint(tick, cx.getLo(), cx.getHi(),
                    cz.getLo(), cz.getHi(), sy));
        }
        Collections.sort(out, new Comparator<ProblemDeriver.Footprint>() {
            @Override
            public int compare(ProblemDeriver.Footprint a, ProblemDeriver.Footprint b) {
                return Integer.compare(a.tick, b.tick);
            }
        });
        return out;
    }

    private StratProblem.Area startArea() {
        Vec3dCore pos = runner.getStartPosition();
        if (pos == null) {
            return null;
        }
        SolidLookup lookup = mc != null && mc.isReady()
                ? (x, y, z) -> mc.isBlockSolid(x, y, z)
                : (x, y, z) -> false;
        StratProblem.Area cell = supportingBlock(pos.x, pos.y, pos.z, lookup);
        return new StratProblem.Area(cell.xLo, cell.xHi, pos.y - 1.0, pos.y,
                cell.zLo, cell.zHi, cell.label);
    }

    interface SolidLookup {
        boolean solid(int x, int y, int z);
    }

    static StratProblem.Area supportingBlock(double x, double y, double z, SolidLookup lookup) {
        int by = (int) Math.floor(y - 0.5);
        int bestX = (int) Math.floor(x);
        int bestZ = (int) Math.floor(z);
        double bestOverlap = -1.0;
        double hw = StratProblem.HALF_WIDTH;
        for (int cx = (int) Math.floor(x - hw); cx <= (int) Math.floor(x + hw); cx++) {
            for (int cz = (int) Math.floor(z - hw); cz <= (int) Math.floor(z + hw); cz++) {
                double ox = Math.min(cx + 1.0, x + hw) - Math.max(cx, x - hw);
                double oz = Math.min(cz + 1.0, z + hw) - Math.max(cz, z - hw);
                if (ox <= 1.0e-9 || oz <= 1.0e-9 || !lookup.solid(cx, by, cz)) {
                    continue;
                }
                double overlap = ox * oz;
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    bestX = cx;
                    bestZ = cz;
                }
            }
        }
        return StratProblem.Area.block(bestX, by, bestZ);
    }

    private static StratProblem.Area areaOf(BlockSelection b) {
        return new StratProblem.Area(b.box.min.x, b.box.max.x, b.box.min.y, b.box.max.y,
                b.box.min.z, b.box.max.z, b.coordLabel());
    }

    public Job start(Request req) {
        if (req == null || (!req.warm && !req.cold)) {
            return null;
        }
        prepareFind();
        StratFinder warm = null;
        if (req.warm) {
            SaveFile file = buildFile();
            if (file != null && inspect().ready) {
                FileSystemSaveStore store = saveController.getSaveStore();
                double[] edge = VelocityMapController.objectiveEdge(angleSolverState);
                boolean max = angleSolverState.getGoal() == AngleSolverState.Goal.MAX;
                StratVariants.Filter filter = new StratVariants.Filter(req.families, req.shape, true);
                int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
                warm = new StratFinder(file, forwardModel, store, req.budgetMs, edge[0], max,
                        filter, threads);
                warm.start();
            }
        }
        List<StratProblem.Area> world = worldObstacles(problem());
        ColdRun cold = null;
        if (req.cold) {
            ProblemCompiler.Compilation comp = compiledProblem();
            if (comp != null && !comp.specs.isEmpty()) {
                StratProblem copy = problem().copy();
                copy.collisions.addAll(world);
                BlockStratFinder.Config cfg = new BlockStratFinder.Config();
                cfg.budgetMs = Math.max(5_000L, req.coldBudgetMs);
                cold = new ColdRun(copy, cfg);
            }
        }
        if (warm == null && cold == null) {
            return null;
        }
        List<StratProblem.Area> gate = new ArrayList<StratProblem.Area>(problem().collisions);
        gate.addAll(world);
        return new CombinedJob(warm, cold, gate);
    }

    static final int OBSTACLE_MARGIN_XZ = 4;
    static final int OBSTACLE_MARGIN_Y = 4;

    private List<StratProblem.Area> worldObstacles(StratProblem p) {
        if (p == null || p.start == null) {
            return new ArrayList<StratProblem.Area>();
        }
        double xLo = p.start.xLo;
        double xHi = p.start.xHi;
        double yLo = p.start.yLo;
        double yHi = p.start.yHi;
        double zLo = p.start.zLo;
        double zHi = p.start.zHi;
        for (StratProblem.Segment s : p.segments) {
            for (StratProblem.Area a : s.landings) {
                xLo = Math.min(xLo, a.xLo);
                xHi = Math.max(xHi, a.xHi);
                yLo = Math.min(yLo, a.yLo);
                yHi = Math.max(yHi, a.yHi);
                zLo = Math.min(zLo, a.zLo);
                zHi = Math.max(zHi, a.zHi);
            }
        }
        return queryWorldBoxes(xLo, xHi, yLo, yHi, zLo, zHi);
    }

    private List<StratProblem.Area> queryWorldBoxes(double xLo, double xHi, double yLo, double yHi,
                                                    double zLo, double zHi) {
        List<StratProblem.Area> out = new ArrayList<StratProblem.Area>();
        if (mc == null || !mc.isReady()) {
            return out;
        }
        List<AABB> boxes = mc.getCollisionBoxes(
                (int) Math.floor(xLo) - OBSTACLE_MARGIN_XZ, (int) Math.floor(yLo) - OBSTACLE_MARGIN_Y,
                (int) Math.floor(zLo) - OBSTACLE_MARGIN_XZ, (int) Math.floor(xHi) + OBSTACLE_MARGIN_XZ,
                (int) Math.floor(yHi) + OBSTACLE_MARGIN_Y, (int) Math.floor(zHi) + OBSTACLE_MARGIN_XZ);
        for (AABB b : boxes) {
            out.add(new StratProblem.Area(b.min.x, b.max.x, b.min.y, b.max.y, b.min.z, b.max.z, "world"));
        }
        return out;
    }

    public String deriveSolverFromBlocks() {
        List<BlockSelection> mom = angleSolverState.getMomentumBlocks();
        List<BlockSelection> lands = angleSolverState.getLandBlocks();
        if (mom.isEmpty() || lands.isEmpty()) {
            return "No blocks are picked (picks reset when the game restarts)."
                    + " Close the panel (G), aim at the start block and press M, aim at each landing"
                    + " block in jump order and press K, then reopen and click this again."
                    + " M/K need Block capture enabled in Settings.";
        }
        List<InputRow> rows = inputData.getRows();
        if (rows.isEmpty()) {
            return "Load a recording with the run's key presses first.";
        }
        int st = Math.max(0, angleSolverState.getStartTick());
        List<Integer> presses = new ArrayList<Integer>();
        boolean prev = false;
        for (int r = st; r < rows.size(); r++) {
            boolean j = rows.get(r).isKeyActive(InputRow.Key.JUMP);
            if (j && !prev) {
                presses.add(r);
            }
            prev = j;
        }
        if (presses.size() != lands.size()) {
            return "The recording presses jump " + presses.size() + " time(s) from T" + (st + 1)
                    + " on, but " + lands.size() + " landing block(s) are picked; they must match.";
        }
        int[] pressArr = new int[presses.size()];
        double[] tops = new double[lands.size()];
        for (int i = 0; i < presses.size(); i++) {
            pressArr[i] = presses.get(i);
            tops[i] = lands.get(i).box.max.y;
        }
        boolean legacy = JumpArcs.legacyThreshold(mcVersionOrDefault());
        DeriveChain chain = DeriveChain.fromPresses(st, pressArr, mom.get(0).box.max.y, tops, legacy);
        if (chain.error != null) {
            return chain.error;
        }
        int landing = chain.landings[chain.landings.length - 1];
        if (rows.size() < landing) {
            return "The last jump lands on tick " + landing + " but the recording has only "
                    + rows.size() + " rows; add rows first.";
        }
        angleSolverState.setLandingTick(landing);
        int footprints = 0;
        footprints += footprintRange(st, pressArr[0], mom.get(0));
        for (int i = 0; i < pressArr.length; i++) {
            int until = i + 1 < pressArr.length ? pressArr[i + 1] : chain.landings[i];
            footprints += footprintRange(chain.landings[i], until, lands.get(i));
        }
        angleSolverState.clearResult();
        saveController.markDirty();
        return "Solver constrained: goal tick " + landing + ", " + footprints
                + " footprints. Solve as usual.";
    }

    private int footprintRange(int from, int to, BlockSelection b) {
        int n = 0;
        for (int t = from; t <= to; t++) {
            if (t < 0) {
                continue;
            }
            angleSolverState.setFootprint(t,
                    b.box.min.x - StratProblem.HALF_WIDTH, b.box.max.x + StratProblem.HALF_WIDTH,
                    b.box.min.z - StratProblem.HALF_WIDTH, b.box.max.z + StratProblem.HALF_WIDTH);
            n++;
        }
        return n;
    }


    public int divergenceTick(Row row) {
        if (row == null) {
            return -1;
        }
        return divergenceTick(row.path, row.startTick);
    }

    public int divergenceTick(ForwardPath path, int startTick) {
        if (path == null || path.posX == null) {
            return -1;
        }
        if (!liveMatchesLastApplied()) {
            return -1;
        }
        List<TickState> states = boxController.getStates();
        int n = Math.min(path.posX.length, states.size() - startTick);
        for (int i = 0; i < n; i++) {
            TickState st = states.get(startTick + i);
            if (st == null || st.position == null) {
                continue;
            }
            double d = Math.abs(st.position.x - path.posX[i]) + Math.abs(st.position.z - path.posZ[i]);
            if (path.posY != null) {
                d += Math.abs(st.position.y - path.posY[i]);
            }
            if (d > 1.0e-3) {
                return startTick + i;
            }
        }
        return -1;
    }

    public void apply(String json) {
        if (json == null) {
            return;
        }
        String cleaned = stripDerived(json);
        if (saveController.isTempActive() && !liveMatchesLastApplied()) {
            saveController.clearTempTrajectory();
        }
        saveController.beginTempTrajectory();
        saveController.applySnapshotJson(cleaned);
        lastAppliedJson = cleaned;
    }

    static String stripDerived(String json) {
        SaveFile f = SaveIO.parseSafe(json);
        if (f == null || f.angleSolver == null || f.angleSolver.ticks == null) {
            return json;
        }
        boolean changed = false;
        List<SaveFile.Tick> keep = new ArrayList<SaveFile.Tick>();
        for (SaveFile.Tick tk : f.angleSolver.ticks) {
            if (tk == null) {
                changed = true;
                continue;
            }
            if (tk.constraints != null) {
                int before = tk.constraints.size();
                tk.constraints.removeIf(c -> c != null && c.derived);
                changed |= tk.constraints.size() != before;
            }
            boolean hasContent = (tk.constraints != null && !tk.constraints.isEmpty())
                    || tk.override != null;
            if (hasContent) {
                keep.add(tk);
            } else {
                changed = true;
            }
        }
        if (!changed) {
            return json;
        }
        f.angleSolver.ticks = keep;
        return SaveIO.saveJson(f);
    }

    public boolean applyShifted(String snapshotJson, int edgeRow, int shift) {
        if (snapshotJson == null) {
            return false;
        }
        if (shift == 0) {
            apply(snapshotJson);
            return true;
        }
        SaveFile solved = SaveIO.parseSafe(snapshotJson);
        if (solved == null) {
            return false;
        }
        SaveFile shifted = StratMeasure.shiftedCopy(solved, edgeRow, shift);
        if (shifted == null) {
            return false;
        }
        apply(SaveIO.saveJson(shifted));
        return true;
    }

    public void reapplyOriginal() {
        if (!saveController.isTempActive()) {
            return;
        }
        if (liveMatchesLastApplied()) {
            saveController.restoreInitialTrajectory();
        } else {
            saveController.clearTempTrajectory();
        }
        lastAppliedJson = null;
    }

    public void keep() {
        saveController.clearTempTrajectory();
        lastAppliedJson = null;
    }

    private void prepareFind() {
        if (!saveController.isTempActive()) {
            return;
        }
        if (liveMatchesLastApplied()) {
            saveController.restoreInitialTrajectory();
        } else {
            saveController.clearTempTrajectory();
        }
        lastAppliedJson = null;
    }

    private boolean liveMatchesLastApplied() {
        if (lastAppliedJson == null) {
            return false;
        }
        SaveFile applied = SaveIO.parseSafe(lastAppliedJson);
        if (applied == null || applied.rows == null) {
            return false;
        }
        List<InputRow> rows = inputData.getRows();
        if (rows.size() != applied.rows.size()) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (!SaveIO.rowMatches(applied.rows.get(i), rows.get(i))) {
                return false;
            }
        }
        return true;
    }

    private SaveFile buildFile() {
        FileSystemSaveStore store = saveController.getSaveStore();
        if (store == null || inputData.size() == 0) {
            return null;
        }
        return SaveIO.buildSaveFile(store, inputData, runner.getStartPosition(), runner.getStartVelocity(),
                runner.getStartYaw(), runner.getStartPitch(), angleSolverState, boxController.getStates(), true);
    }

    private static int countConstraints(SaveFile file) {
        int n = 0;
        if (file.angleSolver == null || file.angleSolver.ticks == null) {
            return 0;
        }
        for (SaveFile.Tick t : file.angleSolver.ticks) {
            if (t == null || t.constraints == null) {
                continue;
            }
            for (SaveFile.Constraint c : t.constraints) {
                if (c != null && !c.disabled) {
                    n++;
                }
            }
        }
        return n;
    }

    public static final class JumpInfo {
        public final boolean loaded;
        public final boolean ready;
        public final String message;
        public final int startTick;
        public final int landingTick;
        public final int constraints;

        private JumpInfo(boolean loaded, boolean ready, String message, int startTick, int landingTick,
                         int constraints) {
            this.loaded = loaded;
            this.ready = ready;
            this.message = message;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.constraints = constraints;
        }

        static JumpInfo empty(String message) {
            return new JumpInfo(false, false, message, 0, 0, 0);
        }

        static JumpInfo notReady(int startTick, int landingTick, int constraints, String message) {
            return new JumpInfo(true, false, message, startTick, landingTick, constraints);
        }

        static JumpInfo ready(int startTick, int landingTick, int constraints) {
            return new JumpInfo(true, true, null, startTick, landingTick, constraints);
        }
    }

    public static final class Request {
        public final boolean warm;
        public final boolean cold;
        public final Set<String> families;
        public final StratVariants.Filter.Shape shape;
        public final int budgetMs;
        public final long coldBudgetMs;

        public Request(boolean warm, boolean cold, Set<String> families,
                       StratVariants.Filter.Shape shape, int budgetMs, long coldBudgetMs) {
            this.warm = warm;
            this.cold = cold;
            this.families = families;
            this.shape = shape;
            this.budgetMs = budgetMs;
            this.coldBudgetMs = coldBudgetMs;
        }
    }

    public static final class Row {
        public final String key;
        public final String label;
        public final String origin;
        public final String detail;
        public final double difficulty;
        public final StratMeasurements measurements;
        public final String snapshotJson;
        public final ForwardPath path;
        public final int startTick;
        public final boolean original;
        public final int corpusEntries;
        public final String corpusExample;
        public final double margin;
        public int pressLo = -1;
        public int pressHi = -1;
        public String[] windowSnapshots;

        Row(String key, String label, String origin, String detail, double difficulty,
            StratMeasurements measurements, String snapshotJson, ForwardPath path, int startTick,
            boolean original, int corpusEntries, String corpusExample, double margin) {
            this.key = key;
            this.label = label;
            this.origin = origin;
            this.detail = detail;
            this.difficulty = difficulty;
            this.measurements = measurements;
            this.snapshotJson = snapshotJson;
            this.path = path;
            this.startTick = startTick;
            this.original = original;
            this.corpusEntries = corpusEntries;
            this.corpusExample = corpusExample;
            this.margin = margin;
        }

        static Row fromWarm(StratFinder.Item it) {
            return new Row("w|" + it.label, StratLabels.display(it.label), "recording",
                    StratLabels.describe(it.label), it.difficulty, it.measurements,
                    it.appliedSnapshotJson, it.path, it.startTick, it.original,
                    it.corpusEntries, it.corpusExample, it.margin);
        }

        static Row fromCold(BlockStratFinder.Found f) {
            return new Row("c|" + f.label + "|" + f.scheduleLabel, f.label, "blocks",
                    "timing " + f.scheduleLabel, f.difficulty, f.measurements,
                    f.snapshotJson, f.path, 0, false, 0, null, Double.NaN);
        }
    }

    static int pickRepresentative(boolean[] measured, double[] difficulty) {
        int best = 0;
        for (int i = 1; i < measured.length; i++) {
            if (measured[i] != measured[best]) {
                if (measured[i]) {
                    best = i;
                }
                continue;
            }
            boolean bestNaN = Double.isNaN(difficulty[best]);
            boolean iNaN = Double.isNaN(difficulty[i]);
            if (bestNaN && !iNaN) {
                best = i;
            } else if (!iNaN && !bestNaN && difficulty[i] < difficulty[best]) {
                best = i;
            }
        }
        return best;
    }

    static Row mergedColdRow(List<BlockStratFinder.Found> members) {
        boolean[] measured = new boolean[members.size()];
        double[] difficulty = new double[members.size()];
        for (int i = 0; i < members.size(); i++) {
            measured[i] = members.get(i).measurements != null;
            difficulty[i] = members.get(i).difficulty;
        }
        BlockStratFinder.Found best = members.get(pickRepresentative(measured, difficulty));
        if (best.strat == null) {
            return Row.fromCold(best);
        }
        String label = BlockStratFinder.patternLabel(best.strat);
        Row row = new Row("c|" + label, label, "blocks", "timing " + best.scheduleLabel,
                best.difficulty, best.measurements, best.snapshotJson, best.path,
                0, false, 0, null, Double.NaN);
        row.pressLo = best.pressLo;
        row.pressHi = best.pressHi;
        row.windowSnapshots = best.windowSnapshots;
        return row;
    }

    public interface Job {
        boolean isRunning();

        boolean isFinished();

        int done();

        int total();

        int feasible();

        boolean canaryFailed();

        List<Row> items();

        List<String> notes();

        String coldProgress();

        double elapsedSeconds();

        void cancel();
    }

    private static final class ColdRun {
        final AtomicBoolean cancel = new AtomicBoolean(false);
        final List<BlockStratFinder.Found> found = new CopyOnWriteArrayList<BlockStratFinder.Found>();
        volatile List<String> notes = Collections.emptyList();
        volatile int specsRun;
        volatile int specsTotal;
        volatile boolean running = true;

        ColdRun(final StratProblem p, final BlockStratFinder.Config cfg) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BlockStratFinder.Outcome out = BlockStratFinder.search(p, cfg,
                                new BlockStratFinder.Progress() {
                                    @Override
                                    public void onSpec(int idx, int count, String label) {
                                        specsRun = idx;
                                        specsTotal = count;
                                    }
                                }, found::add, cancel);
                        notes = out.notes;
                        specsRun = out.specsRun;
                        specsTotal = out.specsTotal;
                        found.clear();
                        found.addAll(out.found);
                    } catch (Throwable ignored) {
                    } finally {
                        running = false;
                    }
                }
            }, "block-strat");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static final class CombinedJob implements Job {
        private final StratFinder warm;
        private final ColdRun cold;
        private final List<StratProblem.Area> obstacles;
        private final long startNanos = System.nanoTime();

        CombinedJob(StratFinder warm, ColdRun cold, List<StratProblem.Area> obstacles) {
            this.warm = warm;
            this.cold = cold;
            this.obstacles = obstacles;
        }

        @Override
        public boolean isRunning() {
            return (warm != null && warm.isRunning()) || (cold != null && cold.running);
        }

        @Override
        public boolean isFinished() {
            return !isRunning();
        }

        @Override
        public int done() {
            int n = warm != null ? warm.done() : 0;
            if (cold != null) {
                n += cold.specsRun;
            }
            return n;
        }

        @Override
        public int total() {
            int n = warm != null ? Math.max(0, warm.total()) : 0;
            if (cold != null) {
                n += Math.max(cold.specsTotal, 0);
            }
            return n;
        }

        @Override
        public int feasible() {
            int n = warm != null ? warm.feasibleCount() : 0;
            if (cold != null) {
                n += cold.found.size();
            }
            return n;
        }

        @Override
        public boolean canaryFailed() {
            return warm != null && warm.canaryFailed();
        }

        @Override
        public List<Row> items() {
            List<Row> out = new ArrayList<Row>();
            if (warm != null) {
                for (StratFinder.Item it : warm.ranked()) {
                    if (it.feasible && it.appliedSnapshotJson != null
                            && (it.original || BlockStratFinder.clearsWithY(it.path, obstacles))) {
                        out.add(Row.fromWarm(it));
                    }
                }
            }
            if (cold != null) {
                Map<String, List<BlockStratFinder.Found>> groups =
                        new LinkedHashMap<String, List<BlockStratFinder.Found>>();
                for (BlockStratFinder.Found f : cold.found) {
                    String key = f.strat != null ? f.strat.patternKey : f.label;
                    List<BlockStratFinder.Found> list = groups.get(key);
                    if (list == null) {
                        list = new ArrayList<BlockStratFinder.Found>();
                        groups.put(key, list);
                    }
                    list.add(f);
                }
                for (List<BlockStratFinder.Found> members : groups.values()) {
                    out.add(mergedColdRow(members));
                }
            }
            Collections.sort(out, new Comparator<Row>() {
                @Override
                public int compare(Row a, Row b) {
                    if (a.original != b.original) return a.original ? -1 : 1;
                    boolean an = Double.isNaN(a.difficulty);
                    boolean bn = Double.isNaN(b.difficulty);
                    if (an != bn) return an ? 1 : -1;
                    if (!an) {
                        int c = Double.compare(a.difficulty, b.difficulty);
                        if (c != 0) return c;
                    }
                    return a.key.compareTo(b.key);
                }
            });
            return out;
        }

        @Override
        public List<String> notes() {
            return cold != null ? cold.notes : Collections.<String>emptyList();
        }

        @Override
        public String coldProgress() {
            if (cold == null || !cold.running) {
                return null;
            }
            if (cold.specsTotal <= 0) {
                return "compiling timings";
            }
            return "timing " + Math.min(cold.specsRun + 1, cold.specsTotal) + " / " + cold.specsTotal;
        }

        @Override
        public double elapsedSeconds() {
            return (System.nanoTime() - startNanos) / 1e9;
        }

        @Override
        public void cancel() {
            if (warm != null) {
                warm.cancel();
            }
            if (cold != null) {
                cold.cancel.set(true);
            }
        }
    }
}
