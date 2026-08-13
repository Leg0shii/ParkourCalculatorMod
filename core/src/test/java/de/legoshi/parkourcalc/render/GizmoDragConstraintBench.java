package de.legoshi.parkourcalc.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.CountingBoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.render.TailPatchGate;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverConstraintSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class GizmoDragConstraintBench {

    private static final int N = 5001;
    private static final int CONSTRAINT_SPACING = 12;
    private static final int WARMUP_FRAMES = 30;
    private static final int FRAMES = 200;

    private static TickState state(double x, double y, double z, float yaw) {
        return new TickState(new Vec3dCore(x, y, z), true, false, false, yaw,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    private static List<TickState> path(int n, int seed) {
        List<TickState> list = new ArrayList<TickState>();
        for (int i = 0; i < n; i++) {
            list.add(state(i * 0.28 + seed * 0.013, 64.0 + (i % 7) * 0.1, seed * 0.4 + i * 0.04,
                    (i * 11 + seed * 5) % 360 - 180));
        }
        return list;
    }

    private static BoxController controllerWith(List<TickState> states) {
        BoxController bc = new BoxController();
        for (TickState s : states) {
            bc.add(s);
        }
        bc.setPitches(new float[states.size()]);
        return bc;
    }

    private static final class LoaderCacheReplica {
        boolean built;
        long lastGeometryRev = -1;
        int lastStructuralHash;
        int boxCount;
        int hitboxEdges;
        boolean useSubtick;
        int constraintFaceVerts;
        int constraintLineVerts;

        boolean fastPathTaken(BoxController bc, PathRenderPlan plan) {
            if (!TailPatchGate.canPatch(built, plan.structuralHash == lastStructuralHash,
                    bc.size() == boxCount, hitboxEdges, useSubtick, plan,
                    constraintFaceVerts, constraintLineVerts)) {
                return false;
            }
            return bc.takeDirtyFrom(lastGeometryRev) > 0;
        }

        void adopt(BoxController bc, PathRenderPlan plan) {
            built = true;
            lastGeometryRev = bc.getGeometryRev();
            lastStructuralHash = plan.structuralHash;
            boxCount = bc.size();
            hitboxEdges = plan.patch.hitboxEdges();
            useSubtick = plan.patch.showSubtick;
            constraintFaceVerts = plan.constraintFaceVerts;
            constraintLineVerts = plan.constraintLineVerts;
        }
    }

    private static long rebuildEmission(BoxController bc, PathRenderPlan plan,
                                        ByteSinkRenderer faces, ByteSinkRenderer lines) {
        long t0 = System.nanoTime();
        CountingBoxRenderer faceCounter = new CountingBoxRenderer(BoxRenderer.Mode.FACES);
        plan.faceEmitter.accept(faceCounter);
        CountingBoxRenderer lineCounter = new CountingBoxRenderer(BoxRenderer.Mode.LINES);
        plan.lineEmitter.accept(lineCounter);
        PathVertexLayout.hitboxVertexStarts(bc, plan.patch.hitboxEdges(), plan.patch.showSubtick);
        faces.reset();
        plan.faceEmitter.accept(faces);
        lines.reset();
        plan.lineEmitter.accept(lines);
        return System.nanoTime() - t0;
    }

    private static long patchEmission(BoxController bc, PathRenderPlan plan, int from,
                                      ByteSinkRenderer faces, ByteSinkRenderer lines) {
        long t0 = System.nanoTime();
        int n = bc.size();
        faces.reset();
        bc.render(faces, plan.patch.facePicker, from, n);
        if (plan.patch.hitboxEdges() != 0) {
            if (plan.patch.showFullHitbox) {
                bc.renderHitboxFullWireframe(faces, plan.patch.hitboxPicker, plan.patch.showSubtick, from, n);
            } else {
                bc.renderHitboxFloorOutline(faces, plan.patch.hitboxPicker, plan.patch.showSubtick, from, n);
            }
        }
        bc.renderFacingArrows(faces, plan.patch.drawYawArrows, plan.patch.drawCombinedArrows,
                plan.patch.yawArrowArgb, plan.patch.combinedArrowArgb, from, Math.max(from, n - 1));
        plan.constraintFaceEmitter.accept(faces);
        lines.reset();
        bc.render(lines, plan.patch.linePicker, from, n);
        plan.constraintLineEmitter.accept(lines);
        return System.nanoTime() - t0;
    }

    private long[] driveFrames(BoxController bc, Settings settings, SelectionManager sel, int frames, int[] pathCounts) {
        ByteSinkRenderer faces = new ByteSinkRenderer(BoxRenderer.Mode.FACES, 0, 64, 0);
        ByteSinkRenderer lines = new ByteSinkRenderer(BoxRenderer.Mode.LINES, 0, 64, 0);
        LoaderCacheReplica cache = new LoaderCacheReplica();

        PathRenderPlan initial = PathRenderPlan.build(bc, settings, sel);
        rebuildEmission(bc, initial, faces, lines);
        bc.takeDirtyFrom(bc.getGeometryRev());
        cache.adopt(bc, initial);

        int dirtyTick = N - 3;
        long rebuildNanos = 0;
        long patchNanos = 0;
        long planNanos = 0;
        for (int f = 0; f < frames; f++) {
            List<TickState> tail = new ArrayList<TickState>();
            tail.add(state(dirtyTick * 0.28 + f * 0.001, 64.0, f * 0.002, (f * 17) % 360 - 180));
            tail.add(state(dirtyTick * 0.28 + f * 0.0015, 64.0, f * 0.003, (f * 19) % 360 - 180));
            bc.replaceFrom(dirtyTick + 1, tail);
            bc.setPitches(new float[bc.size()]);

            long p0 = System.nanoTime();
            PathRenderPlan plan = PathRenderPlan.build(bc, settings, sel);
            planNanos += System.nanoTime() - p0;

            if (cache.fastPathTaken(bc, plan)) {
                pathCounts[0]++;
                patchNanos += patchEmission(bc, plan, Math.max(0, dirtyTick), faces, lines);
            } else {
                pathCounts[1]++;
                rebuildNanos += rebuildEmission(bc, plan, faces, lines);
                bc.takeDirtyFrom(bc.getGeometryRev());
            }
            cache.adopt(bc, plan);
        }
        return new long[]{patchNanos, rebuildNanos, planNanos, faces.vertices + lines.vertices};
    }

    @Test
    public void measure() {
        assumeTrue("1".equals(System.getenv("PKC_BENCH")));
        Settings settings = new Settings();
        SelectionManager sel = new SelectionManager(null);
        try {
            StringBuilder out = new StringBuilder();
            for (int scenario = 0; scenario < 3; scenario++) {
                int withConstraints = scenario == 1 ? 1 : 0;
                settings.showFullHitbox = scenario == 2;
                BoxController bc = controllerWith(path(N, 1));
                AngleSolverState solver = new AngleSolverState();
                if (withConstraints == 1) {
                    for (int t = CONSTRAINT_SPACING; t < N; t += CONSTRAINT_SPACING) {
                        double x = t * 0.28;
                        double z = t * 0.04;
                        solver.tickConstraints(t).getConstraints().add(
                                Constraint.range(Constraint.Field.X, x - 0.8, x + 0.8, true, true));
                        solver.tickConstraints(t).getConstraints().add(
                                Constraint.range(Constraint.Field.Z, z - 0.8, z + 0.8, true, true));
                    }
                }
                PathRenderPlan.setConstraintSource(new AngleSolverConstraintSource(
                        solver, bc, () -> true, settings, sel, new ConstraintSelection()));
                PathRenderPlan.setLiveSource(null);
                PathRenderPlan.setReachProbe(null);

                int[] warmCounts = new int[2];
                driveFrames(bc, settings, sel, WARMUP_FRAMES, warmCounts);
                int[] pathCounts = new int[2];
                long[] r = driveFrames(bc, settings, sel, FRAMES, pathCounts);

                out.append(String.format(
                        "n=%d constraints=%s hitbox=%s: patch frames %d (avg %.3f ms), rebuild frames %d (avg %.3f ms), plan avg %.3f ms, last verts %d%n",
                        N, withConstraints == 1 ? ("every " + CONSTRAINT_SPACING + " ticks") : "none",
                        scenario == 2 ? "full" : "off",
                        pathCounts[0], pathCounts[0] == 0 ? 0 : r[0] / 1e6 / pathCounts[0],
                        pathCounts[1], pathCounts[1] == 0 ? 0 : r[1] / 1e6 / pathCounts[1],
                        r[2] / 1e6 / FRAMES, r[3]));
            }
            System.out.print(out);
        } finally {
            PathRenderPlan.setConstraintSource(null);
            PathRenderPlan.setLiveSource(null);
            PathRenderPlan.setReachProbe(null);
        }
    }
}
