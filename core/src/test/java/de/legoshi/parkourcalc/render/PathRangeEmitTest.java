package de.legoshi.parkourcalc.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PathRangeEmitTest {

    private static final int ARGB = 0xFF336699;

    private static final class Lines implements BoxRenderer {
        final List<String> lines = new ArrayList<String>();

        @Override
        public void drawBox(AABB b, int argb) {
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            lines.add(x1 + "|" + y1 + "|" + z1 + "|" + x2 + "|" + y2 + "|" + z2 + "|" + argb);
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        }
    }

    private static TickState state(Vec3dCore position, List<Vec3dCore> subtick) {
        return new TickState(position, false, false, false, 0f, subtick, Vec3dCore.ZERO, false, Double.NaN);
    }

    private static BoxController path() {
        BoxController controller = new BoxController();
        for (int i = 0; i < 23; i++) {
            Vec3dCore p = new Vec3dCore(i * 0.3, 1.0 + (i % 4) * 0.1, i * 0.05);
            List<Vec3dCore> subtick;
            if (i % 5 == 0) {
                subtick = Collections.emptyList();
            } else if (i % 7 == 0) {
                subtick = Arrays.asList(p, p);
            } else {
                subtick = Arrays.asList(new Vec3dCore(p.x - 0.1, p.y, p.z), new Vec3dCore(p.x - 0.05, p.y + 0.02, p.z), p);
            }
            controller.add(state(i == 9 ? new Vec3dCore(8 * 0.3, 1.0, 8 * 0.05) : p, subtick));
        }
        return controller;
    }

    @Test
    public void chunkedRangesEmitTheSameLinesAsTheWholePath() {
        BoxController controller = path();
        Lines whole = new Lines();
        controller.renderPath(whole, ARGB, 0, 0, 0, Double.POSITIVE_INFINITY);

        for (int chunk = 1; chunk <= controller.size(); chunk++) {
            Lines chunked = new Lines();
            for (int from = 0; from < controller.size(); from += chunk) {
                controller.renderPath(chunked, ARGB, from, Math.min(controller.size(), from + chunk));
            }
            assertEquals("chunk " + chunk, whole.lines, chunked.lines);
        }
    }

    @Test
    public void rangeEmitMatchesTheSubtickVertexLayout() {
        BoxController controller = path();
        int[] starts = controller.subtickVertexStarts();
        for (int i = 0; i < controller.size(); i++) {
            Lines one = new Lines();
            controller.renderPath(one, ARGB, i, i + 1);
            assertEquals("tick " + i, starts[i + 1] - starts[i], one.lines.size() * 2);
        }
    }
}
