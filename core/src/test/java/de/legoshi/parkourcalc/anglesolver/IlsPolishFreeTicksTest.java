package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.IlsPolishNode;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IlsPolishFreeTicksTest {

    @Test
    public void nullPathReproducesGoldenKickSequence() {
        double[] base = {10, 20, 30, 40, 50, 60, 70, 80};
        Random rng = new Random(12345L);
        IlsPolish.Config cfg = new IlsPolish.Config();
        double[] out = IlsPolish.kickCandidate(base, rng, base.length, cfg);
        assertArrayEquals("null-path kick sequence changed", GOLDEN_NULL, out, 0.0);
    }

    private static final double[] GOLDEN_NULL = {
            4.190579301062378, -4.90107970979863, 8.485619866196739, 55.173367421740465,
            50.0, 58.91650220675628, 47.19717248269502, 99.09311374804281
    };

    @Test
    public void freeTicksKickFreezesChainTicks() {
        double[] base = {10, 20, 30, 40, 50, 60, 70, 80};
        IlsPolish.Config cfg = new IlsPolish.Config();
        cfg.freeTicks = new int[]{3, 4, 5, 6, 7};
        int moved = 0;
        for (long seed = 1; seed <= 25; seed++) {
            Random rng = new Random(seed);
            double[] out = IlsPolish.kickCandidate(base, rng, base.length, cfg);
            for (int t = 0; t < 3; t++) {
                assertEquals("chain tick " + t + " moved (seed " + seed + ")", base[t], out[t], 0.0);
            }
            for (int t = 3; t < 8; t++) {
                if (out[t] != base[t]) moved++;
            }
        }
        assertTrue("no free tick ever moved", moved > 0);
    }

    @Test
    public void nodeMapsDfChainToFreeSingletons() {
        int n = 8;
        int[] free = IlsPolishNode.freeTicksFrom(chainConstraints(), n);
        assertNotNull("expected free ticks for a partial dF chain", free);
        assertArrayEquals(new int[]{3, 4, 5, 6, 7}, free);
    }

    @Test
    public void polishKeepsChainFrozenAndImprovesViaFreeTicks() {
        int n = 8;
        ExactJumpModel model = new ExactJumpModel(0.005, true, false);
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startYaw = 0.0F;
        sc.initialVelocity = new Vec3dCore(0.18, 0.0, 0.06);
        sc.jumpTick = -1;

        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, n);
        JumpSpec spec = new JumpSpec(sc, chainConstraints(), obj);

        double[] seed = new double[n];
        assertTrue("zero seed must be byte-exact feasible", feasible(spec, sc, model, seed));

        int[] free = IlsPolishNode.freeTicksFrom(spec.constraints, n);
        assertArrayEquals(new int[]{3, 4, 5, 6, 7}, free);
        IlsPolish.Config cfg = new IlsPolish.Config();
        cfg.freeTicks = free;

        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + 800_000_000L;
        double[] out = IlsPolish.polish(model, spec, seed, deadline, 12, true, cancel, null, cfg);
        assertNotNull(out);
        assertTrue("result must stay byte-exact feasible", feasible(spec, sc, model, out));

        double[] w = Angles.wrapAll(out);
        double[] ws = Angles.wrapAll(seed);
        for (int t = 0; t < 3; t++) {
            assertEquals("chain tick " + t + " moved through the pipeline", ws[t], w[t], 0.0);
        }
        int movedFree = 0;
        for (int t = 3; t < n; t++) {
            if (w[t] != ws[t]) movedFree++;
        }
        assertTrue("ILS never moved a free tick", movedFree > 0);
    }

    private static List<JumpConstraint> chainConstraints() {
        List<JumpConstraint> cs = new ArrayList<>();
        cs.add(new JumpConstraint(JumpConstraint.Mode.F, 1, 0, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.EQ, 0.0, "df01"));
        cs.add(new JumpConstraint(JumpConstraint.Mode.F, 2, 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.EQ, 0.0, "df12"));
        return cs;
    }

    private static boolean feasible(JumpSpec spec, JumpPhysicsInputs sc, ExactJumpModel model, double[] yaws) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        return c.maxViolation(gf, path) <= 0.0;
    }
}
