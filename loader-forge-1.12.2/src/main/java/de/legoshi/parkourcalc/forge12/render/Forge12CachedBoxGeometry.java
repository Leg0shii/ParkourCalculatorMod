package de.legoshi.parkourcalc.forge12.render;

import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
import de.legoshi.parkourcalc.core.render.SelectionPatchSpec;
import de.legoshi.parkourcalc.core.render.TailPatchGate;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.forge.core.render.CountingBoxRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Persistent VBOs for the cached path geometry on MC 1.12.2 (anchor-relative; camera via glTranslate). */
@SuppressWarnings("DuplicatedCode")
public final class Forge12CachedBoxGeometry {

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
    private int arrowsPerBox;
    private boolean useSubtick;
    private int[] hitboxStarts = new int[]{0};
    private int[] subtickStarts = new int[]{0};
    private int hitboxBase;
    private int arrowBase;
    private int faceTotal;
    private int lineMainTotal;
    private int lineTotal;
    private int constraintFaceVerts;
    private int constraintLineVerts;
    private int reachLineVerts;
    private int lastBakeVertices;
    private Set<Integer> bakedSelection = new HashSet<Integer>();

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
            if (dirty > 0) {
                patchTail(boxController, Math.max(0, dirty - 1), plan.patch);
                patchConstraintRegion(plan);
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
        boxController.takeDirtyFrom(rev);
        Perf.stop("geomRebuild", buildStart);
        bakedSelection = new HashSet<Integer>(plan.selection);
        lastGeometryRev = rev;
        lastStructuralHash = plan.structuralHash;
        built = true;
    }

    private void patchConstraintRegion(PathRenderPlan plan) {
        if (constraintFaceVerts > 0) {
            writeVerts(faceVbo, faceTotal - constraintFaceVerts, GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES,
                    constraintFaceVerts, plan.constraintFaceEmitter);
        }
        if (constraintLineVerts > 0) {
            writeVerts(lineVbo, lineTotal - constraintLineVerts, GL11.GL_LINES, BoxRenderer.Mode.LINES,
                    constraintLineVerts, plan.constraintLineEmitter);
        }
    }

    private void patchTail(final BoxController boxController, final int from, final SelectionPatchSpec patch) {
        final int n = boxCount;
        writeVerts(faceVbo, PathVertexLayout.faceMainOffset(from), GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES,
                (n - from) * PathVertexLayout.FACE_VERTS_PER_BOX,
                r -> boxController.render(r, patch.facePicker, from, n)
        );
        if (hitboxEdges != 0) {
            final boolean full = patch.showFullHitbox;
            writeVerts(faceVbo, hitboxBase + hitboxStarts[from], GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES,
                    hitboxStarts[n] - hitboxStarts[from],
                    r -> {
                        if (full) {
                            boxController.renderHitboxFullWireframe(r, patch.hitboxPicker, useSubtick, from, n);
                        } else {
                            boxController.renderHitboxFloorOutline(r, patch.hitboxPicker, useSubtick, from, n);
                        }
                    }
            );
        }
        if (arrowsPerBox > 0 && from < n - 1) {
            int stride = arrowsPerBox * PathVertexLayout.ARROW_VERTS_PER_BOX;
            writeVerts(faceVbo, arrowBase + from * stride, GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES,
                    (n - 1 - from) * stride,
                    r -> boxController.renderFacingArrows(r, patch.drawYawArrows, patch.drawCombinedArrows,
                            patch.yawArrowArgb, patch.combinedArrowArgb, from, n - 1)
            );
        }
        writeVerts(lineVbo, PathVertexLayout.lineMainOffset(from), GL11.GL_LINES, BoxRenderer.Mode.LINES,
                (n - from) * PathVertexLayout.LINE_VERTS_PER_BOX,
                r -> boxController.render(r, patch.linePicker, from, n)
        );
    }

    private void rebuild(BoxController boxController, Consumer<BoxRenderer> faceEmitter, Consumer<BoxRenderer> lineEmitter, SelectionPatchSpec patch) {
        Vec3dCore first = boxController.getPosition(0);
        anchorX = first.x;
        anchorY = first.y;
        anchorZ = first.z;

        release();
        boxCount = boxController.size();
        useSubtick = patch.showSubtick;
        hitboxEdges = patch.hitboxEdges();
        arrowsPerBox = patch.arrowsPerBox;
        hitboxStarts = PathVertexLayout.hitboxVertexStarts(boxController, hitboxEdges, useSubtick);

        faceVbo = bake(GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES, faceEmitter);
        faceTotal = lastBakeVertices;
        lineVbo = bake(GL11.GL_LINES, BoxRenderer.Mode.LINES, lineEmitter);
        lineTotal = lastBakeVertices;

        hitboxBase = PathVertexLayout.hitboxRegionBase(boxCount);
        arrowBase = hitboxBase + hitboxStarts[boxCount];
        lineMainTotal = boxCount * PathVertexLayout.LINE_VERTS_PER_BOX;
        subtickStarts = useSubtick ? boxController.subtickVertexStarts() : new int[]{0};
    }

