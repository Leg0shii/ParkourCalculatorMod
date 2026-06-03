package de.legoshi.parkourcalc.fabric.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathGeometrySource;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.render.SelectionPatchSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/** Persistent GPU buffers for the cached path geometry (segmented; anchor-relative; camera via model-view matrix). */
public final class CachedBoxGeometry implements AutoCloseable {

    private static final int STRIDE = 16; // POSITION_COLOR
    private static final int MAX_SEGMENT_VERTICES = 12_000_000; // < 2^24-1, divisible by 6
    private static final int FACE_BYTES_PER_BOX = 36 * STRIDE;
    private static final int LINE_BYTES_PER_BOX = 24 * STRIDE;

    private List<Segment> faceSegments = new ArrayList<>();
    private List<Segment> lineSegments = new ArrayList<>();

    private double anchorX;
    private double anchorY;
    private double anchorZ;

    private long lastGeometryRev = -1;
    private int lastStructuralHash;
    private boolean built;

    private int boxCount;
    private int hitboxEdges;
    private boolean useSubtick;
    private int[] hitboxStarts = new int[]{0};
    private int[] subtickStarts = new int[]{0}; // per-box subtick offsets relative to lineMainTotal
    private int hitboxBase;     // first hitbox vertex in the faces buffer
    private int arrowBase;      // first yaw-arrow vertex in the faces buffer
    private int faceTotal;      // total face vertices (arrows occupy [arrowBase, faceTotal))
    private int lineMainTotal;  // first subtick vertex in the lines buffer
    private int lineTotal;      // total line vertices (subtick occupies [lineMainTotal, lineTotal))
    private Set<Integer> bakedSelection = new HashSet<>();

    private record Segment(GpuBuffer buffer, int vertexCount) {
    }

    public void ensureBuilt(BoxController boxController, int structuralHash, Set<Integer> selection, PathGeometrySource source, SelectionPatchSpec patch) {
        long rev = boxController.getGeometryRev();
        if (built && rev == lastGeometryRev && structuralHash == lastStructuralHash) {
            if (!selection.equals(bakedSelection)) {
                patchSelection(boxController, selection, patch);
            }
            return;
        }
        rebuild(boxController, source, patch);
        bakedSelection = new HashSet<>(selection);
        lastGeometryRev = rev;
        lastStructuralHash = structuralHash;
        built = true;
    }

    private void rebuild(BoxController boxController, PathGeometrySource source, SelectionPatchSpec patch) {
        Vec3dCore first = boxController.getPosition(0);
        anchorX = first.x;
        anchorY = first.y;
        anchorZ = first.z;

        releaseBuffers();
        boxCount = boxController.size();
        useSubtick = patch.showSubtick;
        hitboxEdges = patch.hitboxEdges();
        hitboxStarts = PathVertexLayout.hitboxVertexStarts(boxController, hitboxEdges, useSubtick);

        faceSegments = bake(BoxRenderer.Mode.FACES, VertexFormat.DrawMode.TRIANGLES, boxCount * FACE_BYTES_PER_BOX, "parkourcalc cached faces", source::emitFaces);
        lineSegments = bake(BoxRenderer.Mode.LINES, VertexFormat.DrawMode.DEBUG_LINES, boxCount * LINE_BYTES_PER_BOX, "parkourcalc cached lines", source::emitLines);

        hitboxBase = PathVertexLayout.hitboxRegionBase(boxCount);
        arrowBase = hitboxBase + hitboxStarts[boxCount];
        faceTotal = totalVertices(faceSegments);
        lineMainTotal = boxCount * PathVertexLayout.LINE_VERTS_PER_BOX;
        lineTotal = totalVertices(lineSegments);
        subtickStarts = useSubtick ? boxController.subtickVertexStarts() : new int[]{0};
    }

    private static int totalVertices(List<Segment> segments) {
        int total = 0;
        for (Segment segment : segments) {
            total += segment.vertexCount();
        }
        return total;
    }

    private List<Segment> bake(BoxRenderer.Mode mode, VertexFormat.DrawMode drawMode, int estBytes, String label, Consumer<BoxRenderer> emitter) {
        SegmentedSink sink = new SegmentedSink(drawMode, label, Math.max(estBytes, 256));
        emitter.accept(new BakingBoxRenderer(sink, mode, anchorX, anchorY, anchorZ));
        return sink.finish();
    }

    private void patchSelection(BoxController boxController, Set<Integer> selection, SelectionPatchSpec patch) {
        Set<Integer> changed = new HashSet<>();
        for (Integer i : selection) {
            if (!bakedSelection.contains(i)) changed.add(i);
        }
        for (Integer i : bakedSelection) {
            if (!selection.contains(i)) changed.add(i);
        }
        for (int i : changed) {
            if (i < 0 || i >= boxCount) continue;
            if (!patchBox(boxController, i, patch)) {
                // Layout mismatch (should not happen): drop the cache so the next frame rebuilds cleanly.
                close();
                return;
            }
        }
        bakedSelection = new HashSet<>(selection);
    }

