package de.legoshi.parkourcalc.fabric.render;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.function.Supplier;

/** Renders the cached path geometry into the world from WorldRendererMixin; the yaw gizmo stays immediate. */
public final class FabricWorldOverlayRenderer {

    private static final int MOMENTUM_OUTLINE = 0xFF36C957, MOMENTUM_FILL = 0x4036C957;
    private static final int LAND_OUTLINE = 0xFFE0463C, LAND_FILL = 0x40E0463C;
    private static final int COLLISION_OUTLINE = 0xFFB061F0, COLLISION_FILL = 0x40B061F0;
    private static final double SELECTION_GROW = 0.0025;

    private final BoxController boxController;
    private final Settings settings;
    private final SelectionManager selection;
    private final YawGizmoController yawGizmo;
    private final Supplier<AngleSolverState> angleSolver;
    private final CachedBoxGeometry cached = new CachedBoxGeometry();

    public FabricWorldOverlayRenderer(BoxController boxController, Settings settings, SelectionManager selection,
                                      YawGizmoController yawGizmo, Supplier<AngleSolverState> angleSolver) {
        this.boxController = boxController;
        this.settings = settings;
        this.selection = selection;
        this.yawGizmo = yawGizmo;
        this.angleSolver = angleSolver;
    }

    public void render(Matrix4f positionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();

        renderSelectionBlocks(client, cameraPos);

        if (boxController.isEmpty()) {
            cached.close();
            return;
        }

        long renderStart = Perf.now();
        boxController.setBoxSize(BoxStyle.tickBoxSize(settings));

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

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
        consumers.draw();

        matrixStack.pop();
        Perf.stop("worldOverlay", renderStart);
        Perf.addBoxes(boxController.size());
    }

    private void renderSelectionBlocks(MinecraftClient client, Vec3d cameraPos) {
        if (!settings.experimentalBlockCapture) return;
        AngleSolverState st = angleSolver != null ? angleSolver.get() : null;
        if (st == null || !st.hasAnyBlocks()) return;

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        emitSelections(st, new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.FACES), true);
        emitSelections(st, new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.LINES), false);
        consumers.draw();

        matrixStack.pop();
    }

    private void emitSelections(AngleSolverState st, FabricBoxRenderer r, boolean fill) {
        for (BlockSelection b : st.getMomentumBlocks()) drawBlock(r, b, fill ? MOMENTUM_FILL : MOMENTUM_OUTLINE);
        for (BlockSelection b : st.getCollisionBlocks()) drawBlock(r, b, fill ? COLLISION_FILL : COLLISION_OUTLINE);
        for (BlockSelection b : st.getLandBlocks()) drawBlock(r, b, fill ? LAND_FILL : LAND_OUTLINE);
    }

    private void drawBlock(FabricBoxRenderer r, BlockSelection b, int argb) {
        for (AABB box : b.boxes) r.drawBox(grow(box), argb);
    }

    private static AABB grow(AABB b) {
        return new AABB(
                new Vec3dCore(b.min.x - SELECTION_GROW, b.min.y - SELECTION_GROW, b.min.z - SELECTION_GROW),
                new Vec3dCore(b.max.x + SELECTION_GROW, b.max.y + SELECTION_GROW, b.max.z + SELECTION_GROW));
    }
}
