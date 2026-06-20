package de.legoshi.parkourcalc.core.ui;

public final class Settings {

    public static final float[] PRESET_SCALES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f};
    public static final int DEFAULT_SCALE_INDEX = 3;
    public static final int AUTO_SCALE_INDEX = -1;

    private static final float[] DEFAULT_TICK_DEFAULT = {0.949f, 0.957f, 1.000f, 0.50f};        // near-white #f2f4ff
    private static final float[] DEFAULT_TICK_SELECTED = {0.145f, 0.388f, 0.922f, 0.50f};       // dark blue  #2563eb
    private static final float[] DEFAULT_TICK_AIR = {0.184f, 0.831f, 0.941f, 0.50f};            // sky        #2fd4f0
    private static final float[] DEFAULT_TICK_SNEAK = {1.000f, 0.604f, 0.239f, 0.50f};          // orange     #ff9a3d
    private static final float[] DEFAULT_TICK_WALL = {1.000f, 0.231f, 0.361f, 0.50f};           // red        #ff3b5c
    private static final float[] DEFAULT_TICK_SOFT_COLLISION = {1.000f, 0.435f, 0.529f, 0.50f}; // rose       #ff6f87
    private static final float[] DEFAULT_SUBTICK_PATH = {0.976f, 0.886f, 0.686f, 0.80f};        // yellow     #f9e2af
    private static final float[] DEFAULT_YAW_ARROW = {0.831f, 0.929f, 0.976f, 0.529f};           // #D4EDF987
    private static final float[] DEFAULT_YAW_GIZMO_CIRCLE = {0.804f, 0.839f, 0.957f, 0.70f};    // text       #cdd6f4
    private static final float[] DEFAULT_YAW_GIZMO_DIRECTION = {0.976f, 0.886f, 0.686f, 1.00f}; // yellow     #f9e2af
    private static final float[] DEFAULT_HITBOX_DEFAULT = {0.804f, 0.839f, 0.957f, 0.80f};      // text       #cdd6f4
    private static final float[] DEFAULT_HITBOX_SELECTED = {0.651f, 0.890f, 0.631f, 0.80f};     // green      #a6e3a1
    private static final float[] DEFAULT_TICK_GROUND_HIGHLIGHT = {0.541f, 0.576f, 0.941f, 0.22f}; // periwinkle #8a93f0
    private static final float[] DEFAULT_CONSTRAINT_OUTLINE = {0.322f, 0.000f, 0.298f, 1.000f};   // #52004cff
    private static final float[] DEFAULT_CONSTRAINT_FILL = {0.329f, 0.000f, 0.255f, 0.451f};      // #54004173
    private static final float[] DEFAULT_CONSTRAINT_BACK = {0.627f, 0.008f, 0.627f, 0.118f};
    private static final float[] DEFAULT_CONSTRAINT_HIGHLIGHT = {0.996f, 0.996f, 0.996f, 1.000f};

    private static final boolean DEFAULT_CONSTRAINT_EXPAND_BY_HITBOX = true;
    private static final float DEFAULT_CONSTRAINT_FRONT_WIDTH = 0.6f;
    private static final float DEFAULT_CONSTRAINT_FRONT_HEIGHT = 0.15f;
    private static final float DEFAULT_CONSTRAINT_FRONT_LENGTH = 0.01f;
    private static final float DEFAULT_CONSTRAINT_BACK_WIDTH = 0.6f;
    private static final float DEFAULT_CONSTRAINT_BACK_HEIGHT = 0.0f;
    private static final float DEFAULT_CONSTRAINT_BACK_LENGTH = 1.0f;

    public static final float CONSTRAINT_MIN_DIM = 0.0f;
    public static final float CONSTRAINT_MAX_WIDTH = 3.0f;
    public static final float CONSTRAINT_MAX_HEIGHT = 2.0f;
    public static final float CONSTRAINT_MAX_FRONT_LENGTH = 1.0f;
    public static final float CONSTRAINT_MAX_BACK_LENGTH = 16.0f;

    private static final boolean DEFAULT_SHOW_YAW_ARROWS = true;
    private static final boolean DEFAULT_SHOW_HITBOX = false;
    private static final boolean DEFAULT_SHOW_FULL_HITBOX = false;
    private static final boolean DEFAULT_SHOW_SUBTICK = false;
    private static final boolean DEFAULT_SHOW_COL_SPEED = false;
    private static final boolean DEFAULT_SHOW_COL_JUMP_BOOST = false;
    private static final boolean DEFAULT_SHOW_CONSTRAINTS = true;
    private static final boolean DEFAULT_HIGHLIGHT_ON_GROUND_ROWS = true;

    private static final boolean DEFAULT_SHOW_COL_A = true;
    private static final boolean DEFAULT_SHOW_COL_S = true;
    private static final boolean DEFAULT_SHOW_COL_D = true;
    private static final boolean DEFAULT_SHOW_COL_SPRINT = true;
    private static final boolean DEFAULT_SHOW_COL_SNEAK = true;
    private static final boolean DEFAULT_SHOW_COL_JUMP = true;
    private static final boolean DEFAULT_SHOW_COL_YAW = true;
    private static final boolean DEFAULT_SHOW_COL_PITCH = false;
    private static final boolean DEFAULT_SHOW_COL_LEFT_CLICK = false;
    private static final boolean DEFAULT_SHOW_COL_RIGHT_CLICK = false;

    private static final boolean DEFAULT_VIEW_TICK_INFO = true;
    private static final boolean DEFAULT_VIEW_PERF_INFO = false;
    private static final boolean DEFAULT_VIEW_ANGLE_SOLVER = false;
    private static final boolean DEFAULT_VIEW_VELOCITY_MAP = false;
    private static final boolean DEFAULT_SAVE_DEBUG_VALUES = false;
    private static final boolean DEFAULT_AUTO_APPLY_SOLVE = false;

    private static final boolean DEFAULT_KEEP_INPUT_TABLE_OPEN = false;
    private static final boolean DEFAULT_KEEP_TICK_INFO_OPEN = false;

    private static final boolean DEFAULT_KEEP_BOXES_DURING_PLAYBACK = false;

    private static final float DEFAULT_YAW_FLICK_SPEED = 7200.0f;
    private static final boolean DEFAULT_AUTO_SAVE = true;
    public static final float MIN_YAW_FLICK_SPEED = 30.0f;
    public static final float MAX_YAW_FLICK_SPEED = 7200.0f;

    private static final int DEFAULT_PATH_RENDER_DISTANCE = 128;
    public static final int MIN_PATH_RENDER_DISTANCE = 16;
    public static final int MAX_PATH_RENDER_DISTANCE = 512;
    private static final boolean DEFAULT_UNLIMITED_PATH_RENDER = false;

    private static final int DEFAULT_TICK_INFO_PRECISION = 5;
    public static final int DEFAULT_SOLVER_STATS_PRECISION = 5;
    public static final int MIN_STAT_PRECISION = 1;
    public static final int MAX_STAT_PRECISION = 12;

    // px @1x; ImGui scales by the active UI scale. Thickness = ScrollbarSize, grab = GrabMinSize.
    private static final float DEFAULT_SCROLLBAR_SIZE = 9.0f;
    public static final float MIN_SCROLLBAR_SIZE = 4.0f;
    public static final float MAX_SCROLLBAR_SIZE = 30.0f;
    private static final float DEFAULT_SCROLLBAR_GRAB_MIN_SIZE = 12.0f;
    public static final float MIN_SCROLLBAR_GRAB_MIN_SIZE = 4.0f;
    public static final float MAX_SCROLLBAR_GRAB_MIN_SIZE = 80.0f;

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
    public final float[] constraintOutline = DEFAULT_CONSTRAINT_OUTLINE.clone();
    public final float[] constraintFill = DEFAULT_CONSTRAINT_FILL.clone();
    public final float[] constraintBack = DEFAULT_CONSTRAINT_BACK.clone();
    public final float[] constraintHighlight = DEFAULT_CONSTRAINT_HIGHLIGHT.clone();

    public boolean constraintExpandByHitbox = DEFAULT_CONSTRAINT_EXPAND_BY_HITBOX;
    public float constraintFrontWidth = DEFAULT_CONSTRAINT_FRONT_WIDTH;
    public float constraintFrontHeight = DEFAULT_CONSTRAINT_FRONT_HEIGHT;
    public float constraintFrontLength = DEFAULT_CONSTRAINT_FRONT_LENGTH;
    public float constraintBackWidth = DEFAULT_CONSTRAINT_BACK_WIDTH;
    public float constraintBackHeight = DEFAULT_CONSTRAINT_BACK_HEIGHT;
    public float constraintBackLength = DEFAULT_CONSTRAINT_BACK_LENGTH;

    public boolean showYawArrows = DEFAULT_SHOW_YAW_ARROWS;
    public boolean showHitbox = DEFAULT_SHOW_HITBOX;
    public boolean showFullHitbox = DEFAULT_SHOW_FULL_HITBOX;
    public boolean showSubtick = DEFAULT_SHOW_SUBTICK;
    public boolean showColSpeed = DEFAULT_SHOW_COL_SPEED;
    public boolean showColJumpBoost = DEFAULT_SHOW_COL_JUMP_BOOST;
    public boolean showConstraints = DEFAULT_SHOW_CONSTRAINTS;
    public boolean highlightOnGroundRows = DEFAULT_HIGHLIGHT_ON_GROUND_ROWS;

    public boolean showColA = DEFAULT_SHOW_COL_A;
    public boolean showColS = DEFAULT_SHOW_COL_S;
    public boolean showColD = DEFAULT_SHOW_COL_D;
    public boolean showColSprint = DEFAULT_SHOW_COL_SPRINT;
    public boolean showColSneak = DEFAULT_SHOW_COL_SNEAK;
    public boolean showColJump = DEFAULT_SHOW_COL_JUMP;
    public boolean showColYaw = DEFAULT_SHOW_COL_YAW;
    public boolean showColPitch = DEFAULT_SHOW_COL_PITCH;
    public boolean showColLeftClick = DEFAULT_SHOW_COL_LEFT_CLICK;
    public boolean showColRightClick = DEFAULT_SHOW_COL_RIGHT_CLICK;

    public float yawFlickSpeed = DEFAULT_YAW_FLICK_SPEED;

    public boolean autoSave = DEFAULT_AUTO_SAVE;

    public int pathRenderDistance = DEFAULT_PATH_RENDER_DISTANCE;
    public boolean unlimitedPathRender = DEFAULT_UNLIMITED_PATH_RENDER;

    public float scrollbarSize = DEFAULT_SCROLLBAR_SIZE;
    public float scrollbarGrabMinSize = DEFAULT_SCROLLBAR_GRAB_MIN_SIZE;

    public int tickInfoPrecision = DEFAULT_TICK_INFO_PRECISION;
    public int solverStatsPrecision = DEFAULT_SOLVER_STATS_PRECISION;

    public TickInfoConfig tickInfoStats = TickInfoConfig.defaultConfig(DEFAULT_TICK_INFO_PRECISION);

    public int scaleIndex = AUTO_SCALE_INDEX; // resolved from display on first run; concrete once chosen

    public String[] recentFiles = new String[0];
    public boolean viewTickInfo = DEFAULT_VIEW_TICK_INFO;
    public boolean viewPerf = DEFAULT_VIEW_PERF_INFO;
    public boolean viewAngleSolver = DEFAULT_VIEW_ANGLE_SOLVER;
    public boolean viewVelocityMap = DEFAULT_VIEW_VELOCITY_MAP;

    // When on, each Save also writes the full per-tick SimulatorEntity state to the file (debug only).
    public boolean saveDebugValues = DEFAULT_SAVE_DEBUG_VALUES;

    // When on, a feasible Angle Solver solve is applied automatically the moment it finishes.
    public boolean autoApplySolve = DEFAULT_AUTO_APPLY_SOLVE;

    // Keep these windows drawn (display-only) while the main UI is closed.
    public boolean keepInputTableOpen = DEFAULT_KEEP_INPUT_TABLE_OPEN;
    public boolean keepTickInfoOpen = DEFAULT_KEEP_TICK_INFO_OPEN;

    // Keep the tick-box path overlay drawn in-world during playback.
    public boolean keepBoxesDuringPlayback = DEFAULT_KEEP_BOXES_DURING_PLAYBACK;

    public static int defaultTickInfoPrecision() {
        return DEFAULT_TICK_INFO_PRECISION;
    }

    // First-run default: bigger displays start at a larger preset so 4K isn't a sliver.
    public static int resolveAutoScaleIndex(int displayHeightPx) {
        if (displayHeightPx < 1000) return 1; // 1.0x
        if (displayHeightPx < 1600) return 3; // 1.5x
        if (displayHeightPx < 2300) return 4; // 2.0x
        return 5;                             // 2.5x
    }

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
        System.arraycopy(DEFAULT_CONSTRAINT_OUTLINE, 0, constraintOutline, 0, 4);
        System.arraycopy(DEFAULT_CONSTRAINT_FILL, 0, constraintFill, 0, 4);
        System.arraycopy(DEFAULT_CONSTRAINT_BACK, 0, constraintBack, 0, 4);
        System.arraycopy(DEFAULT_CONSTRAINT_HIGHLIGHT, 0, constraintHighlight, 0, 4);
        constraintExpandByHitbox = DEFAULT_CONSTRAINT_EXPAND_BY_HITBOX;
        constraintFrontWidth = DEFAULT_CONSTRAINT_FRONT_WIDTH;
        constraintFrontHeight = DEFAULT_CONSTRAINT_FRONT_HEIGHT;
        constraintFrontLength = DEFAULT_CONSTRAINT_FRONT_LENGTH;
        constraintBackWidth = DEFAULT_CONSTRAINT_BACK_WIDTH;
        constraintBackHeight = DEFAULT_CONSTRAINT_BACK_HEIGHT;
        constraintBackLength = DEFAULT_CONSTRAINT_BACK_LENGTH;
        showYawArrows = DEFAULT_SHOW_YAW_ARROWS;
        showHitbox = DEFAULT_SHOW_HITBOX;
        showFullHitbox = DEFAULT_SHOW_FULL_HITBOX;
        showSubtick = DEFAULT_SHOW_SUBTICK;
        showColSpeed = DEFAULT_SHOW_COL_SPEED;
        showColJumpBoost = DEFAULT_SHOW_COL_JUMP_BOOST;
        showConstraints = DEFAULT_SHOW_CONSTRAINTS;
        highlightOnGroundRows = DEFAULT_HIGHLIGHT_ON_GROUND_ROWS;
        showColA = DEFAULT_SHOW_COL_A;
        showColS = DEFAULT_SHOW_COL_S;
        showColD = DEFAULT_SHOW_COL_D;
        showColSprint = DEFAULT_SHOW_COL_SPRINT;
        showColSneak = DEFAULT_SHOW_COL_SNEAK;
        showColJump = DEFAULT_SHOW_COL_JUMP;
        showColYaw = DEFAULT_SHOW_COL_YAW;
        showColPitch = DEFAULT_SHOW_COL_PITCH;
        showColLeftClick = DEFAULT_SHOW_COL_LEFT_CLICK;
        showColRightClick = DEFAULT_SHOW_COL_RIGHT_CLICK;
        yawFlickSpeed = DEFAULT_YAW_FLICK_SPEED;
        autoSave = DEFAULT_AUTO_SAVE;
        pathRenderDistance = DEFAULT_PATH_RENDER_DISTANCE;
        unlimitedPathRender = DEFAULT_UNLIMITED_PATH_RENDER;
        scrollbarSize = DEFAULT_SCROLLBAR_SIZE;
        scrollbarGrabMinSize = DEFAULT_SCROLLBAR_GRAB_MIN_SIZE;
        tickInfoPrecision = DEFAULT_TICK_INFO_PRECISION;
        solverStatsPrecision = DEFAULT_SOLVER_STATS_PRECISION;
        tickInfoStats = TickInfoConfig.defaultConfig(DEFAULT_TICK_INFO_PRECISION);
        scaleIndex = DEFAULT_SCALE_INDEX;
        recentFiles = new String[0];
        viewTickInfo = DEFAULT_VIEW_TICK_INFO;
        viewPerf = DEFAULT_VIEW_PERF_INFO;
        viewAngleSolver = DEFAULT_VIEW_ANGLE_SOLVER;
        viewVelocityMap = DEFAULT_VIEW_VELOCITY_MAP;
        saveDebugValues = DEFAULT_SAVE_DEBUG_VALUES;
        autoApplySolve = DEFAULT_AUTO_APPLY_SOLVE;
        keepInputTableOpen = DEFAULT_KEEP_INPUT_TABLE_OPEN;
        keepTickInfoOpen = DEFAULT_KEEP_TICK_INFO_OPEN;
        keepBoxesDuringPlayback = DEFAULT_KEEP_BOXES_DURING_PLAYBACK;
    }
}
