package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.GateMip;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class GateMipProbe {

    private static final long BUDGET = 8_000_000_000L;
    private static final long LOOPMM_BUDGET = 15_000_000_000L;
    private static final double LOOPMM_BLOCK_EDGE = -279.29999;

    @Test
    public void dsfNeoBandInReachesByteExactOptimum() {
        Ctx c = load("/problems/solve/inertia-1tick-neo.json");
        GateMip.Result r = GateMip.solve(c.model, c.spec, 0.0, new AtomicBoolean(false),
                System.nanoTime() + BUDGET, null);
        report("dsf-neo", c, r);
        assertTrue("dsf-neo gate solve must be feasible", r.feasible && r.yaws != null);
        assertTrue("dsf-neo must be byte-exact feasible", violation(c, r.yaws) <= 0.0);
        assertTrue("dsf-neo must reach the byte-exact optimum 8086.296335803, was " + objective(c, r.yaws),
                objective(c, r.yaws) <= 8086.296335803 + 1.0e-4);
    }

    @Test
    public void loopmmLandsOnBlock() {
        Ctx c = load("/captures/loopmm-3jump-lands.json");
        String[] nm = new String[1];
        double[] baseline = AngleSolverEngine.dualChain(c.model, c.spec, c.sc, new AtomicBoolean(false), nm);
        GateMip.Result r = GateMip.solve(c.model, c.spec, 0.0, new AtomicBoolean(false),
                System.nanoTime() + LOOPMM_BUDGET, baseline == null ? null : Angles.wrapAll(baseline));
        report("loopmm", c, r);
        assertTrue("loopmm gate solve must be feasible", r.feasible && r.yaws != null);
        assertTrue("loopmm must be byte-exact feasible", violation(c, r.yaws) <= 0.0);
        double normed = r.normed;
        assertTrue("loopmm disk bound must be a valid upper bound, bound=" + r.bound + " normed=" + normed,
                Double.isNaN(r.bound) || r.bound >= normed - 1.0e-6);
        assertTrue("loopmm disk-kernel gate bound must certify the block is reachable (bound past the "
                + LOOPMM_BLOCK_EDGE + " near edge, and tighter than the loose clamp-free -279.299065), bound="
                + r.bound, r.bound > LOOPMM_BLOCK_EDGE);
        assertTrue("loopmm must LAND on the block (Z >= " + LOOPMM_BLOCK_EDGE + ", past the block near edge; "
                + "the P1 clamp-free rescue -279.300514 and a plain ILS plateau -279.3004 both MISS), was " + normed,
                normed >= LOOPMM_BLOCK_EDGE);
    }

    @Test
    public void infeasibleGateConfigIsCertified() {
        Ctx c = load("/problems/solve/inertia-1tick-neo.json");
        int badTick = c.sc.numTicks - 1;
        List<JumpConstraint> cons = new ArrayList<>(c.spec.constraints);
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, badTick, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, 1.0e6, "impossibleZ@" + badTick));
        JumpSpec infeasible = new JumpSpec(c.sc, cons, c.spec.objective);
        GateMip.Result r = GateMip.solve(c.model, infeasible, 0.0, new AtomicBoolean(false),
                System.nanoTime() + BUDGET, null);
        System.out.printf("%ninfeasible-config: feasible=%s certifiedInfeasible=%s patternsTried=%d patternsInfeasible=%d%n  cert=%s%n",
                r.feasible, r.certifiedInfeasible, r.patternsTried, r.patternsInfeasible, r.certificate);
        assertTrue("an unreachable gate config must NOT be reported feasible", !r.feasible);
        assertTrue("an unreachable gate config must be certified infeasible", r.certifiedInfeasible);
    }

    @Test
    public void j021NoRegress() {
        Ctx c = load("/captures/j021-rinav1-01.json");
        String[] nm = new String[1];
        double[] baseline = Angles.wrapAll(AngleSolverEngine.dualChain(c.model, c.spec, c.sc, new AtomicBoolean(false), nm));
        double baseObj = objective(c, baseline);
        double[] improved = GateMip.improve(c.model, c.spec, baseline, 0.0, new AtomicBoolean(false),
                System.nanoTime() + BUDGET);
        double impObj = objective(c, Angles.wrapAll(improved));
        System.out.printf("%nj021 no-regress: baseline obj=%.9f improved obj=%.9f viol=%.3e%n",
                baseObj, impObj, violation(c, Angles.wrapAll(improved)));
        assertTrue("j021 improved must be byte-exact feasible", violation(c, Angles.wrapAll(improved)) <= 0.0);
        assertTrue("j021 must not regress (Z/MAX): improved >= baseline", impObj >= baseObj - 1.0e-9);
    }

    private static final class Ctx {
        ExactJumpModel model;
        JumpSpec spec;
        JumpPhysicsInputs sc;
    }

    private static void report(String name, Ctx c, GateMip.Result r) {
        System.out.printf("%nGATE %-10s feasible=%s obj=%s normed=%.9f bound=%.9f certInfeas=%s patterns=%d/%dinf viol=%s%n",
                name, r.feasible,
                r.yaws == null ? "-" : String.format("%.9f", objective(c, r.yaws)),
                r.normed, r.bound, r.certifiedInfeasible, r.patternsTried, r.patternsInfeasible,
                r.yaws == null ? "-" : String.format("%.3e", violation(c, r.yaws)));
    }

    private Ctx load(String path) {
        String raw = read(path);
        SaveFile file = SaveIO.parseSafe(raw);
        Ctx c = new Ctx();
        c.model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, c.model);
        c.spec = engine.debugBuildSpec();
        c.sc = c.spec.asScenario();
        return c;
    }

    private static double objective(Ctx c, double[] yaws) {
        double[] gf = c.sc.toGameFacings(Angles.wrapAll(yaws));
        return c.model.forward(c.sc, gf).getPos(c.spec.objective.tick, c.spec.objective.axis);
    }

    private static double violation(Ctx c, double[] yaws) {
        double[] gf = c.sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = c.model.forward(c.sc, gf);
        return JumpConstraintCompiler.compile(c.spec).maxViolation(gf, path);
    }

    private static String read(String path) {
        try (InputStream in = GateMipProbe.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing: " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("read " + path, e);
        }
    }
}
