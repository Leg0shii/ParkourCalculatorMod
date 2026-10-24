package de.legoshi.parkourcalc.fabric.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.render.SelectionPatchSpec;
import de.legoshi.parkourcalc.core.render.TailPatchGate;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.lwjgl.system.MemoryStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Persistent GPU buffers for the cached path geometry (segmented; anchor-relative; camera via model-view matrix). */
public final class CachedBoxGeometry implements AutoCloseable {

    private static final int STRIDE = 16; // POSITION_COLOR
    private static final int MAX_SEGMENT_VERTICES = 12_000_000; // < 2^24-1, divisible by 6
    private static final int FACE_BYTES_PER_BOX = 36 * STRIDE;
    private static final int LINE_STRIDE = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH.getVertexSize();
    private static final int LINE_BYTES_PER_BOX = 48 * LINE_STRIDE;

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
    private int arrowsPerBox;
    private boolean useSubtick;
    private int[] hitboxStarts = new int[]{0};
    private int[] subtickStarts = new int[]{0}; // per-box subtick offsets relative to lineMainTotal
    private int hitboxBase;     // first hitbox vertex in the faces buffer
    private int arrowBase;      // first yaw-arrow vertex in the faces buffer
    private int faceTotal;      // total face vertices (arrows occupy [arrowBase, faceTotal))
    private int lineMainTotal;  // first subtick vertex in the lines buffer
    private int lineTotal;      // total line vertices (subtick occupies [lineMainTotal, lineTotal))
    private int constraintFaceVerts;
    private int constraintLineVerts;
    private int reachLineVerts;
    private Set<Integer> bakedSelection = new HashSet<>();
    private GpuBuffer noFogBuffer;
    private GpuBufferSlice frameTransforms;
    private int[] frameRuns = new int[0];
    private boolean frameReady;

    private record Segment(GpuBuffer buffer, int vertexCount) {
    }

    public void ensureBuilt(BoxController boxController, PathRenderPlan plan) {
        long rev = boxController.getGeometryRev();
        if (built && rev == lastGeometryRev && plan.structuralHash == lastStructuralHash) {
            if (!plan.selection.equals(bakedSelection)) {
                patchSelection(boxController, plan.selection, plan.patch);
            }
            return;
        }
        long buildStart = Perf.now();
        if (TailPatchGate.canPatch(built, plan.structuralHash == lastStructuralHash,
                boxController.size() == boxCount, hitboxEdges, useSubtick, plan,
                constraintFaceVerts, constraintLineVerts)) {
            int dirty = boxController.takeDirtyFrom(lastGeometryRev);
            if (dirty > 0 && patchTail(boxController, Math.max(0, dirty - 1), plan.patch) && patchConstraintRegion(plan)) {
                lastGeometryRev = rev;
                if (!plan.selection.equals(bakedSelection)) {
                    patchSelection(boxController, plan.selection, plan.patch);
                }
                Perf.stop("geomPatchTail", buildStart);
                return;
            }
        }
        this.constraintFaceVerts = plan.constraintFaceVerts;
        this.constraintLineVerts = plan.constraintLineVerts;
        this.reachLineVerts = plan.reachLineVerts;
        rebuild(boxController, plan.faceEmitter, plan.lineEmitter, plan.patch);
        reserveIndexBuffers();
        boxController.takeDirtyFrom(rev);
        Perf.stop("geomRebuild", buildStart);
        bakedSelection = new HashSet<>(plan.selection);
        lastGeometryRev = rev;
        lastStructuralHash = plan.structuralHash;
        built = true;
    }

    private boolean patchConstraintRegion(PathRenderPlan plan) {
        boolean ok = true;
        if (constraintFaceVerts > 0) {
            ok = writeVerts(faceSegments, faceTotal - constraintFaceVerts, PrimitiveTopology.TRIANGLES,
                    BoxRenderer.Mode.FACES, plan.constraintFaceEmitter);
        }
        if (ok && constraintLineVerts > 0) {
            ok = writeVerts(lineSegments, lineTotal - constraintLineVerts, PrimitiveTopology.LINES,
                    BoxRenderer.Mode.LINES, plan.constraintLineEmitter);
        }
        if (!ok) close();
        return ok;
    }

