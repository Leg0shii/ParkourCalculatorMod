package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The engine's plan path (spec + toGameFacings, exactly as runJob and Apply's deviation check use it) rebuilt
 *  from the capture's saved result yaws, for a debug-enabled capture whose rows were written by Apply: the
 *  recorded resim must sit on it byte-exact per clean tick. The boxes are seed-only placeholders, as the live
 *  tool has them when it builds the plan. Sidecar: {@code minCompared}. */
final class PlanPathCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        SaveFile file = pf.file;
        assertNotNull(name + ": capture carries no debug ticks", file.debug);
        assertNotNull(name + ": capture carries no solve result", file.angleSolver.result);
        assertNotNull(name + ": capture result carries no yaws", file.angleSolver.result.yaws);
        List<SaveFile.DebugTick> d = file.debug;
        int startTick = file.angleSolver.startTick;
        int minCompared = pf.expect.or(pf.expect.minCompared, 100);

        ProblemFixture.Rig rig = pf.rigWithSeedBoxes();
        JumpPhysicsInputs sc = rig.spec().asScenario();
        List<SaveFile.Yaw> resultYaws = file.angleSolver.result.yaws;
        double[] yaws = new double[resultYaws.size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = resultYaws.get(k).yaw;
        ForwardPath plan = pf.model.forward(sc, sc.toGameFacings(yaws));

        List<String> out = new ArrayList<String>();
        int bad = 0;
        int compared = 0;
        for (int k = 1; k <= yaws.length; k++) {
            SaveFile.DebugTick rec = d.get(startTick + k);
            SaveFile.DebugTick prev = d.get(startTick + k - 1);
            if (rec.wallCollision || rec.softCollision || prev.wallCollision || prev.softCollision) continue;
            compared++;
            double ddx = (rec.pos[0] - prev.pos[0]) - (plan.posX[k] - plan.posX[k - 1]);
            double ddz = (rec.pos[2] - prev.pos[2]) - (plan.posZ[k] - plan.posZ[k - 1]);
            if (Math.abs(ddx) > 1.0e-9 || Math.abs(ddz) > 1.0e-9) {
                bad++;
                if (bad <= 10) {
                    out.add(String.format("k=%d abs=%d ddx=%.3e ddz=%.3e recYaw=%.6f",
                            k, startTick + k, ddx, ddz, rec.yaw));
                }
            }
        }
        System.out.printf("PLANPATH %-28s compared=%d divergences=%d%n", name, compared, bad);
        assertTrue(name + ": plan-vs-capture per-tick divergences: " + bad + " of " + compared + "\n"
                + String.join("\n", out), bad == 0);
        assertTrue(name + ": too few ticks compared: " + compared, compared >= minCompared);
    }
}
