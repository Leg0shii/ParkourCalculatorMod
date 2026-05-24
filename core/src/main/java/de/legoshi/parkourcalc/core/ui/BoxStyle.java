package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public final class BoxStyle {

    /** Edge length of each tick box, in blocks. */
    public static final double BOX_SIZE = 0.1;

    /** Shrunk when the subtick path is visible, so the line dominates. */
    public static final double BOX_SIZE_SUBTICK = 0.05;

    public static double tickBoxSize(Settings settings) {
        return settings.showSubtick ? BOX_SIZE_SUBTICK : BOX_SIZE;
    }

    public static int subtickPathArgb(Settings settings) {
        float[] c = settings.subtickPath;
        return toArgb(c[0], c[1], c[2], c[3]);
    }

    public static int yawArrowArgb(Settings settings) {
        float[] c = settings.yawArrow;
        return toArgb(c[0], c[1], c[2], c[3]);
    }

    public static int yawGizmoCircleArgb(Settings settings) {
        float[] c = settings.yawGizmoCircle;
        return toArgb(c[0], c[1], c[2], c[3]);
    }

    public static int yawGizmoDirectionArgb(Settings settings) {
        float[] c = settings.yawGizmoDirection;
        return toArgb(c[0], c[1], c[2], c[3]);
    }

    public static double pathMaxDistanceSq(Settings settings) {
        if (settings.unlimitedPathRender) return Double.POSITIVE_INFINITY;
        double d = settings.pathRenderDistance;
        return d * d;
    }

    /** Distance-scaled radius so the gizmo stays roughly screen-constant. */
    public static double yawGizmoRadius(double dx, double dy, double dz) {
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double r = dist * 0.10;
        if (r < 0.6) return 0.6;
        if (r > 3.5) return 3.5;
        return r;
    }

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

    public static final double HITBOX_HALF_WIDTH = 0.3;
    public static final double HITBOX_HEIGHT_STANDING = 1.8;
    public static final double HITBOX_HEIGHT_SNEAKING = 1.5;

    /** Hitbox edges render as thin world-space boxes (glLineWidth > 1 is ignored by most GPUs and by Fabric's pipeline). */
    public static final double HITBOX_EDGE_THICKNESS = 0.025;

    /** {@code position} is the entity center-bottom (MC convention). */
    public static AABB hitboxAabbAt(Vec3dCore position, boolean sneaking) {
        double h = sneaking ? HITBOX_HEIGHT_SNEAKING : HITBOX_HEIGHT_STANDING;
        return new AABB(
                new Vec3dCore(position.x - HITBOX_HALF_WIDTH, position.y, position.z - HITBOX_HALF_WIDTH),
                new Vec3dCore(position.x + HITBOX_HALF_WIDTH, position.y + h, position.z + HITBOX_HALF_WIDTH)
        );
    }

    public static int hitboxLineArgb(Settings settings, boolean selected) {
        float[] c = selected ? settings.hitboxSelected : settings.hitboxDefault;
        return toArgb(c[0], c[1], c[2], 1.0f);
    }

    /** selected > soft-collision > wall > in-air > sneak > default. Soft collision is a wall hit
     *  that doesn't break sprint (1.21+), so it must win over the regular wall color. */
    private static float[] pickChannel(Settings settings, TickState state, boolean selected) {
        if (selected) return settings.tickSelected;
        if (state.softCollision) return settings.tickSoftCollision;
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
