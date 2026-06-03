package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathGeometrySource;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.render.SelectionPatchSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.forge.core.render.CountingBoxRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Persistent VBOs for the cached path geometry on MC 1.8.9 (anchor-relative; camera via glTranslate). */
public final class Forge8CachedBoxGeometry {

    private static final int STRIDE = 16; // POSITION_COLOR
    private static final int INTS_PER_VERTEX = STRIDE / 4;

    private VertexBuffer faceVbo;
    private VertexBuffer lineVbo;
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
    private int[] subtickStarts = new int[]{0};
    private int hitboxBase;
    private int arrowBase;
    private int faceTotal;
    private int lineMainTotal;
    private int lineTotal;
    private int lastBakeVertices;
    private Set<Integer> bakedSelection = new HashSet<Integer>();

    public void ensureBuilt(BoxController boxController, int structuralHash, Set<Integer> selection, PathGeometrySource source, SelectionPatchSpec patch) {
        long rev = boxController.getGeometryRev();
        if (built && rev == lastGeometryRev && structuralHash == lastStructuralHash) {
            if (!selection.equals(bakedSelection)) {
                patchSelection(boxController, selection, patch);
            }
            return;
        }
        rebuild(boxController, source, patch);
        bakedSelection = new HashSet<Integer>(selection);
        lastGeometryRev = rev;
        lastStructuralHash = structuralHash;
        built = true;
    }

    private void rebuild(BoxController boxController, PathGeometrySource source, SelectionPatchSpec patch) {
        Vec3dCore first = boxController.getPosition(0);
        anchorX = first.x;
        anchorY = first.y;
        anchorZ = first.z;

        release();
        boxCount = boxController.size();
        useSubtick = patch.showSubtick;
        hitboxEdges = patch.hitboxEdges();
        hitboxStarts = PathVertexLayout.hitboxVertexStarts(boxController, hitboxEdges, useSubtick);

        faceVbo = bake(GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES, source::emitFaces);
        faceTotal = lastBakeVertices;
        lineVbo = bake(GL11.GL_LINES, BoxRenderer.Mode.LINES, source::emitLines);
        lineTotal = lastBakeVertices;

        hitboxBase = PathVertexLayout.hitboxRegionBase(boxCount);
        arrowBase = hitboxBase + hitboxStarts[boxCount];
        lineMainTotal = boxCount * PathVertexLayout.LINE_VERTS_PER_BOX;
        subtickStarts = useSubtick ? boxController.subtickVertexStarts() : new int[]{0};
    }

