package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.multireplay.MultiReplay;
import de.legoshi.parkourcalc.core.multireplay.MultiReplayGeometry;
import de.legoshi.parkourcalc.core.multireplay.ReplayTrack;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class Forge8MultiReplayMesh {

    private static final int STRIDE = 16;
    private static final int INTS_PER_VERTEX = STRIDE / 4;
    private static final int VERTS = MultiReplayGeometry.VERTS_PER_SPHERE;

    private VertexBuffer fullVbo;
    private VertexBuffer dimVbo;
    private long lastRev = -1;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private int[] trackBase = new int[0];
    private int[] trackSize = new int[0];
    private WorldRenderer scratch;
    private int scratchInts;

    public void ensureBuilt(MultiReplay state) {
        if (state.geometryRev() == lastRev) return;
        release();
        lastRev = state.geometryRev();
        List<ReplayTrack> tracks = state.tracks();
        trackBase = new int[tracks.size()];
        trackSize = new int[tracks.size()];
        int total = 0;
        Vec3dCore anchor = null;
        for (int i = 0; i < tracks.size(); i++) {
            ReplayTrack t = tracks.get(i);
            trackBase[i] = total * VERTS;
            trackSize[i] = t.size();
            total += t.size();
            if (anchor == null && t.size() > 0) anchor = t.position(0);
        }
        if (total == 0 || anchor == null) return;
        anchorX = anchor.x;
        anchorY = anchor.y;
        anchorZ = anchor.z;
        fullVbo = bake(state, false, total * VERTS);
        dimVbo = bake(state, true, total * VERTS);
    }

    private VertexBuffer bake(MultiReplay state, boolean upcomingStyle, int vertexCount) {
        WorldRenderer builder = scratch(vertexCount);
        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        MultiReplayGeometry.emitAllSpheres(state,
                new Forge8BoxRenderer(builder, anchorX, anchorY, anchorZ, BoxRenderer.Mode.FACES), upcomingStyle);
        builder.finishDrawing();
        VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.POSITION_COLOR);
        vbo.bufferData(builder.getByteBuffer());
        builder.reset();
        return vbo;
    }

    private WorldRenderer scratch(int vertexCount) {
        int ints = vertexCount * INTS_PER_VERTEX + INTS_PER_VERTEX;
        if (scratch == null || ints > scratchInts) {
            scratch = new WorldRenderer(ints);
            scratchInts = ints;
        }
        return scratch;
    }

    public void draw(MultiReplay state, double camX, double camY, double camZ) {
        if (fullVbo == null) return;
        List<ReplayTrack> tracks = state.tracks();
        if (tracks.size() != trackBase.length) return;
        int tick = state.clock().tick();
        GlStateManager.pushMatrix();
        GlStateManager.translate(anchorX - camX, anchorY - camY, anchorZ - camZ);
        beginArrays(fullVbo);
        for (int i = 0; i < tracks.size(); i++) {
            if (!tracks.get(i).isVisible() || trackSize[i] == 0) continue;
            int reached = Math.min(tick, trackSize[i] - 1);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, trackBase[i], (reached + 1) * VERTS);
        }
        endArrays(fullVbo);
        if (state.isShowUpcoming() && dimVbo != null) {
            beginArrays(dimVbo);
            for (int i = 0; i < tracks.size(); i++) {
                if (!tracks.get(i).isVisible() || trackSize[i] == 0) continue;
                int from = Math.min(tick, trackSize[i] - 1) + 1;
                if (from >= trackSize[i]) continue;
                GL11.glDrawArrays(GL11.GL_TRIANGLES, trackBase[i] + from * VERTS, (trackSize[i] - from) * VERTS);
            }
            endArrays(dimVbo);
        }
        GlStateManager.popMatrix();
    }

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
        if (fullVbo != null) {
            fullVbo.deleteGlBuffers();
            fullVbo = null;
        }
        if (dimVbo != null) {
            dimVbo.deleteGlBuffers();
            dimVbo = null;
        }
    }

    public void close() {
        release();
        scratch = null;
        scratchInts = 0;
        lastRev = -1;
    }
}
