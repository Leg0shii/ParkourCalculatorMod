package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
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

/**
 * Renders BoxController's path boxes into the world, invoked from WorldRendererMixin.
 * Two passes (faces then lines) into an Immediate VertexConsumerProvider; the
 * Immediate orders them correctly because TRANSLUCENT_BOX sits on the translucent
 * draw pass and THIN_LINES doesn't.
 */
public final class FabricWorldOverlayRenderer {

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings,
                                      SelectionManager selection, YawGizmoController yawGizmo) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
    }

    public void render(Matrix4f positionMatrix) {
        if (boxController.isEmpty()) return;

        long renderStart = Perf.now();
        boxController.setBoxSize(BoxStyle.tickBoxSize(settings));

        MinecraftClient client = MinecraftClient.getInstance();

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        matrixStack.push();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        double maxSq = BoxStyle.pathMaxDistanceSq(settings);
        FabricBoxRenderer facesRenderer = new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.FACES);
        boxController.render(facesRenderer, (i, s) -> BoxStyle.tickFaceArgb(settings, s, selection.isSelected(i)),
                cameraPos.x, cameraPos.y, cameraPos.z, maxSq);
        FabricBoxRenderer linesRenderer = new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.LINES);
        boxController.render(linesRenderer, (i, s) -> BoxStyle.tickLineArgb(settings, s, selection.isSelected(i)),
                cameraPos.x, cameraPos.y, cameraPos.z, maxSq);
        if (settings.showSubtick) {
            boxController.renderPath(linesRenderer, BoxStyle.subtickPathArgb(settings),
                    cameraPos.x, cameraPos.y, cameraPos.z, maxSq);
        }
        if (settings.showHitbox && !settings.showFullHitbox) {
            boxController.renderHitboxFloorOutline(linesRenderer,
                    (i, s) -> BoxStyle.hitboxLineArgb(settings, selection.isSelected(i)),
                    settings.showSubtick,
                    cameraPos.x, cameraPos.y, cameraPos.z, maxSq);
        }
        if (settings.showFullHitbox) {
            boxController.renderHitboxFullWireframe(linesRenderer,
                    (i, s) -> BoxStyle.hitboxLineArgb(settings, selection.isSelected(i)),
                    settings.showSubtick,
                    cameraPos.x, cameraPos.y, cameraPos.z, maxSq);
        }
        if (settings.showYawArrows) {
            boxController.renderYawArrows(linesRenderer, BoxStyle.yawArrowArgb(settings),
                    cameraPos.x, cameraPos.y, cameraPos.z, maxSq);
        }
        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx >= 0) {
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
