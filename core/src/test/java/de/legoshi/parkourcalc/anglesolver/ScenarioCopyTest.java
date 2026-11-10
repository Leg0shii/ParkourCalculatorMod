package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.PhysicsVariant;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScenarioCopyTest {

    private static final int TICKS = 4;

    private static List<Field> mutableFields() {
        List<Field> out = new ArrayList<>();
        for (Field f : JumpPhysicsInputs.class.getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m)) continue;
            out.add(f);
        }
        return out;
    }

    private static Object distinctValue(Field f, int salt) throws Exception {
        Class<?> t = f.getType();
        if (t == boolean.class) return Boolean.valueOf(!f.getBoolean(new JumpPhysicsInputs(TICKS)));
        if (t == int.class) return Integer.valueOf(7 + salt);
        if (t == float.class) return Float.valueOf(1.5F + salt);
        if (t == double.class) return Double.valueOf(2.5 + salt);
        if (t == Boolean.class) return Boolean.TRUE;
        if (t == Integer.class) return Integer.valueOf(3 + salt);
        if (t == Vec3dCore.class) return new Vec3dCore(1.0 + salt, 2.0, 3.0);
        if (t == StartBox.class) return StartBox.pinned(salt, 1.0, 0.0, 0.0);
        if (t == PhysicsVariant.class) return new PhysicsVariant(Double.valueOf(0.003), Boolean.TRUE, false, false, false, "test");
        if (t.isArray()) return Array.newInstance(t.getComponentType(), TICKS);
        if (t.isEnum()) return t.getEnumConstants()[t.getEnumConstants().length - 1];
        throw new AssertionError("ScenarioCopyTest has no sample value for " + f.getName() + " of type " + t);
    }

    private static JumpPhysicsInputs filled() throws Exception {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(TICKS);
        int salt = 0;
        for (Field f : mutableFields()) f.set(sc, distinctValue(f, salt++));
        return sc;
    }

    @Test
    public void everyFieldHasANonDefaultSample() throws Exception {
        JumpPhysicsInputs fresh = new JumpPhysicsInputs(TICKS);
        JumpPhysicsInputs sc = filled();
        for (Field f : mutableFields()) {
            Object v = f.get(sc);
            assertNotNull(f.getName(), v);
            Object d = f.get(fresh);
            assertTrue(f.getName() + " sample equals the default", d == null || !d.equals(v) || f.getType().isArray());
        }
    }

    @Test
    public void copyCarriesEveryField() throws Exception {
        JumpPhysicsInputs sc = filled();
        JumpPhysicsInputs c = sc.copy();
        assertNotSame(sc, c);
        for (Field f : mutableFields()) assertField(f, sc, c);
    }

    @Test
    public void pinnedAtChangesOnlyTheStart() throws Exception {
        JumpPhysicsInputs sc = filled();
        JumpPhysicsInputs p = sc.pinnedAt(10.25, -4.5);
        for (Field f : mutableFields()) {
            if (f.getName().equals("startPos") || f.getName().equals("startBox")) continue;
            assertField(f, sc, p);
        }
        assertEquals(10.25, p.startPos.x, 0.0);
        assertEquals(sc.startPos.y, p.startPos.y, 0.0);
        assertEquals(-4.5, p.startPos.z, 0.0);
        assertNotNull(p.startBox);
        assertEquals(10.25, p.startBox.pxLo, 0.0);
        assertEquals(10.25, p.startBox.pxHi, 0.0);
        assertEquals(-4.5, p.startBox.pzLo, 0.0);
        assertEquals(-4.5, p.startBox.pzHi, 0.0);
    }

    private static void assertField(Field f, JumpPhysicsInputs a, JumpPhysicsInputs b) throws Exception {
        Object va = f.get(a);
        Object vb = f.get(b);
        if (f.getType().isPrimitive()) assertEquals(f.getName(), va, vb);
        else assertSame(f.getName(), va, vb);
    }
}
