package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;

public final class NoTurnProblem {

    public final JumpSpec baseSpec;
    public final JumpPhysicsInputs base;
    public final ExactJumpModel model;
    public final int n;
    public final Objective objective;
    public final StartBox freeBox;

    public final boolean[] contact;
    public final boolean[] jump;
    public final int[] jumpTicks;
    public final int setupEnd;
    public final boolean[] tied;

    public final List<JumpConstraint> walls;

    public String issue;

    private static final double DF_EPS = 1.0e-3;

    private NoTurnProblem(JumpSpec baseSpec, ExactJumpModel model) {
        this.baseSpec = baseSpec;
        this.model = model;
        this.base = baseSpec.asScenario();
        this.n = base.numTicks;
        this.objective = baseSpec.objective;
        this.freeBox = (base.startBox != null && base.startBox.startFree()) ? base.startBox : null;

        this.contact = new boolean[n];
        this.jump = new boolean[n];
        List<Integer> jt = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            contact[t] = !Double.isNaN(base.slipAt(t));
            jump[t] = base.jumpAt(t) && contact[t];
            if (jump[t]) jt.add(t);
        }
        this.jumpTicks = new int[jt.size()];
        for (int i = 0; i < jt.size(); i++) jumpTicks[i] = jt.get(i);

        this.tied = new boolean[n];
        int maxTied = -1;
        for (JumpConstraint c : baseSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.F && c.t2 != null
                    && c.op == JumpConstraint.Op.MINUS && c.cmp == JumpConstraint.Cmp.EQ
                    && Math.abs(c.rhs) <= DF_EPS) {
                markTied(c.t1);
                markTied(c.t2);
                maxTied = Math.max(maxTied, Math.max(c.t1, c.t2));
            }
        }
        int lastJump = jumpTicks.length > 0 ? jumpTicks[jumpTicks.length - 1] : -1;
        int end;
        if (maxTied >= 0) {
            end = Math.max(maxTied, lastJump);
        } else if (lastJump >= 0) {
            end = lastJump;
            for (int t = 1; t <= end; t++) markTied(t);
        } else {
            end = n - 2;
            for (int t = 1; t <= end; t++) markTied(t);
        }
        this.setupEnd = Math.min(Math.max(end, 0), n - 1);

        this.walls = new ArrayList<>();
        for (JumpConstraint c : baseSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.X || c.mode == JumpConstraint.Mode.Z
                    || c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) {
                walls.add(c);
            }
        }
    }

    private void markTied(int t) {
        if (t >= 0 && t < n) tied[t] = true;
    }

    public static NoTurnProblem from(JumpSpec baseSpec, ExactJumpModel model) {
        if (baseSpec == null) {
            NoTurnProblem p = new NoTurnProblem(new JumpSpec(new JumpPhysicsInputs(1), new ArrayList<>(),
                    new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 0)), model);
            p.issue = "no solver problem is set up (start tick, landing tick, objective)";
            return p;
        }
        NoTurnProblem p = new NoTurnProblem(baseSpec, model);
        if (p.setupEnd < 1) p.issue = "the run-up is too short to search (need at least one setup tick)";
        if (p.walls.isEmpty()) p.issue = "no landing constraints found; add X/Z landing walls first";
        return p;
    }

    public boolean isJaTick(int t) {
        return t == setupEnd && !tied[t];
    }

    public boolean assignsCombo(int t) {
        return t >= 0 && t <= setupEnd;
    }

    public JumpSpec buildSpec(int[] combos, boolean[] sprint, int turnCombo, boolean jaFree) {
        JumpPhysicsInputs sc = base.copy();
        float[] fwd = new float[n];
        float[] strafe = new float[n];
        boolean[] spr = new boolean[n];
        for (int t = 0; t < n; t++) {
            if (t > setupEnd) {
                fwd[t] = base.forwardAt(t);
                strafe[t] = base.strafeInputAt(t);
                spr[t] = base.sprintPerTick == null || base.sprintAt(t);
                continue;
            }
            int combo = combos[t];
            if (jump[t]) {
                fwd[t] = NoTurnKeys.SCALE;
                strafe[t] = 0.0F;
            } else {
                fwd[t] = NoTurnKeys.forwardInput(combo);
                strafe[t] = NoTurnKeys.strafeInput(combo);
            }
            spr[t] = sprint[t];
        }
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = strafe;
        sc.sprintPerTick = spr;
        sc.strafePerTick = null;
        sc.sneakPerTick = null;

        return new JumpSpec(sc, noTurnConstraints(jaFree), objective);
    }

    public List<JumpConstraint> noTurnConstraints(boolean jaFree) {
        List<JumpConstraint> cons = new ArrayList<>(walls);
        int prev = -1;
        for (int t = 0; t <= setupEnd; t++) {
            if (!tied[t] || (jaFree && t == setupEnd)) {
                prev = -1;
                continue;
            }
            if (prev >= 0) {
                cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, prev,
                        JumpConstraint.Op.MINUS, JumpConstraint.Cmp.EQ, 0.0, "noturn.df@" + t));
            }
            prev = t;
        }
        return cons;
    }

    public JumpSpec baseSpecWithDf(boolean jaFree) {
        return new JumpSpec(base.copy(), noTurnConstraints(jaFree), objective);
    }

    public int[] baseCombos() {
        int[] combos = new int[setupEnd + 1];
        for (int t = 0; t <= setupEnd; t++) {
            int f = sign(base.forwardAt(t));
            int s = sign(base.strafeInputAt(t));
            combos[t] = NoTurnKeys.comboFor(f, s);
        }
        return combos;
    }

    public boolean[] baseSprint() {
        boolean[] spr = new boolean[setupEnd + 1];
        for (int t = 0; t <= setupEnd; t++) spr[t] = base.sprintAt(t);
        return spr;
    }

    public boolean hasBaseMovement() {
        for (int t = 0; t <= setupEnd; t++) {
            if (sign(base.forwardAt(t)) != 0 || sign(base.strafeInputAt(t)) != 0) return true;
        }
        return false;
    }

    private static int sign(double v) {
        if (v > 1.0e-4) return 1;
        if (v < -1.0e-4) return -1;
        return 0;
    }

    public Vec3dCore refStart() {
        if (freeBox != null) {
            double rx = Math.max(freeBox.pxLo, Math.min(freeBox.pxHi, base.startPos.x));
            double rz = Math.max(freeBox.pzLo, Math.min(freeBox.pzHi, base.startPos.z));
            return new Vec3dCore(rx, base.startPos.y, rz);
        }
        return base.startPos;
    }
}
