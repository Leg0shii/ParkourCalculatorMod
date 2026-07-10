package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.ArrayList;
import java.util.List;

public final class RazorFixtures {

    public static final double PROOF_OBJX = 212.7001641;
    public static final double WEIRDPANE_OBJX = -8.864771846396799;
    public static final double WEIRDPANE_VIOL = 2.271846e-3;
    public static final double RUNG_RHS = -1.487500011921;
    public static final double RUNG_RAISE = 0.0625;
    public static final int RUNG_COUNT = 3;

    private RazorFixtures() {
    }

    public static final class Loaded {
        public final String name;
        public final SaveFile file;
        public final ExactJumpModel model;
        public final JumpSpec spec;
        public final JumpPhysicsInputs scenario;
        public final int startTick;
        public final int n;
        public final int objTick;
        public final double[] warm;

        Loaded(String name, SaveFile file, ExactJumpModel model, JumpSpec spec, JumpPhysicsInputs scenario,
               int startTick, int n, int objTick, double[] warm) {
            this.name = name;
            this.file = file;
            this.model = model;
            this.spec = spec;
            this.scenario = scenario;
            this.startTick = startTick;
            this.n = n;
            this.objTick = objTick;
            this.warm = warm;
        }
    }

    public static Loaded loadProofSpec() {
        return load("razor-proof");
    }

    public static Loaded loadWeirdpaneSpec() {
        return load("razor-weirdpane");
    }

    private static Loaded load(String capture) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        if (file == null) throw new IllegalStateException(capture + ": failed to parse");
        if (file.debug == null || file.debug.isEmpty()) throw new IllegalStateException(capture + ": no debug ticks");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs scenario = spec.asScenario();
        int startTick = state.getStartTick();
        int n = scenario.numTicks;
        int objTick = spec.objective.tick;
        double[] warm = new double[n];
        for (int k = 0; k < n; k++) warm[k] = file.debug.get(startTick + k + 1).yaw;
        return new Loaded(capture, file, model, spec, scenario, startTick, n, objTick, warm);
    }

    public static double[] warmGameFacings(Loaded l) {
        return l.scenario.toGameFacings(Angles.wrapAll(l.warm));
    }

    public static ForwardPath warmPath(Loaded l) {
        return l.model.forward(l.scenario, warmGameFacings(l));
    }

    public static final class Precheck {
        public final double posDiff;
        public final double viol;
        public final double objX;

        Precheck(double posDiff, double viol, double objX) {
            this.posDiff = posDiff;
            this.viol = viol;
            this.objX = objX;
        }
    }

    private static Precheck measure(Loaded l) {
        double[] gf = warmGameFacings(l);
        ForwardPath p = l.model.forward(l.scenario, gf);
        double posDiff = 0.0;
        for (int k = 0; k <= l.n; k++) {
            SaveFile.DebugTick d = l.file.debug.get(l.startTick + k);
            posDiff = Math.max(posDiff, Math.abs(p.posX[k] - d.pos[0]));
            posDiff = Math.max(posDiff, Math.abs(p.posZ[k] - d.pos[2]));
        }
        double viol = JumpConstraintCompiler.compile(l.spec).maxViolation(gf, p);
        double objX = p.getPos(l.objTick, l.spec.objective.axis);
        return new Precheck(posDiff, viol, objX);
    }

    public static Precheck proofPrecheck(Loaded l) {
        Precheck r = measure(l);
        require(r.viol <= 0.0, "proof replay not feasible, viol=" + r.viol);
        require(Math.abs(r.objX - PROOF_OBJX) <= 1e-6, "proof objX off, got " + r.objX);
        require(r.posDiff < 1e-12, "proof posDiff too large, got " + r.posDiff);
        return r;
    }

    public static Precheck weirdpanePrecheck(Loaded l) {
        Precheck r = measure(l);
        require(Math.abs(r.objX - WEIRDPANE_OBJX) <= 1e-6, "weirdpane objX off, got " + r.objX);
        require(Math.abs(r.viol - WEIRDPANE_VIOL) <= 1e-6, "weirdpane viol off, got " + r.viol);
        require(r.posDiff < 1e-12, "weirdpane posDiff too large, got " + r.posDiff);
        return r;
    }

    public static final class RaisedWall {
        public final String name;
        public final int tick;
        public final double oldRhs;
        public final double newRhs;

        RaisedWall(String name, int tick, double oldRhs, double newRhs) {
            this.name = name;
            this.tick = tick;
            this.oldRhs = oldRhs;
            this.newRhs = newRhs;
        }
    }

    public static final class RungPatch {
        public final JumpSpec spec;
        public final List<RaisedWall> raised;

        RungPatch(JumpSpec spec, List<RaisedWall> raised) {
            this.spec = spec;
            this.raised = raised;
        }
    }

    public static RungPatch applyRung5375Patch(JumpSpec spec) {
        List<JumpConstraint> out = new ArrayList<JumpConstraint>();
        List<RaisedWall> raised = new ArrayList<RaisedWall>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.Z && c.cmp == JumpConstraint.Cmp.GE
                    && Math.abs(c.rhs - RUNG_RHS) < 1e-9) {
                double nr = c.rhs + RUNG_RAISE;
                out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, nr, c.name));
                raised.add(new RaisedWall(c.name, c.t1, c.rhs, nr));
            } else {
                out.add(c);
            }
        }
        if (raised.size() != RUNG_COUNT) {
            throw new IllegalStateException("rung 5.375 patch raised " + raised.size() + " walls, expected " + RUNG_COUNT);
        }
        return new RungPatch(new JumpSpec(spec.asScenario(), out, spec.objective), raised);
    }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
