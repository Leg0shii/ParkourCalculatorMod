package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.PhysicsVariant;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
@Category(SlowSolverTests.class)
public class NoInertiaVariantSolveTest {

    private static final PhysicsVariant BPK_DEFAULTS = new PhysicsVariant(0.0, true, true, false, true, "test");

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        String[] lands = {"j001", "j004", "j008-bfneo", "trp-optimize-feasible-swap", "gh313-j121-dfneo",
                "gh283-j990-cold", "j010-Xp2hsneo", "j014-11bmreversenix", "j019-3jmmtruenix",
                "loopmm-3jump-lands", "gh486-cross2-freestart", "deserthard-sine262"};
        List<Object[]> out = new ArrayList<>();
        for (String c : lands) out.add(new Object[] {c, true});
        out.add(new Object[] {"gh553-j345-dfneo", false});
        return out;
    }

    private final String capture;
    private final boolean lands;

    public NoInertiaVariantSolveTest(String capture, boolean lands) {
        this.capture = capture;
        this.lands = lands;
    }

    @Test
    public void fastWithTheInertiaGateOff() throws Exception {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        assertNotNull(file);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.setStopOnFeasible(true);
        state.clearResult();

        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion, BPK_DEFAULTS);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + 60_000;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(2);
        }
        engine.poll();
        SolveResult result = state.getResult();
        assertNotNull("solve did not finish in time", result);
        if (lands) {
            assertTrue("expected a successful solve, got: " + result.getSolver(), result.isSuccess());
        } else {
            assertFalse(result.isSuccess());
            assertTrue("expected a certified infeasible verdict", result.isCertifiedInfeasible());
        }
    }
}
