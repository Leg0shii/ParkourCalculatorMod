package de.legoshi.parkourcalc.core.multireplay;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MultiReplayGeometryTest {

    private static final class Counting implements BoxRenderer {
        int triangles;
        final List<double[]> lines = new ArrayList<double[]>();

        @Override
        public void drawBox(AABB box, int argb) {
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            lines.add(new double[] { x1, y1, z1, x2, y2, z2 });
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2,
                                 double x3, double y3, double z3, int argb) {
            triangles++;
        }
    }

    private MultiReplay state;
    private ReplayTrack track;

    @Before
    public void setUp() {
        state = new MultiReplay();
        track = new ReplayTrack("a", 0xFFFF0000, Arrays.asList(
                new Vec3dCore(0, 0, 0), new Vec3dCore(1, 0, 0), new Vec3dCore(2, 0, 0), new Vec3dCore(3, 0, 0)));
        state.replaceTracks("f", Collections.singletonList(track), Collections.<String>emptyList());
    }

    @Test
    public void midTickDrawsReachedSpheresAndPartialLine() {
        state.clock().seek(1.5);
        Counting faces = new Counting();
        Counting lines = new Counting();
        MultiReplayGeometry.renderSpheres(state, faces);
        MultiReplayGeometry.renderLines(state, lines);
        assertEquals(2 * MultiReplayGeometry.TRIANGLES_PER_SPHERE, faces.triangles);
        assertEquals(2, lines.lines.size());
        double[] partial = lines.lines.get(1);
        assertEquals(1.0, partial[0], 1e-12);
        assertEquals(1.5, partial[3], 1e-12);
    }

    @Test
    public void endDrawsEverything() {
        state.clock().seek(3);
        Counting faces = new Counting();
        Counting lines = new Counting();
        MultiReplayGeometry.renderSpheres(state, faces);
        MultiReplayGeometry.renderLines(state, lines);
        assertEquals(4 * MultiReplayGeometry.TRIANGLES_PER_SPHERE, faces.triangles);
        assertEquals(3, lines.lines.size());
    }

    @Test
    public void startDrawsOnlyFirstSphere() {
        Counting faces = new Counting();
        Counting lines = new Counting();
        MultiReplayGeometry.renderSpheres(state, faces);
        MultiReplayGeometry.renderLines(state, lines);
        assertEquals(MultiReplayGeometry.TRIANGLES_PER_SPHERE, faces.triangles);
        assertEquals(0, lines.lines.size());
    }

    @Test
    public void upcomingSpheresAreDrawnWhenEnabled() {
        state.clock().seek(1.5);
        state.setShowUpcoming(true);
        Counting faces = new Counting();
        MultiReplayGeometry.renderSpheres(state, faces);
        assertEquals(4 * MultiReplayGeometry.TRIANGLES_PER_SPHERE, faces.triangles);
    }

    @Test
    public void hiddenTrackDrawsNothing() {
        state.clock().seek(3);
        track.setVisible(false);
        Counting faces = new Counting();
        Counting lines = new Counting();
        MultiReplayGeometry.renderSpheres(state, faces);
        MultiReplayGeometry.renderLines(state, lines);
        assertEquals(0, faces.triangles);
        assertEquals(0, lines.lines.size());
    }

    @Test
    public void shorterTrackStopsAtItsOwnEnd() {
        ReplayTrack shortTrack = new ReplayTrack("b", 0xFF00FF00,
                Arrays.asList(new Vec3dCore(0, 0, 0), new Vec3dCore(0, 1, 0)));
        state.replaceTracks("f", Arrays.asList(track, shortTrack), Collections.<String>emptyList());
        state.clock().seek(2.5);
        Counting faces = new Counting();
        Counting lines = new Counting();
        MultiReplayGeometry.renderSpheres(state, faces);
        MultiReplayGeometry.renderLines(state, lines);
        assertEquals(5 * MultiReplayGeometry.TRIANGLES_PER_SPHERE, faces.triangles);
        assertEquals(4, lines.lines.size());
    }
}
