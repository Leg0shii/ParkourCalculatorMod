package de.legoshi.parkourcalc.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21 {
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
//?}
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class CachedBoxGeometry {

    private static final int INITIAL_BYTES = 1 << 20;
    private static final Matrix4f IDENTITY = new Matrix4f();

    //? if >=1.21 {
    private final ByteBufferBuilder faceBytes = new ByteBufferBuilder(INITIAL_BYTES);
    private final ByteBufferBuilder lineBytes = new ByteBufferBuilder(INITIAL_BYTES);
    //?} else {
    /*private final BufferBuilder faceBuilder = new BufferBuilder(INITIAL_BYTES);
    private final BufferBuilder lineBuilder = new BufferBuilder(INITIAL_BYTES);
    *///?}

    private VertexBuffer faces;
    private VertexBuffer lines;
    private boolean hasFaces;
    private boolean hasLines;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private long bakedGeometryRev = -1;
    private int bakedStructuralHash;
    private Set<Integer> bakedSelection = new HashSet<>();
    private boolean built;

    public void ensureBuilt(BoxController boxController, PathRenderPlan plan) {
        long rev = boxController.getGeometryRev();
        if (built && rev == bakedGeometryRev && plan.structuralHash == bakedStructuralHash
                && plan.selection.equals(bakedSelection)) {
            return;
        }
        long buildStart = Perf.now();
        if (faces == null) {
            faces = newBuffer();
            lines = newBuffer();
        }
        Vec3dCore first = boxController.getPosition(0);
        anchorX = first.x;
        anchorY = first.y;
        anchorZ = first.z;
        //? if >=1.21 {
        hasFaces = bake(faces, faceBytes, FabricRenderLayers.TRANSLUCENT_FACES, BoxRenderer.Mode.FACES, plan.faceEmitter);
        hasLines = bake(lines, lineBytes, FabricRenderLayers.LINES, BoxRenderer.Mode.LINES, plan.lineEmitter);
        //?} else {
        /*hasFaces = bake(faces, faceBuilder, FabricRenderLayers.TRANSLUCENT_FACES, BoxRenderer.Mode.FACES, plan.faceEmitter);
        hasLines = bake(lines, lineBuilder, FabricRenderLayers.LINES, BoxRenderer.Mode.LINES, plan.lineEmitter);
        *///?}
        bakedGeometryRev = rev;
        bakedStructuralHash = plan.structuralHash;
        bakedSelection = new HashSet<>(plan.selection);
        built = true;
        Perf.stop("geomRebuild", buildStart);
    }

    //? if >=1.21 {
    private static VertexBuffer newBuffer() {
        return new VertexBuffer(BufferUsage.DYNAMIC_WRITE);
    }

    private boolean bake(VertexBuffer buffer, ByteBufferBuilder bytes, RenderType type, BoxRenderer.Mode mode, Consumer<BoxRenderer> emitter) {
        BufferBuilder builder = new BufferBuilder(bytes, type.mode(), type.format());
        emitter.accept(new FabricBoxRenderer(IDENTITY, builder, mode, anchorX, anchorY, anchorZ));
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
        emitter.accept(new FabricBoxRenderer(IDENTITY, builder, mode, anchorX, anchorY, anchorZ));
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

    public void draw(Matrix4f cameraPose, double camX, double camY, double camZ) {
        if (!built) {
            return;
        }
        Matrix4f model = new Matrix4f(cameraPose).translate(
                (float) (anchorX - camX),
                (float) (anchorY - camY),
                (float) (anchorZ - camZ)
        );
        if (hasFaces) {
            drawBuffer(faces, FabricRenderLayers.TRANSLUCENT_FACES, model);
        }
        if (hasLines) {
            drawBuffer(lines, FabricRenderLayers.LINES, model);
        }
    }

    private static void drawBuffer(VertexBuffer buffer, RenderType type, Matrix4f model) {
        type.setupRenderState();
        var shader = RenderSystem.getShader();
        if (shader != null) {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(model);
            buffer.bind();
            buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
        }
        type.clearRenderState();
    }

    public void close() {
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
        built = false;
    }
}
