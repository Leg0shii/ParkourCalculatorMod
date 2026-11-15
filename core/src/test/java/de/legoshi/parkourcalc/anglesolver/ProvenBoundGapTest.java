package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
@RunWith(Parameterized.class)
public class ProvenBoundGapTest {

    private static final long TIMEOUT_MS = 240_000L;
    private static final String GAP = "Dual bound gap";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> captures() {
        return Arrays.asList(new Object[][]{
                {"nix-t25-setup-tick", 0.1},
                {"j021-rinav1-01", 0.01},
                {"gh553-j345-dfneo", 0.001},
                {"j344-fall-damage-26.3-test1", Double.NaN},
        });
    }

    private final String capture;
    private final double maxGap;

    public ProvenBoundGapTest(String capture, double maxGap) {
        this.capture = capture;
        this.maxGap = maxGap;
    }

    @Test
    public void theShownGapIsMeasuredAgainstTheCertifiedBound() {
        ProblemFixture.Rig rig = ProblemFixture.load("solve", capture).rig();
        rig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        rig.state.setOptimizeSeconds(2);
        rig.state.setRepeatable(true);
        rig.state.setSeed(0L);
        SolveResult result = rig.solve(TIMEOUT_MS).result;
        assertNotNull(capture + ": no result", result);
        assertTrue(capture + ": the solve should land", result.isSuccess());
        String shown = detail(result);
        if (Double.isNaN(maxGap)) {
            assertNull(capture + ": the relaxation does not cover this scenario, so no gap may be shown", shown);
            return;
        }
        assertNotNull(capture + ": no gap is shown", shown);
        double gap = Double.parseDouble(shown);
        assertTrue(capture + ": the shown gap is negative, " + shown, gap >= 0.0);
        assertTrue(capture + ": the shown gap " + shown + " is wider than " + maxGap, gap <= maxGap);
    }

    private static String detail(SolveResult result) {
        for (SolveResult.Detail d : result.getDetails()) {
            if (GAP.equals(d.label)) return d.value;
        }
        return null;
    }
}
