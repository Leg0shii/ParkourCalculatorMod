package de.legoshi.parkourcalc.fabric.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;

/**
 * Custom render layers for ParkourCalculator's path-box visualization.
 *
 * Neither of MC's built-in layers fits:
 *  - RenderLayer.getDebugFilledBox(): no blend phase, so alpha is ignored
 *    (renders fully opaque regardless of vertex alpha).
 *  - RenderLayer.getLines(): line_width[window_scale] + view_offset_z_layering,
 *    which on small (0.1-block) boxes produces a wireframe thick enough to
 *    cover the box silhouette completely. We need 1px lines, no Z offset.
 *
 * Both pipelines borrow the private POSITION_COLOR_SNIPPET to avoid duplicating
 * the shader wiring; access granted by parkourcalculator.accesswidener.
 */
public final class FabricRenderLayers {

    // TRIANGLES, not TRIANGLE_STRIP: VertexConsumerProvider.Immediate#getBuffer flushes on every
    // call for any draw mode where shareVertices=true, so a STRIP submits one draw call per box.
    private static final RenderPipeline TRANSLUCENT_BOX_PIPELINE =
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/parkourcalc_translucent_box")
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build();

    /**
     * Translucent filled box. translucent=true on the layer is load-bearing:
     * it puts the layer in the Immediate's translucent draw pass so it renders
     * after the (opaque) wireframe lines, not under them.
     */
    public static final RenderLayer TRANSLUCENT_BOX = RenderLayer.of(
            "parkourcalc_translucent_box",
            1536,
            false,
            true,
            TRANSLUCENT_BOX_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderPipeline THIN_LINES_PIPELINE =
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/parkourcalc_thin_lines")
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .build();

    /** Thin 1px wireframe lines. Matches the Forge GL_LINES look. */
    public static final RenderLayer THIN_LINES = RenderLayer.of(
            "parkourcalc_thin_lines",
            1536,
            false,
            false,
            THIN_LINES_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    /** Exposed for CachedBoxGeometry's hand-rolled render passes (persistent GpuBuffer draws). */
    public static RenderPipeline translucentBoxPipeline() {
        return TRANSLUCENT_BOX_PIPELINE;
    }

    public static RenderPipeline thinLinesPipeline() {
        return THIN_LINES_PIPELINE;
    }

    private FabricRenderLayers() {
    }
}
