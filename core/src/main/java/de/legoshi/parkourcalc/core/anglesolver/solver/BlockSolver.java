package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Geometric homotopy planner with a forced crossing tick: derives the per-tick keep-out constraints that
 * wrap an obstacle cluster, then verifies with MC's real swept collision ({@link SweptCollision}).
 *
 * <p>Travel axis = the larger seed-&gt;land separation; the perpendicular is the keep-out axis. Each
 * obstacle is wrapped on the perpendicular side nearest the launch corridor (player center vs the
 * obstacle edge +- the half-width). The timing is the hard part, because MC's swept X-clamp tests the
 * player's <b>start-of-tick</b> travel coordinate: a perpendicular move while the travel extent still
 * overlaps an obstacle clips. Two devices fix it:
 * <ul>
 *   <li><b>forced crossing tick k*</b> pins the travel coordinate just past the tightest obstacle's exit
 *       edge so the descent rate (hence the keep-out window) is deterministic; k* is enumerated;</li>
 *   <li><b>+1 dilation</b> keeps each keep-out active one tick past the in-band ticks, covering the move
 *       whose start-of-tick coordinate is still in the band.</li>
 * </ul>
 * The objective is swept too: the pad edge often sits at the reachability frontier, so the toward-pad
 * objective lands on it while the away one overshoots by ~1e-6. Every candidate is checked against
 * {@link SweptCollision} + the landing footprint, so {@link Result#ok()} is never reported for a path
 * that would clip or miss; on a layout it cannot wrap it honestly reports the best non-ok attempt.
 *
 * <p>Inner solves go through a swappable {@link InnerSolve} strategy. The planner runs first with the
 * microsecond closed-form ({@link ClosedFormSolve}); if that pass cannot produce a clean+landed result it
 * re-runs with the proven CMA-ES multistart, so the fast path can only ever speed solving up, never
 * regress it. (The closed form returns null on an infeasible trial constraint set, which the search treats
 * as "skip this candidate" rather than a hard failure -- exactly the prune signal a routing search wants.)
 */
public final class BlockSolver {

    public static boolean DEBUG = false;

    private static final double HALF = 0.3;
    private static final int MAX_ITERS = 5;
    private static final int K_WINDOW = 3;
    private static final int NON_CROSS_SLACK = 3;

    /** Pluggable inner solver: map a constraint set to facings (or null if it cannot certify feasibility). */
    private interface InnerSolve {
        double[] solve(JumpSpec spec, double[] warmStart);
    }

    public static final class Obstacle {
        public final AABB block;

        public Obstacle(AABB block) {
            this.block = block;
        }
    }

    public static final class Face {
        public final int segTick;
        public final boolean axisX;
        public final boolean upper;
        public final double value;

        public Face(int segTick, boolean axisX, boolean upper, double value) {
            this.segTick = segTick;
            this.axisX = axisX;
            this.upper = upper;
            this.value = value;
        }
    }

    public static final class Result {
        public final double[] yaws;
        public final ForwardPath path;
        public final List<Face> faces;
        public final Objective objective;
        public final boolean clean;
        public final boolean landed;

        Result(double[] yaws, ForwardPath path, List<Face> faces, Objective objective, boolean clean, boolean landed) {
            this.yaws = yaws;
            this.path = path;
            this.faces = faces;
            this.objective = objective;
            this.clean = clean;
            this.landed = landed;
        }

        public boolean ok() {
            return clean && landed;
        }
    }

    /** One obstacle's wrap: a perpendicular keep-out active while the player is alongside it. */
    private static final class Gate {
        final boolean keepoutAxisX; // keep-out on X (travel/band on Z) or vice versa
        final boolean ge;           // >= value (hi side) or <= value (lo side)
        final double value;
        final double bandLo, bandHi; // expanded travel-axis band (open interval)
        final double yLo, yHi;
        final boolean crossing;      // seed & land on opposite travel sides => must wrap past it
        final double exitEdge;       // travel-axis value the player passes to clear it
        final boolean descending;    // travel coordinate decreases seed->land

        Gate(boolean keepoutAxisX, boolean ge, double value, double bandLo, double bandHi,
             double yLo, double yHi, boolean crossing, double exitEdge, boolean descending) {
            this.keepoutAxisX = keepoutAxisX;
            this.ge = ge;
            this.value = value;
            this.bandLo = bandLo;
            this.bandHi = bandHi;
            this.yLo = yLo;
            this.yHi = yHi;
            this.crossing = crossing;
            this.exitEdge = exitEdge;
            this.descending = descending;
        }
    }

    /** A candidate constraint set: footprints + the derived keep-outs (tracked as Faces in parallel). */
    private static final class Cand {
        final List<JumpConstraint> cons = new ArrayList<>();
        final List<Face> faces = new ArrayList<>();
        final Set<String> sig = new LinkedHashSet<>();
    }

    private static final class Eval {
        final double[] yaws;
        final ForwardPath path;
        final boolean clean;
        final boolean landed;

        Eval(double[] yaws, ForwardPath path, boolean clean, boolean landed) {
            this.yaws = yaws;
            this.path = path;
            this.clean = clean;
            this.landed = landed;
        }
    }

    public Result solve(ForwardModel model, JumpPhysicsInputs sc, List<JumpConstraint> footprints,
                        double[] landFootprint, List<Obstacle> obstacles, double[] heights, List<Objective> objectives,
                        SolveCore.Budget budget, double sigmaDeg, double feasTol, int maxSolves, AtomicBoolean cancel) {
        // Fast path: microsecond closed form on every inner solve. It only applies to position-wall sets on
        // the byte-exact model, and returns null on an infeasible trial set (the search skips it).
        InnerSolve closedForm = (spec, warm) -> (model instanceof ExactJumpModel)
                ? ClosedFormSolve.optimize((ExactJumpModel) model, spec, feasTol, cancel) : null;
        Result fast = runPlanner(closedForm, model, sc, footprints, landFootprint, obstacles, heights,
                objectives, maxSolves, cancel);
        if ((fast != null && fast.ok()) || cancel.get()) return fast;

        // Fallback: the proven CMA-ES multistart, so a layout the closed form cannot route never regresses.
        InnerSolve cmaes = (spec, warm) ->
                SolveCore.optimize(model, spec, budget, sigmaDeg, feasTol, cancel, warm);
        Result slow = runPlanner(cmaes, model, sc, footprints, landFootprint, obstacles, heights,
                objectives, maxSolves, cancel);
        if (slow != null && (fast == null || slow.ok() || better(slow, fast))) return slow;
        return fast;
    }

    private Result runPlanner(InnerSolve inner, ForwardModel model, JumpPhysicsInputs sc, List<JumpConstraint> footprints,
                              double[] landFootprint, List<Obstacle> obstacles, double[] heights, List<Objective> objectives,
                              int maxSolves, AtomicBoolean cancel) {
        int n = sc.numTicks;
        List<AABB> blocks = new ArrayList<>();
        for (Obstacle o : obstacles) blocks.add(o.block);

        double[] feetY = feetY(model, sc);

        double landCenterX = (landFootprint[0] + landFootprint[1]) * 0.5;
        double landCenterZ = (landFootprint[2] + landFootprint[3]) * 0.5;
        boolean travelZ = Math.abs(landCenterZ - sc.startPos.z) >= Math.abs(landCenterX - sc.startPos.x);
        double seedA = travelZ ? sc.startPos.z : sc.startPos.x;
        double landA = travelZ ? landCenterZ : landCenterX;
        boolean descending = seedA > landA;

        // Feasibility (a swept-clean, landed path) must not depend on which coordinate we optimize: the
        // same keep-out constraints admit the same paths. So first find a feasible constraint set + path,
        // trying the user's objective then the endpoint objectives (each with its OWN crossing-tick /
        // timing search), then optimize the user's chosen direction on that fixed set.
        List<Objective> candidates = new ArrayList<>();
        for (Objective o : objectives) addObjective(candidates, o);
        for (Objective o : endpointObjectives(n)) addObjective(candidates, o);

        // Keep-out side per obstacle. Start-proximity is the default and is right when the run stays on the
        // start side. When the run CROSSES an obstacle's perpendicular band (start and land on opposite
        // sides) the side hinges on the not-yet-known path timing, so it is ambiguous: try the default
        // first, then flip the crossing obstacles' sides until a swept-clean, landed homotopy is found.
        boolean[] base = new boolean[blocks.size()];
        List<Integer> ambiguous = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            base[i] = defaultNearHi(blocks.get(i), sc, travelZ);
            if (ambiguousSide(blocks.get(i), sc, landCenterX, landCenterZ, travelZ)) ambiguous.add(i);
        }

        Result bestNonOk = null;
        for (boolean[] sides : sideCombos(base, ambiguous)) {
            if (cancel.get()) break;
            List<Gate> gates = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                gates.add(gateFor(blocks.get(i), sc, landCenterX, landCenterZ, travelZ, descending, sides[i]));
            }
            Gate tightest = null;
            for (Gate g : gates) {
                if (!g.crossing) continue;
                if (tightest == null || reachedFirst(g.exitEdge, tightest.exitEdge, descending)) tightest = g;
            }

            int[] solvesLeft = {Math.max(maxSolves, 120)};
            Feasible feas = findFeasible(inner, model, sc, footprints, landFootprint, gates, tightest, blocks, heights,
                    feetY, n, candidates, cancel, solvesLeft);
            if (feas.ok) {
                Objective userObj = objectives.isEmpty() ? feas.objective : objectives.get(0);
                if (!cancel.get() && solvesLeft[0] > 0) {
                    Eval opt = evaluate(inner, model, sc, feas.cons, userObj, blocks, heights,
                            landFootprint, cancel, feas.yaws, solvesLeft);
                    if (opt.yaws != null && opt.clean && opt.landed) {
                        return new Result(opt.yaws, opt.path, feas.faces, userObj, true, true);
                    }
                }
                // The user's direction is infeasible (e.g. it would leave the pad); hand back the feasible path.
                return new Result(feas.yaws, feas.path, feas.faces, userObj, true, true);
            }
            if (feas.bestNonOk != null && (bestNonOk == null || better(feas.bestNonOk, bestNonOk))) {
                bestNonOk = feas.bestNonOk;
            }
        }
        return bestNonOk;
    }

    /** Side combinations to try: the default first, then flips of the ambiguous (crossing) obstacles ordered
     *  by how many are flipped. Capped so a pile of crossing obstacles cannot explode the search. */
    private static List<boolean[]> sideCombos(boolean[] base, List<Integer> ambiguous) {
        List<boolean[]> out = new ArrayList<>();
        int k = Math.min(ambiguous.size(), 3); // 2^3 = 8 homotopy classes is plenty in practice
        for (int flips = 0; flips <= k; flips++) {
            for (int mask = 0; mask < (1 << k); mask++) {
                if (Integer.bitCount(mask) != flips) continue;
                boolean[] s = base.clone();
                for (int b = 0; b < k; b++) {
                    if ((mask & (1 << b)) != 0) {
                        int idx = ambiguous.get(b);
                        s[idx] = !s[idx];
                    }
                }
                out.add(s);
            }
        }
        return out;
    }

    /** Original heuristic: keep the run on whichever perpendicular edge of the obstacle is nearer the start. */
    private static boolean defaultNearHi(AABB o, JumpPhysicsInputs sc, boolean travelZ) {
        if (travelZ) {
            double hi = o.max.x + HALF, lo = o.min.x - HALF;
            return Math.abs(sc.startPos.x - hi) <= Math.abs(sc.startPos.x - lo);
        }
        double hi = o.max.z + HALF, lo = o.min.z - HALF;
        return Math.abs(sc.startPos.z - hi) <= Math.abs(sc.startPos.z - lo);
    }

    /** The run crosses this obstacle's perpendicular band (start and land sit on opposite sides), so which
     *  side it passes on depends on path timing and cannot be read off the geometry: try both. */
    private static boolean ambiguousSide(AABB o, JumpPhysicsInputs sc, double landCenterX, double landCenterZ, boolean travelZ) {
        double sp, lp, lo, hi;
        if (travelZ) {
            sp = sc.startPos.x; lp = landCenterX; lo = o.min.x - HALF; hi = o.max.x + HALF;
        } else {
            sp = sc.startPos.z; lp = landCenterZ; lo = o.min.z - HALF; hi = o.max.z + HALF;
        }
        return sideOf(sp, lo, hi) != sideOf(lp, lo, hi);
    }

    private static void addObjective(List<Objective> list, Objective o) {
        for (Objective e : list) if (e.axis == o.axis && e.sense == o.sense && e.tick == o.tick) return;
        list.add(o);
    }

    /** A feasible (swept-clean, landed) constraint set + its path, or ok=false with the best non-ok attempt. */
    private static final class Feasible {
        final boolean ok;
        final List<JumpConstraint> cons;
        final List<Face> faces;
        final double[] yaws;
        final ForwardPath path;
        final Objective objective;
        final Result bestNonOk;

        Feasible(boolean ok, List<JumpConstraint> cons, List<Face> faces, double[] yaws, ForwardPath path,
                 Objective objective, Result bestNonOk) {
            this.ok = ok;
            this.cons = cons;
            this.faces = faces;
            this.yaws = yaws;
            this.path = path;
            this.objective = objective;
            this.bestNonOk = bestNonOk;
        }
    }

    /** Try each candidate objective (its own crossing-tick window + timing iteration) until one yields a
     *  swept-clean, landed path; return that constraint set. Else ok=false with the best non-ok attempt. */
    private Feasible findFeasible(InnerSolve inner, ForwardModel model, JumpPhysicsInputs sc, List<JumpConstraint> footprints,
                                  double[] landFp, List<Gate> gates, Gate tightest, List<AABB> blocks, double[] heights,
                                  double[] feetY, int n, List<Objective> candidates, AtomicBoolean cancel, int[] solvesLeft) {
        Result bestNonOk = null;
        for (Objective objective : candidates) {
            if (cancel.get() || solvesLeft[0] <= 0) break;
            Eval seed = evaluate(inner, model, sc, footprints, objective, blocks, heights, landFp, cancel, null, solvesLeft);
            if (seed.yaws == null) continue; // this objective's relaxed seed is infeasible; try the next
            ForwardPath sched0 = seed.path;

            List<Integer> kStars = new ArrayList<>();
            if (tightest == null) {
                kStars.add(-1);
            } else {
                int k0 = crossingTick(sched0, tightest, n);
                for (int dk = 0; dk <= K_WINDOW; dk++) {
                    addK(kStars, k0, n);
                    if (dk > 0) {
                        addK(kStars, k0 - dk, n);
                        addK(kStars, k0 + dk, n);
                    }
                }
            }

            for (int kStar : kStars) {
                if (cancel.get() || solvesLeft[0] <= 0) break;
                ForwardPath sched = sched0;
                Set<String> prevSig = null;
                for (int iter = 0; iter < MAX_ITERS; iter++) {
                    if (cancel.get() || solvesLeft[0] <= 0) break;
                    Cand cand = build(footprints, gates, tightest, kStar, sched, sc, feetY, heights, n);
                    Eval e = evaluate(inner, model, sc, cand.cons, objective, blocks, heights, landFp, cancel, null, solvesLeft);
                    if (e.yaws == null) break; // infeasible trial set; abandon this k* and try the next
                    if (DEBUG) System.out.println("  obj=" + objective.axis + "/" + objective.sense + " k*=" + kStar
                            + " iter=" + iter + " faces=" + cand.faces.size() + " clean=" + e.clean + " landed=" + e.landed);
                    if (e.clean && e.landed) return new Feasible(true, cand.cons, cand.faces, e.yaws, e.path, objective, bestNonOk);
                    Result r = new Result(e.yaws, e.path, cand.faces, objective, e.clean, e.landed);
                    if (bestNonOk == null || better(r, bestNonOk)) bestNonOk = r;
                    if (e.path == null) break;
                    if (cand.sig.equals(prevSig)) break;
                    prevSig = cand.sig;
                    sched = e.path;
                }
            }
        }
        return new Feasible(false, null, null, null, null,
                candidates.isEmpty() ? null : candidates.get(0), bestNonOk);
    }

    private static List<Objective> endpointObjectives(int n) {
        List<Objective> o = new ArrayList<>();
        o.add(new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, n));
        o.add(new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, n));
        o.add(new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n));
        o.add(new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, n));
        return o;
    }

    private static boolean better(Result a, Result b) {
        if (a.clean != b.clean) return a.clean;
        return a.landed && !b.landed;
    }

    /** Build the constraint set + parallel Face list for one (gates, k*, schedule) timing. */
    private static Cand build(List<JumpConstraint> footprints, List<Gate> gates, Gate tightest, int kStar,
                              ForwardPath sched, JumpPhysicsInputs sc, double[] feetY, double[] heights, int n) {
        Cand c = new Cand();
        c.cons.addAll(footprints);
        if (tightest != null && kStar > 0) {
            boolean axisX = !tightest.keepoutAxisX;
            boolean up = !tightest.descending; // descending => LE (upper=false)
            JumpConstraint fe = face(axisX, up, kStar, tightest.exitEdge, "exit@" + kStar);
            c.cons.add(fe);
            c.faces.add(new Face(kStar, axisX, up, tightest.exitEdge));
            c.sig.add("exit@" + kStar);
        }
        for (int gi = 0; gi < gates.size(); gi++) {
            Gate g = gates.get(gi);
            int cap = n;
            if (g.crossing && kStar > 0) cap = (g == tightest) ? kStar : Math.min(n, kStar + NON_CROSS_SLACK);
            for (int t = 0; t <= n && t <= cap; t++) {
                if (!yActive(g, feetY, heights, t)) continue;
                double aT = g.keepoutAxisX ? sched.posZ[t] : sched.posX[t];
                double aPrev = (t > 0) ? (g.keepoutAxisX ? sched.posZ[t - 1] : sched.posX[t - 1])
                        : (g.keepoutAxisX ? sc.startPos.z : sc.startPos.x);
                boolean in = aT > g.bandLo && aT < g.bandHi;
                boolean inPrev = aPrev > g.bandLo && aPrev < g.bandHi;
                if (in || inPrev) {
                    String nm = "keep" + gi + "@" + t;
                    c.cons.add(face(g.keepoutAxisX, g.ge, t, g.value, nm));
                    c.faces.add(new Face(t, g.keepoutAxisX, g.ge, g.value));
                    c.sig.add(nm);
                }
            }
        }
        return c;
    }

    private static JumpConstraint face(boolean axisX, boolean ge, int t, double v, String name) {
        return new JumpConstraint(axisX ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z, t, null,
                JumpConstraint.Op.PLUS, ge ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, v, name);
    }

    private static Eval evaluate(InnerSolve inner, ForwardModel model, JumpPhysicsInputs sc, List<JumpConstraint> cons,
                                 Objective objective, List<AABB> blocks, double[] heights, double[] landFp,
                                 AtomicBoolean cancel, double[] warmStart, int[] solvesLeft) {
        if (solvesLeft != null) solvesLeft[0]--;
        double[] yaws = inner.solve(new JumpSpec(sc, cons, objective), warmStart);
        if (yaws == null) return new Eval(null, null, false, false);
        ForwardPath path = model.forward(sc, sc.toGameFacings(yaws));
        int n = sc.numTicks;
        boolean clean = true;
        for (int k = 0; k < n; k++) {
            double h = (heights != null && k < heights.length) ? heights[k] : 1.8;
            SweptCollision.Hit hit = SweptCollision.firstHit(
                    path.posX[k], path.posY[k], path.posZ[k],
                    path.posX[k + 1], path.posY[k + 1], path.posZ[k + 1], HALF, h, blocks);
            if (hit.any()) { clean = false; break; }
        }
        double lx = path.posX[n], lz = path.posZ[n];
        boolean landed = lx >= landFp[0] - 1e-9 && lx <= landFp[1] + 1e-9
                && lz >= landFp[2] - 1e-9 && lz <= landFp[3] + 1e-9;
        return new Eval(yaws, path, clean, landed);
    }

    private static Gate gateFor(AABB o, JumpPhysicsInputs sc, double landCenterX, double landCenterZ,
                                boolean travelZ, boolean descending, boolean nearHi) {
        double yLo = o.min.y, yHi = o.max.y;
        if (travelZ) {
            double bandLo = o.min.z - HALF, bandHi = o.max.z + HALF;
            double hi = o.max.x + HALF, lo = o.min.x - HALF;
            double exitEdge = descending ? bandLo : bandHi;
            boolean crossing = sideOf(sc.startPos.z, bandLo, bandHi) != sideOf(landCenterZ, bandLo, bandHi);
            return new Gate(true, nearHi, nearHi ? hi : lo, bandLo, bandHi, yLo, yHi, crossing, exitEdge, descending);
        }
        double bandLo = o.min.x - HALF, bandHi = o.max.x + HALF;
        double hi = o.max.z + HALF, lo = o.min.z - HALF;
        double exitEdge = descending ? bandLo : bandHi;
        boolean crossing = sideOf(sc.startPos.x, bandLo, bandHi) != sideOf(landCenterX, bandLo, bandHi);
        return new Gate(false, nearHi, nearHi ? hi : lo, bandLo, bandHi, yLo, yHi, crossing, exitEdge, descending);
    }

    private static double[] feetY(ForwardModel model, JumpPhysicsInputs sc) {
        double[] flat = new double[sc.numTicks];
        java.util.Arrays.fill(flat, sc.startYaw);
        return model.forward(sc, sc.toGameFacings(flat)).posY.clone();
    }

    private static int sideOf(double v, double lo, double hi) {
        if (v > hi) return 1;
        if (v < lo) return -1;
        return 0;
    }

    private static boolean reachedFirst(double edge, double cur, boolean descending) {
        return descending ? edge > cur : edge < cur;
    }

    private static int crossingTick(ForwardPath sched, Gate tightest, int n) {
        boolean axisX = !tightest.keepoutAxisX;
        for (int t = 1; t <= n; t++) {
            double a = axisX ? sched.posX[t] : sched.posZ[t];
            if (tightest.descending ? a <= tightest.exitEdge : a >= tightest.exitEdge) return t;
        }
        return Math.max(1, n - 2);
    }

    private static boolean yActive(Gate g, double[] feetY, double[] heights, int t) {
        double feet = feetY[t];
        double top = feet + (heights != null && t < heights.length ? heights[t] : 1.8);
        return feet < g.yHi && top > g.yLo;
    }

    private static void addK(List<Integer> ks, int k, int n) {
        if (k >= 1 && k <= n && !ks.contains(k)) ks.add(k);
    }
}
