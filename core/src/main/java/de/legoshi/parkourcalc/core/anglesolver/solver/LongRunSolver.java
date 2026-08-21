package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** From-scratch solver for long multi-jump spans: the post-failure fallback for runs the closed-form dual
 *  cannot converge on across the whole horizon (e.g. the 354-tick "desert hard" runs, ~30 jumps, 81 walls).
 *
 *  <p><b>Receding-horizon (model-predictive) decomposition.</b> The convex Lagrangian dual
 *  ({@link ClosedFormSolve}) solves a single jump to GLOBAL optimality in microseconds, and (measured)
 *  keeps converging for windows of up to ~10 jumps, but not for the full run (the degenerate high-dimensional
 *  landscape of dozens of jumps). So solve a sliding window of {@value #WINDOW} jumps to global optimality,
 *  COMMIT its first few jumps (chaining their exact byte-exact exit state into the next window's seed), and
 *  slide. The committed jumps' exit is, by construction, the entry of a feasible {@code (WINDOW − commit)}-jump
 *  continuation, so it cannot doom the next {@code WINDOW − commit} jumps: the coupling that defeats a greedy
 *  one-jump-at-a-time chain (greedy genuinely fails; measured seam-coupling horizon ~5 jumps).
 *
 *  <p>There is NO free global guarantee: a window sees only {@value #WINDOW} jumps, so this is feasible only
 *  while the lookahead exceeds the run's coupling horizon. Three things make it safe: (1) the lookahead margin
 *  ({@code WINDOW − commit}, default 7) is set above the measured horizon; (2) if a commit gets the chain
 *  stuck, it retries with a smaller commit, i.e. MORE lookahead ({@link #COMMIT_LADDER}); (3) the full
 *  concatenated run is re-verified byte-exact, so a coupling failure returns {@code null} (handing off to the
 *  caller's last-ditch fallback) rather than ever a false success.
 *
 *  <p>This is robust where a global 354-dimensional local search is not: every window is solved by the convex
 *  dual (no local optima, no minimax plateaus, no sine-bucket stalls, no initial guess), so the result does
 *  not depend on tuning or on incidental problem details. It uses only the resume start state, the
 *  input-specified structure (ground/air, jumps, strafe; never a recorded trajectory), and the constraints;
 *  the windows chain their own byte-exact state, so the full concatenated path is feasible by construction.
 *  Returns the chained game facings (certified via the replay {@code toGameFacings(wrapAll(gf))}, the
 *  chain the engine reports and Apply realizes) or {@code null}.
 *
 *  <p>This restores feasibility ("solve at all"); the last window already optimises the real objective, and a
 *  follow-up global objective ascent ({@link BucketAscentPolish}) is a separate, strictly-improving step. */
public final class LongRunSolver {

    /** Jumps solved together per window (the dual converges to ~10-13; 10 leaves margin). */
    private static final int WINDOW = 10;
    /** Jumps committed before sliding, tried in order; a SMALLER commit = MORE lookahead (WINDOW − commit),
     *  used as a retry when a larger commit gets the chain stuck. The measured seam-coupling horizon on the
     *  desert-hard maps is ~5 jumps, so the first try (commit 3 = lookahead 7) carries margin; the retry
     *  (commit 1 = lookahead 9) is the most lookahead a 10-jump window can give. */
    private static final int[] COMMIT_LADDER = {3, 1};
    /** Window sizes tried, largest first; a smaller window is the fallback when a larger one cannot be solved. */
    private static final int[] WINDOW_LADDER = {WINDOW, 7, 5, 3, 2, 1};
    private static final int[] FALLBACK_RUNGS = {7, 5, 3, 2, 1};

    public static boolean DEBUG = false;

    public static final class LongRunConfig {
        final int[] windowLadder;
        final int[] commitLadder;

        LongRunConfig(int[] windowLadder, int[] commitLadder) {
            this.windowLadder = windowLadder;
            this.commitLadder = commitLadder;
        }

        public static LongRunConfig defaults() {
            return new LongRunConfig(WINDOW_LADDER, COMMIT_LADDER);
        }

        public static LongRunConfig of(int window, int commit) {
            int w = Math.max(2, window);
            int c = Math.max(1, Math.min(commit, w - 1));
            int[] commits = (c == 1) ? new int[]{1} : new int[]{c, 1};
            return new LongRunConfig(windowLadderFor(w), commits);
        }

        public static LongRunConfig of(int window, int commit, int[] windowLadder, int[] commitLadder) {
            LongRunConfig base = of(window, commit);
            int[] wl = sanitizeLadder(windowLadder);
            int[] cl = sanitizeLadder(commitLadder);
            return new LongRunConfig(wl != null ? wl : base.windowLadder, cl != null ? cl : base.commitLadder);
        }

        private static int[] sanitizeLadder(int[] ladder) {
            if (ladder == null || ladder.length == 0) return null;
            int[] out = new int[ladder.length];
            for (int i = 0; i < ladder.length; i++) out[i] = Math.max(1, ladder[i]);
            return out;
        }

        public int window() { return windowLadder[0]; }

        public int commit() { return commitLadder[0]; }
    }

    private static int[] windowLadderFor(int w) {
        List<Integer> out = new ArrayList<>();
        out.add(w);
        for (int r : FALLBACK_RUNGS) if (r < w) out.add(r);
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private LongRunSolver() {
    }

    public static final class WindowCache {
        private final Map<String, double[]> map = new HashMap<>();
    }

    private static final double[] WINDOW_MISS = new double[0];

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return solve(exact, spec, feasTol, cancel, LongRunConfig.defaults());
    }

    public static final class FreeRun {
        public final double[] gf;
        public final double startX;
        public final double startZ;

        FreeRun(double[] gf, double startX, double startZ) {
            this.gf = gf;
            this.startX = startX;
            this.startZ = startZ;
        }
    }

    public static FreeRun solveFree(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    LongRunConfig cfg, StartBox freeBox, WindowCache windows) {
        if (freeBox == null || !freeBox.startFree()) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        if (jumps < 1) return null;

        double[] chosen = new double[2];
        Map<Integer, Object> retryCache = new HashMap<>();
        for (int commit : cfg.commitLadder) {
            if (cancel != null && cancel.get()) return null;
            chosen[0] = Double.NaN;
            chosen[1] = Double.NaN;
            double[] gf = runHorizon(exact, sc, spec, bounds, jumps, commit, cfg.windowLadder, cancel,
                    freeBox, chosen, retryCache, windows);
            if (gf == null || Double.isNaN(chosen[0])) continue;
            JumpPhysicsInputs at = FreeStartSolve.copyWithStart(sc, chosen[0], chosen[1]);
            double[] replay = at.toGameFacings(Angles.wrapAll(gf));
            double viol = compiled.maxViolation(replay, exact.forward(at, replay));
            if (viol <= feasTol) return new FreeRun(gf, chosen[0], chosen[1]);
        }
        return null;
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                 LongRunConfig cfg) {
        return solve(exact, spec, feasTol, cancel, cfg, new WindowCache());
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                 LongRunConfig cfg, WindowCache windows) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        if (jumps < 1) return null;
        if (DEBUG) System.err.printf("LRS receding-horizon: %d jumps, %d ticks%n", jumps, sc.numTicks);

        for (int commit : cfg.commitLadder) {
            if (cancel != null && cancel.get()) return null;
            double[] gf = runHorizon(exact, sc, spec, bounds, jumps, commit, cfg.windowLadder, cancel, null, null, null,
                    windows);
            if (gf == null) continue;
            // Certify the chain Apply will actually realize, not the window-chained facings themselves:
            // the plan stores the wrapped facings and the game re-accumulates float deltas from them,
            // which is not guaranteed bit-identical to the seam-chained gf (a delta can re-round when
            // consecutive facings straddle float scales, e.g. crossing 0 deg). Verifying the replay makes
            // the verified, reported, and applied trajectories one and the same object: the engine and
            // Apply both recompute exactly toGameFacings(wrapAll(gf)).
            double[] replay = sc.toGameFacings(Angles.wrapAll(gf));
            double viol = compiled.maxViolation(replay, exact.forward(sc, replay));
            if (DEBUG) System.err.printf("LRS commit=%d -> full viol=%.6f%n", commit, viol);
            if (viol <= feasTol) return gf;
        }
        return null;
    }

    /** One full receding-horizon sweep committing {@code commitJumps} jumps per window. Returns the chained
     *  game facings, or {@code null} if it gets stuck (no window solvable from some seam). */
    private static final Object FREE_RETRY_MISS = new Object();

    private static double[] runHorizon(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, int[] bounds,
                                       int jumps, int commitJumps, int[] windowLadder, AtomicBoolean cancel,
                                       StartBox freeBox, double[] chosenStart, Map<Integer, Object> retryCache,
                                       WindowCache windows) {
        int n = sc.numTicks;
        double[] gf = new double[n];
        Vec3dCore seedPos = sc.startPos, seedVel = sc.initialVelocity;
        float seedYaw = sc.startYaw;

        int i = 0;
        while (i < jumps) {
            if (cancel != null && cancel.get()) return null;
            boolean advanced = false;
            // Try the largest window that solves; shrink on failure for robustness near the run's end / hard spots.
            int prevWe = -1;
            for (int w : windowLadder) {
                int we = Math.min(i + w, jumps);
                if (we == prevWe) continue; // several ladder sizes clamp to the same window near the end: solve it once
                prevWe = we;
                boolean last = (we == jumps);
                int a = bounds[i], c = bounds[we];
                JumpPhysicsInputs win = sliceScenario(sc, a, c, seedPos, seedVel, seedYaw);
                List<JumpConstraint> cons = sliceConstraints(spec, a, c);
                Objective obj = last
                        ? new Objective(spec.objective.axis, spec.objective.sense, c - a)   // last window: real objective
                        : new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, c - a); // lead-in: any feasible
                String winKey = windowKey(a, c, last, seedPos, seedVel, seedYaw);
                double[] cachedWin = windows.map.get(winKey);
                double[] yaws;
                if (cachedWin != null) {
                    yaws = cachedWin == WINDOW_MISS ? null : cachedWin.clone();
                    if (SolverTrace.on()) {
                        SolverTrace.log("LRS", "commit=%d window i=%d we=%d cached -> %s",
                                commitJumps, i, we, yaws != null ? "solved" : "miss");
                    }
                } else {
                    long winT0 = SolverTrace.on() ? System.nanoTime() : 0L;
                    yaws = solveWindow(exact, win, cons, obj, last, cancel);
                    if (cancel == null || !cancel.get()) {
                        windows.map.put(winKey, yaws == null ? WINDOW_MISS : yaws.clone());
                    }
                    if (SolverTrace.on()) {
                        SolverTrace.log("LRS", "commit=%d window i=%d we=%d ticks=%d cons=%d last=%s -> %s ms=%.1f",
                                commitJumps, i, we, c - a, cons.size(), last, yaws != null ? "solved" : "miss",
                                (System.nanoTime() - winT0) / 1.0e6);
                    }
                }
                if (yaws == null && i == 0 && freeBox != null) {
                    Object cached = retryCache.get(we);
                    FreeStartSolve.Result fr;
                    if (cached != null) {
                        fr = cached == FREE_RETRY_MISS ? null : (FreeStartSolve.Result) cached;
                    } else {
                        JumpPhysicsInputs freeSc = FreeStartSolve.copyWithStart(sc, seedPos.x, seedPos.z);
                        freeSc.startBox = new StartBox(seedPos.x, seedPos.z, seedVel.x, seedVel.z,
                                freeBox.pxLo, freeBox.pxHi, freeBox.pzLo, freeBox.pzHi,
                                seedVel.x, seedVel.x, seedVel.z, seedVel.z);
                        List<JumpConstraint> upTo = new ArrayList<>();
                        for (JumpConstraint jc : spec.constraints) {
                            if (jc.t1 <= c && (jc.t2 == null || jc.t2 <= c)) upTo.add(jc);
                        }
                        Objective freeObj = last ? spec.objective
                                : new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, c);
                        fr = FreeStartSolve.solveJoint(exact, new JumpSpec(freeSc, upTo, freeObj), 0.0, cancel);
                        retryCache.put(we, fr == null ? FREE_RETRY_MISS : fr);
                        if (SolverTrace.on()) {
                            SolverTrace.log("FREE", "window free retry we=%d cons=%d -> %s",
                                    we, upTo.size(), fr != null && fr.feasible ? "solved" : "miss");
                        }
                    }
                    if (fr != null && fr.feasible) {
                        chosenStart[0] = fr.startX;
                        chosenStart[1] = fr.startZ;
                        win = FreeStartSolve.copyWithStart(sc, fr.startX, fr.startZ);
                        yaws = Angles.wrapAll(fr.yaws);
                    }
                }
                if (yaws == null) continue;

                // Commit the first commitJumps jumps (all of them for the final window), chaining the exact exit.
                int ce = last ? we : Math.min(i + Math.min(commitJumps, w), jumps);
                int commitTicks = bounds[ce] - a;
                double[] wgf = win.toGameFacings(yaws);
                ForwardPath wp = exact.forward(win, wgf);
                System.arraycopy(wgf, 0, gf, a, commitTicks);
                seedPos = new Vec3dCore(wp.posX[commitTicks], wp.posY[commitTicks], wp.posZ[commitTicks]);
                seedVel = new Vec3dCore(wp.velX[commitTicks], wp.velY[commitTicks], wp.velZ[commitTicks]);
                seedYaw = (float) wgf[commitTicks - 1];
                i = ce;
                advanced = true;
                break;
            }
            if (!advanced) {
                if (DEBUG) System.err.printf("  commit=%d stuck at jump %d (tick %d)%n", commitJumps, i, bounds[i]);
                return null;
            }
        }
        return gf;
    }

    private static String windowKey(int a, int c, boolean last, Vec3dCore pos, Vec3dCore vel, float yaw) {
        return a + ":" + c + ":" + last
                + ":" + Double.doubleToLongBits(pos.x) + ":" + Double.doubleToLongBits(pos.y)
                + ":" + Double.doubleToLongBits(pos.z)
                + ":" + Double.doubleToLongBits(vel.x) + ":" + Double.doubleToLongBits(vel.y)
                + ":" + Double.doubleToLongBits(vel.z)
                + ":" + Float.floatToIntBits(yaw);
    }

    /** Closed form on a window, trying the given objective then the other directions (feasibility is
     *  objective-independent; the closed form only certifies the objective's optimal vertex, so a direction
     *  whose vertex quantizes infeasibly returns null while another solves cleanly). When every direction
     *  fails, {@link SlpSolve} closes the window's duality gap primally; running it here, on the widest
     *  window, keeps the run from degrading into greedy small-window commits. The SLP fallback ladders the
     *  directions too: its dual-recovery seed degenerates objective-dependently, so on the last window a
     *  failed real-objective SLP retries from the other directions' seeds. Only the last window hugs
     *  walls (its objective is the real one); a lead-in window's objective is a surrogate, so it solves
     *  centered, keeping the seam state away from extremes that could doom the continuation. The last
     *  window never returns an alternate direction's solution unhugged when the ascent works: an alternate
     *  solve only seeds the SLP ascent of the real objective, and stays the feasible fallback if that
     *  ascent fails. */
    private static double[] solveWindow(ExactJumpModel exact, JumpPhysicsInputs win, List<JumpConstraint> cons,
                                        Objective first, boolean last, AtomicBoolean cancel) {
        double[] y = closedForm(exact, new JumpSpec(win, cons, first), last, cancel);
        if (y != null) return y;
        for (Objective alt : alternates(first, win.numTicks)) {
            if (cancel != null && cancel.get()) return null;
            y = closedForm(exact, new JumpSpec(win, cons, alt), last, cancel);
            if (y == null) continue;
            if (!last) return y;
            return hugObjective(exact, win, cons, first, y, cancel);
        }
        if (cancel != null && cancel.get()) return null;
        JumpSpec spec = new JumpSpec(win, cons, first);
        y = last ? SlpSolve.optimize(exact, spec, 0.0, cancel)
                 : SlpSolve.optimizeCentered(exact, spec, 0.0, cancel);
        if (y != null || !last) return y;
        for (Objective alt : alternates(first, win.numTicks)) {
            if (cancel != null && cancel.get()) return null;
            y = SlpSolve.optimize(exact, new JumpSpec(win, cons, alt), 0.0, cancel);
            if (y == null) continue;
            return hugObjective(exact, win, cons, first, y, cancel);
        }
        return null;
    }

    private static List<Objective> alternates(Objective first, int len) {
        List<Objective> out = new ArrayList<>();
        for (JumpPhysicsInputs.Axis ax : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X}) {
            for (Objective.Sense se : Objective.Sense.values()) {
                if (ax == first.axis && se == first.sense) continue;
                out.add(new Objective(ax, se, len));
            }
        }
        return out;
    }

    private static double[] hugObjective(ExactJumpModel exact, JumpPhysicsInputs win, List<JumpConstraint> cons,
                                         Objective first, double[] feasible, AtomicBoolean cancel) {
        JumpSpec spec = new JumpSpec(win, cons, first);
        double[] hugged = SlpSolve.optimize(exact, spec, 0.0, cancel, feasible);
        double[] base = hugged != null ? hugged : feasible; // the alternate stays a feasible (if unhugged) fallback
        double[] laddered = LevelSetAscent.improve(exact, spec, base, 0.0, cancel);
        return laddered != null ? laddered : base;
    }

    private static double[] closedForm(ExactJumpModel exact, JumpSpec spec, boolean last, AtomicBoolean cancel) {
        return last ? ClosedFormSolve.optimize(exact, spec, 0.0, cancel)
                    : ClosedFormSolve.optimizeRobust(exact, spec, 0.0, cancel);
    }

    /** Jump launch boundaries: the grounded ticks that begin an airborne arc, plus both endpoints, with
     *  sub-2-tick pieces merged. Ground/air is the input-specified per-tick slip annotation (NaN = air). */
    private static int[] jumpBoundaries(JumpPhysicsInputs sc) {
        int n = sc.numTicks;
        List<Integer> bl = new ArrayList<>();
        bl.add(0);
        for (int t = 1; t < n; t++) {
            boolean g = !Double.isNaN(sc.slipAt(t)), gp = !Double.isNaN(sc.slipAt(t - 1));
            if (g && !gp) bl.add(t);
        }
        if (bl.get(bl.size() - 1) != n) bl.add(n);
        List<Integer> m = new ArrayList<>();
        m.add(bl.get(0));
        for (int k = 1; k < bl.size(); k++) {
            if (bl.get(k) - m.get(m.size() - 1) < 2 && k < bl.size() - 1) continue;
            m.add(bl.get(k));
        }
        int[] o = new int[m.size()];
        for (int k = 0; k < o.length; k++) o[k] = m.get(k);
        return o;
    }

    public static JumpSpec suffixSpec(JumpSpec full, int from, Vec3dCore pos, Vec3dCore vel, float yaw) {
        JumpPhysicsInputs sc = full.asScenario();
        JumpPhysicsInputs win = sliceScenario(sc, from, sc.numTicks, pos, vel, yaw);
        win.incomingSprint = from == 0 ? sc.incomingSprint : sc.sprintAt(from - 1);
        win.incomingAmp = from == 0 ? sc.incomingAmp : sc.speedAmplifierAt(from - 1);
        List<JumpConstraint> cons = sliceConstraints(full, from, sc.numTicks);
        Objective obj = new Objective(full.objective.axis, full.objective.sense, full.objective.tick - from);
        return new JumpSpec(win, cons, obj);
    }

    /** A window's physics inputs: the masks for ticks [a, c), seeded with the chained exit state. */
    private static JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c,
                                                   Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos;
        p.initialVelocity = vel;
        p.startYaw = yaw;
        p.strafeSign = sc.strafeSign;
        p.jumpPerTick = sliceBool(sc.jumpPerTick, a, len);
        p.strafePerTick = sliceBool(sc.strafePerTick, a, len);
        p.yawLockedPerTick = sliceBool(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceDouble(sc.slipPerTick, a, len);
        p.surfacePerTick = sliceKind(sc.surfacePerTick, a, len);
        p.soulsandCellsPerTick = sliceInt(sc.soulsandCellsPerTick, a, len);
        p.sneakPerTick = sliceBool(sc.sneakPerTick, a, len);
        // null arrays stay null so the slice keeps the source's legacy fallbacks (always-sprint, W held).
        p.sprintPerTick = sliceBool(sc.sprintPerTick, a, len);
        p.liveAirSprintFactor = sc.liveAirSprintFactor;
        p.forwardInputPerTick = sliceFloat(sc.forwardInputPerTick, a, len, 1.0F * 0.98F);
        p.strafeInputPerTick = sliceFloat(sc.strafeInputPerTick, a, len, 0.0F);
        return p;
    }

    /** The full spec's constraints that fall entirely within [a, c], remapped to window-local ticks. */
    private static List<JumpConstraint> sliceConstraints(JumpSpec full, int a, int c) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint jc : full.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                out.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return out;
    }

    private static boolean[] sliceBool(boolean[] x, int from, int len) {
        if (x == null) return null;
        boolean[] o = new boolean[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length && x[from + i];
        return o;
    }

    private static int[] sliceInt(int[] x, int from, int len) {
        if (x == null) return null;
        int[] o = new int[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : 0;
        return o;
    }

    private static double[] sliceDouble(double[] x, int from, int len) {
        if (x == null) return null;
        double[] o = new double[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : Double.NaN;
        return o;
    }

    private static SurfaceKind[] sliceKind(SurfaceKind[] x, int from, int len) {
        if (x == null) return null;
        SurfaceKind[] o = new SurfaceKind[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : SurfaceKind.NORMAL;
        return o;
    }

    private static float[] sliceFloat(float[] x, int from, int len, float dflt) {
        if (x == null) return null;
        float[] o = new float[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : dflt;
        return o;
    }
}
