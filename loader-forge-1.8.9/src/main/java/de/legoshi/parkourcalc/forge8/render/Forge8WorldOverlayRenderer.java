package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Renders BoxController's path boxes during RenderWorldLastEvent on MC 1.8.9.
 * Translucent fill first, wireframe on top; both share the Tessellator's
 * WorldRenderer buffer with camera-relative vertices.
 */
public final class Forge8WorldOverlayRenderer {

    private final BoxController boxController;
    private final Settings settings;

    public Forge8WorldOverlayRenderer(BoxController boxController, Settings settings) {
        this.boxController = boxController;
        this.settings = settings;
    }

    public void render(float partialTicks) {
        if (boxController.isEmpty()) return;

        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return;

        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(BoxStyle.LINE_WIDTH);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer buf = tess.getWorldRenderer();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(
                new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.FACES),
                (i, s) -> BoxStyle.tickFaceArgb(settings, s, false));
        tess.draw();

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(
                new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES),
                (i, s) -> BoxStyle.tickLineArgb(settings, s, false));
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
