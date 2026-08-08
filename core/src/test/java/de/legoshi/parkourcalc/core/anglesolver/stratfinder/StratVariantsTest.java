package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StratVariantsTest {

    private static final Gson GSON = new Gson();
    private static final Set<String> EDITABLE = new HashSet<String>(
            Arrays.asList("W", "SPRINT", "A", "D", "S"));

    private static SaveFile witness() {
        return GSON.fromJson(Fixtures.rawPool("hpk_human/d2/j012_1bm_4.25b"), SaveFile.class);
    }

    @Test
    public void enumeratesSelfFirstThenSingleKeyEditsAndYawShapes() {
        SaveFile save = witness();
        ExactJumpModel model = ExactJumpModel.forMcVersion(save.mcVersion);
        List<StratVariants.Variant> variants = StratVariants.variants(save, model);

        assertFalse("no variants enumerated", variants.isEmpty());
        assertTrue("cap exceeded", variants.size() <= 3 * 41);

        StratVariants.Variant self = variants.get(0);
        assertEquals("self", self.label);
        assertEquals(0, self.edits);
        assertEquals(rowSignature(save), rowSignature(self.save));

        int startTick = save.angleSolver.startTick;
        int landing = save.angleSolver.landingTick;
        int lastFire = lastJump(save, startTick, landing);
        Set<String> labels = new HashSet<String>();
        int patterns = 0;
        for (StratVariants.Variant v : variants) {
            assertTrue("duplicate label " + v.label, labels.add(v.label));
            assertEquals("row count changed for " + v.label, save.rows.size(), v.save.rows.size());
            assertEquals("FAST", v.save.angleSolver.effort);
            assertEquals(Boolean.TRUE, v.save.angleSolver.stopOnFeasible);
            assertNull("stale result on " + v.label, v.save.angleSolver.result);
            if (v.label.startsWith("nt[")) {
                patterns++;
                assertStrafePattern(save, v, startTick, lastFire, landing);
            } else if (isShape(v.label)) {
                assertShapeChain(v, startTick, lastFire);
            } else {
                assertEquals("constraints changed for " + v.label,
                        GSON.toJson(save.angleSolver.ticks), GSON.toJson(v.save.angleSolver.ticks));
                if (!"self".equals(v.label)) {
                    assertEquals(1, v.edits);
                    assertSingleEditableKeyEdit(save, v);
                }
            }
        }

        boolean momentumChainAlready = true;
        for (int t = startTick + 1; t <= lastFire; t++) {
            if (!hasDf(save, t)) {
                momentumChainAlready = false;
                break;
            }
        }
        assertEquals("nt presence disagrees with the witness chain",
                !momentumChainAlready, labels.contains("nt"));
        assertTrue("nt45 missing", labels.contains("nt45"));
        assertTrue("strafe patterns missing: " + patterns, patterns >= 8);
        for (StratVariants.Variant v : variants) {
            if (!"nt45".equals(v.label)) continue;
            assertEquals("FORCE_45", v.save.angleSolver.defaultInputs);
            for (SaveFile.Tick t : v.save.angleSolver.ticks) {
                if (t == null || t.constraints == null) continue;
                for (SaveFile.Constraint c : t.constraints) {
                    assertFalse("F pin survived in nt45 at T" + t.tick,
                            c != null && !c.disabled && "F".equals(c.field));
                }
            }
        }
    }

    private static boolean isShape(String label) {
        return "nt".equals(label) || "ja".equals(label) || "nt45".equals(label)
                || label.endsWith("/nt") || label.endsWith("/ja") || label.startsWith("nt[");
    }

    private static void assertShapeChain(StratVariants.Variant v, int startTick, int lastFire) {
        boolean ja = "ja".equals(v.label) || v.label.endsWith("/ja");
        for (int t = startTick + 1; t <= lastFire; t++) {
            boolean pinned = hasDf(v.save, t);
            if (ja && t == lastFire) {
                assertFalse("ja pinned its jump tick in " + v.label, pinned);
            } else {
                assertTrue("chain gap at T" + t + " in " + v.label, pinned);
            }
        }
    }

    private static void assertStrafePattern(SaveFile witness, StratVariants.Variant v,
                                            int startTick, int lastFire, int landing) {
        assertShapeChain(v, startTick, lastFire);
        String momentum = null;
        String air = null;
        for (int r = startTick; r <= landing && r < v.save.rows.size(); r++) {
            TreeSet<String> ks = keySet(v.save.rows.get(r));
            assertTrue("W missing at row " + r + " in " + v.label, ks.contains("W"));
            assertTrue("SPRINT missing at row " + r + " in " + v.label, ks.contains("SPRINT"));
            assertEquals("JUMP drifted at row " + r + " in " + v.label,
                    keySet(witness.rows.get(r)).contains("JUMP"), ks.contains("JUMP"));
            String strafe = ks.contains("A") ? "A" : ks.contains("D") ? "D" : "-";
            assertFalse("A and D together at row " + r + " in " + v.label,
                    ks.contains("A") && ks.contains("D"));
            if (r < lastFire) {
                if (momentum == null) momentum = strafe;
                assertEquals("momentum strafe inconsistent in " + v.label, momentum, strafe);
            } else if (r == lastFire) {
                assertEquals("strafe on the jump row in " + v.label, "-", strafe);
            } else {
                if (air == null) air = strafe;
                assertEquals("air strafe inconsistent in " + v.label, air, strafe);
            }
        }
    }

    private static int lastJump(SaveFile s, int startTick, int landing) {
        int last = -1;
        for (int t = Math.max(0, startTick); t <= landing && t < s.rows.size(); t++) {
            if (keySet(s.rows.get(t)).contains("JUMP")) last = t;
        }
        return last;
    }

    private static boolean hasDf(SaveFile s, int absTick) {
        if (s.angleSolver.ticks == null) return false;
        for (SaveFile.Tick t : s.angleSolver.ticks) {
            if (t == null || t.tick != absTick || t.constraints == null) continue;
            for (SaveFile.Constraint c : t.constraints) {
                if (c != null && !c.disabled && "DF".equals(c.field)) return true;
            }
        }
        return false;
    }

    private static void assertSingleEditableKeyEdit(SaveFile witness, StratVariants.Variant v) {
        int changedRows = 0;
        Set<String> changedKeys = new HashSet<String>();
        int adds = 0;
        int removes = 0;
        for (int r = 0; r < witness.rows.size(); r++) {
            TreeSet<String> a = keySet(witness.rows.get(r));
            TreeSet<String> b = keySet(v.save.rows.get(r));
            if (a.equals(b)) continue;
            changedRows++;
            Set<String> removed = new HashSet<String>(a);
            removed.removeAll(b);
            Set<String> added = new HashSet<String>(b);
            added.removeAll(a);
            assertEquals("more than one key changed in " + v.label + " row " + r,
                    1, removed.size() + added.size());
            String key = removed.isEmpty() ? added.iterator().next() : removed.iterator().next();
            assertTrue("non-editable key " + key + " changed in " + v.label, EDITABLE.contains(key));
            changedKeys.add(key);
            adds += added.size();
            removes += removed.size();
        }
        assertTrue("rows changed in " + v.label, changedRows == 1 || changedRows == 2);
        if (changedRows == 2) {
            assertEquals("tap move must shift one key in " + v.label, 1, changedKeys.size());
            assertEquals("tap move must add once in " + v.label, 1, adds);
            assertEquals("tap move must remove once in " + v.label, 1, removes);
        }
    }

    @Test
    public void deriveDebugSamplesFollowsKeepifyConvention() {
        SaveFile save = new SaveFile();
        save.angleSolver = new SaveFile.AngleSolver();
        save.angleSolver.startTick = 0;
        save.rows = new ArrayList<SaveFile.Row>();
        save.rows.add(row("W", "SPRINT", "JUMP"));
        save.rows.add(row("W", "S"));
        save.rows.add(row("W", "SPRINT", "SNEAK"));
        save.rows.add(row("W", "A"));
        save.debug = new ArrayList<SaveFile.DebugTick>();
        for (int i = 0; i < 5; i++) {
            save.debug.add(new SaveFile.DebugTick());
        }

        StratVariants.deriveDebugSamples(save, new JumpPhysicsInputs(4));

        float full = StratVariants.KEY_INPUT_SCALE;
        float sneak = StratVariants.KEY_INPUT_SCALE * StratVariants.SNEAK_INPUT_SCALE;
        assertEquals(full, save.debug.get(1).moveForward, 0f);
        assertEquals(0f, save.debug.get(1).moveStrafe, 0f);
        assertTrue(save.debug.get(1).sprinting);
        assertEquals(0f, save.debug.get(2).moveForward, 0f);
        assertFalse(save.debug.get(2).sprinting);
        assertEquals(sneak, save.debug.get(3).moveForward, 0f);
        assertFalse(save.debug.get(3).sprinting);
        assertEquals(full, save.debug.get(4).moveForward, 0f);
        assertEquals(full, save.debug.get(4).moveStrafe, 0f);
        assertFalse(save.debug.get(4).sprinting);
    }

    private static SaveFile.Row row(String... keys) {
        SaveFile.Row r = new SaveFile.Row();
        r.keys = new ArrayList<String>(Arrays.asList(keys));
        return r;
    }

    private static TreeSet<String> keySet(SaveFile.Row r) {
        TreeSet<String> set = new TreeSet<String>();
        if (r.keys != null) set.addAll(r.keys);
        return set;
    }

    private static String rowSignature(SaveFile s) {
        StringBuilder sb = new StringBuilder();
        for (SaveFile.Row r : s.rows) {
            sb.append(new TreeSet<String>(r.keys)).append(';');
        }
        return sb.toString();
    }
}
