package de.legoshi.parkourcalc.anglesolver.metriclab;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratMeasure;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratMeasurements;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.List;

public final class MeasurementEngine {

    public static final double COARSE_STEP_DEG = StratMeasure.COARSE_STEP_DEG;
    public static final double WINDOW_CAP_DEG = StratMeasure.WINDOW_CAP_DEG;
    public static final double BISECT_TOL_DEG = StratMeasure.BISECT_TOL_DEG;
    public static final int JITTER_SAMPLES = StratMeasure.JITTER_SAMPLES;
    public static final double TURN_EPS_DEG = StratMeasure.TURN_EPS_DEG;
    public static final int CENTER_PASSES = StratMeasure.CENTER_PASSES;
    public static final double CENTER_STOP_DEG = StratMeasure.CENTER_STOP_DEG;
    public static final int SHIFT_CAP_TICKS = StratMeasure.SHIFT_CAP_TICKS;
    public static final int JUMP_HOLD_COOLDOWN_TICKS = StratMeasure.JUMP_HOLD_COOLDOWN_TICKS;
    public static final int SMOOTH_PASSES = StratMeasure.SMOOTH_PASSES;
    public static final double SMOOTH_STOP_DEG = StratMeasure.SMOOTH_STOP_DEG;
    public static final float KEY_INPUT_SCALE = StratMeasure.KEY_INPUT_SCALE;
    public static final float SNEAK_INPUT_SCALE = StratMeasure.SNEAK_INPUT_SCALE;

    private MeasurementEngine() {
    }

    public static JumpMeasurements measure(HpkHumanSet.Sample sample) {
        JumpMeasurements m = measure(sample.save, sample.name);
        m.dLevel = sample.dLevel;
        m.meta = sample.meta;
        return m;
    }

    public static JumpMeasurements measure(SaveFile file, String name) {
        StratMeasurements s;
        try {
            s = StratMeasure.measure(file, name);
        } catch (StratMeasure.BadSaveException ex) {
            throw new BadSampleException(ex.getMessage());
        }
        JumpMeasurements m = new JumpMeasurements();
        m.name = s.name;
        m.mcVersion = s.mcVersion;
        m.numTicks = s.numTicks;
        m.startYawDeg = s.startYawDeg;
        m.jumps = s.jumps;
        m.takeoffTick = s.takeoffTick;
        m.facingConstraints = s.facingConstraints;
        m.minMargin = s.minMargin;
        m.windowLo = s.windowLo;
        m.windowHi = s.windowHi;
        m.jitterDeg = s.jitterDeg;
        m.centerDriftDeg = s.centerDriftDeg;
        m.winMinDeg = s.winMinDeg;
        m.winGeoDeg = s.winGeoDeg;
        m.winMinMomentumDeg = s.winMinMomentumDeg;
        m.winGeoMomentumDeg = s.winGeoMomentumDeg;
        m.winMinJumpDeg = s.winMinJumpDeg;
        m.winGeoJumpDeg = s.winGeoJumpDeg;
        m.inputEdgesMomentum = s.inputEdgesMomentum;
        m.inputEdgesJump = s.inputEdgesJump;
        m.turnTicksMomentum = s.turnTicksMomentum;
        m.turnTicksJump = s.turnTicksJump;
        m.jumpAngle = s.jumpAngle;
        m.yawTravelDeg = s.yawTravelDeg;
        m.yawReversals = s.yawReversals;
        m.yawJerkDeg = s.yawJerkDeg;
        m.yawVelSdDeg = s.yawVelSdDeg;
        m.smoothTravelDeg = s.smoothTravelDeg;
        m.smoothReversals = s.smoothReversals;
        m.smoothJerkDeg = s.smoothJerkDeg;
        m.smoothVelSdDeg = s.smoothVelSdDeg;
        m.smoothMaxTurnDeg = s.smoothMaxTurnDeg;
        m.smoothYaw = s.smoothYaw;
        m.smoothMs = s.smoothMs;
        m.shiftEdgeRow = s.shiftEdgeRow;
        m.shiftEdgeMomentum = s.shiftEdgeMomentum;
        m.shiftEdgeKeys = s.shiftEdgeKeys;
        m.shiftLo = s.shiftLo;
        m.shiftHi = s.shiftHi;
        m.shiftLoCensored = s.shiftLoCensored;
        m.shiftHiCensored = s.shiftHiCensored;
        m.shiftLoFree = s.shiftLoFree;
        m.shiftHiFree = s.shiftHiFree;
        m.shiftMinMomentumTicks = s.shiftMinMomentumTicks;
        m.shiftGeoMomentumTicks = s.shiftGeoMomentumTicks;
        m.shiftMinJumpTicks = s.shiftMinJumpTicks;
        m.shiftGeoJumpTicks = s.shiftGeoJumpTicks;
        m.shiftGeoMomentumEffTicks = s.shiftGeoMomentumEffTicks;
        m.shiftGeoJumpEffTicks = s.shiftGeoJumpEffTicks;
        m.shiftMs = s.shiftMs;
        return m;
    }

    static double[] smoothestLine(ExactJumpModel model, JumpPhysicsInputs sc,
                                  JumpConstraintCompiler.Compiled compiled, double[] anchor, String name) {
        return StratMeasure.smoothestLine(model, sc, compiled, anchor, name);
    }

    public static double minNarrowSide(double[] lo, double[] hi) {
        return StratMeasure.minNarrowSide(lo, hi);
    }

    public static double effWidth(JumpMeasurements m, int i) {
        if (m.shiftLoFree[i] || m.shiftHiFree[i]) {
            return 2.0 * SHIFT_CAP_TICKS;
        }
        return m.shiftLo[i] + m.shiftHi[i];
    }

    static boolean applyShift(SaveFile copy, int t, int shift, int startTick, JumpPhysicsInputs sc0,
                              List<double[]> supportOut) {
        return StratMeasure.applyShift(copy, t, shift, startTick, sc0, supportOut);
    }

    static void syncMovementSample(SaveFile copy, int row, int srcRow) {
        StratMeasure.syncMovementSample(copy, row, srcRow);
    }

    static void syncMovementSample(SaveFile copy, int row, int srcRow, boolean syncSprint) {
        StratMeasure.syncMovementSample(copy, row, srcRow, syncSprint);
    }

    static boolean supported(ForwardPath path, List<double[]> support) {
        return StratMeasure.supported(path, support);
    }

    static boolean groundedAt(JumpPhysicsInputs sc, int k) {
        return StratMeasure.groundedAt(sc, k);
    }

    static double violation(ExactJumpModel model, JumpPhysicsInputs sc,
                            JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        return StratMeasure.violation(model, sc, compiled, yaws);
    }

    static double[] recordedYaws(SaveFile file, String name, int n) {
        try {
            return StratMeasure.recordedYaws(file, name, n);
        } catch (StratMeasure.BadSaveException ex) {
            throw new BadSampleException(ex.getMessage());
        }
    }

    static JumpSpec buildSpec(SaveFile file, ExactJumpModel model) {
        return StratMeasure.buildSpec(file, model);
    }
}
