package de.legoshi.parkourcalc.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.StartDragGate;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StartDragGateTest {

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double TOL = 1.0e-9;
    private static final double EPS = 1.0e-9;
    private static final double CLAMP_EPS = 1.0e-4;

    private static final class FakeProbe implements StartDragGate.SimProbe {
        private final Vec3dCore offset;
        private final double wallX;
        private final double wallZ;
        Vec3dCore start = Vec3dCore.ZERO;

        FakeProbe(Vec3dCore offset, double wallX, double wallZ) {
            this.offset = offset;
            this.wallX = wallX;
            this.wallZ = wallZ;
        }

        @Override public void simulateAt(Vec3dCore s) {
            this.start = s;
        }

        @Override public Vec3dCore tickPosition(int index) {
            double x = Math.min(start.x + offset.x, wallX);
            double z = Math.min(start.z + offset.z, wallZ);
            return new Vec3dCore(x, start.y + offset.y, z);
        }
    }

    private static List<Integer> idx(int... values) {
        List<Integer> list = new ArrayList<>();
        for (int v : values) list.add(v);
        return list;
    }

    private static List<Vec3dCore> baseline(FakeProbe probe, Vec3dCore start, int... indices) {
        probe.simulateAt(start);
        List<Vec3dCore> list = new ArrayList<>();
        for (int i : indices) list.add(probe.tickPosition(i));
        return list;
    }

    @Test
    public void openSpaceCommitsCandidateOnBothAxes() {
        FakeProbe probe = new FakeProbe(new Vec3dCore(1, 0, 1), INF, INF);
        StartDragGate gate = new StartDragGate(probe, TOL);
        Vec3dCore start = new Vec3dCore(0, 0, 0);
        gate.begin(start, idx(0), baseline(probe, start, 0));

        gate.move(new Vec3dCore(0.7, 0, 0.4));

        assertEquals(0.7, probe.start.x, EPS);
        assertEquals(0.4, probe.start.z, EPS);
    }

    @Test
    public void clampMovesXFullyWhileZHitsWall() {
        FakeProbe probe = new FakeProbe(new Vec3dCore(1, 0, 1), INF, 1.3);
        StartDragGate gate = new StartDragGate(probe, TOL);
        Vec3dCore start = new Vec3dCore(0, 0, 0);
        gate.begin(start, idx(0), baseline(probe, start, 0));

        gate.move(new Vec3dCore(0.5, 0, 0.5));

        assertEquals("X moves fully despite the Z wall", 0.5, probe.start.x, EPS);
        assertEquals("Z clamps at the wall boundary", 0.3, probe.start.z, CLAMP_EPS);
    }

    @Test
    public void endReportsMovedWhenStartShifted() {
        FakeProbe probe = new FakeProbe(new Vec3dCore(1, 0, 1), INF, INF);
        StartDragGate gate = new StartDragGate(probe, TOL);
        Vec3dCore start = new Vec3dCore(0, 0, 0);
        gate.begin(start, idx(0), baseline(probe, start, 0));

        gate.move(new Vec3dCore(0.5, 0, 0.5));
        assertTrue(gate.end());

        gate.begin(start, idx(0), baseline(probe, start, 0));
        assertFalse("no move means not dirty", gate.end());
    }

    @Test
    public void emptySelectionNeverConstrains() {
        FakeProbe probe = new FakeProbe(new Vec3dCore(1, 0, 1), INF, 1.3);
        StartDragGate gate = new StartDragGate(probe, TOL);
        Vec3dCore start = new Vec3dCore(0, 0, 0);
        gate.begin(start, Collections.<Integer>emptyList(), Collections.<Vec3dCore>emptyList());

        gate.move(new Vec3dCore(0.5, 0, 5.0));

        assertEquals(0.5, probe.start.x, EPS);
        assertEquals("no selection means no constraint", 5.0, probe.start.z, EPS);
    }
}
