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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Renders the cached path geometry into the world from WorldRendererMixin; the yaw gizmo stays immediate. */
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

        Minecraft client = Minecraft.getInstance();

        PoseStack matrixStack = new PoseStack();

        Vec3 cameraPos = client.gameRenderer.mainCamera().position();
        matrixStack.pushPose();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MultiBufferSource.BufferSource consumers = client.gameRenderer.renderBuffers().stagedVertexBuffer();

        PathRenderPlan plan = PathRenderPlan.build(boxController, settings, selection);
        cached.ensureBuilt(boxController, plan.structuralHash, plan.selection, plan.faceEmitter, plan.lineEmitter, plan.patch, plan.constraintFaceVerts, plan.constraintLineVerts);

        Matrix4f modelView = new Matrix4f(positionMatrix).translate(
                (float) (cached.anchorX() - cameraPos.x),
                (float) (cached.anchorY() - cameraPos.y),
                (float) (cached.anchorZ() - cameraPos.z)
        );
        int[] runs = boxController.inRangeRuns(cameraPos.x, cameraPos.y, cameraPos.z, BoxStyle.pathMaxDistanceSq(settings));
        cached.drawLines(modelView, runs);
        cached.drawFaces(modelView, runs);

        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx >= 0) {
            FabricBoxRenderer linesRenderer = new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.LINES);
            Vec3dCore center = boxController.getCenter(gizmoIdx);
            Float liveYaw = yawGizmo.getCurrentYawDegrees();
            double yawDeg = liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx);
            if (center != null) {
                double radius = BoxStyle.yawGizmoRadius(cameraPos.x - center.x, cameraPos.y - center.y, cameraPos.z - center.z);
                boxController.renderYawGizmo(linesRenderer, center, yawDeg, radius, BoxStyle.yawGizmoCircleArgb(settings), BoxStyle.yawGizmoDirectionArgb(settings));
            }
        }
        consumers.endBatch();

        matrixStack.popPose();
        Perf.stop("worldOverlay", renderStart);
        Perf.addBoxes(boxController.size());
    }
}
