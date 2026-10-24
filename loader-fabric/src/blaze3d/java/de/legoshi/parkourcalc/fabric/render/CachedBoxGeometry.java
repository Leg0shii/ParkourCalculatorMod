package de.legoshi.parkourcalc.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21 {
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
//?}
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
//? if >=1.21 {
import net.minecraft.client.renderer.FogParameters;
//?}
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.SelectionPatchSpec;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class CachedBoxGeometry {

    private static final int CHUNK = 256;
    private static final double CHUNK_MARGIN = 1.5;
    private static final double ANCHOR_RANGE = 1024.0;
    private static final long FRAME_BUDGET_NANOS = 3_000_000L;
    private static final int INITIAL_BYTES = 1 << 20;

    //? if >=1.21 {
    private final ByteBufferBuilder faceBytes = new ByteBufferBuilder(INITIAL_BYTES);
    private final ByteBufferBuilder lineBytes = new ByteBufferBuilder(INITIAL_BYTES);
    //?} else {
    /*private final BufferBuilder faceBuilder = new BufferBuilder(INITIAL_BYTES);
    private final BufferBuilder lineBuilder = new BufferBuilder(INITIAL_BYTES);
    *///?}

    private final List<Chunk> chunks = new ArrayList<>();
    private final Chunk tail = new Chunk();
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private long bakedGeometryRev = -1;
    private int bakedChunkHash;
    private int bakedStructuralHash;
    private Set<Integer> bakedSelection = new HashSet<>();
    private boolean built;
    private int pendingFrom = -1;
    private boolean pendingTail;
    private final Set<Integer> pendingSelection = new HashSet<>();

    private static final class Chunk {
        VertexBuffer faces;
        VertexBuffer lines;
        boolean hasFaces;
        boolean hasLines;
        boolean ready;
        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;

        void close() {
            if (faces != null) {
                faces.close();
                faces = null;
            }
            if (lines != null) {
                lines.close();
                lines = null;
            }
            hasFaces = false;
            hasLines = false;
            ready = false;
        }

        double distanceSq(double x, double y, double z) {
            double dx = Math.max(Math.max(minX - x, 0.0), x - maxX);
            double dy = Math.max(Math.max(minY - y, 0.0), y - maxY);
            double dz = Math.max(Math.max(minZ - z, 0.0), z - maxZ);
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public void ensureBuilt(BoxController boxController, PathRenderPlan plan, Settings settings) {
        int n = boxController.size();
        if (n == 0) {
            close();
            return;
        }
        long rev = boxController.getGeometryRev();
        int from;
        if (!built || plan.chunkHash != bakedChunkHash) {
            from = 0;
            boxController.takeDirtyFrom(rev);
        } else if (rev != bakedGeometryRev) {
            from = Math.max(0, boxController.takeDirtyFrom(bakedGeometryRev) - 1);
        } else {
            from = -1;
        }
        if (from >= 0) {
            boolean geometryChanged = rev != bakedGeometryRev;
            if (from == 0) {
                Vec3dCore first = boxController.getPosition(0);
                if (!built || Math.abs(first.x - anchorX) > ANCHOR_RANGE || Math.abs(first.y - anchorY) > ANCHOR_RANGE
                        || Math.abs(first.z - anchorZ) > ANCHOR_RANGE) {
                    anchorX = first.x;
                    anchorY = first.y;
                    anchorZ = first.z;
                    geometryChanged = true;
                }
            }
            int chunkCount = (n + CHUNK - 1) / CHUNK;
            while (chunks.size() > chunkCount) {
                chunks.remove(chunks.size() - 1).close();
            }
            while (chunks.size() < chunkCount) {
                chunks.add(new Chunk());
            }
            int firstChunk = from / CHUNK;
            if (geometryChanged) {
                for (int k = firstChunk; k < chunkCount; k++) {
                    chunks.get(k).ready = false;
                }
                tail.ready = false;
            }
            pendingFrom = pendingFrom < 0 ? firstChunk : Math.min(pendingFrom, firstChunk);
            pendingTail = true;
        } else if (plan.structuralHash != bakedStructuralHash) {
            pendingTail = true;
        }
        if (!plan.selection.equals(bakedSelection)) {
            pendingSelection.addAll(changedChunks(plan.selection, n));
            bakedSelection = new HashSet<>(plan.selection);
        }
        bakedGeometryRev = rev;
        bakedChunkHash = plan.chunkHash;
        bakedStructuralHash = plan.structuralHash;
        built = true;
        work(boxController, plan, settings);
    }

    private void work(BoxController boxController, PathRenderPlan plan, Settings settings) {
        if (pendingFrom < 0 && !pendingTail && pendingSelection.isEmpty()) {
            return;
        }
        long start = Perf.now();
        long deadline = System.nanoTime() + FRAME_BUDGET_NANOS;
        if (pendingTail) {
            bakeTail(boxController, plan, settings);
            pendingTail = false;
        }
        Iterator<Integer> selected = pendingSelection.iterator();
        while (selected.hasNext()) {
            int k = selected.next();
            if (k >= chunks.size() || (pendingFrom >= 0 && k >= pendingFrom)) {
                selected.remove();
                continue;
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            bakeChunk(boxController, plan, settings, k);
            selected.remove();
        }
        while (pendingFrom >= 0 && pendingFrom < chunks.size()) {
            bakeChunk(boxController, plan, settings, pendingFrom);
            pendingFrom++;
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (pendingFrom >= chunks.size()) {
            pendingFrom = -1;
        }
        Perf.stop("geomRebuild", start);
    }

    private Set<Integer> changedChunks(Set<Integer> selection, int n) {
        Set<Integer> result = new HashSet<>();
        for (int i : selection) {
            if (!bakedSelection.contains(i) && i >= 0 && i < n) {
                result.add(i / CHUNK);
            }
        }
        for (int i : bakedSelection) {
            if (!selection.contains(i) && i >= 0 && i < n) {
                result.add(i / CHUNK);
            }
        }
        return result;
    }

    private void bakeChunk(BoxController boxController, PathRenderPlan plan, Settings settings, int k) {
        Chunk chunk = chunks.get(k);
        int n = boxController.size();
        int from = k * CHUNK;
        int to = Math.min(n, from + CHUNK);
        SelectionPatchSpec patch = plan.patch;
        boolean hitbox = patch.hitboxEdges() != 0;
        int arrowTo = Math.min(to, n - 1);
        int subtickArgb = BoxStyle.subtickPathArgb(settings);

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = from; i < to; i++) {
            Vec3dCore p = boxController.getPosition(i);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        chunk.minX = minX - CHUNK_MARGIN;
        chunk.minY = minY - CHUNK_MARGIN;
        chunk.minZ = minZ - CHUNK_MARGIN;
        chunk.maxX = maxX + CHUNK_MARGIN;
        chunk.maxY = maxY + CHUNK_MARGIN;
        chunk.maxZ = maxZ + CHUNK_MARGIN;

        Consumer<BoxRenderer> faces = r -> {
            boxController.render(r, patch.facePicker, from, to);
            if (hitbox && patch.showFullHitbox) {
                boxController.renderHitboxFullWireframe(r, patch.hitboxPicker, patch.showSubtick, from, to);
            } else if (hitbox) {
                boxController.renderHitboxFloorOutline(r, patch.hitboxPicker, patch.showSubtick, from, to);
            }
            if (patch.arrowsPerBox > 0 && arrowTo > from) {
                boxController.renderFacingArrows(r, patch.drawYawArrows, patch.drawCombinedArrows,
                        patch.yawArrowArgb, patch.combinedArrowArgb, from, arrowTo);
            }
        };
        Consumer<BoxRenderer> lines = r -> {
            boxController.render(r, patch.linePicker, from, to);
            if (patch.showSubtick) {
                boxController.renderPath(r, subtickArgb, from, to);
            }
        };
        bakeInto(chunk, faces, lines);
        chunk.ready = true;
    }

    private void bakeTail(BoxController boxController, PathRenderPlan plan, Settings settings) {
        boolean reach = plan.reachLineVerts > 0;
        int miss = BoxStyle.hitDistanceMissArgb(settings);
        int hit = BoxStyle.hitDistanceHitArgb(settings);
        Consumer<BoxRenderer> lines = r -> {
            if (reach) {
                boxController.renderHitDistanceLines(r, PathRenderPlan.reachProbe(), miss, hit);
            }
            plan.constraintLineEmitter.accept(r);
        };
        bakeInto(tail, plan.constraintFaceEmitter, lines);
        tail.minX = Double.NEGATIVE_INFINITY;
        tail.minY = Double.NEGATIVE_INFINITY;
        tail.minZ = Double.NEGATIVE_INFINITY;
        tail.maxX = Double.POSITIVE_INFINITY;
        tail.maxY = Double.POSITIVE_INFINITY;
        tail.maxZ = Double.POSITIVE_INFINITY;
        tail.ready = true;
    }

    private void bakeInto(Chunk chunk, Consumer<BoxRenderer> faces, Consumer<BoxRenderer> lines) {
        if (chunk.faces == null) {
            chunk.faces = newBuffer();
            chunk.lines = newBuffer();
        }
        //? if >=1.21 {
        chunk.hasFaces = bake(chunk.faces, faceBytes, FabricRenderLayers.TRANSLUCENT_FACES, BoxRenderer.Mode.FACES, faces);
        chunk.hasLines = bake(chunk.lines, lineBytes, FabricRenderLayers.LINES, BoxRenderer.Mode.LINES, lines);
        //?} else {
        /*chunk.hasFaces = bake(chunk.faces, faceBuilder, FabricRenderLayers.TRANSLUCENT_FACES, BoxRenderer.Mode.FACES, faces);
        chunk.hasLines = bake(chunk.lines, lineBuilder, FabricRenderLayers.LINES, BoxRenderer.Mode.LINES, lines);
        *///?}
    }

    //? if >=1.21 {
    private static VertexBuffer newBuffer() {
        return new VertexBuffer(BufferUsage.DYNAMIC_WRITE);
    }

    private boolean bake(VertexBuffer buffer, ByteBufferBuilder bytes, RenderType type, BoxRenderer.Mode mode, Consumer<BoxRenderer> emitter) {
        BufferBuilder builder = new BufferBuilder(bytes, type.mode(), type.format());
        emitter.accept(new FabricBoxRenderer(null, builder, mode, anchorX, anchorY, anchorZ));
        MeshData mesh = builder.build();
        if (mesh == null) {
            return false;
        }
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return true;
    }
    //?} else {
    /*private static VertexBuffer newBuffer() {
        return new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    }

    private boolean bake(VertexBuffer buffer, BufferBuilder builder, RenderType type, BoxRenderer.Mode mode, Consumer<BoxRenderer> emitter) {
        builder.begin(type.mode(), type.format());
        emitter.accept(new FabricBoxRenderer(null, builder, mode, anchorX, anchorY, anchorZ));
        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered == null) {
            return false;
        }
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        return true;
    }
    *///?}

    public void draw(Matrix4f cameraPose, double camX, double camY, double camZ, double maxDistanceSq) {
        if (!built) {
            return;
        }
        Matrix4f model = new Matrix4f(cameraPose).translate(
                (float) (anchorX - camX),
                (float) (anchorY - camY),
                (float) (anchorZ - camZ)
        );
        List<Chunk> visible = new ArrayList<>(chunks.size() + 1);
        if (tail.ready) {
            visible.add(tail);
        }
        for (Chunk chunk : chunks) {
            if (chunk.ready && (maxDistanceSq == Double.POSITIVE_INFINITY || chunk.distanceSq(camX, camY, camZ) <= maxDistanceSq)) {
                visible.add(chunk);
            }
        }
        drawPass(FabricRenderLayers.TRANSLUCENT_FACES, true, model, visible);
        drawPass(FabricRenderLayers.LINES, false, model, visible);
    }

    private static void drawPass(RenderType type, boolean faces, Matrix4f model, List<Chunk> visible) {
        type.setupRenderState();
        //? if >=1.21 {
        FogParameters fog = RenderSystem.getShaderFog();
        if (!faces) {
            RenderSystem.setShaderFog(FogParameters.NO_FOG);
        }
        //?} else {
        /*float fogStart = RenderSystem.getShaderFogStart();
        if (!faces) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        }
        *///?}
        var shader = RenderSystem.getShader();
        if (shader != null) {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(model);
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            boolean bound = false;
            for (Chunk chunk : visible) {
                VertexBuffer buffer = faces ? chunk.faces : chunk.lines;
                if (buffer == null || !(faces ? chunk.hasFaces : chunk.hasLines)) {
                    continue;
                }
                buffer.bind();
                buffer.drawWithShader(modelView, projection, shader);
                bound = true;
            }
            if (bound) {
                VertexBuffer.unbind();
            }
        }
        //? if >=1.21 {
        RenderSystem.setShaderFog(fog);
        //?} else {
        /*RenderSystem.setShaderFogStart(fogStart);
        *///?}
        type.clearRenderState();
    }

    public void close() {
        for (Chunk chunk : chunks) {
            chunk.close();
        }
        chunks.clear();
        tail.close();
        bakedGeometryRev = -1;
        pendingFrom = -1;
        pendingTail = false;
        pendingSelection.clear();
        built = false;
    }
}
