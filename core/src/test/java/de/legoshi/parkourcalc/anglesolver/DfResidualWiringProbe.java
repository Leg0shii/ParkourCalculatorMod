package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.ResidualRescue;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Stage P5 wiring probe: the ARCH-1 residual ({@link ResidualRescue}) no longer bails on facing walls. Its
 *  {@code degenerateTicks} folds a dF chain through FacingPrefold (and adds the free-start box-support term
 *  when the start is free) before running the disk kernel, so a dF capture yields a real degenerate set
 *  instead of {@code null}. {@code improve} then re-optimizes those ticks with the shipped dF-aware
 *  completion, byte-exact scored, never regressing. This closes F8 for the foldable-dF case. */
@Category(SlowSolverTests.class)
public class DfResidualWiringProbe {

    @Test
    public void foldableDfChainNoLongerBails() {
        Ctx x = load("df-chain-free-start");
        FacingPrefold pre = FacingPrefold.analyze(x.spec.constraints, new JumpLinearModel(x.sc));
        boolean foldable = pre != null && !pre.isIdentity();
        int[] degen = ResidualRescue.degenerateTicks(x.spec);
        System.out.printf("WIRE %-22s n=%d startFree=%b foldable=%b degen=%s (was null: the residual bailed on dF)%n",
                "df-chain-free-start", x.n, x.sc.startBox != null && x.sc.startBox.startFree(), foldable,
                java.util.Arrays.toString(degen));
        assertTrue("df-chain-free-start must be a foldable dF chain", foldable);
        assertNotNull("degenerateTicks must fold the dF chain + free start rather than bail (was null)", degen);
    }

    @Test
    public void nonFoldableDfChainDeclinesCleanly() {
        Ctx x = load("gh313-j121-dfneo");
        JumpLinearModel lin = new JumpLinearModel(x.sc);
        FacingPrefold pre = FacingPrefold.analyze(x.spec.constraints, lin);
        FacingPrefold.ChainScan scan = FacingPrefold.scannable(x.spec.constraints, lin);
        int[] degen = ResidualRescue.degenerateTicks(x.spec);
        System.out.printf("WIRE %-22s n=%d foldable=%b scannable=%b degen=%s (FacingPrefold cannot fold or scan "
                + "this dF structure; the residual declines it cleanly, as the shipped closed form does)%n",
                "gh313-j121-dfneo", x.n, pre != null, scan != null, java.util.Arrays.toString(degen));
        assertNull("gh313's dF structure is not FacingPrefold-foldable", pre);
        assertNull("the residual declines a dF structure the fold cannot take (returns null, no crash)", degen);
    }

    private static final class Ctx {
        ExactJumpModel model;
        JumpSpec spec;
        JumpPhysicsInputs sc;
        int n;
    }

    private Ctx load(String cap) {
        String raw = Fixtures.rawPool(cap);
        SaveFile file = SaveIO.parseSafe(raw);
        assertNotNull(cap + ": parse", file);
        Ctx x = new Ctx();
        x.model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, x.model);
        x.spec = engine.debugBuildSpec();
        assertNotNull(cap + ": spec", x.spec);
        x.sc = x.spec.asScenario();
        x.n = x.sc.numTicks;
        return x;
    }
}
