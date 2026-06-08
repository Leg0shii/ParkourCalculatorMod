package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.sim.AABB;
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

import java.util.function.Supplier;

/** Renders the cached path geometry during RenderWorldLastEvent on MC 1.8.9; the yaw gizmo stays immediate. */
@SuppressWarnings("DuplicatedCode")
public final class Forge8WorldOverlayRenderer {

    // Selection overlay colors (ARGB): outline opaque, fill low-alpha so the real block shows beneath.
    private static final int START_OUTLINE = 0xFF36C957, START_FILL = 0x4036C957;   // green
    private static final int LAND_OUTLINE = 0xFFE0463C, LAND_FILL = 0x40E0463C;     // red
    private static final int COLLISION_OUTLINE = 0xFFB061F0, COLLISION_FILL = 0x40B061F0; // purple
    private static final double SELECTION_GROW = 0.0025; // nudge outward so the overlay doesn't z-fight the block face

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;
    private final Supplier<AngleSolverState> angleSolver;
    private final Forge8CachedBoxGeometry cached = new Forge8CachedBoxGeometry();

    public Forge8WorldOverlayRenderer(BoxController boxController, Settings settings, SelectionManager selection,
                                      YawGizmoController yawGizmo, Supplier<AngleSolverState> angleSolver) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
        this.angleSolver = angleSolver;
    }

    public void render(float partialTicks) {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return;

        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        // Selected Angle Solver blocks render independently of the sim path (the player picks them via
        // keybind with the overlay closed and no path yet).
        renderSelectionBlocks(camX, camY, camZ);

        if (boxController.isEmpty()) {
            cached.close();
            return;
        }

        long renderStart = Perf.now();
        boxController.setBoxSize(BoxStyle.tickBoxSize(settings));

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(BoxStyle.LINE_WIDTH);

        PathRenderPlan plan = PathRenderPlan.build(boxController, settings, selection);
        cached.ensureBuilt(boxController, plan.structuralHash, plan.selection, plan.faceEmitter, plan.lineEmitter, plan.patch);

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
                WorldRenderer buf = tess.getWorldRenderer();
                buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                Forge8BoxRenderer linesRenderer = new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES);
                double radius = BoxStyle.yawGizmoRadius(camX - center.x, camY - center.y, camZ - center.z);
                boxController.renderYawGizmo(
                        linesRenderer, center, yawDeg, radius,
                        BoxStyle.yawGizmoCircleArgb(settings),
                        BoxStyle.yawGizmoDirectionArgb(settings)
                );
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

    /** Draws the picked start (green) / land (red) / collision (purple) blocks as a low-alpha fill plus a
     *  crisp outline, so they read on top of the real block while the hitbox stays visible beneath. */
    private void renderSelectionBlocks(double camX, double camY, double camZ) {
        AngleSolverState st = angleSolver != null ? angleSolver.get() : null;
        if (st == null) return;
        boolean any = st.getStartBlock() != null || st.getLandBlock() != null || !st.getCollisionBlocks().isEmpty();
        if (!any) return;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer buf = tess.getWorldRenderer();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        emitSelections(st, new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.FACES), true);
        tess.draw();

        GL11.glLineWidth(BoxStyle.LINE_WIDTH);
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        emitSelections(st, new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES), false);
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void emitSelections(AngleSolverState st, Forge8BoxRenderer r, boolean fill) {
        BlockSelection start = st.getStartBlock();
        if (start != null) r.drawBox(grow(start.box), fill ? START_FILL : START_OUTLINE);
        BlockSelection land = st.getLandBlock();
        if (land != null) r.drawBox(grow(land.box), fill ? LAND_FILL : LAND_OUTLINE);
        for (BlockSelection c : st.getCollisionBlocks()) {
            r.drawBox(grow(c.box), fill ? COLLISION_FILL : COLLISION_OUTLINE);
        }
    }

    private static AABB grow(AABB b) {
        return new AABB(
                new Vec3dCore(b.min.x - SELECTION_GROW, b.min.y - SELECTION_GROW, b.min.z - SELECTION_GROW),
                new Vec3dCore(b.max.x + SELECTION_GROW, b.max.y + SELECTION_GROW, b.max.z + SELECTION_GROW));
    }
}
