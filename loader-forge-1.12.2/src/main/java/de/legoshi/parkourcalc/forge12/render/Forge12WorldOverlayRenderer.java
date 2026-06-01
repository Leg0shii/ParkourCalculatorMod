package de.legoshi.parkourcalc.forge12.render;

import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
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

/** Renders the cached path geometry during RenderWorldLastEvent on MC 1.12.2; the yaw gizmo stays immediate. */
public final class Forge12WorldOverlayRenderer {

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;
    private final Forge12CachedBoxGeometry cached = new Forge12CachedBoxGeometry();

    public Forge12WorldOverlayRenderer(BoxController boxController, Settings settings,
                                       SelectionManager selection, YawGizmoController yawGizmo) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
    }

    public void render(float partialTicks) {
        if (boxController.isEmpty()) {
            cached.close();
            return;
        }

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
        GlStateManager.glLineWidth(BoxStyle.LINE_WIDTH);

        PathRenderPlan plan = PathRenderPlan.build(boxController, settings, selection);
        cached.ensureBuilt(boxController, plan.structuralHash, plan.selection, plan.source, plan.patch);

        int[] runs = boxController.inRangeRuns(camX, camY, camZ, BoxStyle.pathMaxDistanceSq(settings));
        GlStateManager.pushMatrix();
        GlStateManager.translate(cached.anchorX() - camX, cached.anchorY() - camY, cached.anchorZ() - camZ);
        cached.drawFaces(runs);
        cached.drawLines(runs);
        GlStateManager.popMatrix();

        int gizmoIdx = yawGizmo.getSelectedIndex();
        if (gizmoIdx >= 0) {
            Vec3dCore center = boxController.getCenter(gizmoIdx);
            Float liveYaw = yawGizmo.getCurrentYawDegrees();
            double yawDeg = liveYaw != null ? liveYaw : boxController.getYaw(gizmoIdx);
            if (center != null) {
                Tessellator tess = Tessellator.getInstance();
                BufferBuilder buf = tess.getBuffer();
                buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                Forge12BoxRenderer linesRenderer = new Forge12BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES);
                double radius = BoxStyle.yawGizmoRadius(camX - center.x, camY - center.y, camZ - center.z);
                boxController.renderYawGizmo(linesRenderer, center, yawDeg, radius,
                        BoxStyle.yawGizmoCircleArgb(settings),
                        BoxStyle.yawGizmoDirectionArgb(settings));
                tess.draw();
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        Perf.stop("worldOverlay", renderStart);
        Perf.addBoxes(boxController.size());
    }
}
