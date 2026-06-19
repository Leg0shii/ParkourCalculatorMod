package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
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

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return solve(exact, spec, feasTol, cancel, LongRunConfig.defaults());
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                 LongRunConfig cfg) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        if (jumps < 1) return null;
        if (DEBUG) System.err.printf("LRS receding-horizon: %d jumps, %d ticks%n", jumps, sc.numTicks);

        for (int commit : cfg.commitLadder) {
            if (cancel != null && cancel.get()) return null;
            double[] gf = runHorizon(exact, sc, spec, bounds, jumps, commit, cfg.windowLadder, cancel);
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
    private static double[] runHorizon(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, int[] bounds,
                                       int jumps, int commitJumps, int[] windowLadder, AtomicBoolean cancel) {
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
                double[] yaws = solveWindow(exact, win, cons, obj, last, cancel);
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

    /** Closed form on a window, trying the given objective then the other directions (feasibility is
     *  objective-independent; the closed form only certifies the objective's optimal vertex, so a direction
     *  whose vertex quantizes infeasibly returns null while another solves cleanly). When every direction
     *  fails, {@link SlpSolve} closes the window's duality gap primally; running it here, on the widest
     *  window, keeps the run from degrading into greedy small-window commits. Only the last window hugs
     *  walls (its objective is the real one); a lead-in window's objective is a surrogate, so it solves
     *  centered, keeping the seam state away from extremes that could doom the continuation. The last
     *  window never returns an alternate direction's solution: that only seeds the SLP ascent of the
     *  real objective. */
    private static double[] solveWindow(ExactJumpModel exact, JumpPhysicsInputs win, List<JumpConstraint> cons,
                                        Objective first, boolean last, AtomicBoolean cancel) {
        int len = win.numTicks;
        double[] y = closedForm(exact, new JumpSpec(win, cons, first), last, cancel);
        if (y != null) return y;
        for (JumpPhysicsInputs.Axis ax : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X}) {
            for (Objective.Sense se : Objective.Sense.values()) {
                if (ax == first.axis && se == first.sense) continue;
                if (cancel != null && cancel.get()) return null;
                y = closedForm(exact, new JumpSpec(win, cons, new Objective(ax, se, len)), last, cancel);
                if (y == null) continue;
                if (!last) return y;
                double[] hugged = SlpSolve.optimize(exact, new JumpSpec(win, cons, first), 0.0, cancel, y);
                return hugged != null ? hugged : y; // y stays a feasible (if unhugged) fallback
            }
        }
        if (cancel != null && cancel.get()) return null;
        JumpSpec spec = new JumpSpec(win, cons, first);
        return last ? SlpSolve.optimize(exact, spec, 0.0, cancel)
                    : SlpSolve.optimizeCentered(exact, spec, 0.0, cancel);
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
        // null arrays stay null so the slice keeps the source's legacy fallbacks (always-sprint, W held).
        p.sprintPerTick = sliceBool(sc.sprintPerTick, a, len);
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

    private static float[] sliceFloat(float[] x, int from, int len, float dflt) {
        if (x == null) return null;
        float[] o = new float[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : dflt;
        return o;
    }
}
