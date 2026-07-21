package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
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

/** Renders the cached path geometry into the world from the AFTER_SOLID_FEATURES event; the yaw gizmo goes through the submit phase. */
public final class FabricWorldOverlayRenderer {

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;
    private final CachedBoxGeometry cached = new CachedBoxGeometry();

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings, SelectionManager selection, YawGizmoController yawGizmo) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
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
        Float liveYaw = yawGizmo.getCurrentYawDegrees();
        double yawDeg = liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx);
        double radius = BoxStyle.yawGizmoRadius(cameraPos.x - center.x, cameraPos.y - center.y, cameraPos.z - center.z);
        int circleArgb = BoxStyle.yawGizmoCircleArgb(settings);
        int directionArgb = BoxStyle.yawGizmoDirectionArgb(settings);

        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, FabricRenderLayers.THIN_LINES, (pose, buffer) ->
                boxController.renderYawGizmo(
                        new FabricBoxRenderer(pose.pose(), buffer, BoxRenderer.Mode.LINES),
                        center, yawDeg, radius, circleArgb, directionArgb
                )
        );
        poseStack.popPose();
    }
}
