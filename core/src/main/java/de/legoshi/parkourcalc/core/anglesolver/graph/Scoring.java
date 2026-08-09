package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public final class Scoring {

    public static final double CAP_GAP_TOL = 1.0e-6;
    public static final double REACH_GAP_EPS = 1.0e-6;

    private Scoring() {
    }

    public static String chain(String prev, String name) {
        return prev == null ? name : prev + " -> " + name;
    }

    public static int countJumps(JumpPhysicsInputs sc) {
        int count = 0;
        boolean prev = false;
        for (int t = 0; t < sc.numTicks; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean jump = sc.jumpAt(t) && grounded;
            if (jump && !prev) count++;
            prev = jump;
        }
        return count;
    }

    public static double exactObjective(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbsWrapped) {
        ForwardPath p = model.forward(sc, sc.toGameFacings(yawsAbsWrapped));
        return p.getPos(spec.objective.tick, spec.objective.axis);
    }

    public static double violationOf(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbsWrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbsWrapped));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    public static double[] translationDomain(JumpPhysicsInputs sc, StartBox freeBox) {
        return new double[] {
                freeBox.pxLo - sc.startPos.x, freeBox.pxHi - sc.startPos.x,
                freeBox.pzLo - sc.startPos.z, freeBox.pzHi - sc.startPos.z };
    }

    public static double scoredViol(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec, StartBox freeBox, double[] yaws) {
        if (freeBox == null) return violationOf(model, sc, spec, yaws);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath p = model.forward(sc, gf);
        double[] d = translationDomain(sc, freeBox);
        return SnapRepairPolish.bestTranslation(JumpConstraintCompiler.compile(spec), gf, p,
                d[0], d[1], d[2], d[3]).viol;
    }

    public static double scoredObjective(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec, StartBox freeBox, double[] yaws) {
        if (freeBox == null) return spec.objective.scored(exactObjective(model, sc, spec, yaws), yaws);
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath p = model.forward(sc, gf);
        double[] d = translationDomain(sc, freeBox);
        boolean objX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
        SnapRepairPolish.Trans tr = SnapRepairPolish.bestTranslationObj(JumpConstraintCompiler.compile(spec), gf, p,
                d[0], d[1], d[2], d[3], objX ? 0 : 1, spec.objective.sense == Objective.Sense.MAX);
        return spec.objective.scored(p.getPos(spec.objective.tick, spec.objective.axis) + (objX ? tr.tx : tr.tz), yaws);
    }

    public static boolean reachHeadroom(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec,
                                        double[] yaws, double dualBound, double feasTol) {
        if (yaws == null || Double.isNaN(dualBound)) return false;
        if (violationOf(model, sc, spec, yaws) > feasTol) return false;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double achieved = exactObjective(model, sc, spec, yaws);
        double gap = max ? dualBound - achieved : achieved - dualBound;
        return gap > REACH_GAP_EPS;
    }

    public static double objectiveCap(JumpSpec spec) {
        Objective o = spec.objective;
        JumpConstraint.Mode mode = o.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        boolean max = o.sense == Objective.Sense.MAX;
        double cap = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != mode || c.t1 != o.tick || c.t2 != null) continue;
            if (c.cmp != (max ? JumpConstraint.Cmp.LE : JumpConstraint.Cmp.GE)) continue;
            if (Double.isNaN(cap) || (max ? c.rhs < cap : c.rhs > cap)) cap = c.rhs;
        }
        return cap;
    }

    public static JumpPhysicsInputs pinnedScenario(JumpPhysicsInputs b, double x, double z) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(x, b.startPos.y, z);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = StartBox.pinned(x, z, b.initialVelocity.x, b.initialVelocity.z);
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.surfacePerTick = b.surfacePerTick;
        a.soulsandCellsPerTick = b.soulsandCellsPerTick;
        a.sneakPerTick = b.sneakPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.liveAirSprintFactor = b.liveAirSprintFactor;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return a;
    }

    public static boolean adoptWinningTranslation(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec,
                                                  StartBox freeBox, double[] yaws, double feasTol) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath p = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double curViol = compiled.maxViolation(gf, p);
        double curObj = p.getPos(spec.objective.tick, spec.objective.axis);
        double[] d = translationDomain(sc, freeBox);
        boolean objX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
        boolean objMax = spec.objective.sense == Objective.Sense.MAX;
        SnapRepairPolish.Trans trObj = SnapRepairPolish.bestTranslationObj(compiled, gf, p,
                d[0], d[1], d[2], d[3], objX ? 0 : 1, objMax);
        SnapRepairPolish.Trans trMin = SnapRepairPolish.bestTranslation(compiled, gf, p, d[0], d[1], d[2], d[3]);
        if (tryAdoptTranslation(model, sc, spec, yaws, trObj, curViol, curObj, objMax, feasTol)) return true;
        return tryAdoptTranslation(model, sc, spec, yaws, trMin, curViol, curObj, objMax, feasTol);
    }

    public static boolean adoptStageResult(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec,
                                           StartBox freeBox, double[] gf, double feasTol) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        ForwardPath p0 = model.forward(sc, gf);
        double[] d = freeBox != null ? translationDomain(sc, freeBox) : new double[] {0.0, 0.0, 0.0, 0.0};
        boolean objX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
        boolean objMax = spec.objective.sense == Objective.Sense.MAX;
        SnapRepairPolish.Trans trObj = SnapRepairPolish.bestTranslationObj(compiled, gf, p0,
                d[0], d[1], d[2], d[3], objX ? 0 : 1, objMax);
        SnapRepairPolish.Trans trMin = SnapRepairPolish.bestTranslation(compiled, gf, p0, d[0], d[1], d[2], d[3]);
        SnapRepairPolish.Trans[] cands = {trObj, trMin, new SnapRepairPolish.Trans(0.0, 0.0, 0.0)};
        for (SnapRepairPolish.Trans tr : cands) {
            double x = sc.startPos.x + tr.tx;
            double z = sc.startPos.z + tr.tz;
            JumpPhysicsInputs at = pinnedScenario(sc, x, z);
            if (compiled.maxViolation(gf, model.forward(at, gf)) > feasTol) continue;
            if (tr.tx != 0.0 || tr.tz != 0.0) {
                sc.startPos = new Vec3dCore(x, sc.startPos.y, z);
            }
            sc.startBox = StartBox.pinned(x, z, sc.initialVelocity.x, sc.initialVelocity.z);
            return true;
        }
        return false;
    }

    private static boolean tryAdoptTranslation(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws,
                                               SnapRepairPolish.Trans tr, double curViol, double curObj,
                                               boolean objMax, double feasTol) {
        if (tr.tx == 0.0 && tr.tz == 0.0) return false;
        double x = sc.startPos.x + tr.tx;
        double z = sc.startPos.z + tr.tz;
        JumpPhysicsInputs cand = pinnedScenario(sc, x, z);
        double[] gf = cand.toGameFacings(yaws);
        ForwardPath p = model.forward(cand, gf);
        if (JumpConstraintCompiler.compile(spec).maxViolation(gf, p) > feasTol) return false;
        if (curViol <= feasTol) {
            double obj = p.getPos(spec.objective.tick, spec.objective.axis);
            if (!(objMax ? obj > curObj : obj < curObj)) return false;
        }
        sc.startPos = new Vec3dCore(x, sc.startPos.y, z);
        sc.startBox = StartBox.pinned(x, z, sc.initialVelocity.x, sc.initialVelocity.z);
        return true;
    }
}
