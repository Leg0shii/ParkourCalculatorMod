package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdBeamSolver;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdSearch;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdStratFinder;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BlockStratFinder {

    public static final double PLAYER_HALF_WIDTH = StratProblem.HALF_WIDTH;
    public static final double PLAYER_HEIGHT = 1.8;

    private BlockStratFinder() {
    }

    public static final class Config {
        public long budgetMs = 60_000L;
        public int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    public static final class Found {
        public final String label;
        public final String scheduleLabel;
        public final ColdStratFinder.Strat strat;
        public final String snapshotJson;
        public final StratMeasurements measurements;
        public final double difficulty;
        public final ForwardPath path;
        public final int landingTick;
        public final ProblemCompiler.Compiled spec;
        public final int pressLo;
        public final int pressHi;
        public String[] windowSnapshots;

        Found(String label, String scheduleLabel, ColdStratFinder.Strat strat, String snapshotJson,
              StratMeasurements measurements, double difficulty, ForwardPath path, int landingTick,
              ProblemCompiler.Compiled spec, int pressLo, int pressHi) {
            this.label = label;
            this.scheduleLabel = scheduleLabel;
            this.strat = strat;
            this.snapshotJson = snapshotJson;
            this.measurements = measurements;
            this.difficulty = difficulty;
            this.path = path;
            this.landingTick = landingTick;
            this.spec = spec;
            this.pressLo = pressLo;
            this.pressHi = pressHi;
        }
    }

    public interface Progress {
        void onSpec(int specIndex, int specCount, String scheduleLabel);
    }

    public static final class Outcome {
        public final List<Found> found;
        public final List<String> notes;
        public final boolean truncated;
        public final int specsRun;
        public final int specsTotal;

        Outcome(List<Found> found, List<String> notes, boolean truncated, int specsRun, int specsTotal) {
            this.found = found;
            this.notes = notes;
            this.truncated = truncated;
            this.specsRun = specsRun;
            this.specsTotal = specsTotal;
        }
    }

    public static Outcome search(StratProblem problem, Config cfg, Progress progress,
                                 Consumer<Found> stream, AtomicBoolean cancel) {
        Config c = cfg != null ? cfg : new Config();
        AtomicBoolean cancelToken = cancel != null ? cancel : new AtomicBoolean(false);
        List<Found> found = new ArrayList<Found>();
        ProblemCompiler.Compilation comp = ProblemCompiler.compile(problem);
        List<String> notes = new ArrayList<String>(comp.notes);
        if (comp.specs.isEmpty()) {
            return new Outcome(found, notes, comp.truncated, 0, 0);
        }
        long deadline = System.currentTimeMillis() + c.budgetMs;
        Set<String> seen = new HashSet<String>();
        boolean truncated = comp.truncated;
        int run = 0;
        for (int i = 0; i < comp.specs.size(); i++) {
            if (cancelToken.get()) {
                truncated = true;
                break;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                truncated = true;
                notes.add("budget exhausted after " + run + " of " + comp.specs.size() + " timings");
                break;
            }
            ProblemCompiler.Compiled spec = comp.specs.get(i);
            if (progress != null) {
                progress.onSpec(i, comp.specs.size(), spec.label);
            }
            long specBudget = Math.max(500L, remaining / (comp.specs.size() - i));
            ColdStratFinder.Request req = requestFor(problem, spec, specBudget, c.threads);
            final AtomicBoolean specCancel = new AtomicBoolean(false);
            final AtomicBoolean outer = cancelToken;
            final long specDeadline = System.currentTimeMillis() + specBudget + 2000L;
            Thread watchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (System.currentTimeMillis() < specDeadline) {
                        if (outer.get()) {
                            specCancel.set(true);
                            return;
                        }
                        try {
                            Thread.sleep(50L);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    specCancel.set(true);
                }
            }, "block-strat-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            ColdStratFinder.Result res;
            try {
                res = ColdStratFinder.find(spec.save, req, ColdBeamSolver.NO_PROGRESS, specCancel);
            } catch (RuntimeException ex) {
                notes.add("timing " + spec.label + ": " + ex.getMessage());
                run++;
                continue;
            } finally {
                watchdog.interrupt();
            }
            truncated = truncated || res.truncated;
            for (ColdStratFinder.Strat s : res.strats) {
                if (cancelToken.get()) {
                    break;
                }
                ColdResult rep = s.representative;
                if (rep == null || !rep.solved()) {
                    continue;
                }
                if (!pathClears(rep, spec.yPerTick, problem.collisions)) {
                    rep = repairAgainstObstacles(spec, rep, problem.collisions);
                    if (rep == null) {
                        continue;
                    }
                }
                Found f = toFound(spec, s, rep, problem.collisions);
                if (f == null || f.snapshotJson == null) {
                    continue;
                }
                String sig = rowSignature(f.snapshotJson);
                if (sig != null && !seen.add(sig)) {
                    continue;
                }
                found.add(f);
                if (stream != null) {
                    stream.accept(f);
                }
            }
            run++;
        }
        if (found.isEmpty() && run > 0 && problem.collisions != null && !problem.collisions.isEmpty()) {
            notes.add("every line collided with nearby blocks; if the jump runs under a ceiling,"
                    + " set the segment ceiling so the arc is computed with the bonk");
        }
        try {
            consolidatePressWindows(found, problem.collisions);
        } catch (RuntimeException ex) {
            notes.add("press window consolidation failed: " + ex.getMessage());
        }
        return new Outcome(found, notes, truncated, run, comp.specs.size());
    }

    static void consolidatePressWindows(List<Found> found, List<StratProblem.Area> obstacles) {
        Map<String, List<Found>> groups = new LinkedHashMap<String, List<Found>>();
        for (Found f : found) {
            if (f.strat == null || f.spec == null || f.spec.fireTicks.length != 1) {
                continue;
            }
            if (f.strat.seq.length != 1 || f.strat.seq[0].length > 1) {
                continue;
            }
            List<Found> g = groups.get(f.strat.patternKey);
            if (g == null) {
                g = new ArrayList<Found>();
                groups.put(f.strat.patternKey, g);
            }
            g.add(f);
        }
        for (List<Found> g : groups.values()) {
            if (g.size() < 2) {
                continue;
            }
            Collections.sort(g, new java.util.Comparator<Found>() {
                @Override
                public int compare(Found a, Found b) {
                    return Integer.compare(a.spec.fireTicks[0], b.spec.fireTicks[0]);
                }
            });
            double bestArea = -1.0;
            int bestLo = -1;
            int bestHi = -1;
            double bestTheta = 0.0;
            double bestX = 0.0;
            double bestZ = 0.0;
            for (Found thetaSrc : g) {
                double theta = thetaSrc.strat.representative.facingDeg;
                double[][] rects = new double[g.size()][];
                for (int i = 0; i < g.size(); i++) {
                    rects[i] = PressWindows.startRegion(g.get(i).spec,
                            g.get(i).strat.representative.line, theta);
                }
                for (int i = 0; i < g.size(); i++) {
                    double[] acc = rects[i];
                    int j = i;
                    while (acc != null) {
                        if (j - i > bestHi - bestLo
                                || (j - i == bestHi - bestLo && PressWindows.area(acc) > bestArea)) {
                            bestLo = i;
                            bestHi = j;
                            bestArea = PressWindows.area(acc);
                            bestTheta = theta;
                            bestX = 0.5 * (acc[0] + acc[1]);
                            bestZ = 0.5 * (acc[2] + acc[3]);
                        }
                        j++;
                        if (j >= g.size()) {
                            break;
                        }
                        if (g.get(j).spec.fireTicks[0] != g.get(j - 1).spec.fireTicks[0] + 1) {
                            break;
                        }
                        acc = PressWindows.intersect(acc, rects[j]);
                    }
                }
            }
            if (bestLo < 0 || bestHi <= bestLo) {
                continue;
            }
            boolean clear = true;
            for (int i = bestLo; i <= bestHi && clear; i++) {
                Found m = g.get(i);
                ColdResult r = ColdStratFinder.concreteResult(m.spec.save,
                        m.strat.representative.line, bestTheta, bestX, bestZ);
                if (!pathClears(r, m.spec.yPerTick, obstacles)) {
                    clear = false;
                }
            }
            if (!clear) {
                continue;
            }
            int mid = (bestLo + bestHi) / 2;
            Found m = g.get(mid);
            ColdResult rep2 = ColdStratFinder.concreteResult(m.spec.save,
                    m.strat.representative.line, bestTheta, bestX, bestZ);
            Found up = toFound(m.spec, m.strat, rep2, obstacles);
            if (up == null || up.snapshotJson == null) {
                continue;
            }
            String[] snapshots = new String[bestHi - bestLo + 1];
            boolean complete = true;
            for (int i = bestLo; i <= bestHi && complete; i++) {
                if (i == mid) {
                    snapshots[i - bestLo] = up.snapshotJson;
                    continue;
                }
                Found mi = g.get(i);
                ColdResult ri = ColdStratFinder.concreteResult(mi.spec.save,
                        mi.strat.representative.line, bestTheta, bestX, bestZ);
                Found fi = toFound(mi.spec, mi.strat, ri, obstacles);
                if (fi == null || fi.snapshotJson == null) {
                    complete = false;
                } else {
                    snapshots[i - bestLo] = fi.snapshotJson;
                }
            }
            if (!complete) {
                continue;
            }
            Found windowed = new Found(up.label, up.scheduleLabel, up.strat, up.snapshotJson,
                    up.measurements, up.difficulty, up.path, up.landingTick, up.spec,
                    g.get(bestLo).spec.fireTicks[0], g.get(bestHi).spec.fireTicks[0]);
            windowed.windowSnapshots = snapshots;
            found.removeAll(g);
            found.add(windowed);
        }
    }

    private static ColdStratFinder.Request requestFor(StratProblem problem, ProblemCompiler.Compiled spec,
                                                      long budgetMs, int threads) {
        ColdStratFinder.Request req = new ColdStratFinder.Request();
        req.segments = new ArrayList<ColdStratFinder.SegmentConfig>();
        for (StratProblem.Segment seg : problem.segments) {
            ColdStratFinder.SegmentConfig sc = new ColdStratFinder.SegmentConfig();
            if (seg.alphabet != null && seg.alphabet.length > 0) {
                sc.alphabet = seg.alphabet.clone();
            }
            if (seg.maxChanges > 0) {
                sc.maxChanges = seg.maxChanges;
            }
            sc.ja = seg.ja;
            req.segments.add(sc);
        }
        req.beam.budgetMs = budgetMs;
        req.beam.threads = Math.max(1, threads);
        return req;
    }

    static ColdResult repairAgainstObstacles(ProblemCompiler.Compiled spec, ColdResult seed,
                                             List<StratProblem.Area> obstacles) {
        if (seed.line == null || obstacles == null || obstacles.isEmpty()) {
            return null;
        }
        String sig = seed.line.signature();
        ColdSearch.Config cfg = new ColdSearch.Config();
        Set<String> seen = new HashSet<String>();
        List<SaveFile.Constraint> walls = new ArrayList<SaveFile.Constraint>();
        List<Integer> wallTicks = new ArrayList<Integer>();
        ColdResult cur = seed;
        for (int round = 0; round < 4; round++) {
            if (!collectRepairWalls(cur, spec.yPerTick, obstacles, seen, walls, wallTicks)) {
                return null;
            }
            SaveFile copy = SaveIO.parseSafe(SaveIO.saveJson(spec.save));
            if (copy == null) {
                return null;
            }
            for (int i = 0; i < walls.size(); i++) {
                tickOf(copy, wallTicks.get(i)).constraints.add(walls.get(i));
            }
            ColdResult r;
            try {
                r = ColdSearch.certifyLine(copy, sig, cfg);
            } catch (RuntimeException ex) {
                return null;
            }
            if (r == null || !r.solved()) {
                return null;
            }
            if (pathClears(r, spec.yPerTick, obstacles)) {
                return r;
            }
            cur = r;
        }
        return null;
    }

    private static boolean collectRepairWalls(ColdResult r, double[] yPerTick,
                                              List<StratProblem.Area> obstacles, Set<String> seen,
                                              List<SaveFile.Constraint> walls, List<Integer> wallTicks) {
        ForwardPath path = r.path;
        if (path == null || path.posX == null) {
            return false;
        }
        boolean added = false;
        int n = Math.min(path.posX.length, yPerTick.length - 1);
        double px = r.startX;
        double pz = r.startZ;
        double py = yPerTick[0];
        for (int k = 0; k < n; k++) {
            double x = path.posX[k];
            double z = path.posZ[k];
            double y = yPerTick[k + 1];
            int t = k + 1;
            for (StratProblem.Area o : obstacles) {
                if (hits(x, y, z, Collections.singletonList(o))) {
                    added |= addWall(t, escapeWall(x, z, o), seen, walls, wallTicks);
                } else if (hits(0.5 * (px + x), 0.5 * (py + y), 0.5 * (pz + z),
                        Collections.singletonList(o))) {
                    if (t > 1) {
                        added |= addWall(t - 1, keepSideWall(px, pz, o), seen, walls, wallTicks);
                    }
                    added |= addWall(t, keepSideWall(x, z, o), seen, walls, wallTicks);
                }
            }
            px = x;
            pz = z;
            py = y;
        }
        return added;
    }

    private static SaveFile.Constraint escapeWall(double x, double z, StratProblem.Area o) {
        double xLo = o.xLo - PLAYER_HALF_WIDTH;
        double xHi = o.xHi + PLAYER_HALF_WIDTH;
        double zLo = o.zLo - PLAYER_HALF_WIDTH;
        double zHi = o.zHi + PLAYER_HALF_WIDTH;
        double best = x - xLo;
        SaveFile.Constraint wall = sideConstraint("X", "LE", xLo);
        if (xHi - x < best) {
            best = xHi - x;
            wall = sideConstraint("X", "GE", xHi);
        }
        if (z - zLo < best) {
            best = z - zLo;
            wall = sideConstraint("Z", "LE", zLo);
        }
        if (zHi - z < best) {
            wall = sideConstraint("Z", "GE", zHi);
        }
        return wall;
    }

    private static SaveFile.Constraint keepSideWall(double x, double z, StratProblem.Area o) {
        double xLo = o.xLo - PLAYER_HALF_WIDTH;
        double xHi = o.xHi + PLAYER_HALF_WIDTH;
        double zLo = o.zLo - PLAYER_HALF_WIDTH;
        double zHi = o.zHi + PLAYER_HALF_WIDTH;
        double best = xLo - x;
        SaveFile.Constraint wall = sideConstraint("X", "LE", xLo);
        if (x - xHi > best) {
            best = x - xHi;
            wall = sideConstraint("X", "GE", xHi);
        }
        if (zLo - z > best) {
            best = zLo - z;
            wall = sideConstraint("Z", "LE", zLo);
        }
        if (z - zHi > best) {
            best = z - zHi;
            wall = sideConstraint("Z", "GE", zHi);
        }
        return best >= 0.0 ? wall : null;
    }

    private static boolean addWall(int tick, SaveFile.Constraint wall, Set<String> seen,
                                   List<SaveFile.Constraint> walls, List<Integer> wallTicks) {
        if (wall == null) {
            return false;
        }
        String key = tick + "|" + wall.field + "|" + wall.op + "|" + wall.value;
        if (!seen.add(key)) {
            return false;
        }
        walls.add(wall);
        wallTicks.add(tick);
        return true;
    }

    static boolean pathClears(ColdResult r, double[] yPerTick, List<StratProblem.Area> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return true;
        }
        ForwardPath path = r.path;
        if (path == null || path.posX == null) {
            return false;
        }
        double px = r.startX;
        double pz = r.startZ;
        double py = yPerTick[0];
        if (hits(px, py, pz, obstacles)) {
            return false;
        }
        int n = Math.min(path.posX.length, yPerTick.length - 1);
        for (int k = 0; k < n; k++) {
            double x = path.posX[k];
            double z = path.posZ[k];
            double y = yPerTick[k + 1];
            if (hits(x, y, z, obstacles)) {
                return false;
            }
            if (hits(0.5 * (px + x), 0.5 * (py + y), 0.5 * (pz + z), obstacles)) {
                return false;
            }
            px = x;
            pz = z;
            py = y;
        }
        return true;
    }

    private static boolean hits(double x, double y, double z, List<StratProblem.Area> obstacles) {
        for (StratProblem.Area o : obstacles) {
            if (x + PLAYER_HALF_WIDTH > o.xLo && x - PLAYER_HALF_WIDTH < o.xHi
                    && z + PLAYER_HALF_WIDTH > o.zLo && z - PLAYER_HALF_WIDTH < o.zHi
                    && y < o.yHi && y + PLAYER_HEIGHT > o.yLo) {
                return true;
            }
        }
        return false;
    }

    private static Found toFound(ProblemCompiler.Compiled spec, ColdStratFinder.Strat s,
                                 ColdResult rep, List<StratProblem.Area> obstacles) {
        if (rep == null || !rep.solved()) {
            return null;
        }
        SaveFile snapshot = ColdSnapshots.build(spec, rep);
        if (snapshot == null) {
            return null;
        }
        if (!injectObstacleWalls(snapshot, rep.path, spec.yPerTick, obstacles)) {
            return null;
        }
        StratMeasurements meas = null;
        double difficulty = Double.NaN;
        SaveFile use = snapshot;
        ForwardPath shipPath = fullPath(rep, spec.yPerTick);
        try {
            SaveFile smoothed = StratMeasure.withSmoothedLine(snapshot, displayLabel(s));
            if (smoothed != null) {
                ForwardPath sp = StratMeasure.linePath(smoothed, displayLabel(s));
                if (sp != null && clearsFull(sp, spec.yPerTick, obstacles)) {
                    use = smoothed;
                    shipPath = sp;
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            meas = StratMeasure.measure(use, displayLabel(s));
            difficulty = StratDifficulty.combinedV4(meas);
        } catch (RuntimeException ignored) {
        }
        int lastFire = spec.fireTicks[spec.fireTicks.length - 1];
        return new Found(displayLabel(s), spec.label, s, SaveIO.saveJson(use), meas, difficulty,
                shipPath, spec.save.angleSolver.landingTick, spec, lastFire, lastFire);
    }

    static boolean injectObstacleWalls(SaveFile snapshot, ForwardPath stepPath, double[] yPerTick,
                                       List<StratProblem.Area> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return true;
        }
        if (stepPath == null || stepPath.posX == null) {
            return false;
        }
        int landingTick = snapshot.angleSolver.landingTick;
        int n = Math.min(landingTick, Math.min(stepPath.posX.length, yPerTick.length - 1));
        for (int t = 1; t <= n; t++) {
            double x = stepPath.posX[t - 1];
            double z = stepPath.posZ[t - 1];
            double y = yPerTick[t];
            for (StratProblem.Area o : obstacles) {
                if (y >= o.yHi || y + PLAYER_HEIGHT <= o.yLo) {
                    continue;
                }
                double xLo = o.xLo - PLAYER_HALF_WIDTH;
                double xHi = o.xHi + PLAYER_HALF_WIDTH;
                double zLo = o.zLo - PLAYER_HALF_WIDTH;
                double zHi = o.zHi + PLAYER_HALF_WIDTH;
                double best = xLo - x;
                SaveFile.Constraint wall = sideConstraint("X", "LE", xLo);
                if (x - xHi > best) {
                    best = x - xHi;
                    wall = sideConstraint("X", "GE", xHi);
                }
                if (zLo - z > best) {
                    best = zLo - z;
                    wall = sideConstraint("Z", "LE", zLo);
                }
                if (z - zHi > best) {
                    best = z - zHi;
                    wall = sideConstraint("Z", "GE", zHi);
                }
                if (best < 0.0) {
                    return false;
                }
                tickOf(snapshot, t).constraints.add(wall);
            }
        }
        return true;
    }

    private static SaveFile.Constraint sideConstraint(String field, String op, double value) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = field;
        c.op = op;
        c.value = value;
        c.derived = true;
        return c;
    }

    private static SaveFile.Tick tickOf(SaveFile save, int t) {
        List<SaveFile.Tick> ticks = save.angleSolver.ticks;
        if (ticks == null) {
            ticks = new ArrayList<SaveFile.Tick>();
            save.angleSolver.ticks = ticks;
        }
        int at = ticks.size();
        for (int i = 0; i < ticks.size(); i++) {
            if (ticks.get(i).tick == t) {
                return ticks.get(i);
            }
            if (ticks.get(i).tick > t) {
                at = i;
                break;
            }
        }
        SaveFile.Tick tk = new SaveFile.Tick();
        tk.tick = t;
        ticks.add(at, tk);
        return tk;
    }

    static ForwardPath fullPath(ColdResult r, double[] yPerTick) {
        int n = r.path.posX.length;
        double[] px = new double[n + 1];
        double[] py = new double[n + 1];
        double[] pz = new double[n + 1];
        px[0] = r.startX;
        pz[0] = r.startZ;
        for (int t = 1; t <= n; t++) {
            px[t] = r.path.posX[t - 1];
            pz[t] = r.path.posZ[t - 1];
        }
        for (int t = 0; t <= n; t++) {
            py[t] = t < yPerTick.length ? yPerTick[t] : yPerTick[yPerTick.length - 1];
        }
        return new ForwardPath(px, py, pz);
    }

    static boolean clearsFull(ForwardPath path, double[] yPerTick, List<StratProblem.Area> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return true;
        }
        if (path == null || path.posX == null) {
            return false;
        }
        int n = Math.min(path.posX.length, yPerTick.length);
        for (int t = 0; t < n; t++) {
            if (hits(path.posX[t], yPerTick[t], path.posZ[t], obstacles)) {
                return false;
            }
            if (t > 0 && hits(0.5 * (path.posX[t - 1] + path.posX[t]),
                    0.5 * (yPerTick[t - 1] + yPerTick[t]),
                    0.5 * (path.posZ[t - 1] + path.posZ[t]), obstacles)) {
                return false;
            }
        }
        return true;
    }

    public static boolean clearsWithY(ForwardPath path, List<StratProblem.Area> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return true;
        }
        if (path == null || path.posX == null || path.posY == null) {
            return true;
        }
        for (int t = 0; t < path.posX.length; t++) {
            if (hits(path.posX[t], path.posY[t], path.posZ[t], obstacles)) {
                return false;
            }
            if (t > 0 && hits(0.5 * (path.posX[t - 1] + path.posX[t]),
                    0.5 * (path.posY[t - 1] + path.posY[t]),
                    0.5 * (path.posZ[t - 1] + path.posZ[t]), obstacles)) {
                return false;
            }
        }
        return true;
    }

    static String displayLabel(ColdStratFinder.Strat s) {
        return ColdStratFinder.sequenceLabel(s.seq, s.tailCombo);
    }

    public static String patternLabel(ColdStratFinder.Strat s) {
        return ColdStratFinder.sequenceLabel(s.seq, s.tailCombo);
    }

    private static String rowSignature(String snapshotJson) {
        SaveFile f = SaveIO.parseSafe(snapshotJson);
        if (f == null || f.rows == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean leading = true;
        for (SaveFile.Row r : f.rows) {
            boolean empty = r.keys == null || r.keys.isEmpty();
            if (leading && empty) {
                continue;
            }
            leading = false;
            if (r.keys != null) {
                List<String> ks = new ArrayList<String>(r.keys);
                java.util.Collections.sort(ks);
                sb.append(ks);
            }
            sb.append(';');
        }
        return sb.toString();
    }
}