    private boolean patchTail(BoxController boxController, int from, SelectionPatchSpec patch) {
        int n = boxCount;
        boolean ok = writeVerts(
                faceSegments,
                PathVertexLayout.faceMainOffset(from),
                PrimitiveTopology.TRIANGLES,
                BoxRenderer.Mode.FACES,
                r -> boxController.render(r, patch.facePicker, from, n)
        );
        if (ok && hitboxEdges != 0) {
            Consumer<BoxRenderer> emit = patch.showFullHitbox
                    ? r -> boxController.renderHitboxFullWireframe(r, patch.hitboxPicker, useSubtick, from, n)
                    : r -> boxController.renderHitboxFloorOutline(r, patch.hitboxPicker, useSubtick, from, n);
            ok = writeVerts(
                    faceSegments,
                    hitboxBase + hitboxStarts[from],
                    PrimitiveTopology.TRIANGLES,
                    BoxRenderer.Mode.FACES,
                    emit
            );
        }
        if (ok && arrowsPerBox > 0 && from < n - 1) {
            int stride = arrowsPerBox * PathVertexLayout.ARROW_VERTS_PER_BOX;
            ok = writeVerts(
                    faceSegments,
                    arrowBase + from * stride,
                    PrimitiveTopology.TRIANGLES,
                    BoxRenderer.Mode.FACES,
                    r -> boxController.renderFacingArrows(r, patch.drawYawArrows, patch.drawCombinedArrows,
                            patch.yawArrowArgb, patch.combinedArrowArgb, from, n - 1)
            );
        }
        if (ok) {
            ok = writeVerts(
                    lineSegments,
                    PathVertexLayout.lineMainOffset(from),
                    PrimitiveTopology.LINES,
                    BoxRenderer.Mode.LINES,
                    r -> boxController.render(r, patch.linePicker, from, n)
            );
        }
        if (!ok) close();
        return ok;
    }

    private void rebuild(BoxController boxController, Consumer<BoxRenderer> faceEmitter, Consumer<BoxRenderer> lineEmitter, SelectionPatchSpec patch) {
        Vec3dCore first = boxController.getPosition(0);
        anchorX = first.x;
        anchorY = first.y;
        anchorZ = first.z;

        releaseBuffers();
        boxCount = boxController.size();
        useSubtick = patch.showSubtick;
        hitboxEdges = patch.hitboxEdges();
        arrowsPerBox = patch.arrowsPerBox;
        hitboxStarts = PathVertexLayout.hitboxVertexStarts(boxController, hitboxEdges, useSubtick);

        faceSegments = bake(BoxRenderer.Mode.FACES, PrimitiveTopology.TRIANGLES, boxCount * FACE_BYTES_PER_BOX, "parkourcalc cached faces", faceEmitter);
        lineSegments = bake(BoxRenderer.Mode.LINES, PrimitiveTopology.LINES, boxCount * LINE_BYTES_PER_BOX, "parkourcalc cached lines", lineEmitter);

        hitboxBase = PathVertexLayout.hitboxRegionBase(boxCount);
        arrowBase = hitboxBase + hitboxStarts[boxCount];
        faceTotal = totalVertices(faceSegments);
        lineMainTotal = boxCount * PathVertexLayout.LINE_VERTS_PER_BOX;
        lineTotal = totalVertices(lineSegments) / 2;
        subtickStarts = useSubtick ? boxController.subtickVertexStarts() : new int[]{0};
    }

