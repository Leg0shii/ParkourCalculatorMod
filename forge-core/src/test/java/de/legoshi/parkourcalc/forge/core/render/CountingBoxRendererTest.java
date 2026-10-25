package de.legoshi.parkourcalc.forge.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CountingBoxRendererTest {

    private static final AABB BOX = new AABB(Vec3dCore.ZERO, new Vec3dCore(1, 1, 1));

    private static void drawEverything(BoxRenderer r) {
        r.drawBox(BOX, 0xFFFFFFFF);
        r.drawLine(0, 0, 0, 1, 1, 1, 0xFFFFFFFF);
        r.drawTriangle(0, 0, 0, 1, 0, 0, 0, 1, 0, 0xFFFFFFFF);
    }

    @Test
    public void theLinePassCountsBoxEdgesAndLines() {
        CountingBoxRenderer r = new CountingBoxRenderer(BoxRenderer.Mode.LINES);
        drawEverything(r);
        assertEquals(24 + 2, r.vertexCount());
    }

    @Test
    public void theFacePassCountsBoxFacesAndTriangles() {
        CountingBoxRenderer r = new CountingBoxRenderer(BoxRenderer.Mode.FACES);
        drawEverything(r);
        assertEquals(36 + 3, r.vertexCount());
    }

    @Test
    public void theCountAccumulatesOverRepeatedDraws() {
        CountingBoxRenderer r = new CountingBoxRenderer(BoxRenderer.Mode.LINES);
        for (int i = 0; i < 5; i++) r.drawBox(BOX, 0);
        assertEquals(5 * 24, r.vertexCount());
    }
}
