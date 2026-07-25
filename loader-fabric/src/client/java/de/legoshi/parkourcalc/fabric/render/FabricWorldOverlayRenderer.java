package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.function.Supplier;

/** Renders the cached path geometry into the world from the AFTER_SOLID_FEATURES event; the yaw gizmo goes through the submit phase. */
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
    private final CachedBoxGeometry cached = new CachedBoxGeometry();

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings, SelectionManager selection,
                                      YawGizmoController yawGizmo, Supplier<AngleSolverState> angleSolver) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
        this.angleSolver = angleSolver;
    }

    public void render(LevelRenderContext context) {
        if (boxController.isEmpty()) {
            cached.close();
            return;
        }

        long renderStart = Perf.now();
        boxController.setBoxSize(BoxStyle.tickBoxSize(settings));

        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cameraPos = camera.pos;

        PathRenderPlan plan = PathRenderPlan.build(boxController, settings, selection);
        cached.ensureBuilt(boxController, plan.structuralHash, plan.selection, plan.faceEmitter, plan.lineEmitter, plan.patch, plan.constraintFaceVerts, plan.constraintLineVerts);

        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix).translate(
                (float) (cached.anchorX() - cameraPos.x),
                (float) (cached.anchorY() - cameraPos.y),
                (float) (cached.anchorZ() - cameraPos.z)
        );
        int[] runs = boxController.inRangeRuns(cameraPos.x, cameraPos.y, cameraPos.z, BoxStyle.pathMaxDistanceSq(settings));
        cached.drawLines(modelView, runs);
        cached.drawFaces(modelView, runs);

        Perf.stop("worldOverlay", renderStart);
        Perf.addBoxes(boxController.size());
    }

    public void submitGizmo(LevelRenderContext context) {
        submitSelectionBlocks(context);

        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx < 0) {
            return;
        }
        Vec3dCore center = boxController.getCenter(gizmoIdx);
        if (center == null) {
            return;
        }

        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cameraPos = camera.pos;
        boolean pitchMode = yawGizmo.isPitchMode();
        Float liveYaw = yawGizmo.getCurrentYawDegrees();
        double yawDeg = pitchMode ? yawGizmo.getPlaneYawDegrees()
                : (liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx));
        double pitchDeg = yawGizmo.getGizmoPitchDegrees();
        double radius = BoxStyle.yawGizmoRadius(cameraPos.x - center.x, cameraPos.y - center.y, cameraPos.z - center.z);
        int circleArgb = BoxStyle.yawGizmoCircleArgb(settings);
        int directionArgb = BoxStyle.yawGizmoDirectionArgb(settings);

        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, FabricRenderLayers.THIN_LINES, (pose, buffer) -> {
            FabricBoxRenderer linesRenderer = new FabricBoxRenderer(pose.pose(), buffer, BoxRenderer.Mode.LINES);
            if (pitchMode) {
                boxController.renderPitchGizmo(linesRenderer, center, yawDeg, pitchDeg, radius, circleArgb, directionArgb);
            } else {
                boxController.renderYawGizmo(linesRenderer, center, yawDeg, radius, circleArgb, directionArgb);
            }
        });
        poseStack.popPose();
    }

    private void submitSelectionBlocks(LevelRenderContext context) {
        if (!settings.experimentalBlockCapture) return;
        AngleSolverState st = angleSolver != null ? angleSolver.get() : null;
        if (st == null || !st.hasAnyBlocks()) return;

        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cameraPos = camera.pos;
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, FabricRenderLayers.TRANSLUCENT_FACES, (pose, buffer) ->
                emitSelections(st, new FabricBoxRenderer(pose.pose(), buffer, BoxRenderer.Mode.FACES), true)
        );
        context.submitNodeCollector().submitCustomGeometry(poseStack, FabricRenderLayers.THIN_LINES, (pose, buffer) ->
                emitSelections(st, new FabricBoxRenderer(pose.pose(), buffer, BoxRenderer.Mode.LINES), false)
        );
        poseStack.popPose();
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
