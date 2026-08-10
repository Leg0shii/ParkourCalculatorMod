package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;

import java.util.ArrayList;
import java.util.List;

public final class ColdProblem {

    public static final class Wall {
        public final int segTick;
        public final boolean axisX;
        public final double lo;
        public final double hi;

        Wall(int segTick, boolean axisX, double lo, double hi) {
            this.segTick = segTick;
            this.axisX = axisX;
            this.lo = lo;
            this.hi = hi;
        }
    }

    public final AngleSolverState state;
    public final SaveFile solverOnly;
    public final ExactJumpModel model;
    public final String mcVersion;
    public final int startTick;
    public final int landingTick;
    public final int numTicks;
    public final double[] slip;
    public final boolean[] ground;
    public final int[] pressSegTicks;
    public final int lastPressSeg;
    public final boolean lastPressYawTied;
    public final double rectXLo;
    public final double rectXHi;
    public final double rectZLo;
    public final double rectZHi;
    public final List<Wall> momentumWalls;
    public final List<Wall> tailWalls;
    public final boolean tailYawsFree;
    public final boolean singleHeld;

    private static final double EQ_TOL = 1.0e-4;

    private ColdProblem(AngleSolverState state, SaveFile solverOnly, ExactJumpModel model, String mcVersion,
                        int startTick, int landingTick, double[] slip, boolean[] ground,
                        int[] pressSegTicks, boolean lastPressYawTied,
                        double rectXLo, double rectXHi, double rectZLo, double rectZHi,
                        List<Wall> momentumWalls, List<Wall> tailWalls, boolean tailYawsFree,
                        boolean singleHeld) {
        this.state = state;
        this.solverOnly = solverOnly;
        this.model = model;
        this.mcVersion = mcVersion;
        this.startTick = startTick;
        this.landingTick = landingTick;
        this.numTicks = landingTick - startTick;
        this.slip = slip;
        this.ground = ground;
        this.pressSegTicks = pressSegTicks;
        this.lastPressSeg = pressSegTicks[pressSegTicks.length - 1];
        this.lastPressYawTied = lastPressYawTied;
        this.rectXLo = rectXLo;
        this.rectXHi = rectXHi;
        this.rectZLo = rectZLo;
        this.rectZHi = rectZHi;
        this.momentumWalls = momentumWalls;
        this.tailWalls = tailWalls;
        this.tailYawsFree = tailYawsFree;
        this.singleHeld = singleHeld;
    }

    public static ColdProblem fromSave(SaveFile file) {
        SaveFile solverOnly = new SaveFile();
        solverOnly.mcVersion = file.mcVersion;
        solverOnly.angleSolver = file.angleSolver;
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(solverOnly, state);
        state.clearResult();
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);

        int startTick = state.getStartTick();
        int landingTick = state.getLandingTick();
        int numTicks = landingTick - startTick;
        if (numTicks <= 0) throw new IllegalArgumentException("empty segment " + startTick + ".." + landingTick);
        if (startTick != 0) throw new IllegalArgumentException("free start needs startTick 0, got " + startTick);

        double[] slip = new double[numTicks];
        boolean[] ground = new boolean[numTicks];
        for (int k = 0; k < numTicks; k++) {
            int t = startTick + k;
            Slipperiness s = effSlip(state, t);
            if (effMedium(state, t) != Medium.NONE) {
                throw new IllegalArgumentException("unsupported medium override at tick " + t);
            }
            slip[k] = s.slip;
            ground[k] = s.slip < 1.0;
        }

        List<Integer> presses = new ArrayList<Integer>();
        for (int k = 0; k < numTicks; k++) {
            boolean airNext = k + 1 >= numTicks || !ground[k + 1];
            if (ground[k] && airNext && k + 1 < numTicks) presses.add(k);
        }
        if (presses.isEmpty()) throw new IllegalArgumentException("no press ticks derivable from the slip pattern");
        int[] pressSeg = new int[presses.size()];
        for (int i = 0; i < pressSeg.length; i++) pressSeg[i] = presses.get(i);
        int lastPress = pressSeg[pressSeg.length - 1];

        for (int k = 1; k < lastPress; k++) {
            if (!hasDfZero(state, startTick + k)) {
                throw new IllegalArgumentException("momentum tick " + (startTick + k)
                        + " is not dF=0 tied; fixed-facing search does not apply");
            }
        }
        boolean lastTied = hasDfZero(state, startTick + lastPress);
        boolean airAllTied = true;
        for (int k = lastPress + 1; k < numTicks; k++) {
            if (!hasDfZero(state, startTick + k)) {
                airAllTied = false;
                break;
            }
        }
        boolean singleHeld = lastTied && airAllTied;

