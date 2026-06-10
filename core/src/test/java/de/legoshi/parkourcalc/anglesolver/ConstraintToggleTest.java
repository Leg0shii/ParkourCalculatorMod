package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-118: a constraint can be disabled without deleting it. Disabled constraints keep their
 * definition (and survive save/load) but are invisible to the solve: excluded from the compiled
 * spec and from the met/total counts.
 */
public class ConstraintToggleTest {

    private static final int TICKS = 4;

    private static JumpSpec compile(AngleSolverState state) {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            inputs.getRows().add(row);
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        return spec;
    }

    @Test
    public void disabledConstraintIsInvisibleToTheSolve() {
        AngleSolverState state = new AngleSolverState();
        Constraint on = Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.0);
        Constraint off = Constraint.scalar(Constraint.Field.Z, Constraint.Op.LE, 2.0);
        off.setEnabled(false);
        state.tickConstraints(2).getConstraints().add(on);
        state.tickConstraints(2).getConstraints().add(off);

        JumpSpec spec = compile(state);
        boolean sawX = false;
        for (JumpConstraint jc : spec.constraints) {
            assertFalse("disabled Z constraint leaked into the spec", jc.name.startsWith("Z@"));
            if (jc.name.startsWith("X@2")) sawX = true;
        }
        assertTrue("enabled constraint still compiles", sawX);
    }

    @Test
    public void copyCarriesTheToggle() {
        Constraint c = Constraint.scalar(Constraint.Field.DX, Constraint.Op.GE, 0.1);
        c.setEnabled(false);
        assertFalse(c.copy().isEnabled());
        c.setEnabled(true);
        assertTrue(c.copy().isEnabled());
    }

    @Test
    public void toggleSurvivesSaveAndLoad() throws Exception {
        Path dir = Files.createTempDirectory("pkc-toggle-test");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);

        AngleSolverState state = new AngleSolverState();
        Constraint off = Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 3.0);
        off.setEnabled(false);
        state.tickConstraints(1).getConstraints().add(off);
        state.tickConstraints(1).getConstraints().add(Constraint.range(Constraint.Field.Z, 0.0, 1.0, true, true));

        InputData inputs = new InputData();
        List<TickState> states = new ArrayList<TickState>();
        for (int t = 0; t < 2; t++) {
            inputs.getRows().add(new InputRow());
            states.add(new TickState(Vec3dCore.ZERO, false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        assertTrue(SaveIO.save(store, "toggle", inputs, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f,
                state, states, false).ok);

        SaveFile loaded = SaveIO.load(store, "toggle").value;
        assertNotNull(loaded);
        AngleSolverState restored = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded, restored);
        List<Constraint> list = restored.tickConstraintsOrNull(1).getConstraints();
        assertEquals(2, list.size());
        assertFalse("disabled flag survives the round trip", list.get(0).isEnabled());
        assertTrue("enabled stays enabled", list.get(1).isEnabled());
    }

    @Test
    public void oldSavesWithoutTheFieldLoadEnabled() {
        SaveFile.Constraint raw = new SaveFile.Constraint(); // 'disabled' absent in old JSON = false
        raw.range = false;
        raw.field = "X";
        raw.op = "GT";
        raw.value = 1.0;
        SaveFile file = new SaveFile();
        file.angleSolver = new SaveFile.AngleSolver();
        SaveFile.Tick tick = new SaveFile.Tick();
        tick.tick = 0;
        tick.constraints.add(raw);
        file.angleSolver.ticks.add(tick);

        AngleSolverState restored = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, restored);
        assertTrue(restored.tickConstraintsOrNull(0).getConstraints().get(0).isEnabled());
    }
}
