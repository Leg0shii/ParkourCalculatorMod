package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.BoxGeometry;
import de.legoshi.parkourcalc.core.sim.AABB;
import net.minecraft.client.renderer.WorldRenderer;

/** Thin lambda shim that adapts BoxGeometry vertices to MC 1.8.9's WorldRenderer buffer. */
public final class Forge8BoxRenderer implements BoxRenderer {

    private final BoxGeometry.VertexEmitter emitter;
    private final double camX;
    private final double camY;
    private final double camZ;
    private final Mode mode;

    public Forge8BoxRenderer(WorldRenderer buf, double camX, double camY, double camZ, Mode mode) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.mode = mode;
        this.emitter = (x, y, z, argb) -> {
            float a = ((argb >>> 24) & 0xFF) / 255.0f;
            float r = ((argb >>> 16) & 0xFF) / 255.0f;
            float g = ((argb >>> 8) & 0xFF) / 255.0f;
            float b = (argb & 0xFF) / 255.0f;
            buf.pos(x, y, z).color(r, g, b, a).endVertex();
        };
    }

    @Override
    public void drawBox(AABB box, int argb) {
        if (mode == Mode.LINES) {
            BoxGeometry.emitEdges(box, camX, camY, camZ, argb, emitter);
        } else {
            BoxGeometry.emitFaces(box, camX, camY, camZ, argb, emitter);
        }
    }
}
