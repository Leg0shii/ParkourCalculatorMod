package de.legoshi.parkourcalc.core.ui;

public final class Settings {

    public static final float[] PRESET_SCALES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f};
    public static final int DEFAULT_SCALE_INDEX = 3;

    private static final float[] DEFAULT_TICK_DEFAULT = {0.70f, 0.70f, 0.70f, 0.25f};
    private static final float[] DEFAULT_TICK_SELECTED = {0.20f, 0.55f, 1.00f, 0.25f};
    private static final float[] DEFAULT_TICK_AIR = {0.40f, 0.80f, 1.00f, 0.25f};
    private static final float[] DEFAULT_TICK_SNEAK = {1.00f, 0.60f, 0.20f, 0.25f};
    private static final float[] DEFAULT_TICK_WALL = {1.00f, 0.25f, 0.25f, 0.25f};
    private static final float[] DEFAULT_SUBTICK_PATH = {1.00f, 1.00f, 0.40f, 0.25f};
    private static final float[] DEFAULT_YAW_ARROW = {1.00f, 0.30f, 0.30f, 1.00f};
    private static final float[] DEFAULT_YAW_GIZMO_CIRCLE = {1.00f, 1.00f, 1.00f, 0.70f};
    private static final float[] DEFAULT_YAW_GIZMO_DIRECTION = {1.00f, 1.00f, 0.00f, 1.00f};
    private static final float[] DEFAULT_HITBOX_DEFAULT = {1.00f, 1.00f, 1.00f, 0.80f};
    private static final float[] DEFAULT_HITBOX_SELECTED = {0.30f, 1.00f, 0.30f, 0.80f};

    private static final boolean DEFAULT_SHOW_YAW_ARROWS = true;
    private static final boolean DEFAULT_SHOW_HITBOX = false;
    private static final boolean DEFAULT_SHOW_FULL_HITBOX = false;
    private static final boolean DEFAULT_SHOW_SUBTICK = true;

    public final float[] tickDefault = DEFAULT_TICK_DEFAULT.clone();
    public final float[] tickSelected = DEFAULT_TICK_SELECTED.clone();
    public final float[] tickAir = DEFAULT_TICK_AIR.clone();
    public final float[] tickSneak = DEFAULT_TICK_SNEAK.clone();
    public final float[] tickWall = DEFAULT_TICK_WALL.clone();
    public final float[] subtickPath = DEFAULT_SUBTICK_PATH.clone();
    public final float[] yawArrow = DEFAULT_YAW_ARROW.clone();
    public final float[] yawGizmoCircle = DEFAULT_YAW_GIZMO_CIRCLE.clone();
    public final float[] yawGizmoDirection = DEFAULT_YAW_GIZMO_DIRECTION.clone();
    public final float[] hitboxDefault = DEFAULT_HITBOX_DEFAULT.clone();
    public final float[] hitboxSelected = DEFAULT_HITBOX_SELECTED.clone();

    public boolean showYawArrows = DEFAULT_SHOW_YAW_ARROWS;
    public boolean showHitbox = DEFAULT_SHOW_HITBOX;
    public boolean showFullHitbox = DEFAULT_SHOW_FULL_HITBOX;
    public boolean showSubtick = DEFAULT_SHOW_SUBTICK;

    public int scaleIndex = DEFAULT_SCALE_INDEX;

    public String[] pinnedOverlays = new String[0];

    public void reset() {
        System.arraycopy(DEFAULT_TICK_DEFAULT, 0, tickDefault, 0, 4);
        System.arraycopy(DEFAULT_TICK_SELECTED, 0, tickSelected, 0, 4);
        System.arraycopy(DEFAULT_TICK_AIR, 0, tickAir, 0, 4);
        System.arraycopy(DEFAULT_TICK_SNEAK, 0, tickSneak, 0, 4);
        System.arraycopy(DEFAULT_TICK_WALL, 0, tickWall, 0, 4);
        System.arraycopy(DEFAULT_SUBTICK_PATH, 0, subtickPath, 0, 4);
        System.arraycopy(DEFAULT_YAW_ARROW, 0, yawArrow, 0, 4);
        System.arraycopy(DEFAULT_YAW_GIZMO_CIRCLE, 0, yawGizmoCircle, 0, 4);
        System.arraycopy(DEFAULT_YAW_GIZMO_DIRECTION, 0, yawGizmoDirection, 0, 4);
        System.arraycopy(DEFAULT_HITBOX_DEFAULT, 0, hitboxDefault, 0, 4);
        System.arraycopy(DEFAULT_HITBOX_SELECTED, 0, hitboxSelected, 0, 4);
        showYawArrows = DEFAULT_SHOW_YAW_ARROWS;
        showHitbox = DEFAULT_SHOW_HITBOX;
        showFullHitbox = DEFAULT_SHOW_FULL_HITBOX;
        showSubtick = DEFAULT_SHOW_SUBTICK;
        scaleIndex = DEFAULT_SCALE_INDEX;
        pinnedOverlays = new String[0];
    }
}
