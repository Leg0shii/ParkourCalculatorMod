package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
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
    public final boolean explicitTies;
    public final boolean jaAll;
    public final int[] segment;
    public final int[] unit;
    public final int unitCount;
    public final int mainUnit;
    public final int tiedUnits;

    public final List<JumpConstraint> walls;
    public final List<JumpConstraint> flatWalls;

    public String issue;

    private static final double DF_EPS = 1.0e-3;
    private static final int DEFAULT_AIR_COMBO = NoTurnKeys.WA;

    private NoTurnProblem(JumpSpec baseSpec, ExactJumpModel model) {
        this(baseSpec, model, false);
    }

    private NoTurnProblem(JumpSpec baseSpec, ExactJumpModel model, boolean jaAll) {
        this.jaAll = jaAll;
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
        this.segment = new int[n];
        for (int t = 0; t < n; t++) segment[t] = t;
        int maxTied = -1;
        for (JumpConstraint c : baseSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.F && c.t2 != null
                    && c.op == JumpConstraint.Op.MINUS && Math.abs(c.rhs) <= DF_EPS) {
                markTied(c.t1);
                markTied(c.t2);
                maxTied = Math.max(maxTied, Math.max(c.t1, c.t2));
                if (c.t1 >= 0 && c.t1 < n && c.t2 >= 0 && c.t2 < n) {
                    int a = root(c.t1);
                    int b = root(c.t2);
                    if (a != b) segment[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        for (int t = 0; t < n; t++) segment[t] = root(t);
        int lastJump = jumpTicks.length > 0 ? jumpTicks[jumpTicks.length - 1] : -1;
        this.explicitTies = maxTied >= 0;
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
        if (jaAll) {
            for (int jtk : jumpTicks) if (jtk <= setupEnd) tied[jtk] = false;
        }
        this.unit = new int[n];
        int[] unitOfRoot = new int[n];
        java.util.Arrays.fill(unitOfRoot, -1);
        int count = 0;
        int main = -1;
        int tiedRoots = 0;
        for (int t = 0; t < n; t++) {
            boolean shared = explicitTies ? tied[t] : t <= setupEnd && !(jaAll && jump[t]);
            if (!shared) {
                unit[t] = count++;
                continue;
            }
            int root = explicitTies ? segment[t] : 0;
            if (unitOfRoot[root] < 0) {
                unitOfRoot[root] = count++;
                tiedRoots++;
            }
            unit[t] = unitOfRoot[root];
            if (main < 0) main = unit[t];
        }
        this.unitCount = count;
        this.mainUnit = main;
        this.tiedUnits = tiedRoots;
        if (base.forwardInputPerTick != null && base.strafeInputPerTick != null) {
            for (int t = setupEnd + 1; t < n; t++) {
                boolean idle = Math.abs(base.forwardInputPerTick[t]) < 1.0e-4
                        && Math.abs(base.strafeInputPerTick[t]) < 1.0e-4;
                if (!idle) continue;
                base.forwardInputPerTick[t] = NoTurnKeys.forwardInput(DEFAULT_AIR_COMBO);
                base.strafeInputPerTick[t] = NoTurnKeys.strafeInput(DEFAULT_AIR_COMBO);
                if (base.sprintPerTick != null) base.sprintPerTick[t] = true;
            }
        }

        this.walls = new ArrayList<>();
        this.flatWalls = new ArrayList<>();
        for (JumpConstraint c : baseSpec.constraints) {
            if (c.mode == JumpConstraint.Mode.X || c.mode == JumpConstraint.Mode.Z
                    || c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) {
                walls.add(c);
                if (isFlat(c)) flatWalls.add(c);
            }
        }
    }

    public static boolean isFlat(JumpConstraint w) {
        return w.t2 == null && (w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z);
    }

    public NoTurnProblem widened(double delta) {
        List<JumpConstraint> wc = new ArrayList<>();
        for (JumpConstraint w : baseSpec.constraints) {
            if (isFlat(w)) {
                double rhs = w.rhs;
                if (w.cmp == JumpConstraint.Cmp.LE) rhs += delta;
                else if (w.cmp == JumpConstraint.Cmp.GE) rhs -= delta;
                wc.add(new JumpConstraint(w.mode, w.t1, w.t2, w.op, w.cmp, rhs, w.name));
            } else {
                wc.add(w);
            }
        }
        return from(new JumpSpec(base.copy(), wc, objective), model);
    }

    public static JumpSpec pinnedAtRef(JumpSpec spec) {
        JumpPhysicsInputs scFree = spec.asScenario();
        JumpPhysicsInputs scRun = scFree.copy();
        if (scFree.startBox != null && scFree.startBox.startFree()) {
            Vec3dCore ref = refStart(scFree);
            scRun.startPos = ref;
            scRun.startBox = StartBox.pinned(ref.x, ref.z, scFree.initialVelocity.x, scFree.initialVelocity.z);
        }
        return new JumpSpec(scRun, spec.constraints, spec.objective);
    }

    public static int worstFlatWallTick(List<JumpConstraint> walls, ForwardPath fp, double[] worstInOut) {
        double worst = worstInOut[0];
        int tick = -1;
        for (JumpConstraint w : walls) {
            if (!isFlat(w)) continue;
            int axis = w.mode == JumpConstraint.Mode.X ? 0 : 1;
            double v = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double viol = w.cmp == JumpConstraint.Cmp.LE ? v - w.rhs
                    : w.cmp == JumpConstraint.Cmp.GE ? w.rhs - v : Math.abs(v - w.rhs);
            if (viol > worst) {
                worst = viol;
                tick = w.t1;
            }
        }
        worstInOut[0] = worst;
        return tick;
    }

    private void markTied(int t) {
        if (t >= 0 && t < n) tied[t] = true;
    }

    private int root(int t) {
        while (segment[t] != t) t = segment[t];
        return t;
    }

    private int mainSegment() {
        for (int t = 0; t <= setupEnd; t++) if (tied[t]) return segment[t];
        return -1;
    }

    public static NoTurnProblem from(JumpSpec baseSpec, ExactJumpModel model) {
        return from(baseSpec, model, false);
    }

    public static NoTurnProblem from(JumpSpec baseSpec, ExactJumpModel model, boolean jaAll) {
        if (baseSpec == null) {
            NoTurnProblem p = new NoTurnProblem(new JumpSpec(new JumpPhysicsInputs(1), new ArrayList<>(),
                    new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 0)), model);
            p.issue = "no solver problem is set up (start tick, landing tick, objective)";
            return p;
        }
        NoTurnProblem p = new NoTurnProblem(baseSpec, model, jaAll);
        if (p.setupEnd < 1) p.issue = "the run-up is too short to search (need at least one setup tick)";
        if (p.walls.isEmpty()) p.issue = "no landing constraints found; add X/Z landing walls first";
        return p;
    }

    public boolean isJaTick(int t) {
        return t == setupEnd && !tied[t];
    }

    public boolean jaAllowed() {
        return !explicitTies;
    }

    public boolean freeDirection(int t) {
        if (t < 0 || t >= n) return false;
        if (explicitTies) return !tied[t] || segment[t] != mainSegment();
        return jaAll && jump[t];
    }

    public boolean assignsCombo(int t) {
        return t >= 0 && t <= setupEnd;
    }

    public boolean multiTied() {
        return explicitTies && tiedUnits >= 2;
    }

    public JumpSpec buildSpec(int[] combos, boolean[] sprint, int turnCombo, boolean jaFree) {
        JumpPhysicsInputs sc = base.copy();
        float[] fwd = new float[n];
        float[] strafe = new float[n];
        boolean[] spr = new boolean[n];
        int given = Math.min(n, combos.length);
        for (int t = 0; t < n; t++) {
            if (t >= given) {
                boolean idle = Math.abs(base.forwardAt(t)) < 1.0e-4 && Math.abs(base.strafeInputAt(t)) < 1.0e-4;
                if (idle && NoTurnKeys.isMove(turnCombo)) {
                    fwd[t] = NoTurnKeys.forwardInput(turnCombo);
                    strafe[t] = NoTurnKeys.strafeInput(turnCombo);
                    spr[t] = NoTurnKeys.isRun(turnCombo);
                } else {
                    fwd[t] = base.forwardAt(t);
                    strafe[t] = base.strafeInputAt(t);
                    spr[t] = base.sprintPerTick == null || base.sprintAt(t);
                }
                continue;
            }
            int combo = combos[t];
            fwd[t] = NoTurnKeys.forwardInput(combo);
            strafe[t] = NoTurnKeys.strafeInput(combo);
            spr[t] = sprint[t];
        }
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = strafe;
        sc.sprintPerTick = spr;
        sc.strafePerTick = null;
        sc.sneakPerTick = null;

        return new JumpSpec(sc, explicitTies ? new ArrayList<>(baseSpec.constraints) : noTurnConstraints(jaFree), objective);
    }

    public List<JumpConstraint> noTurnConstraints(boolean jaFree) {
        List<JumpConstraint> cons = new ArrayList<>(walls);
        int prev = -1;
        for (int t = 0; t <= setupEnd; t++) {
            if (!tied[t] || (jaFree && t == setupEnd)) {
                if (!(jaAll && jump[t])) prev = -1;
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
        return new JumpSpec(base.copy(), explicitTies ? new ArrayList<>(baseSpec.constraints) : noTurnConstraints(jaFree), objective);
    }

    public int[] baseCombos() {
        return combosOf(base, n - 1);
    }

    public boolean[] baseSprint() {
        return sprintOf(base, n - 1);
    }

    public static int[] combosOf(JumpPhysicsInputs sc, int setupEnd) {
        int[] combos = new int[setupEnd + 1];
        for (int t = 0; t <= setupEnd; t++) {
            combos[t] = NoTurnKeys.comboFor(sign(sc.forwardAt(t)), sign(sc.strafeInputAt(t)));
        }
        return combos;
    }

    public static boolean[] sprintOf(JumpPhysicsInputs sc, int setupEnd) {
        boolean[] spr = new boolean[setupEnd + 1];
        for (int t = 0; t <= setupEnd; t++) spr[t] = sc.sprintAt(t);
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
        return refStart(base);
    }

    public static Vec3dCore refStart(JumpPhysicsInputs sc) {
        StartBox box = sc.startBox;
        if (box != null && box.startFree()) {
            double rx = Math.max(box.pxLo, Math.min(box.pxHi, sc.startPos.x));
            double rz = Math.max(box.pzLo, Math.min(box.pzHi, sc.startPos.z));
            return new Vec3dCore(rx, sc.startPos.y, rz);
        }
        return sc.startPos;
    }
}
