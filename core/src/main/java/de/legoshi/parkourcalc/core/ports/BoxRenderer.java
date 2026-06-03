package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.AABB;

/**
 * Per-frame in-world box renderer. The loader implements this against its MC
 * version's render API (Fabric: VertexRendering.drawFilledBox + custom
 * RenderLayers; Forge 1.8.9 / 1.12.2: hand-rolled Tessellator + GL state).
 * Core's BoxController calls drawBox once per stored AABB during the loader's
 * render hook.
 *
 * Color is passed as packed ARGB: high byte = alpha, then R, G, B. 0xFFB2B2B2
 * is the grey we use today (matches the previous BoxInfo default).
 */
public interface BoxRenderer {

    /**
     * Render-pass mode. Loader impls construct one renderer instance per mode
     * and the caller iterates the box list twice (faces then lines) so the
     * wireframe sits on top of the translucent fill.
     */
    enum Mode { LINES, FACES }

    void drawBox(AABB box, int argb);

    /** No-op on FACES-mode impls. */
    void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb);

    /** No-op on LINES-mode impls. Used for oriented geometry that can't be expressed as an AABB. */
    void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb);
}
