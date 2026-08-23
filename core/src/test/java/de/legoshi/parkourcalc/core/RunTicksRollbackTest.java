package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RunTicksRollbackTest {

    private final InputData inputs = new InputData();
    private final AngleSolverState state = new AngleSolverState();
    private final BoxController boxes = new BoxController();
    private final AngleSolverEngine engine =
            new AngleSolverEngine(state, boxes, inputs, tick -> { }, ExactJumpModel.forMcVersion("1.8.9"));
    private int simulations;
    private int dirtyMarks;

    private RunTicksController controller() {
        return new RunTicksController(state, engine, inputs, new ConstraintSelection(), new HudMessages(),
                () -> simulations++, () -> dirtyMarks++, (text, color) -> { });
    }

    private InputRow row(boolean jump) {
        InputRow r = new InputRow();
        r.setKeyActive(InputRow.Key.W, true);
        r.setKeyActive(InputRow.Key.SPRINT, true);
        r.setKeyActive(InputRow.Key.JUMP, jump);
        return r;
    }

    private void buildPath() {
        inputs.getRows().add(row(false));
        InputRow runTick = row(false);
        runTick.setYaw(11.5f);
        inputs.getRows().add(runTick);
        inputs.getRows().add(row(true));
        inputs.getRows().add(row(false));
        state.tickConstraints(1).getOverride().setSlipperiness(Slipperiness.DEFAULT);
        state.setStartTick(0);
        state.setLandingTick(3);
        state.getRunTicks().setEnabled(true);
        state.getRunTicks().setMaxTicks(0);
    }

    @Test
    public void aFailedSearchPutsThePathBack() {
        buildPath();
        RunTicksController runTicks = controller();

        runTicks.start();
        for (int i = 0; i < 8 && runTicks.isRunning(); i++) runTicks.poll();

        assertFalse("the search must finish, not stall", runTicks.isRunning());
        assertEquals("the dropped run tick has to come back", 4, inputs.size());
        assertFalse("row 1 is the restored run tick", inputs.get(1).isKeyActive(InputRow.Key.JUMP));
        assertTrue("the jump sits back at row 2", inputs.get(2).isKeyActive(InputRow.Key.JUMP));
        assertEquals(Float.valueOf(11.5f), inputs.get(1).getYaw());
        assertTrue("its ground override has to come back too",
                state.tickConstraints(1).getOverride().overridesSlipperiness());
        assertEquals(0, state.getStartTick());
        assertEquals("the goal tick must not stay shifted", 3, state.getLandingTick());
    }

    @Test
    public void aFailedSearchReportsFailureAndNeverAppliesAPartial() {
        buildPath();
        RunTicksController runTicks = controller();

        runTicks.start();
        for (int i = 0; i < 8 && runTicks.isRunning(); i++) runTicks.poll();

        SolveResult result = state.getResult();
        assertNotNull(result);
        assertFalse("a partial route is not a solution", result.isSuccess());
        assertTrue("the run must say the path was restored", detail(result, "Run ticks").contains("restored"));
        assertEquals("nothing changed, so nothing to save", 0, dirtyMarks);
    }

    private static String detail(SolveResult result, String label) {
        for (SolveResult.Detail d : result.getDetails()) {
            if (label.equals(d.label)) return d.value;
        }
        return "";
    }
}
