package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;
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

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings) {
        this.boxController = boxController;
        this.settings = settings;
    }

    public void render(Matrix4f positionMatrix) {
        if (boxController.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        matrixStack.push();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        boxController.render(new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.FACES), BoxStyle.tickDefaultFaceArgb(settings));
        boxController.render(new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.LINES), BoxStyle.tickDefaultLineArgb(settings));
        consumers.draw();

        matrixStack.pop();
    }
}
