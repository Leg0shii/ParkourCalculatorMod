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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Renders the cached path geometry into the world from WorldRendererMixin; the yaw gizmo stays immediate. */
public final class FabricWorldOverlayRenderer {

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;
    private final CachedBoxGeometry cached = new CachedBoxGeometry();

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings,
                                      SelectionManager selection, YawGizmoController yawGizmo) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
    }

    public void render(Matrix4f positionMatrix) {
        if (boxController.isEmpty()) {
            cached.close();
            return;
        }

        long renderStart = Perf.now();
        boxController.setBoxSize(BoxStyle.tickBoxSize(settings));

        MinecraftClient client = MinecraftClient.getInstance();

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        matrixStack.push();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

        PathRenderPlan plan = PathRenderPlan.build(boxController, settings, selection);
        cached.ensureBuilt(boxController, plan.structuralHash, plan.selection, plan.source, plan.patch);

        Matrix4f modelView = new Matrix4f(positionMatrix).translate(
                (float) (cached.anchorX() - cameraPos.x),
                (float) (cached.anchorY() - cameraPos.y),
                (float) (cached.anchorZ() - cameraPos.z));
        int[] runs = boxController.inRangeRuns(cameraPos.x, cameraPos.y, cameraPos.z,
                BoxStyle.pathMaxDistanceSq(settings));
        cached.drawLines(modelView, runs);
        cached.drawFaces(modelView, runs);

        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx >= 0) {
            FabricBoxRenderer linesRenderer = new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.LINES);
            Vec3dCore center = boxController.getCenter(gizmoIdx);
            Float liveYaw = yawGizmo.getCurrentYawDegrees();
            double yawDeg = liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx);
            if (center != null) {
                double radius = BoxStyle.yawGizmoRadius(
                        cameraPos.x - center.x, cameraPos.y - center.y, cameraPos.z - center.z);
                boxController.renderYawGizmo(linesRenderer, center, yawDeg, radius,
                        BoxStyle.yawGizmoCircleArgb(settings),
                        BoxStyle.yawGizmoDirectionArgb(settings));
            }
        }
        consumers.draw();

        matrixStack.pop();
        Perf.stop("worldOverlay", renderStart);
        Perf.addBoxes(boxController.size());
    }
}
