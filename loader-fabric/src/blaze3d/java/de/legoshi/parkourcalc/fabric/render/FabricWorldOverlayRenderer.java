package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.ReachProbe;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.function.Supplier;

public final class FabricWorldOverlayRenderer {

    private static final int MOMENTUM_OUTLINE = 0xFF36C957, MOMENTUM_FILL = 0x4036C957;
    private static final int LAND_OUTLINE = 0xFFE0463C, LAND_FILL = 0x40E0463C;
    private static final int COLLISION_OUTLINE = 0xFFB061F0, COLLISION_FILL = 0x40B061F0;
    private static final double SELECTION_GROW = 0.0025;

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;
    private final Supplier<AngleSolverState> angleSolver;

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings, SelectionManager selection,
                                      YawGizmoController yawGizmo, Supplier<AngleSolverState> angleSolver) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
        this.angleSolver = angleSolver;
    }

    public void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camPos) {
        long renderStart = Perf.now();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f pose = poseStack.last().pose();

        PathRenderPlan plan = null;
        if (!boxController.isEmpty()) {
            boxController.setBoxSize(BoxStyle.tickBoxSize(settings));
            plan = PathRenderPlan.build(boxController, settings, selection);
            Perf.addBoxes(boxController.size());
        }

        FabricBoxRenderer faces = new FabricBoxRenderer(pose, buffers.getBuffer(FabricRenderLayers.TRANSLUCENT_FACES), BoxRenderer.Mode.FACES);
        emitSelectionFaces(faces);
        if (plan != null) {
            plan.faceEmitter.accept(faces);
        }

        FabricBoxRenderer lines = new FabricBoxRenderer(pose, buffers.getBuffer(FabricRenderLayers.LINES), BoxRenderer.Mode.LINES);
        emitSelectionLines(lines);
        if (plan != null) {
            plan.lineEmitter.accept(lines);
            emitGizmo(lines, camPos);
            emitSelectedHitDistance(lines);
        }

        poseStack.popPose();
        buffers.endBatch();

        Perf.stop("worldOverlay", renderStart);
    }

    private void emitGizmo(FabricBoxRenderer lines, Vec3 camPos) {
        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx < 0) {
            return;
        }
        Vec3dCore center = boxController.getCenter(gizmoIdx);
        if (center == null) {
            return;
        }
        boolean pitchMode = yawGizmo.isPitchMode();
        Float liveYaw = yawGizmo.getCurrentYawDegrees();
        double yawDeg = pitchMode ? yawGizmo.getPlaneYawDegrees()
                : (liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx));
        double pitchDeg = yawGizmo.getGizmoPitchDegrees();
        double radius = BoxStyle.yawGizmoRadius(camPos.x - center.x, camPos.y - center.y, camPos.z - center.z);
        int circleArgb = BoxStyle.yawGizmoCircleArgb(settings);
        int directionArgb = BoxStyle.yawGizmoDirectionArgb(settings);
        if (pitchMode) {
            boxController.renderPitchGizmo(lines, center, yawDeg, pitchDeg, radius, circleArgb, directionArgb);
        } else {
            boxController.renderYawGizmo(lines, center, yawDeg, radius, circleArgb, directionArgb);
        }
    }

    private void emitSelectedHitDistance(FabricBoxRenderer lines) {
        if (!settings.showHitDistanceLines || !settings.hitDistanceSelectedOnly) return;
        Set<Integer> selected = selection.getSelectedBoxes();
        if (selected.isEmpty()) return;
        ReachProbe probe = PathRenderPlan.reachProbe();
        int miss = BoxStyle.hitDistanceMissArgb(settings);
        int hit = BoxStyle.hitDistanceHitArgb(settings);
        for (int i : selected) {
            boxController.renderHitDistanceLineAt(lines, probe, i, miss, hit);
        }
    }

    private void emitSelectionFaces(FabricBoxRenderer faces) {
        AngleSolverState st = selectionBlocks();
        if (st != null) emitSelections(st, faces, true);
    }

    private void emitSelectionLines(FabricBoxRenderer lines) {
        AngleSolverState st = selectionBlocks();
        if (st != null) emitSelections(st, lines, false);
    }

    private AngleSolverState selectionBlocks() {
        if (!settings.experimentalBlockCapture) return null;
        AngleSolverState st = angleSolver != null ? angleSolver.get() : null;
        return (st != null && st.hasAnyBlocks()) ? st : null;
    }

    private void emitSelections(AngleSolverState st, FabricBoxRenderer r, boolean fill) {
        for (BlockSelection b : st.getMomentumBlocks()) drawBlock(r, b, fill ? MOMENTUM_FILL : MOMENTUM_OUTLINE);
        for (BlockSelection b : st.getCollisionBlocks()) drawBlock(r, b, fill ? COLLISION_FILL : COLLISION_OUTLINE);
        for (BlockSelection b : st.getLandBlocks()) drawBlock(r, b, fill ? LAND_FILL : LAND_OUTLINE);
    }

    private void drawBlock(FabricBoxRenderer r, BlockSelection b, int argb) {
        for (AABB box : b.boxes) r.drawBox(grow(box), argb);
    }

    private static AABB grow(AABB b) {
        return new AABB(
                new Vec3dCore(b.min.x - SELECTION_GROW, b.min.y - SELECTION_GROW, b.min.z - SELECTION_GROW),
                new Vec3dCore(b.max.x + SELECTION_GROW, b.max.y + SELECTION_GROW, b.max.z + SELECTION_GROW));
    }
}
