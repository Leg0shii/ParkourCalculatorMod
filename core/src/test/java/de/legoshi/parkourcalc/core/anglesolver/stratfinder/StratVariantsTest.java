package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final Map<String, StratPlans.Plan> PLANS = planIndex();

    private static Map<String, StratPlans.Plan> planIndex() {
        Map<String, StratPlans.Plan> out = new HashMap<String, StratPlans.Plan>();
        for (StratPlans.Plan p : StratPlans.plans(false)) {
            out.put(p.label, p);
        }
        return out;
    }

    private static SaveFile witness() {
        return GSON.fromJson(Fixtures.rawPool("hpk_human/d2/j012_1bm_4.25b"), SaveFile.class);
    }

    @Test
    public void enumeratesSelfFirstThenSingleKeyEditsAndYawShapes() {
        SaveFile save = witness();
        ExactJumpModel model = ExactJumpModel.forMcVersion(save.mcVersion);
        List<StratVariants.Variant> variants = StratVariants.variants(save, model);

        assertFalse("no variants enumerated", variants.isEmpty());
        assertTrue("cap exceeded", variants.size() <= 450);

        StratVariants.Variant self = variants.get(0);
        assertEquals("self", self.label);
        assertEquals(0, self.edits);
        assertEquals(rowSignature(save), rowSignature(self.save));

        int startTick = save.angleSolver.startTick;
        int landing = save.angleSolver.landingTick;
        int lastFire = lastJump(save, startTick, landing);
        Set<String> labels = new HashSet<String>();
        int patterns = 0;
        int familyBases = 0;
        int familyPatterns = 0;
        for (StratVariants.Variant v : variants) {
            assertTrue("duplicate label " + v.label, labels.add(v.label));
            assertEquals("row count changed for " + v.label, save.rows.size(), v.save.rows.size());
            assertEquals("FAST", v.save.angleSolver.effort);
            assertEquals(Boolean.TRUE, v.save.angleSolver.stopOnFeasible);
            assertNull("stale result on " + v.label, v.save.angleSolver.result);
            if (v.label.startsWith("nt[")) {
                patterns++;
                assertKeepDerive(v);
                assertStrafePattern(save, v, startTick, lastFire, landing);
            } else if (v.label.contains("/nt[")) {
                familyPatterns++;
                assertFamilyPattern(save, v, startTick, lastFire, landing);
            } else if (isFamily(v.label)) {
                familyBases++;
                assertFamilyVariant(save, v, startTick, landing);
            } else if (isShape(v.label)) {
                assertShapeChain(save, v, startTick, lastFire);
                String baseLabel = v.label.contains("/")
                        ? v.label.substring(0, v.label.indexOf('/')) : "";
                if (isFamily(baseLabel)) {
                    assertKeepDerive(v);
                }
            } else {
                assertEquals("constraints changed for " + v.label,
                        GSON.toJson(save.angleSolver.ticks), GSON.toJson(v.save.angleSolver.ticks));
                if (!"self".equals(v.label)) {
                    assertEquals(1, v.edits);
                    assertSingleEditableKeyEdit(save, v);
                }
            }
        }
        assertTrue("no family bases enumerated: " + familyBases, familyBases >= 8);
        assertTrue("no family pattern composites: " + familyPatterns, familyPatterns >= 9);

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

    private static boolean isFamily(String label) {
        return PLANS.containsKey(label);
    }

    private static void assertKeepDerive(StratVariants.Variant v) {
        assertEquals("defaultInputs not KEEP in " + v.label, "KEEP", v.save.angleSolver.defaultInputs);
        assertEquals("defaultSprint not DERIVE in " + v.label, "DERIVE", v.save.angleSolver.defaultSprint);
    }

    private static int firstJumpRow(SaveFile s, int startTick, int landing) {
        for (int t = Math.max(0, startTick); t <= landing && t < s.rows.size(); t++) {
            if (keySet(s.rows.get(t)).contains("JUMP")) return t;
        }
        return -1;
    }

    private static void assertJumpColumnPreserved(SaveFile witness, StratVariants.Variant v,
                                                  int startTick, int landing) {
        for (int r = Math.max(0, startTick); r <= landing && r < witness.rows.size(); r++) {
            assertEquals("JUMP drifted at row " + r + " in " + v.label,
                    keySet(witness.rows.get(r)).contains("JUMP"),
                    keySet(v.save.rows.get(r)).contains("JUMP"));
        }
    }

    private static void assertFamilyVariant(SaveFile witness, StratVariants.Variant v,
                                            int startTick, int landing) {
        StratPlans.Plan plan = PLANS.get(v.label);
        assertKeepDerive(v);
        assertJumpColumnPreserved(witness, v, startTick, landing);
        Map<Integer, String> a = constraintDump(witness);
        Map<Integer, String> b = constraintDump(v.save);
        assertEquals("constraints changed in " + v.label, a, b);
        int fire = firstJumpRow(witness, startTick, landing);
        assertTrue("no fire row for " + v.label, fire >= 0);
        assertEquals("fire sprint sample wrong in " + v.label,
                plan.fireKeys.contains("SPRINT"), v.save.debug.get(fire + 1).sprinting);
        int k = plan.lastPatchRel();
        if (k > 0) {
            assertTrue("sprint did not engage at rel " + k + " in " + v.label,
                    v.save.debug.get(fire + k + 1).sprinting);
        }
    }

    private static void assertFamilyPattern(SaveFile witness, StratVariants.Variant v,
                                            int startTick, int lastFire, int landing) {
        String familyLabel = v.label.substring(0, v.label.indexOf('/'));
        StratPlans.Plan plan = PLANS.get(familyLabel);
        assertTrue("unknown family in " + v.label, plan != null);
        assertKeepDerive(v);
        assertShapeChain(witness, v, startTick, lastFire);
        int fire = firstJumpRow(witness, startTick, landing);
        int k = plan.lastPatchRel();
        String[] phases = parsePattern(v.label);
        for (int r = Math.max(0, startTick); r <= landing && r < v.save.rows.size(); r++) {
            TreeSet<String> ks = keySet(v.save.rows.get(r));
            assertEquals("JUMP drifted at row " + r + " in " + v.label,
                    keySet(witness.rows.get(r)).contains("JUMP"), ks.contains("JUMP"));
            assertEquals("W wrong at row " + r + " in " + v.label,
                    expectW(plan, fire, r), ks.contains("W"));
            if (r >= fire) {
                assertEquals("SPRINT wrong at row " + r + " in " + v.label,
                        r >= fire + k, ks.contains("SPRINT"));
            }
            assertFalse("A and D together at row " + r + " in " + v.label,
                    ks.contains("A") && ks.contains("D"));
            String strafe = ks.contains("A") ? "A" : ks.contains("D") ? "D" : "-";
            assertEquals("strafe wrong at row " + r + " in " + v.label,
                    expectedStrafe(phases, r, lastFire), strafe);
        }
        assertEquals("fire sprint sample wrong in " + v.label,
                false, v.save.debug.get(fire + 1).sprinting);
    }

    private static String expectedStrafe(String[] phases, int r, int lastFire) {
        if (r < lastFire) return phases[0];
        if (r > lastFire) return phases[1];
        return "1".equals(phases[2]) ? phases[1] : "-";
    }

    private static boolean expectW(StratPlans.Plan plan, int fire, int r) {
        if (r == fire) {
            return plan.fireKeys.contains("W");
        }
        int rel = r - fire;
        if (rel >= 1 && plan.post.containsKey(rel)) {
            for (String on : plan.post.get(rel)[0]) {
                if ("W".equals(on)) return true;
            }
            for (String off : plan.post.get(rel)[1]) {
                if ("W".equals(off)) return false;
            }
        }
        return true;
    }

    private static String[] parsePattern(String label) {
        String inner = label.substring(label.indexOf('[') + 1, label.length() - 1);
        boolean airOnPress = inner.endsWith("*");
        if (airOnPress) {
            inner = inner.substring(0, inner.length() - 1);
        }
        int bar = inner.indexOf('|');
        return new String[]{inner.substring(0, bar), inner.substring(bar + 1), airOnPress ? "1" : "0"};
    }

    private static Map<Integer, String> constraintDump(SaveFile s) {
        Map<Integer, String> out = new HashMap<Integer, String>();
        if (s.angleSolver.ticks == null) return out;
        for (SaveFile.Tick t : s.angleSolver.ticks) {
            if (t == null) continue;
            String slip = t.override != null ? t.override.slipperiness : null;
            out.put(t.tick, GSON.toJson(t.constraints) + "|" + slip);
        }
        return out;
    }

    private static void assertShapeChain(SaveFile witness, StratVariants.Variant v,
                                         int startTick, int lastFire) {
        boolean ja = "ja".equals(v.label) || v.label.endsWith("/ja");
        for (int t = startTick + 1; t <= lastFire; t++) {
            boolean pinned = hasDf(v.save, t);
            if (ja && t == lastFire) {
                if (!hasDf(witness, t)) {
                    assertFalse("ja pinned its jump tick in " + v.label, pinned);
                }
            } else {
                assertTrue("chain gap at T" + t + " in " + v.label, pinned);
            }
        }
    }

    private static void assertStrafePattern(SaveFile witness, StratVariants.Variant v,
                                            int startTick, int lastFire, int landing) {
        assertShapeChain(witness, v, startTick, lastFire);
        String[] phases = parsePattern(v.label);
        for (int r = startTick; r <= landing && r < v.save.rows.size(); r++) {
            TreeSet<String> ks = keySet(v.save.rows.get(r));
            assertTrue("W missing at row " + r + " in " + v.label, ks.contains("W"));
            assertTrue("SPRINT missing at row " + r + " in " + v.label, ks.contains("SPRINT"));
            assertEquals("JUMP drifted at row " + r + " in " + v.label,
                    keySet(witness.rows.get(r)).contains("JUMP"), ks.contains("JUMP"));
            String strafe = ks.contains("A") ? "A" : ks.contains("D") ? "D" : "-";
            assertFalse("A and D together at row " + r + " in " + v.label,
                    ks.contains("A") && ks.contains("D"));
            assertEquals("strafe wrong at row " + r + " in " + v.label,
                    expectedStrafe(phases, r, lastFire), strafe);
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
