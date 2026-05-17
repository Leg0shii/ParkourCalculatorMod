package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.TickState;

public final class BoxStyle {

    /** Edge length of each tick box, in blocks. */
    public static final double BOX_SIZE = 0.1;

    /**
     * Wireframe line thickness in pixels. The Forge loaders pass this to
     * GL11.glLineWidth before the LINES pass; Fabric draws lines through a
     * DrawMode.DEBUG_LINES pipeline that's always 1px regardless of this
     * value. So this is effectively the Forge knob; drivers that ignore
     * glLineWidth > 1.0F (most desktop GPUs) will render Fabric and Forge
     * at the same 1px width.
     */
    public static final float LINE_WIDTH = 3.0F;

    /** Packs four normalized [0,1] color components into an ARGB int. */
    public static int toArgb(float r, float g, float b, float a) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int toArgb(float[] rgba) {
        return toArgb(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /** Fill color for a tick box: user-controlled alpha for translucency. */
    public static int tickFaceArgb(Settings settings, TickState state, boolean selected) {
        return toArgb(pickChannel(settings, state, selected));
    }

    /** Outline color for a tick box: full alpha so the wireframe always reads. */
    public static int tickLineArgb(Settings settings, TickState state, boolean selected) {
        float[] c = pickChannel(settings, state, selected);
        return toArgb(c[0], c[1], c[2], 1.0f);
    }

    /** selected > wall > in-air > sneak > default. */
    private static float[] pickChannel(Settings settings, TickState state, boolean selected) {
        if (selected) return settings.tickSelected;
        if (state.wallCollision) return settings.tickWall;
        if (!state.onGround) return settings.tickAir;
        if (state.sneaking) return settings.tickSneak;
        return settings.tickDefault;
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    private BoxStyle() {
    }
}
