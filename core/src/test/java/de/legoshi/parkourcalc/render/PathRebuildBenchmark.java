package de.legoshi.parkourcalc.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class PathRebuildBenchmark {

    private static final class ByteSink implements BoxRenderer {
        final ByteBuffer buf = ByteBuffer.allocateDirect(1 << 24).order(ByteOrder.nativeOrder());
        final Mode mode;
        final double ax;
        final double ay;
        final double az;
        long vertices;

        ByteSink(Mode mode, double ax, double ay, double az) {
            this.mode = mode;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
        }

        void reset() {
            buf.clear();
            vertices = 0;
        }

        private void vertex(double x, double y, double z, int argb) {
            if (buf.remaining() < 16) buf.clear();
            buf.putFloat((float) (x - ax));
            buf.putFloat((float) (y - ay));
            buf.putFloat((float) (z - az));
            buf.putInt(argb);
            vertices++;
        }

        @Override
        public void drawBox(AABB b, int argb) {
            double x0 = b.min.x;
            double y0 = b.min.y;
            double z0 = b.min.z;
            double x1 = b.max.x;
            double y1 = b.max.y;
            double z1 = b.max.z;
            if (mode == Mode.LINES) {
                line(x0, y0, z0, x1, y0, z0, argb);
                line(x1, y0, z0, x1, y0, z1, argb);
                line(x1, y0, z1, x0, y0, z1, argb);
                line(x0, y0, z1, x0, y0, z0, argb);
                line(x0, y1, z0, x1, y1, z0, argb);
                line(x1, y1, z0, x1, y1, z1, argb);
                line(x1, y1, z1, x0, y1, z1, argb);
                line(x0, y1, z1, x0, y1, z0, argb);
                line(x0, y0, z0, x0, y1, z0, argb);
                line(x1, y0, z0, x1, y1, z0, argb);
                line(x1, y0, z1, x1, y1, z1, argb);
                line(x0, y0, z1, x0, y1, z1, argb);
            } else {
                tri(x0, y0, z0, x1, y0, z0, x1, y0, z1, argb);
                tri(x0, y0, z0, x1, y0, z1, x0, y0, z1, argb);
                tri(x0, y1, z0, x0, y1, z1, x1, y1, z1, argb);
                tri(x0, y1, z0, x1, y1, z1, x1, y1, z0, argb);
                tri(x0, y0, z0, x0, y1, z0, x1, y1, z0, argb);
                tri(x0, y0, z0, x1, y1, z0, x1, y0, z0, argb);
                tri(x0, y0, z1, x1, y0, z1, x1, y1, z1, argb);
                tri(x0, y0, z1, x1, y1, z1, x0, y1, z1, argb);
                tri(x0, y0, z0, x0, y0, z1, x0, y1, z1, argb);
                tri(x0, y0, z0, x0, y1, z1, x0, y1, z0, argb);
                tri(x1, y0, z0, x1, y1, z0, x1, y1, z1, argb);
                tri(x1, y0, z0, x1, y1, z1, x1, y0, z1, argb);
            }
        }

        private void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            vertex(x1, y1, z1, argb);
            vertex(x2, y2, z2, argb);
        }

        private void tri(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            vertex(x1, y1, z1, argb);
            vertex(x2, y2, z2, argb);
            vertex(x3, y3, z3, argb);
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            if (mode != Mode.LINES) return;
            line(x1, y1, z1, x2, y2, z2, argb);
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            if (mode != Mode.FACES) return;
            tri(x1, y1, z1, x2, y2, z2, x3, y3, z3, argb);
        }
    }

    private static List<TickState> syntheticPath(int n) {
        List<TickState> states = new ArrayList<TickState>(n);
        for (int i = 0; i < n; i++) {
            Vec3dCore p = new Vec3dCore(i * 0.25, 64.0 + Math.sin(i * 0.05), Math.cos(i * 0.03));
            states.add(new TickState(p, (i % 12) < 6, false, false, (i * 7) % 360 - 180f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        return states;
    }

    private static long fullEmission(BoxController bc, List<TickState> states, Settings settings,
                                     SelectionManager sel, ByteSink faces, ByteSink lines, long[] splits) {
        long t0 = System.nanoTime();
        bc.clearAll();
        for (int i = 0; i < states.size(); i++) {
            bc.add(states.get(i));
        }
        float[] pitches = new float[states.size()];
        for (int i = 1; i < pitches.length; i++) {
            pitches[i] = pitches[i - 1];
        }
        bc.setPitches(pitches);
        long t1 = System.nanoTime();
        PathRenderPlan plan = PathRenderPlan.build(bc, settings, sel);
        PathVertexLayout.hitboxVertexStarts(bc,
                PathVertexLayout.hitboxEdges(settings.showHitbox, settings.showFullHitbox), settings.showSubtick);
        long t2 = System.nanoTime();
        faces.reset();
        plan.faceEmitter.accept(faces);
        long t3 = System.nanoTime();
        lines.reset();
        plan.lineEmitter.accept(lines);
        long t4 = System.nanoTime();
        splits[0] += t1 - t0;
        splits[1] += t2 - t1;
        splits[2] += t3 - t2;
        splits[3] += t4 - t3;
        return t4 - t0;
    }

    private static long incrementalEmission(BoxController bc, List<TickState> path, int dirtyTick, Settings settings,
                                            ByteSink faces, ByteSink lines) {
        long t0 = System.nanoTime();
        bc.replaceFrom(dirtyTick + 1, path.subList(dirtyTick + 1, path.size()));
        int n = bc.size();
        int from = dirtyTick;
        faces.reset();
        bc.render(faces, (i, s) -> BoxStyle.tickFaceArgb(settings, s, false), from, n);
        bc.renderFacingArrows(faces, true, false,
                BoxStyle.yawArrowArgb(settings), BoxStyle.pitchArrowArgb(settings), from, n - 1);
        lines.reset();
        bc.render(lines, (i, s) -> BoxStyle.tickLineArgb(settings, s, false), from, n);
        return System.nanoTime() - t0;
    }

    @Test
    public void measure() {
        assumeTrue("1".equals(System.getenv("PKC_BENCH")));
        PathRenderPlan.setConstraintSource(null);
        PathRenderPlan.setLiveSource(null);
        PathRenderPlan.setReachProbe(null);

        Settings settings = new Settings();
        SelectionManager sel = new SelectionManager(null);
        ByteSink faces = new ByteSink(BoxRenderer.Mode.FACES, 0, 64, 0);
        ByteSink lines = new ByteSink(BoxRenderer.Mode.LINES, 0, 64, 0);

        int big = 100_000;
        int small = 80;
        List<TickState> bigPath = syntheticPath(big);
        List<TickState> smallPath = syntheticPath(small);
        BoxController bc = new BoxController();

        long[] splits = new long[4];
        for (int i = 0; i < 2; i++) {
            fullEmission(bc, bigPath, settings, sel, faces, lines, splits);
        }
        Arrays.fill(splits, 0);
        long[] bigRuns = new long[5];
        for (int i = 0; i < bigRuns.length; i++) {
            bigRuns[i] = fullEmission(bc, bigPath, settings, sel, faces, lines, splits);
        }
        Arrays.sort(bigRuns);
        long bigVerts = faces.vertices + lines.vertices;

        long[] smallSplits = new long[4];
        for (int i = 0; i < 100; i++) {
            fullEmission(bc, smallPath, settings, sel, faces, lines, smallSplits);
        }
        long smallTotal = 0;
        int smallReps = 500;
        for (int i = 0; i < smallReps; i++) {
            smallTotal += fullEmission(bc, smallPath, settings, sel, faces, lines, smallSplits);
        }

        fullEmission(bc, bigPath, settings, sel, faces, lines, new long[4]);
        int tail = 10;
        int dirtyTick = big - tail - 1;
        for (int i = 0; i < 100; i++) {
            incrementalEmission(bc, bigPath, dirtyTick, settings, faces, lines);
        }
        long tailTotal = 0;
        int tailReps = 2000;
        long tailVerts = 0;
        for (int i = 0; i < tailReps; i++) {
            tailTotal += incrementalEmission(bc, bigPath, dirtyTick, settings, faces, lines);
            tailVerts = faces.vertices + lines.vertices;
        }

        StringBuilder out = new StringBuilder();
        out.append(String.format("full emission n=%d: median %.1f ms, min %.1f ms, %d verts baked%n",
                big, bigRuns[bigRuns.length / 2] / 1e6, bigRuns[0] / 1e6, bigVerts));
        out.append(String.format("  split (avg over %d runs): controller rebuild %.1f ms, plan %.2f ms, face bake %.1f ms, line bake %.1f ms%n",
                bigRuns.length, splits[0] / 1e6 / bigRuns.length, splits[1] / 1e6 / bigRuns.length,
                splits[2] / 1e6 / bigRuns.length, splits[3] / 1e6 / bigRuns.length));
        out.append(String.format("full emission n=%d: avg %.3f ms%n", small, smallTotal / 1e6 / smallReps));
        out.append(String.format("incremental emission (n=%d, dirtyTick=%d): avg %.4f ms, %d verts baked%n",
                big, dirtyTick, tailTotal / 1e6 / tailReps, tailVerts));
        System.out.print(out);
    }
}
