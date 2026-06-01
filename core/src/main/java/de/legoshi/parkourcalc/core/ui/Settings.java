package de.legoshi.parkourcalc.core.ui;

public final class Settings {

    public static final float[] PRESET_SCALES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f};
    public static final int DEFAULT_SCALE_INDEX = 3;
    public static final int AUTO_SCALE_INDEX = -1;

    // First-run default: bigger displays start at a larger preset so 4K isn't a sliver.
    public static int resolveAutoScaleIndex(int displayHeightPx) {
        if (displayHeightPx < 1000) return 1; // 1.0x
        if (displayHeightPx < 1600) return 3; // 1.5x
        if (displayHeightPx < 2300) return 4; // 2.0x
        return 5;                             // 2.5x
    }

    private static final float[] DEFAULT_TICK_DEFAULT = {0.949f, 0.957f, 1.000f, 0.50f};        // near-white #f2f4ff
    private static final float[] DEFAULT_TICK_SELECTED = {0.145f, 0.388f, 0.922f, 0.50f};       // dark blue  #2563eb
    private static final float[] DEFAULT_TICK_AIR = {0.184f, 0.831f, 0.941f, 0.50f};            // sky        #2fd4f0
    private static final float[] DEFAULT_TICK_SNEAK = {1.000f, 0.604f, 0.239f, 0.50f};          // orange     #ff9a3d
    private static final float[] DEFAULT_TICK_WALL = {1.000f, 0.231f, 0.361f, 0.50f};           // red        #ff3b5c
    private static final float[] DEFAULT_TICK_SOFT_COLLISION = {1.000f, 0.435f, 0.529f, 0.50f}; // rose       #ff6f87
    private static final float[] DEFAULT_SUBTICK_PATH = {0.976f, 0.886f, 0.686f, 0.80f};        // yellow     #f9e2af
    private static final float[] DEFAULT_YAW_ARROW = {0.812f, 0.478f, 0.239f, 1.00f};           // burnt orange #cf7a3d
    private static final float[] DEFAULT_YAW_GIZMO_CIRCLE = {0.804f, 0.839f, 0.957f, 0.70f};    // text       #cdd6f4
    private static final float[] DEFAULT_YAW_GIZMO_DIRECTION = {0.976f, 0.886f, 0.686f, 1.00f}; // yellow     #f9e2af
    private static final float[] DEFAULT_HITBOX_DEFAULT = {0.804f, 0.839f, 0.957f, 0.80f};      // text       #cdd6f4
    private static final float[] DEFAULT_HITBOX_SELECTED = {0.651f, 0.890f, 0.631f, 0.80f};     // green      #a6e3a1
    private static final float[] DEFAULT_TICK_GROUND_HIGHLIGHT = {0.541f, 0.576f, 0.941f, 0.22f}; // periwinkle #8a93f0

    private static final boolean DEFAULT_SHOW_YAW_ARROWS = true;
    private static final boolean DEFAULT_SHOW_HITBOX = false;
    private static final boolean DEFAULT_SHOW_FULL_HITBOX = false;
    private static final boolean DEFAULT_SHOW_SUBTICK = false;
    private static final boolean DEFAULT_SHOW_POTION_COLUMNS = false;
    private static final boolean DEFAULT_HIGHLIGHT_ON_GROUND_ROWS = false;

    private static final boolean DEFAULT_VIEW_TICK_INFO = true;
    private static final boolean DEFAULT_VIEW_PERF_INFO = false;

    private static final float DEFAULT_YAW_FLICK_SPEED = 720.0f;
    public static final float MIN_YAW_FLICK_SPEED = 30.0f;
    public static final float MAX_YAW_FLICK_SPEED = 7200.0f;

    private static final int DEFAULT_PATH_RENDER_DISTANCE = 128;
    public static final int MIN_PATH_RENDER_DISTANCE = 16;
    public static final int MAX_PATH_RENDER_DISTANCE = 512;
    private static final boolean DEFAULT_UNLIMITED_PATH_RENDER = false;

    public final float[] tickDefault = DEFAULT_TICK_DEFAULT.clone();
    public final float[] tickSelected = DEFAULT_TICK_SELECTED.clone();
    public final float[] tickAir = DEFAULT_TICK_AIR.clone();
    public final float[] tickSneak = DEFAULT_TICK_SNEAK.clone();
    public final float[] tickWall = DEFAULT_TICK_WALL.clone();
    public final float[] tickSoftCollision = DEFAULT_TICK_SOFT_COLLISION.clone();
    public final float[] subtickPath = DEFAULT_SUBTICK_PATH.clone();
    public final float[] yawArrow = DEFAULT_YAW_ARROW.clone();
    public final float[] yawGizmoCircle = DEFAULT_YAW_GIZMO_CIRCLE.clone();
    public final float[] yawGizmoDirection = DEFAULT_YAW_GIZMO_DIRECTION.clone();
    public final float[] hitboxDefault = DEFAULT_HITBOX_DEFAULT.clone();
    public final float[] hitboxSelected = DEFAULT_HITBOX_SELECTED.clone();
    public final float[] tickGroundHighlight = DEFAULT_TICK_GROUND_HIGHLIGHT.clone();

    public boolean showYawArrows = DEFAULT_SHOW_YAW_ARROWS;
    public boolean showHitbox = DEFAULT_SHOW_HITBOX;
    public boolean showFullHitbox = DEFAULT_SHOW_FULL_HITBOX;
    public boolean showSubtick = DEFAULT_SHOW_SUBTICK;
    public boolean showPotionColumns = DEFAULT_SHOW_POTION_COLUMNS;
    public boolean highlightOnGroundRows = DEFAULT_HIGHLIGHT_ON_GROUND_ROWS;

    public float yawFlickSpeed = DEFAULT_YAW_FLICK_SPEED;

    public int pathRenderDistance = DEFAULT_PATH_RENDER_DISTANCE;
    public boolean unlimitedPathRender = DEFAULT_UNLIMITED_PATH_RENDER;

    public int scaleIndex = AUTO_SCALE_INDEX; // resolved from display on first run; concrete once chosen

    public String[] recentFiles = new String[0];
    public boolean viewTickInfo = DEFAULT_VIEW_TICK_INFO;
    public boolean viewPerf = DEFAULT_VIEW_PERF_INFO;

    public void reset() {
        System.arraycopy(DEFAULT_TICK_DEFAULT, 0, tickDefault, 0, 4);
        System.arraycopy(DEFAULT_TICK_SELECTED, 0, tickSelected, 0, 4);
        System.arraycopy(DEFAULT_TICK_AIR, 0, tickAir, 0, 4);
        System.arraycopy(DEFAULT_TICK_SNEAK, 0, tickSneak, 0, 4);
        System.arraycopy(DEFAULT_TICK_WALL, 0, tickWall, 0, 4);
        System.arraycopy(DEFAULT_TICK_SOFT_COLLISION, 0, tickSoftCollision, 0, 4);
        System.arraycopy(DEFAULT_SUBTICK_PATH, 0, subtickPath, 0, 4);
        System.arraycopy(DEFAULT_YAW_ARROW, 0, yawArrow, 0, 4);
        System.arraycopy(DEFAULT_YAW_GIZMO_CIRCLE, 0, yawGizmoCircle, 0, 4);
        System.arraycopy(DEFAULT_YAW_GIZMO_DIRECTION, 0, yawGizmoDirection, 0, 4);
        System.arraycopy(DEFAULT_HITBOX_DEFAULT, 0, hitboxDefault, 0, 4);
        System.arraycopy(DEFAULT_HITBOX_SELECTED, 0, hitboxSelected, 0, 4);
        System.arraycopy(DEFAULT_TICK_GROUND_HIGHLIGHT, 0, tickGroundHighlight, 0, 4);
        showYawArrows = DEFAULT_SHOW_YAW_ARROWS;
        showHitbox = DEFAULT_SHOW_HITBOX;
        showFullHitbox = DEFAULT_SHOW_FULL_HITBOX;
        showSubtick = DEFAULT_SHOW_SUBTICK;
        showPotionColumns = DEFAULT_SHOW_POTION_COLUMNS;
        highlightOnGroundRows = DEFAULT_HIGHLIGHT_ON_GROUND_ROWS;
        yawFlickSpeed = DEFAULT_YAW_FLICK_SPEED;
        pathRenderDistance = DEFAULT_PATH_RENDER_DISTANCE;
        unlimitedPathRender = DEFAULT_UNLIMITED_PATH_RENDER;
        scaleIndex = DEFAULT_SCALE_INDEX;
        recentFiles = new String[0];
        viewTickInfo = DEFAULT_VIEW_TICK_INFO;
        viewPerf = DEFAULT_VIEW_PERF_INFO;
    }
}