    private boolean patchBox(BoxController boxController, int i, SelectionPatchSpec patch) {
        TickState state = boxController.getState(i);

        int faceArgb = patch.facePicker.argbFor(i, state);
        if (
                !writeVerts(
                        faceSegments,
                        PathVertexLayout.faceMainOffset(i),
                        VertexFormat.DrawMode.TRIANGLES,
                        BoxRenderer.Mode.FACES,
                        r -> r.drawBox(boxController.getTickAabb(i), faceArgb)
                )
        ) {
            return false;
        }

        if (hitboxEdges != 0) {
            int offset = PathVertexLayout.hitboxRegionBase(boxCount) + hitboxStarts[i];
            int hitboxArgb = patch.hitboxPicker.argbFor(i, state);
            Consumer<BoxRenderer> emit = patch.showFullHitbox
                    ? r -> boxController.emitHitboxFullWireframeAt(r, hitboxArgb, useSubtick, i)
                    : r -> boxController.emitHitboxFloorOutlineAt(r, hitboxArgb, useSubtick, i);
            if (!writeVerts(faceSegments, offset, VertexFormat.DrawMode.TRIANGLES, BoxRenderer.Mode.FACES, emit)) {
                return false;
            }
        }

        int lineArgb = patch.linePicker.argbFor(i, state);
        return writeVerts(
                lineSegments,
                PathVertexLayout.lineMainOffset(i),
                VertexFormat.DrawMode.DEBUG_LINES,
                BoxRenderer.Mode.LINES,
                r -> r.drawBox(boxController.getTickAabb(i), lineArgb)
        );
    }

    private boolean writeVerts(List<Segment> segments, int globalVertexOffset, VertexFormat.DrawMode drawMode,
                               BoxRenderer.Mode mode, Consumer<BoxRenderer> emit) {
        try (BufferAllocator allocator = new BufferAllocator(8192)) {
            BufferBuilder builder = new BufferBuilder(allocator, drawMode, VertexFormats.POSITION_COLOR);
            emit.accept(new BakingBoxRenderer(new DirectSink(builder), mode, anchorX, anchorY, anchorZ));
            BuiltBuffer builtBuffer = builder.endNullable();
            try (builtBuffer) {
                if (builtBuffer == null) return true;
                int vertexCount = builtBuffer.getDrawParameters().vertexCount();
                return writeBytesAcrossSegments(segments, globalVertexOffset, vertexCount, builtBuffer.getBuffer());
            }
        }
    }

    private static boolean writeBytesAcrossSegments(List<Segment> segments, int globalVertexOffset, int vertexCount, ByteBuffer source) {
        int cursorVertex = globalVertexOffset;
        int sourceVertex = 0;
        int remaining = vertexCount;
        int segmentStart = 0;
        for (Segment segment : segments) {
            int segmentEnd = segmentStart + segment.vertexCount();
            if (remaining > 0 && cursorVertex < segmentEnd) {
                int localVertex = cursorVertex - segmentStart;
                int writable = Math.min(remaining, segment.vertexCount() - localVertex);
                ByteBuffer slice = source.slice(sourceVertex * STRIDE, writable * STRIDE);
                GpuBufferSlice destination = segment.buffer().slice(localVertex * STRIDE, writable * STRIDE);
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(destination, slice);
                cursorVertex += writable;
                sourceVertex += writable;
                remaining -= writable;
            }
            segmentStart = segmentEnd;
            if (remaining == 0) return true;
        }
        return remaining == 0;
    }

