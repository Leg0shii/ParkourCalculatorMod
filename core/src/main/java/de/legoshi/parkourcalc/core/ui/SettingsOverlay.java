package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

public final class SettingsOverlay implements RenderInterface {

    private static final String WINDOW_TITLE = "Settings";

    private static final String LABEL_SHOW_YAW_ARROWS = "Show yaw arrows";
    private static final String LABEL_SHOW_HITBOX = "Show Hitbox";
    private static final String LABEL_SHOW_FULL_HITBOX = "Show Full Hitbox";
    private static final String LABEL_SHOW_SUBTICK = "Subtick Visualization";
    private static final String LABEL_SHOW_POTION_COLUMNS = "Show potion effect columns";
    private static final String LABEL_UI_SCALE = "UI Scale";
    private static final String LABEL_RENDER_COLORS = "Render Colors";
    private static final String LABEL_PLAYBACK = "Playback";
    private static final String LABEL_YAW_TURN_CAP = "Max yaw turn rate";
    private static final String ID_YAW_TURN_CAP = "##yaw_turn_cap";
    private static final String YAW_TURN_CAP_FORMAT = "%.0f deg/s";
    private static final String LABEL_PATH_RENDER_DISTANCE = "Path render distance";
    private static final String ID_PATH_RENDER_DISTANCE = "##path_render_distance";
    private static final String PATH_RENDER_DISTANCE_FORMAT = "%d blocks";
    private static final String LABEL_UNLIMITED_PATH_RENDER = "Unlimited path render distance";
    private static final String BTN_RESET = "Reset all";

    private static final String COLOR_TICK_DEFAULT = "tick box default";
    private static final String COLOR_TICK_SELECTED = "tick box selected";
    private static final String COLOR_TICK_AIR = "tick box in-air";
    private static final String COLOR_TICK_SNEAK = "tick box sneak";
    private static final String COLOR_TICK_WALL = "tick box wall";
    private static final String COLOR_TICK_SOFT_COLLISION = "tick box soft collision";
    private static final String COLOR_SUBTICK_PATH = "subtick path";
    private static final String COLOR_YAW_ARROW = "yaw arrows";
    private static final String COLOR_YAW_GIZMO_CIRCLE = "yaw gizmo circle";
    private static final String COLOR_YAW_GIZMO_DIRECTION = "yaw gizmo direction";
    private static final String COLOR_HITBOX_DEFAULT = "hitbox default";
    private static final String COLOR_HITBOX_SELECTED = "hitbox selected";

    private static final String ID_UI_SCALE = "##ui_scale";
    private static final String SCALE_SUFFIX = "x";

    private final Settings settings;
    private final Runnable onChanged;
    private final ImInt scaleIndexBuf = new ImInt();
    private final float[] yawTurnCapBuf = new float[1];
    private final int[] pathRenderDistanceBuf = new int[1];
    private final String[] scaleLabels;

    public SettingsOverlay(Settings settings, Runnable onChanged) {
        this.settings = settings;
        this.onChanged = onChanged;
        this.scaleLabels = buildScaleLabels();
    }

    private static String[] buildScaleLabels() {
        String[] labels = new String[Settings.PRESET_SCALES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Settings.PRESET_SCALES[i] + SCALE_SUFFIX;
        }
        return labels;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!ImGui.begin(WINDOW_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.end();
            return;
        }

        renderToggles();
        ImGui.separator();
        renderScale();
        ImGui.separator();
        renderPlayback();
        ImGui.separator();
        renderColors();
        ImGui.separator();

        if (ImGui.button(BTN_RESET)) {
            settings.reset();
            onChanged.run();
        }

        ImGui.end();
    }

