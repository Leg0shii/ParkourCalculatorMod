package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SweptCollision;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** The ground-truth validator (== real MC for any strictly-outside path):
 *  SolveCore.optimize -> forward -> {@link SweptCollision} over every move vs the collision blocks +
 *  landing-footprint membership. {@code SweptCollision} is the byte-exact MC swept clamp and
 *  {@code ExactJumpModel} equals the live SimulatorEntity for collision-free arcs, so a {@link Validation}
 *  reported VALID here works in-game; there is no need for the user or an in-game run. */
public final class DeriveOracle {

    private static final double LAND_EPS = 1.0e-9;

    private final JumpPhysicsInputs scenario;
    private final List<AABB> obstacles;
    private final AABB land;
    private final double[] heights;
    private final double half;
    private final int numTicks;
    private final ForwardModel model;
    private final SolveCore.Budget budget;
    private final double sigmaDeg;
    private final double feasTol;
    private final AtomicBoolean cancel;

    public DeriveOracle(JumpPhysicsInputs scenario, List<AABB> obstacles, AABB land, double[] heights,
                        double half, ForwardModel model, SolveCore.Budget budget, double sigmaDeg,
                        double feasTol, AtomicBoolean cancel) {
        this.scenario = scenario;
        this.obstacles = obstacles;
        this.land = land;
        this.heights = heights;
        this.half = half;
        this.numTicks = scenario.numTicks;
        this.model = model;
        this.budget = budget;
        this.sigmaDeg = sigmaDeg;
        this.feasTol = feasTol;
        this.cancel = cancel;
    }

    /** Solve the given constraints+objective with the fast CMA-ES oracle, then validate the result. */
    public Validation validate(List<JumpConstraint> constraints, Objective objective) {
        JumpSpec spec = new JumpSpec(scenario, new ArrayList<>(constraints), objective);
        double[] yaws = SolveCore.optimize(model, spec, budget, sigmaDeg, feasTol, cancel);
        if (yaws == null) return Validation.cancelled();
        ForwardPath path = model.forward(scenario, scenario.toGameFacings(yaws));
        return check(yaws, path);
    }

    /** Validate an externally produced set of GAME facings (e.g. recorded in-game yaws), forwarded directly. */
    public Validation validateGameFacings(double[] gameFacings) {
        ForwardPath path = model.forward(scenario, gameFacings);
        return check(gameFacings, path);
    }

    private Validation check(double[] yaws, ForwardPath path) {
        int hitK = -1;
        char hitAxis = SweptCollision.NONE;
        int hitObs = -1;
        for (int k = 0; k < numTicks; k++) {
            double h = (heights != null && k < heights.length) ? heights[k] : 1.8;
            SweptCollision.Hit hit = SweptCollision.firstHit(
                    path.posX[k], path.posY[k], path.posZ[k],
                    path.posX[k + 1], path.posY[k + 1], path.posZ[k + 1], half, h, obstacles);
            if (hit.any()) {
                hitK = k;
                hitAxis = hit.axis;
                hitObs = hit.blockIndex;
                break;
            }
        }
        boolean clean = hitK < 0;
        double lx = path.posX[numTicks];
        double lz = path.posZ[numTicks];
        double[] e = DeriveSupport.expand(land, half);
        boolean landed = lx >= e[0] - LAND_EPS && lx <= e[1] + LAND_EPS
                && lz >= e[2] - LAND_EPS && lz <= e[3] + LAND_EPS;
        return new Validation(clean && landed, clean, landed, yaws, path, hitK, hitAxis, hitObs, lx, lz);
    }
}
