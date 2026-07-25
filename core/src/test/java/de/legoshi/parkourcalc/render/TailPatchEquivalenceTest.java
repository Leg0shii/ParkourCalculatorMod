package de.legoshi.parkourcalc.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxColorPicker;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TailPatchEquivalenceTest {

    private static final double ALL = Double.POSITIVE_INFINITY;
    private static final int FACE_VERTS = PathVertexLayout.FACE_VERTS_PER_BOX;
    private static final int ARROW_VERTS = PathVertexLayout.ARROW_VERTS_PER_BOX;

    private final Settings settings = new Settings();
    private final BoxColorPicker facePicker = (i, s) -> BoxStyle.tickFaceArgb(settings, s, false);
    private final BoxColorPicker linePicker = (i, s) -> BoxStyle.tickLineArgb(settings, s, false);
    private final int yawArgb = BoxStyle.yawArrowArgb(settings);
    private final int combinedArgb = BoxStyle.pitchArrowArgb(settings);

    private static final class Rec implements BoxRenderer {
        final Mode mode;
        final List<String> verts = new ArrayList<String>();

        Rec(Mode mode) {
            this.mode = mode;
        }

        @Override
        public void drawBox(AABB b, int argb) {
            int count = mode == Mode.LINES ? 24 : 36;
            String s = "B|" + b.min.x + "|" + b.min.y + "|" + b.min.z + "|" + b.max.x + "|" + b.max.y + "|" + b.max.z + "|" + argb;
            for (int i = 0; i < count; i++) {
                verts.add(s);
            }
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            if (mode != Mode.LINES) return;
            String s = "L|" + x1 + "|" + y1 + "|" + z1 + "|" + x2 + "|" + y2 + "|" + z2 + "|" + argb;
            verts.add(s);
            verts.add(s);
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            if (mode != Mode.FACES) return;
            String s = "T|" + x1 + "|" + y1 + "|" + z1 + "|" + x2 + "|" + y2 + "|" + z2 + "|" + x3 + "|" + y3 + "|" + z3 + "|" + argb;
            verts.add(s);
            verts.add(s);
            verts.add(s);
        }
    }

    private static TickState state(double x, double y, double z, float yaw) {
        return new TickState(new Vec3dCore(x, y, z), true, false, false, yaw,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    private static List<TickState> path(int n, int seed) {
        List<TickState> list = new ArrayList<TickState>();
        for (int i = 0; i < n; i++) {
            list.add(state(i * 0.3 + seed * 0.017, 64.0 + (i % 5) * 0.1, seed * 0.5 + i * 0.05, (i * 13 + seed * 7) % 360 - 180));
        }
        return list;
    }

    private static BoxController controllerWith(List<TickState> states, float[] pitches) {
        BoxController bc = new BoxController();
        for (TickState s : states) {
            bc.add(s);
        }
        bc.setPitches(pitches);
        return bc;
    }

    private List<String> fullFaces(BoxController bc, boolean drawYaw, boolean drawCombined) {
        Rec r = new Rec(BoxRenderer.Mode.FACES);
        bc.render(r, facePicker, 0, 0, 0, ALL);
        bc.renderFacingArrows(r, drawYaw, drawCombined, yawArgb, combinedArgb, 0, 0, 0, ALL);
        return r.verts;
    }

    private List<String> fullLines(BoxController bc) {
        Rec r = new Rec(BoxRenderer.Mode.LINES);
        bc.render(r, linePicker, 0, 0, 0, ALL);
        return r.verts;
    }

    private List<String> patchedFaces(List<String> base, BoxController bc, int from, boolean drawYaw, boolean drawCombined) {
        int n = bc.size();
        List<String> out = new ArrayList<String>(base);
        Rec fr = new Rec(BoxRenderer.Mode.FACES);
        bc.render(fr, facePicker, from, n);
        splice(out, from * FACE_VERTS, n * FACE_VERTS, fr.verts);
        int arrowsPerBox = (drawYaw ? 1 : 0) + (drawCombined ? 1 : 0);
        if (arrowsPerBox > 0 && from < n - 1) {
            Rec ar = new Rec(BoxRenderer.Mode.FACES);
            bc.renderFacingArrows(ar, drawYaw, drawCombined, yawArgb, combinedArgb, from, n - 1);
            int arrowBase = n * FACE_VERTS;
            int stride = arrowsPerBox * ARROW_VERTS;
            splice(out, arrowBase + from * stride, arrowBase + (n - 1) * stride, ar.verts);
        }
        return out;
    }

    private List<String> patchedLines(List<String> base, BoxController bc, int from) {
        int n = bc.size();
        List<String> out = new ArrayList<String>(base);
        Rec lr = new Rec(BoxRenderer.Mode.LINES);
        bc.render(lr, linePicker, from, n);
        splice(out, from * 24, n * 24, lr.verts);
        return out;
    }

    private static void splice(List<String> target, int start, int end, List<String> replacement) {
        assertEquals(end - start, replacement.size());
        for (int k = 0; k < replacement.size(); k++) {
            target.set(start + k, replacement.get(k));
        }
    }

    @Test
    public void yawDragTailPatchMatchesFullRebake() {
        int n = 40;
        int dirtyTick = 25;
        List<TickState> before = path(n, 1);
        List<TickState> after = new ArrayList<TickState>(before.subList(0, dirtyTick + 1));
        after.addAll(path(n, 2).subList(dirtyTick + 1, n));

        BoxController bc = controllerWith(before, new float[n]);
        List<String> baseFaces = fullFaces(bc, true, false);
        List<String> baseLines = fullLines(bc);

        long revBefore = bc.getGeometryRev();
        bc.takeDirtyFrom(revBefore);
        bc.replaceFrom(dirtyTick + 1, after.subList(dirtyTick + 1, n));
        bc.setPitches(new float[n]);
        assertEquals(dirtyTick + 1, bc.takeDirtyFrom(revBefore));

        int from = dirtyTick;
        BoxController expected = controllerWith(after, new float[n]);
        assertEquals(fullFaces(expected, true, false), patchedFaces(baseFaces, bc, from, true, false));
        assertEquals(fullLines(expected), patchedLines(baseLines, bc, from));
        assertEquals(fullFaces(expected, true, false), fullFaces(bc, true, false));
    }

    @Test
    public void pitchOnlyChangePatchesArrowTail() {
        int n = 30;
        int firstChangedPitch = 18;
        List<TickState> states = path(n, 3);
        float[] before = new float[n];
        float[] after = new float[n];
        for (int i = firstChangedPitch; i < n; i++) {
            after[i] = 30.0f + i;
        }

        BoxController bc = controllerWith(states, before);
        List<String> baseFaces = fullFaces(bc, true, true);

        long revBefore = bc.getGeometryRev();
        bc.takeDirtyFrom(revBefore);
        bc.setPitches(after);
        assertEquals(firstChangedPitch, bc.takeDirtyFrom(revBefore));

        int from = firstChangedPitch - 1;
        BoxController expected = controllerWith(states, after);
        assertEquals(fullFaces(expected, true, true), patchedFaces(baseFaces, bc, from, true, true));
    }

    @Test
    public void takeDirtyFromTracksMinAndStaleness() {
        BoxController bc = controllerWith(path(5, 4), new float[5]);
        long r0 = bc.getGeometryRev();
        bc.takeDirtyFrom(r0);

        assertEquals(5, bc.takeDirtyFrom(bc.getGeometryRev()));

        bc.add(state(9, 9, 9, 0));
        bc.add(state(10, 9, 9, 0));
        assertEquals(5, bc.takeDirtyFrom(r0));

        long r1 = bc.getGeometryRev();
        bc.replaceFrom(3, path(4, 5));
        assertEquals(3, bc.takeDirtyFrom(r1));

        bc.add(state(11, 9, 9, 0));
        assertEquals(0, bc.takeDirtyFrom(r0));
    }
}
