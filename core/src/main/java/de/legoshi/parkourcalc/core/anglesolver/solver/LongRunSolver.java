package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** From-scratch solver for long multi-jump spans -- the post-failure fallback for runs the closed-form dual
 *  cannot converge on across the whole horizon (e.g. the 354-tick "desert hard" runs, ~30 jumps, 81 walls).
 *
 *  <p><b>Receding-horizon (model-predictive) decomposition.</b> The convex Lagrangian dual
 *  ({@link ClosedFormSolve}) solves a single jump to GLOBAL optimality in microseconds, and -- measured --
 *  keeps converging for windows of up to ~10 jumps, but not for the full run (the degenerate high-dimensional
 *  landscape of dozens of jumps). So solve a sliding WINDOW of {@code WINDOW} jumps to global optimality,
 *  COMMIT its first {@code COMMIT} jumps (chaining their exact byte-exact exit state into the next window's
 *  seed), and slide. The {@code WINDOW − COMMIT} jumps of overlap are pure lookahead: a committed jump's exit
 *  is always part of a feasible multi-jump continuation, so it can never doom the jumps that follow -- the
 *  coupling that defeats a greedy one-jump-at-a-time chain.
 *
 *  <p>This is robust where a global 354-dimensional local search is not: every window is solved by the convex
 *  dual (no local optima, no minimax plateaus, no sine-bucket stalls, no initial guess), so the result does
 *  not depend on tuning or on incidental problem details. It uses only the resume start state, the
 *  input-specified structure (ground/air, jumps, strafe -- never a recorded trajectory), and the constraints;
 *  the windows chain their own byte-exact state, so the full concatenated path is feasible by construction
 *  (and re-verified). Returns the game facings, or {@code null} if a window cannot be solved even alone.
 *
 *  <p>This restores feasibility ("solve at all"); the last window already optimises the real objective, and a
 *  follow-up global objective ascent ({@link BucketAscentPolish}) is a separate, strictly-improving step. */
public final class LongRunSolver {

    /** Jumps solved together per window (the dual converges to ~10-13; 10 leaves margin). */
    private static final int WINDOW = 10;
    /** Jumps committed before sliding; WINDOW − COMMIT jumps of lookahead absorb the seam coupling. */
    private static final int COMMIT = 5;
    /** Window sizes tried, largest first; a smaller window is the fallback when a larger one cannot be solved. */
    private static final int[] WINDOW_LADDER = {WINDOW, 7, 5, 3, 2, 1};

    public static boolean DEBUG = false;

    private LongRunSolver() {
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        if (jumps < 1) return null;
        if (DEBUG) System.err.printf("LRS receding-horizon: %d jumps, %d ticks%n", jumps, n);

        double[] gf = new double[n];
        Vec3dCore seedPos = sc.startPos, seedVel = sc.initialVelocity;
        float seedYaw = sc.startYaw;

        int i = 0;
        while (i < jumps) {
            if (cancel != null && cancel.get()) return null;
            boolean advanced = false;
            // Try the largest window that solves; shrink on failure for robustness near the run's end / hard spots.
            for (int w : WINDOW_LADDER) {
                int we = Math.min(i + w, jumps);
                boolean last = (we == jumps);
                int a = bounds[i], c = bounds[we];
                JumpPhysicsInputs win = sliceScenario(sc, a, c, seedPos, seedVel, seedYaw);
                List<JumpConstraint> cons = sliceConstraints(spec, a, c);
                Objective obj = last
                        ? new Objective(spec.objective.axis, spec.objective.sense, c - a)   // last window: real objective
                        : new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, c - a); // lead-in: any feasible
                double[] yaws = solveWindow(exact, win, cons, obj, cancel);
                if (yaws == null) continue;

                // Commit the first COMMIT jumps (all of them for the final window), chaining the exact exit.
                int ce = last ? we : Math.min(i + Math.max(1, w / 2), jumps);
                int commitTicks = bounds[ce] - a;
                double[] wgf = win.toGameFacings(yaws);
                ForwardPath wp = exact.forward(win, wgf);
                System.arraycopy(wgf, 0, gf, a, commitTicks);
                seedPos = new Vec3dCore(wp.posX[commitTicks], wp.posY[commitTicks], wp.posZ[commitTicks]);
                seedVel = new Vec3dCore(wp.velX[commitTicks], wp.velY[commitTicks], wp.velZ[commitTicks]);
                seedYaw = (float) wgf[commitTicks - 1];
                if (DEBUG) System.err.printf("  window jumps %d..%d (ticks %d..%d) -> commit %d jumps%n", i, we, a, c, ce - i);
                i = ce;
                advanced = true;
                break;
            }
            if (!advanced) {
                if (DEBUG) System.err.printf("  STUCK at jump %d (tick %d) -- no window solvable%n", i, bounds[i]);
                return null;
            }
        }

        // The committed game facings concatenate into one continuous run; re-verify byte-exact (each window
        // was forwarded from the previous window's exact exit, so this holds by construction).
        ForwardPath path = exact.forward(sc, gf);
        double viol = compiled.maxViolation(gf, path);
        if (DEBUG) System.err.printf("LRS full viol=%.6f%n", viol);
        return viol <= feasTol ? gf : null;
    }

    /** Closed form on a window, trying the given objective then the other directions (feasibility is
     *  objective-independent; the closed form only certifies the objective's optimal vertex, so a direction
     *  whose vertex quantizes infeasibly returns null while another solves cleanly). */
    private static double[] solveWindow(ExactJumpModel exact, JumpPhysicsInputs win, List<JumpConstraint> cons,
                                        Objective first, AtomicBoolean cancel) {
        int len = win.numTicks;
        double[] y = ClosedFormSolve.optimize(exact, new JumpSpec(win, cons, first), 0.0, cancel);
        if (y != null) return y;
        for (JumpPhysicsInputs.Axis ax : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X}) {
            for (Objective.Sense se : Objective.Sense.values()) {
                if (ax == first.axis && se == first.sense) continue;
                if (cancel != null && cancel.get()) return null;
                y = ClosedFormSolve.optimize(exact, new JumpSpec(win, cons, new Objective(ax, se, len)), 0.0, cancel);
                if (y != null) return y;
            }
        }
        return null;
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
}
