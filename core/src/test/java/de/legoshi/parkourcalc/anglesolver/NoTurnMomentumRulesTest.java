package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FreeAirExact;
import de.legoshi.parkourcalc.core.anglesolver.noturn.MomentumRules;
import de.legoshi.parkourcalc.core.anglesolver.noturn.MomentumSearch;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoTurnMomentumRulesTest {

    private static final String J315 = "hpk/d9/j315_Egg_to_Block_Butterfly_Neo";
    private static final String J154 = "hpk/d12/j154_1bm_Head_Butterfly_Neo";
    private static final String J424 = "hpk/d10/j424_1.4375bm_Double_Winged_Neo__0.25";
    private static final String J144 = "hpk/d10/j144_1bm_5-1_Triple_Neo";
    private static final String J155 = "hpk/d11/j155-4jmm_3bcmm_4.9375b";

    private static boolean accepts(NoTurnProblem p, String keys, int changes, int dwell) {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        return new MomentumRules(p, cfg, changes, dwell).accepts(NoTurnKeys.parse(keys, p.n));
    }

    @Test
    public void aChangeOnAJumpTickOrTheTickAfterIsFree() throws Exception {
        NoTurnProblem p = NoTurnCapture.problem(J315);
        String recorded = "SAx13 WD D WDx11 W WAx10";
        assertArrayEquals(NoTurnKeys.parse(recorded, p.n), p.baseCombos());
        assertTrue(accepts(p, recorded, 2, 3));
        assertFalse(accepts(p, recorded, 1, 3));
        assertTrue(accepts(p, "SAx13 WDx13 W WAx10", 0, 3));
        assertTrue(accepts(p, "Wx37", 0, 3));
        assertFalse(accepts(p, "Wx5 WAx32", 0, 3));
        assertTrue(accepts(p, "Wx5 WAx32", 1, 3));
    }

    @Test
    public void aShortHoldAwayFromAJumpNeedsTheTapRules() throws Exception {
        NoTurnProblem p = NoTurnCapture.problem(J315);
        String shortHold = "SAx13 WDx5 Wx2 WDx6 W WAx10";
        assertFalse(accepts(p, shortHold, 2, 3));
        assertTrue(accepts(p, shortHold, 2, 1));
        assertTrue(accepts(p, "SAx13 WDx5 Wx3 WDx5 W WAx10", 2, 3));
    }

    @Test
    public void theSprintVariantsDelayTheSprintAfterTheFirstRunTick() {
        int[] combos = NoTurnKeys.parse("-x2 Wx3 S Wx2", 8);
        boolean[] sprint = new boolean[8];
        MomentumRules.sprintOf(combos, 0, sprint);
        assertArrayEquals(new boolean[]{false, false, true, true, true, false, true, true}, sprint);
        MomentumRules.sprintOf(combos, 1, sprint);
        assertArrayEquals(new boolean[]{false, false, false, true, true, false, true, true}, sprint);
        MomentumRules.sprintOf(combos, 2, sprint);
        assertArrayEquals(new boolean[]{false, false, false, false, true, false, true, true}, sprint);
    }

    @Test
    public void theWalkedTicksEndWhereEveryLaterTickHasItsOwnFacing() throws Exception {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        NoTurnProblem one = NoTurnCapture.problem(J154);
        NoTurnProblem two = NoTurnCapture.problem(J424);
        NoTurnProblem tiedToTheEnd = NoTurnCapture.problem(J144);
        NoTurnProblem turnsOnEveryJump = NoTurnCapture.problem(J155);
        assertEquals(28, MomentumSearch.walkedTicks(FreeAirExact.Tables.of(one, cfg.turnCombo, cfg.allowJa), one.jumpTicks));
        assertEquals(39, MomentumSearch.walkedTicks(FreeAirExact.Tables.of(two, cfg.turnCombo, cfg.allowJa), two.jumpTicks));
        assertEquals(42, MomentumSearch.walkedTicks(FreeAirExact.Tables.of(tiedToTheEnd, cfg.turnCombo, cfg.allowJa),
                tiedToTheEnd.jumpTicks));
        assertEquals(55, MomentumSearch.walkedTicks(FreeAirExact.Tables.of(turnsOnEveryJump, cfg.turnCombo, cfg.allowJa),
                turnsOnEveryJump.jumpTicks));
    }
}