    private VertexBuffer bake(int glMode, BoxRenderer.Mode mode, Consumer<BoxRenderer> emitter) {
        // WorldRenderer doesn't grow during pos()/endVertex(), so count first and pre-size exactly.
        CountingBoxRenderer counter = new CountingBoxRenderer(mode);
        emitter.accept(counter);
        long vertices = counter.vertexCount();
        lastBakeVertices = (int) vertices;
        if (vertices == 0) return null;

        // A private buffer, not the shared Tessellator, so a huge bake doesn't permanently inflate it.
        WorldRenderer builder = new WorldRenderer((int) (vertices * INTS_PER_VERTEX) + INTS_PER_VERTEX);
        builder.begin(glMode, DefaultVertexFormats.POSITION_COLOR);
        emitter.accept(new Forge8BoxRenderer(builder, anchorX, anchorY, anchorZ, mode));
        builder.finishDrawing();
        VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.POSITION_COLOR);
        vbo.bufferData(builder.getByteBuffer());
        builder.reset();
        return vbo;
    }

    private void patchSelection(BoxController boxController, Set<Integer> selection, SelectionPatchSpec patch) {
        Set<Integer> changed = new HashSet<Integer>();
        for (Integer i : selection) {
            if (!bakedSelection.contains(i)) changed.add(i);
        }
        for (Integer i : bakedSelection) {
            if (!selection.contains(i)) changed.add(i);
        }
        for (int i : changed) {
            if (i >= 0 && i < boxCount) {
                patchBox(boxController, i, patch);
            }
        }
        bakedSelection = new HashSet<Integer>(selection);
    }

    private void patchBox(final BoxController boxController, final int i, SelectionPatchSpec patch) {
        TickState state = boxController.getState(i);

        final int faceArgb = patch.facePicker.argbFor(i, state);
        writeVerts(faceVbo, PathVertexLayout.faceMainOffset(i), GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES,
                PathVertexLayout.FACE_VERTS_PER_BOX,
                r -> r.drawBox(boxController.getTickAabb(i), faceArgb)
        );

        if (hitboxEdges != 0) {
            int offset = PathVertexLayout.hitboxRegionBase(boxCount) + hitboxStarts[i];
            int count = hitboxStarts[i + 1] - hitboxStarts[i];
            final int hitboxArgb = patch.hitboxPicker.argbFor(i, state);
            final boolean full = patch.showFullHitbox;
            writeVerts(faceVbo, offset, GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES, count,
                    r -> {
                        if (full) {
                            boxController.emitHitboxFullWireframeAt(r, hitboxArgb, useSubtick, i);
                        } else {
                            boxController.emitHitboxFloorOutlineAt(r, hitboxArgb, useSubtick, i);
                        }
                    }
            );
        }

        final int lineArgb = patch.linePicker.argbFor(i, state);
        writeVerts(lineVbo, PathVertexLayout.lineMainOffset(i), GL11.GL_LINES, BoxRenderer.Mode.LINES,
                PathVertexLayout.LINE_VERTS_PER_BOX,
                r -> r.drawBox(boxController.getTickAabb(i), lineArgb)
        );
    }

    private void writeVerts(VertexBuffer vbo, int globalVertexOffset, int glMode, BoxRenderer.Mode mode,  int vertexCount, Consumer<BoxRenderer> emit) {
        if (vbo == null || vertexCount == 0) return;
        WorldRenderer builder = new WorldRenderer(vertexCount * INTS_PER_VERTEX + INTS_PER_VERTEX);
        builder.begin(glMode, DefaultVertexFormats.POSITION_COLOR);
        emit.accept(new Forge8BoxRenderer(builder, anchorX, anchorY, anchorZ, mode));
        builder.finishDrawing();
        ByteBuffer bytes = builder.getByteBuffer();
        vbo.bindBuffer();
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) globalVertexOffset * STRIDE, bytes);
        vbo.unbindBuffer();
        builder.reset();
    }

    public void drawFaces(int[] runs) {
        if (faceVbo == null) return;
        beginArrays(faceVbo);
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            GL11.glDrawArrays(GL11.GL_TRIANGLES, PathVertexLayout.faceMainOffset(a), (b - a) * PathVertexLayout.FACE_VERTS_PER_BOX);
            if (hitboxEdges != 0) {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, hitboxBase + hitboxStarts[a], hitboxStarts[b] - hitboxStarts[a]);
            }
            if (arrowBase < faceTotal) {
                int arrowEnd = Math.min(b, boxCount - 1);
                int arrowStart = Math.min(a, boxCount - 1);
                if (arrowEnd > arrowStart) {
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, arrowBase + arrowStart * 60, (arrowEnd - arrowStart) * 60);
                }
            }
        }
        endArrays(faceVbo);
    }

    public void drawLines(int[] runs) {
        if (lineVbo == null) return;
        beginArrays(lineVbo);
        boolean hasSubtick = lineMainTotal < lineTotal;
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            GL11.glDrawArrays(GL11.GL_LINES, PathVertexLayout.lineMainOffset(a), (b - a) * PathVertexLayout.LINE_VERTS_PER_BOX);
            if (hasSubtick) {
                GL11.glDrawArrays(GL11.GL_LINES, lineMainTotal + subtickStarts[a], subtickStarts[b] - subtickStarts[a]);
            }
        }
        endArrays(lineVbo);
    }

    public double anchorX() { return anchorX; }
    public double anchorY() { return anchorY; }
    public double anchorZ() { return anchorZ; }

    private static void beginArrays(VertexBuffer vbo) {
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        vbo.bindBuffer();
        GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE, 0L);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, STRIDE, 12L);
    }

    private static void endArrays(VertexBuffer vbo) {
        vbo.unbindBuffer();
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private void release() {
        if (faceVbo != null) {
            faceVbo.deleteGlBuffers();
            faceVbo = null;
        }
        if (lineVbo != null) {
            lineVbo.deleteGlBuffers();
            lineVbo = null;
        }
    }

    public void close() {
        release();
        built = false;
        lastGeometryRev = -1;
    }
}