        double[] rx = rectInterval(state, startTick, Constraint.Field.X);
        double[] rz = rectInterval(state, startTick, Constraint.Field.Z);
        if (rx == null || rz == null || rx[1] <= rx[0] || rz[1] <= rz[0]) {
            throw new IllegalArgumentException("tick " + startTick + " has no free X/Z start rect");
        }

        List<Wall> walls = new ArrayList<Wall>();
        List<Wall> tail = new ArrayList<Wall>();
        boolean tailFree = true;
        for (Integer tickKey : state.populatedTicks()) {
            int seg = tickKey - startTick;
            if (seg < 0 || seg > numTicks) continue;
            TickConstraints tc = state.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled()) continue;
                if (seg > lastPress && seg < numTicks
                        && (c.getField() == Constraint.Field.DF || c.getField() == Constraint.Field.F)) {
                    tailFree = false;
                }
                if (c.isRelative()) continue;
                if (c.getField() != Constraint.Field.X && c.getField() != Constraint.Field.Z) continue;
                boolean axisX = c.getField() == Constraint.Field.X;
                if (seg == 0) continue;
                double[] iv = boundsOf(c);
                if (iv == null) continue;
                if (seg <= lastPress) {
                    walls.add(new Wall(seg, axisX, iv[0], iv[1]));
                } else {
                    tail.add(new Wall(seg, axisX, iv[0], iv[1]));
                }
            }
        }

        return new ColdProblem(state, solverOnly, model, file.mcVersion, startTick, landingTick, slip, ground,
                pressSeg, lastTied, rx[0], rx[1], rz[0], rz[1], walls, tail, tailFree, singleHeld);
    }

    private static Slipperiness effSlip(AngleSolverState state, int tick) {
        TickConstraints tc = state.tickConstraintsOrNull(tick);
        StateOverride ov = tc == null ? null : tc.getOverride();
        if (ov != null && ov.overridesSlipperiness()) return ov.getSlipperiness();
        return state.getDefaultSlipperiness();
    }

    private static Medium effMedium(AngleSolverState state, int tick) {
        TickConstraints tc = state.tickConstraintsOrNull(tick);
        StateOverride ov = tc == null ? null : tc.getOverride();
        if (ov != null && ov.overridesMedium()) return ov.getMedium();
        return Medium.NONE;
    }

    private static boolean hasDfZero(AngleSolverState state, int tick) {
        TickConstraints tc = state.tickConstraintsOrNull(tick);
        if (tc == null) return false;
        for (Constraint c : tc.getConstraints()) {
            if (!c.isEnabled()) continue;
            if (c.getField() != Constraint.Field.DF) continue;
            if (c.isRange()) continue;
            if (c.getOp() == Constraint.Op.EQ && c.getValue() == 0.0) return true;
        }
        return false;
    }

    private static double[] rectInterval(AngleSolverState state, int tick, Constraint.Field field) {
        TickConstraints tc = state.tickConstraintsOrNull(tick);
        if (tc == null) return null;
        boolean hasRange = false;
        double lo = Double.NEGATIVE_INFINITY;
        double hi = Double.POSITIVE_INFINITY;
        for (Constraint c : tc.getConstraints()) {
            if (!c.isEnabled() || c.isRelative() || c.getField() != field) continue;
            if (c.isRange()) {
                hasRange = true;
                lo = Math.max(lo, c.getLo());
                hi = Math.min(hi, c.getHi());
            } else {
                switch (c.getOp()) {
                    case GT:
                    case GE:
                        lo = Math.max(lo, c.getValue());
                        break;
                    case LT:
                    case LE:
                        hi = Math.min(hi, c.getValue());
                        break;
                    case EQ:
                        lo = Math.max(lo, c.getValue());
                        hi = Math.min(hi, c.getValue());
                        break;
                    default:
                        break;
                }
            }
        }
        if (!hasRange) return null;
        return new double[] {lo, hi};
    }

    private static double[] boundsOf(Constraint c) {
        if (c.isRange()) return new double[] {c.getLo(), c.getHi()};
        switch (c.getOp()) {
            case GT:
            case GE:
                return new double[] {c.getValue(), Double.POSITIVE_INFINITY};
            case LT:
            case LE:
                return new double[] {Double.NEGATIVE_INFINITY, c.getValue()};
            case EQ:
                return new double[] {c.getValue() - EQ_TOL, c.getValue() + EQ_TOL};
            default:
                return null;
        }
    }
}
