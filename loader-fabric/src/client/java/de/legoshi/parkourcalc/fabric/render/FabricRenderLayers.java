package de.legoshi.parkourcalc.fabric.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Custom render pipelines for ParkourCalculator's path-box visualization.
 *
 * Neither of MC's built-in layers fits:
 *  - RenderLayer.getDebugFilledBox(): no blend phase, so alpha is ignored
 *    (renders fully opaque regardless of vertex alpha).
 *  - RenderLayer.getLines(): line_width[window_scale] + view_offset_z_layering,
 *    which on small (0.1-block) boxes produces a wireframe thick enough to
 *    cover the box silhouette completely. We need 1px lines, no Z offset.
 *
 * All pipelines derive from DEBUG_FILLED_SNIPPET (position_color shaders,
 * translucent blend, no depth write); 26.2 uses reversed-Z, so depth bias
 * toward the viewer is positive.
 */
public final class FabricRenderLayers {

    private static final RenderPipeline TRANSLUCENT_BOX_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/parkourcalc_translucent_box")
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 10.0F))
                    .build();

    private static final RenderPipeline THIN_LINES_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/parkourcalc_thin_lines")
                    .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .build();

    /** Thin 1px wireframe lines for the yaw gizmo's submitCustomGeometry path. */
    public static final RenderType THIN_LINES = RenderType.create(
            "parkourcalc_thin_lines",
            RenderSetup.builder(THIN_LINES_PIPELINE).createRenderSetup()
    );

    public static final RenderType TRANSLUCENT_FACES = RenderType.create(
            "parkourcalc_translucent_faces",
            RenderSetup.builder(TRANSLUCENT_BOX_PIPELINE).createRenderSetup()
    );

    /** Exposed for CachedBoxGeometry's hand-rolled render passes (persistent GpuBuffer draws). */
    public static RenderPipeline translucentBoxPipeline() {
        return TRANSLUCENT_BOX_PIPELINE;
    }

    public static RenderPipeline thinLinesPipeline() {
        return THIN_LINES_PIPELINE;
    }

    private static final RenderPipeline CONSTRAINT_FILL_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/parkourcalc_constraint_fill")
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(true)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 10.0F))
                    .build();

    public static RenderPipeline constraintFillPipeline() {
        return CONSTRAINT_FILL_PIPELINE;
    }

}
