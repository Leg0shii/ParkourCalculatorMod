package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunState;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeStatus;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.ConstraintBoxSource;
import de.legoshi.parkourcalc.core.render.ConstraintPlate;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LivePathVisualizationTest {

    private static TickState tickAt(Vec3dCore p) {
        return new TickState(p, false, false, false, 0f, Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    private static BoxController boxesWith(Vec3dCore... feet) {
        BoxController boxes = new BoxController();
        for (Vec3dCore f : feet) boxes.add(tickAt(f));
        return boxes;
    }

    private static ConstraintBoxSource plateSource(final ConstraintPlate plate, final long revision) {
        return new ConstraintBoxSource() {
            @Override
            public List<ConstraintPlate> platesAt(int tickIndex) {
                return tickIndex == plate.tick
                        ? Collections.singletonList(plate)
                        : Collections.<ConstraintPlate>emptyList();
            }

            @Override
            public long revision() {
                return revision;
            }
        };
    }

    private static ConstraintPlate marker(double x, double z, int tick, boolean pickable) {
        AABB box = AABB.ofCenteredXZ(new Vec3dCore(x, 64.0, z), 0.12);
        return new ConstraintPlate(ConstraintShapes.Sense.INCLUDE, true,
                Collections.singletonList(box), Collections.<AABB>emptyList(), tick, new int[0], pickable);
    }

    private static final class VertCounter implements BoxRenderer {
        final Mode mode;
        long count;

        VertCounter(Mode mode) {
            this.mode = mode;
        }

        @Override
        public void drawBox(AABB box, int argb) {
            count += (mode == Mode.LINES) ? 24 : 36;
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            if (mode == Mode.LINES) count += 2;
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            if (mode == Mode.FACES) count += 3;
        }
    }

    @Test
    public void runStateRecordsStepsWithIdsAndBranches() {
        GraphRunState rs = new GraphRunState();
        rs.begin("race", "CMA-ES race", 3_000_000_000L);
        assertEquals("race", rs.activeNodeId());
        List<GraphRunState.Step> steps = rs.steps();
        assertEquals(1, steps.size());
        assertEquals("race", steps.get(0).nodeId);
        assertEquals("CMA-ES race", steps.get(0).label);
        assertNull(steps.get(0).taken);

        rs.end("race", Guarantee.FOUND, 120L);
        rs.begin("race", "CMA-ES race", 0L);
        rs.end("race", Guarantee.NONE, 30L);

        steps = rs.steps();
        assertEquals(2, steps.size());
        assertEquals(Guarantee.FOUND, steps.get(0).taken);
        assertEquals(Guarantee.NONE, steps.get(1).taken);
        assertNull(rs.activeNodeId());

        NodeStatus st = rs.status("race");
        assertEquals(NodeStatus.Phase.DONE, st.phase);
        assertEquals(2, st.visits);
        assertEquals(150L, st.evals);
    }

    @Test
    public void runStateVersionBumpsOnBeginAndEnd() {
        GraphRunState rs = new GraphRunState();
        int v0 = rs.version();
        rs.begin("a", "A", 0L);
        int v1 = rs.version();
        assertTrue(v1 > v0);
        rs.end("a", Guarantee.DONE, 0L);
        assertTrue(rs.version() > v1);
    }

    @Test
    public void stepCopiesAreDetachedFromLiveMutation() {
        GraphRunState rs = new GraphRunState();
        rs.begin("a", "A", 0L);
        List<GraphRunState.Step> before = rs.steps();
        rs.end("a", Guarantee.FOUND, 0L);
        assertNull(before.get(0).taken);
        assertEquals(Guarantee.FOUND, rs.steps().get(0).taken);
    }

    @Test
    public void nonPickablePlatesAreInvisibleToPicking() {
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        Vec3dCore down = new Vec3dCore(0.0, -1.0, 0.0);
        Vec3dCore above = new Vec3dCore(5.5, 100.0, 0.5);

        ConstraintBoxSource pickableSrc = plateSource(marker(5.5, 0.5, 1, true), 1L);
        assertTrue(boxes.isCursorOverConstraint(above, down, pickableSrc));
        assertEquals(1, boxes.pickWorld(above, down, pickableSrc).index);

        ConstraintBoxSource liveSrc = plateSource(marker(5.5, 0.5, 1, false), 1L);
        assertFalse(boxes.isCursorOverConstraint(above, down, liveSrc));
        assertNull(boxes.pickWorld(above, down, liveSrc));
    }

    @Test
    public void liveRegionBakesIndependentlyOfShowConstraints() {
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        Settings settings = new Settings();
        settings.showConstraints = false;
        SelectionManager selection = new SelectionManager(null);
        try {
            PathRenderPlan.setConstraintSource(ConstraintBoxSource.NONE);
            PathRenderPlan.setLiveSource(plateSource(marker(3.5, 0.5, 0, false), 7L));

            PathRenderPlan with = PathRenderPlan.build(boxes, settings, selection);
            VertCounter facesWith = new VertCounter(BoxRenderer.Mode.FACES);
            with.faceEmitter.accept(facesWith);
            VertCounter linesWith = new VertCounter(BoxRenderer.Mode.LINES);
            with.lineEmitter.accept(linesWith);
            assertEquals(36, with.constraintFaceVerts);
            assertEquals(24, with.constraintLineVerts);

            PathRenderPlan.setLiveSource(ConstraintBoxSource.NONE);
            PathRenderPlan without = PathRenderPlan.build(boxes, settings, selection);
            VertCounter facesWithout = new VertCounter(BoxRenderer.Mode.FACES);
            without.faceEmitter.accept(facesWithout);
            VertCounter linesWithout = new VertCounter(BoxRenderer.Mode.LINES);
            without.lineEmitter.accept(linesWithout);
            assertEquals(0, without.constraintFaceVerts);
            assertEquals(with.constraintFaceVerts, facesWith.count - facesWithout.count);
            assertEquals(with.constraintLineVerts, linesWith.count - linesWithout.count);

            PathRenderPlan.setLiveSource(plateSource(marker(3.5, 0.5, 0, false), 8L));
            PathRenderPlan bumped = PathRenderPlan.build(boxes, settings, selection);
            assertNotEquals(with.structuralHash, bumped.structuralHash);
            assertNotEquals(without.structuralHash, bumped.structuralHash);
        } finally {
            PathRenderPlan.setConstraintSource(ConstraintBoxSource.NONE);
            PathRenderPlan.setLiveSource(ConstraintBoxSource.NONE);
        }
    }
}
