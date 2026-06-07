package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Everything a DERIVE algorithm gets: the fixed launch scenario, the real block hitboxes (collision /
 *  land / optional start), the yaw-independent per-tick feet-Y and torso heights, plus the shared model,
 *  solve budget and oracle so a candidate can search internally (try a homotopy, validate, retry) and
 *  return the one that holds. The oracle here is the same ground truth the harness re-checks with. */
public final class DeriveProblem {

    public final JumpPhysicsInputs scenario;
    public final List<AABB> obstacles;  // COLLISION blocks (real MC hitboxes)
    public final AABB land;             // LAND block
    public final AABB start;            // START block (may be null / unused when jump is on startTick)
    public final double[] feetY;        // index 0..N, yaw-independent
    public final double[] heights;      // index 0..N, torso height (1.8 standing / 1.5 sneaking)
    public final double half;           // player half-width (0.3)
    public final int numTicks;          // N
    public final ForwardModel model;
    public final SolveCore.Budget budget;
    public final double sigmaDeg;
    public final double feasTol;
    public final DeriveOracle oracle;
    public final AtomicBoolean cancel;

    public DeriveProblem(JumpPhysicsInputs scenario, List<AABB> obstacles, AABB land, AABB start,
                         double[] feetY, double[] heights, double half, ForwardModel model,
                         SolveCore.Budget budget, double sigmaDeg, double feasTol, DeriveOracle oracle,
                         AtomicBoolean cancel) {
        this.scenario = scenario;
        this.obstacles = Collections.unmodifiableList(obstacles);
        this.land = land;
        this.start = start;
        this.feetY = feetY;
        this.heights = heights;
        this.half = half;
        this.numTicks = scenario.numTicks;
        this.model = model;
        this.budget = budget;
        this.sigmaDeg = sigmaDeg;
        this.feasTol = feasTol;
        this.oracle = oracle;
        this.cancel = cancel;
    }
}
