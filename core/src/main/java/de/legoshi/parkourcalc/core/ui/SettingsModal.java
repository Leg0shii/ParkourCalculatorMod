package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ui.theme.Controls;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

/**
 * Tabbed Preferences modal. Replaces the v1.2 SettingsOverlay window. Render-color
 * pickers stay user-editable on the Render Colors tab; the rest groups under
 * General / Visualization / Playback per docs/UI_REDESIGN.md.
 */
public final class SettingsModal {

    private static final String POPUP_ID = "Preferences##settings_modal";
    private static final String CLOSE_BTN = "Close";
    private static final String RESET_BTN = "Reset All";
    private static final String LAYOUT_TABLE_ID = "##settings_layout";
    private static final float LABEL_COL_FRACTION = 0.42f;
    private static final float CONTROL_WIDTH = 240f;

    private final Settings settings;
    private final Runnable onChanged;

    private final ImInt scaleIndexBuf = new ImInt();
    private final float[] yawTurnCapBuf = new float[1];
    private final int[] pathRenderDistanceBuf = new int[1];
    private final String[] scaleLabels;

    private boolean openRequested;

    public SettingsModal(Settings settings, Runnable onChanged) {
        this.settings = settings;
        this.onChanged = onChanged;
        this.scaleLabels = buildScaleLabels();
    }

    private static String[] buildScaleLabels() {
        String[] labels = new String[Settings.PRESET_SCALES.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Settings.PRESET_SCALES[i] + "x";
        return labels;
    }

    public void open() {
        openRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        ImGui.setNextWindowSize(540, 480, ImGuiCond.FirstUseEver);
        if (!ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoCollapse)) return;

        if (ImGui.beginTabBar("##settings_tabs")) {
            if (ImGui.beginTabItem("General"))       { renderGeneral();       ImGui.endTabItem(); }
            if (ImGui.beginTabItem("Visualization")) { renderVisualization(); ImGui.endTabItem(); }
            if (ImGui.beginTabItem("Playback"))      { renderPlayback();      ImGui.endTabItem(); }
            if (ImGui.beginTabItem("Render Colors")) { renderColors();        ImGui.endTabItem(); }
            ImGui.endTabBar();
        }

        ImGui.separator();
        float rightWidth = ImGui.calcTextSize(CLOSE_BTN).x + 32;
        if (Controls.secondaryButton(RESET_BTN)) {
            settings.reset();
            onChanged.run();
        }
        ImGui.sameLine(ImGui.getWindowWidth() - rightWidth - 16);
        if (Controls.secondaryButton(CLOSE_BTN)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private void renderGeneral() {
        sectionHeader("Interface");
        if (beginLayoutTable()) {
            scaleIndexBuf.set(settings.scaleIndex);
            row("UI Scale", () -> {
                ImGui.setNextItemWidth(CONTROL_WIDTH);
                if (Controls.combo("##ui_scale", scaleIndexBuf, scaleLabels)) {
                    settings.scaleIndex = scaleIndexBuf.get();
                    onChanged.run();
                }
            });
            ImGui.endTable();
        }
    }

    private void renderVisualization() {
        sectionHeader("In-world overlays");
        if (Controls.checkbox("Show yaw arrows", settings.showYawArrows)) {
            settings.showYawArrows = !settings.showYawArrows;
            onChanged.run();
        }
        if (Controls.checkbox("Show hitbox", settings.showHitbox)) {
            settings.showHitbox = !settings.showHitbox;
            onChanged.run();
        }
        if (Controls.checkbox("Show full hitbox", settings.showFullHitbox)) {
            settings.showFullHitbox = !settings.showFullHitbox;
            onChanged.run();
        }
        if (Controls.checkbox("Subtick visualization", settings.showSubtick)) {
            settings.showSubtick = !settings.showSubtick;
            onChanged.run();
        }

        ImGui.spacing();
        sectionHeader("Editor table");
        if (Controls.checkbox("Show potion effect columns", settings.showPotionColumns)) {
            settings.showPotionColumns = !settings.showPotionColumns;
            onChanged.run();
        }
    }

    private void renderPlayback() {
        sectionHeader("Camera and turning");
        if (beginLayoutTable()) {
            row("Max yaw turn rate", () -> {
                yawTurnCapBuf[0] = settings.yawFlickSpeed;
                ImGui.setNextItemWidth(CONTROL_WIDTH);
                if (Controls.sliderFloat("##yaw_turn_cap", yawTurnCapBuf,
                        Settings.MIN_YAW_FLICK_SPEED, Settings.MAX_YAW_FLICK_SPEED, "%.0f deg/s")) {
                    settings.yawFlickSpeed = yawTurnCapBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
            });
            row("Path render distance", () -> {
                pathRenderDistanceBuf[0] = settings.pathRenderDistance;
                ImGui.setNextItemWidth(CONTROL_WIDTH);
                if (Controls.sliderInt("##path_render_distance", pathRenderDistanceBuf,
                        Settings.MIN_PATH_RENDER_DISTANCE, Settings.MAX_PATH_RENDER_DISTANCE, "%d blocks")) {
                    settings.pathRenderDistance = pathRenderDistanceBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
            });
            ImGui.endTable();
        }
        if (Controls.checkbox("Unlimited path render distance", settings.unlimitedPathRender)) {
            settings.unlimitedPathRender = !settings.unlimitedPathRender;
            onChanged.run();
        }
    }

    private void renderColors() {
        sectionHeader("Tick boxes");
        int flags = ImGuiColorEditFlags.NoInputs;
        renderColor("tick box default", settings.tickDefault, flags);
        renderColor("tick box selected", settings.tickSelected, flags);
        renderColor("tick box in-air", settings.tickAir, flags);
        renderColor("tick box sneak", settings.tickSneak, flags);
        renderColor("tick box wall", settings.tickWall, flags);
        renderColor("tick box soft collision", settings.tickSoftCollision, flags);

        ImGui.spacing();
        sectionHeader("Path and gizmos");
        renderColor("subtick path", settings.subtickPath, flags);
        renderColor("yaw arrows", settings.yawArrow, flags);
        renderColor("yaw gizmo circle", settings.yawGizmoCircle, flags);
        renderColor("yaw gizmo direction", settings.yawGizmoDirection, flags);

        ImGui.spacing();
        sectionHeader("Hitbox");
        renderColor("hitbox default", settings.hitboxDefault, flags);
        renderColor("hitbox selected", settings.hitboxSelected, flags);
    }

    private void renderColor(String label, float[] color, int flags) {
        ImGui.colorEdit4(label, color, flags);
        if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
    }

    private void sectionHeader(String title) {
        ImGui.textDisabled(title);
        ImGui.separator();
    }

    private boolean beginLayoutTable() {
        if (!ImGui.beginTable(LAYOUT_TABLE_ID, 2, ImGuiTableFlags.SizingStretchProp)) return false;
        ImGui.tableSetupColumn("##label", ImGuiTableColumnFlags.WidthStretch, LABEL_COL_FRACTION);
        ImGui.tableSetupColumn("##control", ImGuiTableColumnFlags.WidthStretch, 1.0f - LABEL_COL_FRACTION);
        return true;
    }

    private void row(String label, Runnable controlBody) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        Controls.labelCell(label);
        ImGui.tableNextColumn();
        controlBody.run();
    }
}
