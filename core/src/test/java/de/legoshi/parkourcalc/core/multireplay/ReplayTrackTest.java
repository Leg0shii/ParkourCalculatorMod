package de.legoshi.parkourcalc.core.multireplay;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ReplayTrackTest {

    private static ReplayTrack track() {
        return new ReplayTrack("a", 0xFFFF0000,
                Arrays.asList(new Vec3dCore(0, 0, 0), new Vec3dCore(2, 0, 0), new Vec3dCore(2, 1, 2)),
                new float[] { 350f, 10f, 90f }, new float[] { 0f, 20f, -10f });
    }

    @Test
    public void sneakAndSwingFollowTheTick() {
        ReplayTrack t = new ReplayTrack("s", 0,
                Arrays.asList(new Vec3dCore(0, 0, 0), new Vec3dCore(1, 0, 0), new Vec3dCore(2, 0, 0),
                        new Vec3dCore(3, 0, 0), new Vec3dCore(4, 0, 0), new Vec3dCore(5, 0, 0),
                        new Vec3dCore(6, 0, 0), new Vec3dCore(7, 0, 0), new Vec3dCore(8, 0, 0)),
                null, null,
                new boolean[] { false, true, true, false, false, false, false, false, false },
                new boolean[] { false, true, false, false, false, false, false, false, true });
        assertEquals(false, t.sneakingAt(0.9));
        assertEquals(true, t.sneakingAt(1.0));
        assertEquals(true, t.sneakingAt(2.99));
        assertEquals(false, t.sneakingAt(3.0));
        assertEquals(0f, t.swingProgressAt(0.5), 0f);
        assertEquals(0f, t.swingProgressAt(1.0), 0f);
        assertEquals(0.5f, t.swingProgressAt(4.0), 1e-6);
        assertEquals(0f, t.swingProgressAt(7.0), 0f);
        assertEquals(0f, t.swingProgressAt(8.0), 0f);
        assertEquals(1f / 12f, t.swingProgressAt(8.5), 1e-6);
    }

    @Test
    public void pitchInterpolatesLinearly() {
        assertEquals(10f, track().pitchAt(0.5), 1e-4);
        assertEquals(5f, track().pitchAt(1.5), 1e-4);
        assertEquals(-10f, track().pitchAt(7), 0f);
    }

    @Test
    public void interpolatesPositionBetweenTicks() {
        Vec3dCore p = track().positionAt(0.5);
        assertEquals(1.0, p.x, 1e-12);
        assertEquals(0.0, p.y, 1e-12);
        Vec3dCore q = track().positionAt(1.25);
        assertEquals(2.0, q.x, 1e-12);
        assertEquals(0.25, q.y, 1e-12);
        assertEquals(0.5, q.z, 1e-12);
    }

    @Test
    public void clampsBeyondEnds() {
        assertEquals(new Vec3dCore(2, 1, 2), track().positionAt(9));
        assertEquals(new Vec3dCore(0, 0, 0), track().positionAt(-3));
        assertEquals(90f, track().yawAt(9), 0f);
    }

    @Test
    public void yawInterpolatesAcrossWrap() {
        assertEquals(0f, normalize(track().yawAt(0.5)), 1e-4);
        assertEquals(50f, track().yawAt(1.5), 1e-4);
        assertEquals(350f, track().yawAt(0), 0f);
    }

    @Test
    public void emptyTrackIsSafe() {
        ReplayTrack empty = new ReplayTrack("e", 0, Collections.<Vec3dCore>emptyList());
        assertEquals(Vec3dCore.ZERO, empty.positionAt(3));
        assertEquals(0f, empty.yawAt(3), 0f);
        assertEquals(0, empty.lastTick());
    }

    @Test
    public void missingYawsDefaultToZero() {
        ReplayTrack t = new ReplayTrack("n", 0, Arrays.asList(new Vec3dCore(0, 0, 0), new Vec3dCore(1, 0, 0)));
        assertEquals(0f, t.yaw(1), 0f);
    }

    private static float normalize(float yaw) {
        float y = yaw % 360f;
        if (y < 0) y += 360f;
        return y;
    }
}
