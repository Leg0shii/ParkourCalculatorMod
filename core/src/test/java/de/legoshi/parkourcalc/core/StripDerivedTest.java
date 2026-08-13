package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StripDerivedTest {

    private static SaveFile.Constraint scalar(String field, String op, double value, boolean derived) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = field;
        c.op = op;
        c.value = value;
        c.derived = derived;
        return c;
    }

    @Test
    public void applyDropsDerivedConstraintsAndEmptyTicks() {
        SaveFile f = new SaveFile();
        f.angleSolver = new SaveFile.AngleSolver();
        f.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        SaveFile.Tick mixed = new SaveFile.Tick();
        mixed.tick = 5;
        mixed.constraints.add(scalar("X", "GE", 602.8, false));
        mixed.constraints.add(scalar("Z", "LE", 415.7, true));
        f.angleSolver.ticks.add(mixed);
        SaveFile.Tick onlyDerived = new SaveFile.Tick();
        onlyDerived.tick = 7;
        onlyDerived.constraints.add(scalar("X", "LE", 600.0, true));
        f.angleSolver.ticks.add(onlyDerived);
        SaveFile.Tick withOverride = new SaveFile.Tick();
        withOverride.tick = 9;
        withOverride.override = new SaveFile.Override();
        withOverride.override.slipperiness = "SLIME";
        withOverride.constraints.add(scalar("Z", "GE", 410.0, true));
        f.angleSolver.ticks.add(withOverride);

        SaveFile out = SaveIO.parseSafe(ColdStratController.stripDerived(SaveIO.saveJson(f)));

        assertEquals(2, out.angleSolver.ticks.size());
        SaveFile.Tick t5 = out.angleSolver.ticks.get(0);
        assertEquals(5, t5.tick);
        assertEquals(1, t5.constraints.size());
        assertEquals("X", t5.constraints.get(0).field);
        assertFalse(t5.constraints.get(0).derived);
        SaveFile.Tick t9 = out.angleSolver.ticks.get(1);
        assertEquals(9, t9.tick);
        assertEquals(0, t9.constraints.size());
        assertEquals("SLIME", t9.override.slipperiness);
    }
}
