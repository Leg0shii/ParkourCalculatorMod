package de.legoshi.parkourcalc.forge12.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Renders BoxController's path boxes during RenderWorldLastEvent on MC 1.12.2.
 * Translucent fill first, wireframe on top; both share the Tessellator's
 * BufferBuilder with camera-relative vertices.
 */
public final class Forge12WorldOverlayRenderer {

    private final BoxController boxController;
    private final Settings settings;

    public Forge12WorldOverlayRenderer(BoxController boxController, Settings settings) {
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
        GlStateManager.glLineWidth(BoxStyle.LINE_WIDTH);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(new Forge12BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.FACES), BoxStyle.tickDefaultFaceArgb(settings));
        tess.draw();

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(new Forge12BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES), BoxStyle.tickDefaultLineArgb(settings));
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