    private VertexBuffer bake(int glMode, BoxRenderer.Mode mode, Consumer<BoxRenderer> emitter) {
        // BufferBuilder doesn't grow during pos()/endVertex(), so count first and pre-size exactly.
        CountingBoxRenderer counter = new CountingBoxRenderer(mode);
        emitter.accept(counter);
        long vertices = counter.vertexCount();
        lastBakeVertices = (int) vertices;
        if (vertices == 0) return null;

        // A private builder, not the shared Tessellator, so a huge bake doesn't permanently inflate it.
        BufferBuilder builder = new BufferBuilder((int) (vertices * INTS_PER_VERTEX) + INTS_PER_VERTEX);
        builder.begin(glMode, DefaultVertexFormats.POSITION_COLOR);
        emitter.accept(new Forge12BoxRenderer(builder, anchorX, anchorY, anchorZ, mode));
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
                new Consumer<BoxRenderer>() {
                    public void accept(BoxRenderer r) { r.drawBox(boxController.getTickAabb(i), faceArgb); }
                });

        if (hitboxEdges != 0) {
            int offset = PathVertexLayout.hitboxRegionBase(boxCount) + hitboxStarts[i];
            int count = hitboxStarts[i + 1] - hitboxStarts[i];
            final int hitboxArgb = patch.hitboxPicker.argbFor(i, state);
            final boolean full = patch.showFullHitbox;
            writeVerts(faceVbo, offset, GL11.GL_TRIANGLES, BoxRenderer.Mode.FACES, count,
                    new Consumer<BoxRenderer>() {
                        public void accept(BoxRenderer r) {
                            if (full) {
                                boxController.emitHitboxFullWireframeAt(r, hitboxArgb, useSubtick, i);
                            } else {
                                boxController.emitHitboxFloorOutlineAt(r, hitboxArgb, useSubtick, i);
                            }
                        }
                    });
        }

        final int lineArgb = patch.linePicker.argbFor(i, state);
        writeVerts(lineVbo, PathVertexLayout.lineMainOffset(i), GL11.GL_LINES, BoxRenderer.Mode.LINES,
                PathVertexLayout.LINE_VERTS_PER_BOX,
                new Consumer<BoxRenderer>() {
                    public void accept(BoxRenderer r) { r.drawBox(boxController.getTickAabb(i), lineArgb); }
                });
    }

    private void writeVerts(VertexBuffer vbo, int globalVertexOffset, int glMode, BoxRenderer.Mode mode,
                            int vertexCount, Consumer<BoxRenderer> emit) {
        if (vbo == null || vertexCount == 0) return;
        BufferBuilder builder = new BufferBuilder(vertexCount * INTS_PER_VERTEX + INTS_PER_VERTEX);
        builder.begin(glMode, DefaultVertexFormats.POSITION_COLOR);
        emit.accept(new Forge12BoxRenderer(builder, anchorX, anchorY, anchorZ, mode));
        builder.finishDrawing();
        ByteBuffer bytes = builder.getByteBuffer();
        vbo.bindBuffer();
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) globalVertexOffset * STRIDE, bytes);
        vbo.unbindBuffer();
        builder.reset();
    }

    public void drawFaces(int[] runs) {
        if (faceVbo == null) return;
        int constraintFaceBase = faceTotal - constraintFaceVerts;
        beginArrays(faceVbo);
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            GL11.glDrawArrays(GL11.GL_TRIANGLES, PathVertexLayout.faceMainOffset(a),
                    (b - a) * PathVertexLayout.FACE_VERTS_PER_BOX);
            if (hitboxEdges != 0) {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, hitboxBase + hitboxStarts[a],
                        hitboxStarts[b] - hitboxStarts[a]);
            }
            if (arrowsPerBox > 0 && arrowBase < constraintFaceBase) {
                int arrowStride = arrowsPerBox * PathVertexLayout.ARROW_VERTS_PER_BOX;
                int arrowEnd = Math.min(b, boxCount - 1);
                int arrowStart = Math.min(a, boxCount - 1);
                if (arrowEnd > arrowStart) {
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, arrowBase + arrowStart * arrowStride, (arrowEnd - arrowStart) * arrowStride);
                }
            }
        }
        if (constraintFaceVerts > 0) {
            GlStateManager.depthMask(false);
            GlStateManager.enableCull();
            GL11.glDrawArrays(GL11.GL_TRIANGLES, constraintFaceBase, constraintFaceVerts);
            GlStateManager.disableCull();
            GlStateManager.depthMask(true);
        }
        endArrays(faceVbo);
    }

    public void drawLines(int[] runs) {
        if (lineVbo == null) return;
        int trailingLineVerts = constraintLineVerts + reachLineVerts;
        int trailingLineBase = lineTotal - trailingLineVerts;
        boolean hasSubtick = lineMainTotal < trailingLineBase;
        beginArrays(lineVbo);
        for (int k = 0; k + 1 < runs.length; k += 2) {
            int a = runs[k];
            int b = runs[k + 1];
            GL11.glDrawArrays(GL11.GL_LINES, PathVertexLayout.lineMainOffset(a),
                    (b - a) * PathVertexLayout.LINE_VERTS_PER_BOX);
            if (hasSubtick) {
                GL11.glDrawArrays(GL11.GL_LINES, lineMainTotal + subtickStarts[a],
                        subtickStarts[b] - subtickStarts[a]);
            }
        }
        if (trailingLineVerts > 0) {
            GL11.glDrawArrays(GL11.GL_LINES, trailingLineBase, trailingLineVerts);
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