    private void renderToggles() {
        if (ImGui.checkbox(LABEL_SHOW_YAW_ARROWS, settings.showYawArrows)) {
            settings.showYawArrows = !settings.showYawArrows;
            onChanged.run();
        }
        if (ImGui.checkbox(LABEL_SHOW_HITBOX, settings.showHitbox)) {
            settings.showHitbox = !settings.showHitbox;
            onChanged.run();
        }
        if (ImGui.checkbox(LABEL_SHOW_FULL_HITBOX, settings.showFullHitbox)) {
            settings.showFullHitbox = !settings.showFullHitbox;
            onChanged.run();
        }
        if (ImGui.checkbox(LABEL_SHOW_SUBTICK, settings.showSubtick)) {
            settings.showSubtick = !settings.showSubtick;
            onChanged.run();
        }
        if (ImGui.checkbox(LABEL_SHOW_POTION_COLUMNS, settings.showPotionColumns)) {
            settings.showPotionColumns = !settings.showPotionColumns;
            onChanged.run();
        }
    }

    private void renderScale() {
        scaleIndexBuf.set(settings.scaleIndex);
        ImGui.text(LABEL_UI_SCALE);
        ImGui.sameLine();
        if (ImGui.combo(ID_UI_SCALE, scaleIndexBuf, scaleLabels)) {
            settings.scaleIndex = scaleIndexBuf.get();
            onChanged.run();
        }
    }

    private void renderPlayback() {
        ImGui.text(LABEL_PLAYBACK);
        ImGui.text(LABEL_YAW_TURN_CAP);
        ImGui.sameLine();
        yawTurnCapBuf[0] = settings.yawFlickSpeed;
        if (ImGui.sliderFloat(ID_YAW_TURN_CAP,
                yawTurnCapBuf,
                Settings.MIN_YAW_FLICK_SPEED,
                Settings.MAX_YAW_FLICK_SPEED,
                YAW_TURN_CAP_FORMAT)) {
            settings.yawFlickSpeed = yawTurnCapBuf[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            onChanged.run();
        }

        ImGui.text(LABEL_PATH_RENDER_DISTANCE);
        ImGui.sameLine();
        pathRenderDistanceBuf[0] = settings.pathRenderDistance;
        if (ImGui.sliderInt(ID_PATH_RENDER_DISTANCE,
                pathRenderDistanceBuf,
                Settings.MIN_PATH_RENDER_DISTANCE,
                Settings.MAX_PATH_RENDER_DISTANCE,
                PATH_RENDER_DISTANCE_FORMAT)) {
            settings.pathRenderDistance = pathRenderDistanceBuf[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            onChanged.run();
        }
        if (ImGui.checkbox(LABEL_UNLIMITED_PATH_RENDER, settings.unlimitedPathRender)) {
            settings.unlimitedPathRender = !settings.unlimitedPathRender;
            onChanged.run();
        }
    }

    private void renderColors() {
        ImGui.text(LABEL_RENDER_COLORS);
        int flags = ImGuiColorEditFlags.NoInputs;
        renderColor(COLOR_TICK_DEFAULT, settings.tickDefault, flags);
        renderColor(COLOR_TICK_SELECTED, settings.tickSelected, flags);
        renderColor(COLOR_TICK_AIR, settings.tickAir, flags);
        renderColor(COLOR_TICK_SNEAK, settings.tickSneak, flags);
        renderColor(COLOR_TICK_WALL, settings.tickWall, flags);
        renderColor(COLOR_TICK_SOFT_COLLISION, settings.tickSoftCollision, flags);
        renderColor(COLOR_SUBTICK_PATH, settings.subtickPath, flags);
        renderColor(COLOR_YAW_ARROW, settings.yawArrow, flags);
        renderColor(COLOR_YAW_GIZMO_CIRCLE, settings.yawGizmoCircle, flags);
        renderColor(COLOR_YAW_GIZMO_DIRECTION, settings.yawGizmoDirection, flags);
        renderColor(COLOR_HITBOX_DEFAULT, settings.hitboxDefault, flags);
        renderColor(COLOR_HITBOX_SELECTED, settings.hitboxSelected, flags);
    }

    private void renderColor(String label, float[] color, int flags) {
        ImGui.colorEdit4(label, color, flags);
        if (ImGui.isItemDeactivatedAfterEdit()) {
            onChanged.run();
        }
    }
}
