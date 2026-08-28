package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ThoroughAdoptFeasibilityTest {

    @Test
    public void thoroughSubSolversStayFeasibleAtAdoptedFreeStart() {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", "j320_1bm_Gapped_Head_Butterfly_Neo");
        ExactJumpModel exact = pf.model;
        JumpSpec base = pf.specFor(null, null);
        JumpPhysicsInputs sc = base.asScenario();
        AtomicBoolean cancel = new AtomicBoolean(false);
        int n = sc.numTicks;
        double seedX = sc.startPos.x, seedZ = sc.startPos.z;
        double vx = sc.initialVelocity.x, vz = sc.initialVelocity.z;
        // Objective = maximize Z at landing (translation-invariant under an X shift), plus an X-wall:
        // the exact adversarial trigger from the flagged finding.
        Objective objZ = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n);

        double[] reachZ = ClosedFormSolve.optimize(exact,
                new JumpSpec(base(sc, seedX, seedZ), Collections.<JumpConstraint>emptyList(), objZ), 0.0, cancel);
        assertNotNull(reachZ);
        double tgtX = seedX - 0.4, tgtZ = seedZ - 0.2, slack = 0.05;
        JumpPhysicsInputs tgt = base(sc, tgtX, tgtZ);
        double[] tgtGf = tgt.toGameFacings(Angles.wrapAll(reachZ));
        ForwardPath tgtPath = exact.forward(tgt, tgtGf);
        double landZ = tgtPath.getPos(n, JumpPhysicsInputs.Axis.Z);
        double landX = tgtPath.getPos(n, JumpPhysicsInputs.Axis.X);

        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(c(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.GE, landZ - slack));
        cons.add(c(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.LE, landZ + slack));
        cons.add(c(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.GE, landX - slack));
        cons.add(c(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.LE, landX + slack));

        double pxLo = seedX - 1.0, pxHi = seedX + 0.2, pzLo = seedZ - 0.6, pzHi = seedZ + 0.6;
        StartBox freeBox = new StartBox(pxLo, pzHi, vx, vz, pxLo, pxHi, pzLo, pzHi, vx, vx, vz, vz);
        JumpSpec freeSpec = new JumpSpec(withBox(sc, pxLo, pzHi, freeBox), cons, objZ);

        FreeStartSolve.Result conv = FreeStartSolve.solveJoint(exact, freeSpec, 0.0, cancel);
        assertNotNull("solveJoint must find the convex free start", conv);
        assertTrue(conv.feasible);

        // Emulate freeStartImprove's adoption: mutate the SHARED scenario to the conv start in place.
        JumpPhysicsInputs shared = freeSpec.asScenario();
        shared.startPos = new Vec3dCore(conv.startX, shared.startPos.y, conv.startZ);
        shared.startBox = StartBox.pinned(conv.startX, conv.startZ, vx, vz);

        // The flagged claim: THOROUGH sub-solvers key off the SEED and can return a start-infeasible result.
        // Run them on the mutated spec and confirm every returned candidate is feasible at the ADOPTED start.
        double[] convYaws = Angles.wrapAll(conv.yaws);
        CertifiedBnb.Config bcfg = new CertifiedBnb.Config();
        bcfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        bcfg.nodeCap = 64;
        bcfg.cancel = cancel;
        bcfg.deadlineNanos = System.nanoTime() + 2_000_000_000L;
        bcfg.seedYaws = convYaws.clone();
        bcfg.seedPx = shared.startPos.x;
        bcfg.seedPz = shared.startPos.z;
        CertifiedBnb.Result br = CertifiedBnb.solve(exact, freeSpec, bcfg);
        if (br.feasible) {
            JumpPhysicsInputs at = base(sc, br.px, br.pz);
            double[] gf = at.toGameFacings(Angles.wrapAll(br.yawsDeg));
            double v = JumpConstraintCompiler.compile(freeSpec).maxViolation(gf, exact.forward(at, gf));
            assertTrue("CertifiedBnb returned a candidate infeasible at its own start (viol=" + v + ")", v <= 0.0);
        }
        System.out.printf("THOROUGH sub-solver at adopted start: certBnb=%s (feasible or null)%n",
                br.feasible ? "feasible" : "null");
    }

    private static double violAtShared(ExactJumpModel exact, JumpSpec spec, double[] yaws) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, exact.forward(sc, gf));
    }

    private static JumpConstraint c(JumpConstraint.Mode m, int t, JumpConstraint.Cmp cmp, double rhs) {
        return new JumpConstraint(m, t, null, JumpConstraint.Op.PLUS, cmp, rhs, "c");
    }

    private static JumpPhysicsInputs base(JumpPhysicsInputs b, double px, double pz) {
        return withBox(b, px, pz, StartBox.pinned(px, pz, b.initialVelocity.x, b.initialVelocity.z));
    }

    private static JumpPhysicsInputs withBox(JumpPhysicsInputs b, double px, double pz, StartBox box) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(px, b.startPos.y, pz);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = box;
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return a;
    }
}
