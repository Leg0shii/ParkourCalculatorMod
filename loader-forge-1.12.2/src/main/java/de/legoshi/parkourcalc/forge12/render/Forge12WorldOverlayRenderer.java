package de.legoshi.parkourcalc.forge12.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;
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
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;

    public Forge12WorldOverlayRenderer(BoxController boxController, Settings settings,
                                       SelectionManager selection, YawGizmoController yawGizmo) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
    }

    public void render(float partialTicks) {
        if (boxController.isEmpty()) return;

        boxController.setBoxSize(BoxStyle.tickBoxSize(settings));

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
        boxController.render(
                new Forge12BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.FACES),
                (i, s) -> BoxStyle.tickFaceArgb(settings, s, selection.isSelected(i)));
        tess.draw();

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        Forge12BoxRenderer linesRenderer = new Forge12BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES);
        boxController.render(linesRenderer, (i, s) -> BoxStyle.tickLineArgb(settings, s, selection.isSelected(i)));
        if (settings.showSubtick) {
            boxController.renderPath(linesRenderer, BoxStyle.subtickPathArgb(settings));
        }
        if (settings.showHitbox) {
            boxController.renderHitboxFloorOutline(linesRenderer,
                    (i, s) -> BoxStyle.hitboxLineArgb(settings, selection.isSelected(i)),
                    settings.showSubtick);
        }
        if (settings.showFullHitbox) {
            boxController.renderHitboxFullWireframe(linesRenderer,
                    (i, s) -> BoxStyle.hitboxLineArgb(settings, selection.isSelected(i)),
                    settings.showSubtick);
        }
        if (settings.showYawArrows) {
            boxController.renderYawArrows(linesRenderer, BoxStyle.yawArrowArgb(settings));
        }
        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx >= 0) {
            Vec3dCore center = boxController.getCenter(gizmoIdx);
            Float liveYaw = yawGizmo.getCurrentYawDegrees();
            double yawDeg = liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx);
            if (center != null) {
                double radius = BoxStyle.yawGizmoRadius(camX - center.x, camY - center.y, camZ - center.z);
                boxController.renderYawGizmo(linesRenderer, center, yawDeg, radius,
                        BoxStyle.yawGizmoCircleArgb(settings),
                        BoxStyle.yawGizmoDirectionArgb(settings));
            }
        }
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