    public void prepareFrame(Matrix4f modelView, int[] runs) {
        if (!built) {
            frameReady = false;
            return;
        }
        frameTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector3f(),
                new Matrix4f()
        );
        frameRuns = runs;
        RenderSystem.getCompiledPipeline(FabricRenderLayers.wideLinesPipeline());
        RenderSystem.getCompiledPipeline(FabricRenderLayers.lineDepthPipeline());
        RenderSystem.getCompiledPipeline(FabricRenderLayers.translucentBoxPipeline());
        RenderSystem.getCompiledPipeline(FabricRenderLayers.boxDepthPipeline());
        RenderSystem.getCompiledPipeline(FabricRenderLayers.constraintFillPipeline());
        RenderSystem.getCompiledPipeline(FabricRenderLayers.constraintDepthPipeline());
        frameReady = true;
    }

    public boolean frameReady() {
        return frameReady;
    }

    public void finishFrame() {
        frameReady = false;
    }

    private void reserveIndexBuffers() {
        reserveIndexBuffer(PrimitiveTopology.TRIANGLES, faceSegments);
        reserveIndexBuffer(PrimitiveTopology.LINES, lineSegments);
    }

    private static void reserveIndexBuffer(PrimitiveTopology drawMode, List<Segment> segments) {
        int largest = 0;
        for (Segment segment : segments) {
            largest = Math.max(largest, segment.vertexCount());
        }
        if (largest > 0) {
            RenderSystem.getSequentialBuffer(drawMode).getBuffer(drawMode.indexCount(largest));
        }
    }

    private static int totalVertices(List<Segment> segments) {
        int total = 0;
        for (Segment segment : segments) {
            total += segment.vertexCount();
        }
        return total;
    }

    private List<Segment> bake(BoxRenderer.Mode mode, PrimitiveTopology drawMode, int estBytes, String label, Consumer<BoxRenderer> emitter) {
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
                        PrimitiveTopology.TRIANGLES,
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
            if (!writeVerts(faceSegments, offset, PrimitiveTopology.TRIANGLES, BoxRenderer.Mode.FACES, emit)) {
                return false;
            }
        }

        int lineArgb = patch.linePicker.argbFor(i, state);
        return writeVerts(
                lineSegments,
                PathVertexLayout.lineMainOffset(i),
                PrimitiveTopology.LINES,
                BoxRenderer.Mode.LINES,
                r -> r.drawBox(boxController.getTickAabb(i), lineArgb)
        );
    }

    private boolean writeVerts(List<Segment> segments, int globalVertexOffset, PrimitiveTopology drawMode,
                               BoxRenderer.Mode mode, Consumer<BoxRenderer> emit) {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(8192)) {
            BufferBuilder builder = new BufferBuilder(allocator, drawMode, formatFor(drawMode));
            emit.accept(new BakingBoxRenderer(new DirectSink(new VertexWriter(builder, drawMode)), mode, anchorX, anchorY, anchorZ));
            MeshData builtBuffer = builder.build();
            try (builtBuffer) {
                if (builtBuffer == null) return true;
                int vertexCount = builtBuffer.drawState().vertexCount();
                return writeBytesAcrossSegments(segments, globalVertexOffset * verticesPerLogical(drawMode), vertexCount,
                        builtBuffer.vertexBuffer(), strideFor(drawMode));
            }
        }
    }

    private static VertexFormat formatFor(PrimitiveTopology drawMode) {
        return drawMode == PrimitiveTopology.LINES ? DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH : DefaultVertexFormat.POSITION_COLOR;
    }

    private static int strideFor(PrimitiveTopology drawMode) {
        return drawMode == PrimitiveTopology.LINES ? LINE_STRIDE : STRIDE;
    }

    private static int verticesPerLogical(PrimitiveTopology drawMode) {
        return drawMode == PrimitiveTopology.LINES ? 2 : 1;
    }

    private static boolean writeBytesAcrossSegments(List<Segment> segments, int globalVertexOffset, int vertexCount, ByteBuffer source, int stride) {
        int cursorVertex = globalVertexOffset;
        int sourceVertex = 0;
        int remaining = vertexCount;
        int segmentStart = 0;
        for (Segment segment : segments) {
            int segmentEnd = segmentStart + segment.vertexCount();
            if (remaining > 0 && cursorVertex < segmentEnd) {
                int localVertex = cursorVertex - segmentStart;
                int writable = Math.min(remaining, segment.vertexCount() - localVertex);
                ByteBuffer slice = source.slice(sourceVertex * stride, writable * stride);
                GpuBufferSlice destination = segment.buffer().slice(localVertex * stride, writable * stride);
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

    public void drawFaces(RenderPass pass) {
        drawFaces(pass, FabricRenderLayers.translucentBoxPipeline());
    }

    public void drawFaceDepth(RenderPass pass) {
        drawFaces(pass, FabricRenderLayers.boxDepthPipeline());
    }

    private void drawFaces(RenderPass pass, RenderPipeline pipeline) {
        int[] runs = frameRuns;
        int constraintFaceBase = faceTotal - constraintFaceVerts;
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            drawRange(pass, faceSegments, pipeline, PrimitiveTopology.TRIANGLES, PathVertexLayout.faceMainOffset(a), (b - a) * PathVertexLayout.FACE_VERTS_PER_BOX);
            if (hitboxEdges != 0) {
                drawRange(pass, faceSegments, pipeline, PrimitiveTopology.TRIANGLES, hitboxBase + hitboxStarts[a], hitboxStarts[b] - hitboxStarts[a]);
            }
            if (arrowsPerBox > 0 && arrowBase < constraintFaceBase) {
                int arrowStride = arrowsPerBox * PathVertexLayout.ARROW_VERTS_PER_BOX;
                int arrowEnd = Math.min(b, boxCount - 1);
                int arrowStart = Math.min(a, boxCount - 1);
                if (arrowEnd > arrowStart) {
                    drawRange(pass, faceSegments, pipeline, PrimitiveTopology.TRIANGLES, arrowBase + arrowStart * arrowStride, (arrowEnd - arrowStart) * arrowStride);
                }
            }
        }
    }

    public void drawConstraintFill(RenderPass pass) {
        drawConstraintFaces(pass, FabricRenderLayers.constraintFillPipeline());
    }

    public void drawConstraintDepth(RenderPass pass) {
        drawConstraintFaces(pass, FabricRenderLayers.constraintDepthPipeline());
    }

    private void drawConstraintFaces(RenderPass pass, RenderPipeline pipeline) {
        if (constraintFaceVerts > 0) {
            drawRange(pass, faceSegments, pipeline, PrimitiveTopology.TRIANGLES, faceTotal - constraintFaceVerts, constraintFaceVerts);
        }
    }

    public void drawPathLines(RenderPass pass) {
        drawPathLines(pass, FabricRenderLayers.wideLinesPipeline());
    }

    public void drawLineDepth(RenderPass pass) {
        drawPathLines(pass, FabricRenderLayers.lineDepthPipeline());
    }

    private void drawPathLines(RenderPass pass, RenderPipeline pipeline) {
        int[] runs = frameRuns;
        int trailingLineBase = lineTotal - constraintLineVerts - reachLineVerts;
        boolean hasSubtick = lineMainTotal < trailingLineBase;
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            drawRange(pass, lineSegments, pipeline, PrimitiveTopology.LINES, PathVertexLayout.lineMainOffset(a), (b - a) * PathVertexLayout.LINE_VERTS_PER_BOX);
            if (hasSubtick) {
                drawRange(pass, lineSegments, pipeline, PrimitiveTopology.LINES, lineMainTotal + subtickStarts[a], subtickStarts[b] - subtickStarts[a]);
            }
        }
        drawRange(pass, lineSegments, pipeline, PrimitiveTopology.LINES, trailingLineBase, reachLineVerts);
    }

    public void drawConstraintLines(RenderPass pass) {
        drawRange(pass, lineSegments, FabricRenderLayers.wideLinesPipeline(), PrimitiveTopology.LINES, lineTotal - constraintLineVerts, constraintLineVerts);
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

    private void drawRange(RenderPass pass, List<Segment> segments, RenderPipeline pipeline, PrimitiveTopology drawMode, int globalVertexOffset, int count) {
        if (count <= 0) return;
        int cursor = globalVertexOffset * verticesPerLogical(drawMode);
        int remaining = count * verticesPerLogical(drawMode);
        int segmentStart = 0;
        for (Segment segment : segments) {
            int segmentEnd = segmentStart + segment.vertexCount();
            if (remaining > 0 && cursor < segmentEnd) {
                int localVertex = cursor - segmentStart;
                int drawable = Math.min(remaining, segment.vertexCount() - localVertex);
                drawSegment(pass, pipeline, segment.buffer(), drawMode, localVertex, drawable);
                cursor += drawable;
                remaining -= drawable;
            }
            segmentStart = segmentEnd;
            if (remaining == 0) return;
        }
    }

    private void drawSegment(RenderPass pass, RenderPipeline pipeline, GpuBuffer vbo, PrimitiveTopology drawMode, int firstVertex, int count) {
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(drawMode);
        GpuBuffer ibo = indexBuffer.getBuffer(drawMode.indexCount(firstVertex + count));

        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(pass);
        if (drawMode == PrimitiveTopology.LINES) {
            pass.setUniform("Fog", noFog());
        }
        pass.setUniform("DynamicTransforms", frameTransforms);
        pass.setVertexBuffer(0, vbo.slice());
        pass.setIndexBuffer(ibo, indexBuffer.type());
        pass.drawIndexed(drawMode.indexCount(count), 1, drawMode.indexCount(firstVertex), 0, 0);
    }

    private GpuBufferSlice noFog() {
        if (noFogBuffer == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.malloc(FogRenderer.FOG_UBO_SIZE);
                for (int i = 0; i < 4; i++) {
                    data.putFloat(0.0f);
                }
                while (data.remaining() >= 4) {
                    data.putFloat(Float.MAX_VALUE);
                }
                data.flip();
                noFogBuffer = RenderSystem.getDevice().createBuffer(() -> "parkourcalc no fog", GpuBuffer.USAGE_UNIFORM, data);
            }
        }
        return noFogBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE);
    }

    private void releaseBuffers() {
        if (noFogBuffer != null) {
            noFogBuffer.close();
            noFogBuffer = null;
        }
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
        frameReady = false;
        lastGeometryRev = -1;
    }

    /**
     * Writes vertices straight into one pre-sized BufferBuilder; used for the small per-box patch re-emit.
     */
    private record DirectSink(VertexWriter writer) implements BakingBoxRenderer.VertexSink {

        @Override
        public void beginPrimitive(int vertexCount) {
        }

        @Override
        public void vertex(float x, float y, float z, int argb) {
            writer.vertex(x, y, z, argb);
        }
    }

    private static final class VertexWriter {

        private final BufferBuilder builder;
        private final boolean lines;
        private boolean pending;
        private float px;
        private float py;
        private float pz;
        private int pargb;

        VertexWriter(BufferBuilder builder, PrimitiveTopology drawMode) {
            this.builder = builder;
            this.lines = drawMode == PrimitiveTopology.LINES;
        }

        void vertex(float x, float y, float z, int argb) {
            if (!lines) {
                builder.addVertex(x, y, z).setColor(red(argb), green(argb), blue(argb), alpha(argb));
                return;
            }
            if (!pending) {
                px = x;
                py = y;
                pz = z;
                pargb = argb;
                pending = true;
                return;
            }
            pending = false;
            float dx = x - px;
            float dy = y - py;
            float dz = z - pz;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0.0f) {
                dx /= length;
                dy /= length;
                dz /= length;
            } else {
                dy = 1.0f;
            }
            lineVertex(px, py, pz, pargb, dx, dy, dz);
            lineVertex(x, y, z, argb, dx, dy, dz);
        }

        private void lineVertex(float x, float y, float z, int argb, float nx, float ny, float nz) {
            builder.addVertex(x, y, z)
                    .setColor(red(argb), green(argb), blue(argb), alpha(argb))
                    .setNormal(nx, ny, nz)
                    .setLineWidth(BoxStyle.LINE_WIDTH);
        }

        private static float alpha(int argb) {
            return ((argb >>> 24) & 0xFF) / 255.0f;
        }

        private static float red(int argb) {
            return ((argb >>> 16) & 0xFF) / 255.0f;
        }

        private static float green(int argb) {
            return ((argb >>> 8) & 0xFF) / 255.0f;
        }

        private static float blue(int argb) {
            return (argb & 0xFF) / 255.0f;
        }
    }

    /** Accumulates baked vertices, rolling to a new GpuBuffer before a BufferBuilder would overflow. */
    private static final class SegmentedSink implements BakingBoxRenderer.VertexSink {

        private final PrimitiveTopology drawMode;
        private final String label;
        private final int estBytes;
        private final List<Segment> segments = new ArrayList<>();

        private ByteBufferBuilder allocator;
        private BufferBuilder builder;
        private VertexWriter writer;
        private int currentVertices;

        SegmentedSink(PrimitiveTopology drawMode, String label, int estBytes) {
            this.drawMode = drawMode;
            this.label = label;
            this.estBytes = estBytes;
        }

        @Override
        public void beginPrimitive(int vertexCount) {
            int physical = vertexCount * verticesPerLogical(drawMode);
            if (builder != null && currentVertices + physical > MAX_SEGMENT_VERTICES) {
                flush();
            }
            if (builder == null) {
                allocator = new ByteBufferBuilder(estBytes);
                builder = new BufferBuilder(allocator, drawMode, formatFor(drawMode));
                writer = new VertexWriter(builder, drawMode);
                currentVertices = 0;
            }
        }

        @Override
        public void vertex(float x, float y, float z, int argb) {
            writer.vertex(x, y, z, argb);
            currentVertices += verticesPerLogical(drawMode);
        }

        private void flush() {
            if (builder == null) return;
            try {
                MeshData builtBuffer = builder.build();
                if (builtBuffer != null) {
                    try {
                        int vertexCount = builtBuffer.drawState().vertexCount();
                        // COPY_DST so selection patching can writeToBuffer into this buffer later.
                        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label,
                                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, builtBuffer.vertexBuffer());
                        segments.add(new Segment(buffer, vertexCount));
                    } finally {
                        builtBuffer.close();
                    }
                }
            } finally {
                allocator.close();
                allocator = null;
                builder = null;
                writer = null;
                currentVertices = 0;
            }
        }

        List<Segment> finish() {
            flush();
            return segments;
        }
    }
}