    public void drawFaces(Matrix4f modelView, int[] runs) {
        RenderPipeline pipeline = FabricRenderLayers.translucentBoxPipeline();
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            drawRange(faceSegments, pipeline, VertexFormat.DrawMode.TRIANGLES, modelView, PathVertexLayout.faceMainOffset(a), (b - a) * PathVertexLayout.FACE_VERTS_PER_BOX);
            if (hitboxEdges != 0) {
                drawRange(faceSegments, pipeline, VertexFormat.DrawMode.TRIANGLES, modelView,hitboxBase + hitboxStarts[a], hitboxStarts[b] - hitboxStarts[a]);
            }
            if (arrowBase < faceTotal) {
                int arrowEnd = Math.min(b, boxCount - 1);
                int arrowStart = Math.min(a, boxCount - 1);
                if (arrowEnd > arrowStart) {
                    drawRange(faceSegments, pipeline, VertexFormat.DrawMode.TRIANGLES, modelView,arrowBase + arrowStart * 60, (arrowEnd - arrowStart) * 60);
                }
            }
        }
    }

    public void drawLines(Matrix4f modelView, int[] runs) {
        RenderPipeline pipeline = FabricRenderLayers.thinLinesPipeline();
        boolean hasSubtick = lineMainTotal < lineTotal;
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            drawRange(lineSegments, pipeline, VertexFormat.DrawMode.DEBUG_LINES, modelView, PathVertexLayout.lineMainOffset(a), (b - a) * PathVertexLayout.LINE_VERTS_PER_BOX);
            if (hasSubtick) {
                drawRange(lineSegments, pipeline, VertexFormat.DrawMode.DEBUG_LINES, modelView,lineMainTotal + subtickStarts[a], subtickStarts[b] - subtickStarts[a]);
            }
        }
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double anchorZ() {
        return anchorZ;
    }

    private static void drawRange(List<Segment> segments, RenderPipeline pipeline, VertexFormat.DrawMode drawMode, Matrix4f modelView, int globalVertexOffset, int count) {
        if (count <= 0) return;
        int cursor = globalVertexOffset;
        int remaining = count;
        int segmentStart = 0;
        for (Segment segment : segments) {
            int segmentEnd = segmentStart + segment.vertexCount();
            if (remaining > 0 && cursor < segmentEnd) {
                int localVertex = cursor - segmentStart;
                int drawable = Math.min(remaining, segment.vertexCount() - localVertex);
                drawSegment(pipeline, segment.buffer(), drawMode, modelView, localVertex, drawable);
                cursor += drawable;
                remaining -= drawable;
            }
            segmentStart = segmentEnd;
            if (remaining == 0) return;
        }
    }

    private static void drawSegment(RenderPipeline pipeline, GpuBuffer vbo, VertexFormat.DrawMode drawMode, Matrix4f modelView, int firstVertex, int count) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
                modelView,
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector3f(),
                new Matrix4f(),
                1.0f
        );

        RenderSystem.ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(drawMode);
        GpuBuffer ibo = indexBuffer.getIndexBuffer(firstVertex + count);

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        GpuTextureView color = framebuffer.getColorAttachmentView();
        GpuTextureView depth = framebuffer.getDepthAttachmentView();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "parkourcalc cached", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vbo);
            pass.setIndexBuffer(ibo, indexBuffer.getIndexType());
            pass.drawIndexed(0, firstVertex, count, 1);
        }
    }

    private void releaseBuffers() {
        for (Segment segment : faceSegments) {
            segment.buffer().close();
        }
        for (Segment segment : lineSegments) {
            segment.buffer().close();
        }
        faceSegments = new ArrayList<>();
        lineSegments = new ArrayList<>();
    }

    @Override
    public void close() {
        releaseBuffers();
        built = false;
        lastGeometryRev = -1;
    }

    /**
     * Writes vertices straight into one pre-sized BufferBuilder; used for the small per-box patch re-emit.
     */
        private record DirectSink(BufferBuilder builder) implements BakingBoxRenderer.VertexSink {

        @Override
            public void beginPrimitive(int vertexCount) {
            }

            @Override
            public void vertex(float x, float y, float z, int argb) {
                float a = ((argb >>> 24) & 0xFF) / 255.0f;
                float r = ((argb >>> 16) & 0xFF) / 255.0f;
                float g = ((argb >>> 8) & 0xFF) / 255.0f;
                float b = (argb & 0xFF) / 255.0f;
                builder.vertex(x, y, z).color(r, g, b, a);
            }
        }

    /** Accumulates baked vertices, rolling to a new GpuBuffer before a BufferBuilder would overflow. */
    private static final class SegmentedSink implements BakingBoxRenderer.VertexSink {

        private final VertexFormat.DrawMode drawMode;
        private final String label;
        private final int estBytes;
        private final List<Segment> segments = new ArrayList<>();

        private BufferAllocator allocator;
        private BufferBuilder builder;
        private int currentVertices;

        SegmentedSink(VertexFormat.DrawMode drawMode, String label, int estBytes) {
            this.drawMode = drawMode;
            this.label = label;
            this.estBytes = estBytes;
        }

        @Override
        public void beginPrimitive(int vertexCount) {
            if (builder != null && currentVertices + vertexCount > MAX_SEGMENT_VERTICES) {
                flush();
            }
            if (builder == null) {
                allocator = new BufferAllocator(estBytes);
                builder = new BufferBuilder(allocator, drawMode, VertexFormats.POSITION_COLOR);
                currentVertices = 0;
            }
        }

        @Override
        public void vertex(float x, float y, float z, int argb) {
            float a = ((argb >>> 24) & 0xFF) / 255.0f;
            float r = ((argb >>> 16) & 0xFF) / 255.0f;
            float g = ((argb >>> 8) & 0xFF) / 255.0f;
            float b = (argb & 0xFF) / 255.0f;
            builder.vertex(x, y, z).color(r, g, b, a);
            currentVertices++;
        }

        private void flush() {
            if (builder == null) return;
            try {
                BuiltBuffer builtBuffer = builder.endNullable();
                if (builtBuffer != null) {
                    try {
                        int vertexCount = builtBuffer.getDrawParameters().vertexCount();
                        // COPY_DST so selection patching can writeToBuffer into this buffer later.
                        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label,
                                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, builtBuffer.getBuffer());
                        segments.add(new Segment(buffer, vertexCount));
                    } finally {
                        builtBuffer.close();
                    }
                }
            } finally {
                allocator.close();
                allocator = null;
                builder = null;
                currentVertices = 0;
            }
        }

        List<Segment> finish() {
            flush();
            return segments;
        }
    }
}
