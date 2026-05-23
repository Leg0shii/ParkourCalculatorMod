package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;
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
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;

    public Forge8WorldOverlayRenderer(BoxController boxController, Settings settings,
                                      SelectionManager selection, YawGizmoController yawGizmo) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
    }

    public void render(float partialTicks) {
        if (boxController.isEmpty()) return;

        long renderStart = Perf.now();
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
        GL11.glLineWidth(BoxStyle.LINE_WIDTH);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer buf = tess.getWorldRenderer();

        double maxSq = BoxStyle.pathMaxDistanceSq(settings);

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(
                new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.FACES),
                (i, s) -> BoxStyle.tickFaceArgb(settings, s, selection.isSelected(i)),
                camX, camY, camZ, maxSq);
        tess.draw();

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        Forge8BoxRenderer linesRenderer = new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES);
        boxController.render(linesRenderer, (i, s) -> BoxStyle.tickLineArgb(settings, s, selection.isSelected(i)),
                camX, camY, camZ, maxSq);
        if (settings.showSubtick) {
            boxController.renderPath(linesRenderer, BoxStyle.subtickPathArgb(settings),
                    camX, camY, camZ, maxSq);
        }
        if (settings.showHitbox && !settings.showFullHitbox) {
            boxController.renderHitboxFloorOutline(linesRenderer,
                    (i, s) -> BoxStyle.hitboxLineArgb(settings, selection.isSelected(i)),
                    settings.showSubtick,
                    camX, camY, camZ, maxSq);
        }
        if (settings.showFullHitbox) {
            boxController.renderHitboxFullWireframe(linesRenderer,
                    (i, s) -> BoxStyle.hitboxLineArgb(settings, selection.isSelected(i)),
                    settings.showSubtick,
                    camX, camY, camZ, maxSq);
        }
        if (settings.showYawArrows) {
            boxController.renderYawArrows(linesRenderer, BoxStyle.yawArrowArgb(settings),
                    camX, camY, camZ, maxSq);
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
        Perf.stop("worldOverlay", renderStart);
        Perf.addBoxes(boxController.size());
    }
}
