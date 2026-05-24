package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

/** Tabbed Preferences modal. See docs/UI_REDESIGN.md for tab layout. */
public final class SettingsModal {

    private static final String POPUP_ID = "Preferences##settings_modal";
    private static final String CLOSE_BTN = "Close";
    private static final String RESET_BTN = "Reset All";
    private static final String LAYOUT_TABLE_ID = "##settings_layout";
    private static final float LABEL_COL_FRACTION = 0.55f;

    private static final String TT_UI_SCALE = "Multiplier applied to all ImGui widgets and fonts. 1.5x is the default for 1080p.";
    private static final String TT_YAW_ARROWS = "Draws an arrow at each tick's position showing the facing angle that frame.";
    private static final String TT_HITBOX = "Draws the player's hitbox at the currently selected tick.";
    private static final String TT_FULL_HITBOX = "Draws hitboxes for every tick in the TAS, not just the active one. Heavy on long TASes.";
    private static final String TT_SUBTICK = "Renders the interpolated path between adjacent ticks, exposing collision moments inside a tick.";
    private static final String TT_POTION_COLS = "Adds Speed and Jump Boost amplifier columns to the input table.";
    private static final String TT_YAW_TURN_RATE = "Caps how fast the macro rotates the camera during playback (deg per second).";
    private static final String TT_PATH_DIST = "Maximum world distance for the simulated path overlay.";
    private static final String TT_PATH_UNLIMITED = "Disables the distance cap. Heavy on long TASes.";
    private static final String TT_COLOR_GENERIC = "Color used for this overlay. Alpha applies in-world.";

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
        if (!ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }

        if (ImGui.beginTabBar("##settings_tabs")) {
            if (ImGui.beginTabItem("General")) {
                renderGeneral();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Visualization")) {
                renderVisualization();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Playback")) {
                renderPlayback();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Render Colors")) {
                renderColors();
                ImGui.endTabItem();
            }
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
                ImGui.setNextItemWidth(-1);
                if (Controls.combo("##ui_scale", scaleIndexBuf, scaleLabels)) {
                    settings.scaleIndex = scaleIndexBuf.get();
                    onChanged.run();
                }
                tooltipForLastItem(TT_UI_SCALE);
            });
            ImGui.endTable();
        }
    }

    private void renderVisualization() {
        sectionHeader("In-world overlays");
        if (beginLayoutTable()) {
            checkboxRow("Show yaw arrows", "##show_yaw_arrows", settings.showYawArrows, TT_YAW_ARROWS,
                    v -> settings.showYawArrows = v);
            checkboxRow("Show hitbox", "##show_hitbox", settings.showHitbox, TT_HITBOX,
                    v -> settings.showHitbox = v);
            checkboxRow("Show full hitbox", "##show_full_hitbox", settings.showFullHitbox, TT_FULL_HITBOX,
                    v -> settings.showFullHitbox = v);
            checkboxRow("Subtick visualization", "##show_subtick", settings.showSubtick, TT_SUBTICK,
                    v -> settings.showSubtick = v);
            ImGui.endTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Path");
        if (beginLayoutTable()) {
            row("Path render distance", () -> {
                pathRenderDistanceBuf[0] = settings.pathRenderDistance;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##path_render_distance", pathRenderDistanceBuf,
                        Settings.MIN_PATH_RENDER_DISTANCE, Settings.MAX_PATH_RENDER_DISTANCE, "%d blocks")) {
                    settings.pathRenderDistance = pathRenderDistanceBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_PATH_DIST);
            });
            checkboxRow("Unlimited path render distance", "##unlimited_path", settings.unlimitedPathRender,
                    TT_PATH_UNLIMITED, v -> settings.unlimitedPathRender = v);
            ImGui.endTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Editor table");
        if (beginLayoutTable()) {
            checkboxRow("Show potion effect columns", "##show_potion", settings.showPotionColumns, TT_POTION_COLS,
                    v -> settings.showPotionColumns = v);
            ImGui.endTable();
        }
    }

    private void renderPlayback() {
        sectionHeader("Camera and turning");
        if (beginLayoutTable()) {
            row("Max yaw turn rate", () -> {
                yawTurnCapBuf[0] = settings.yawFlickSpeed;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderFloat("##yaw_turn_cap", yawTurnCapBuf,
                        Settings.MIN_YAW_FLICK_SPEED, Settings.MAX_YAW_FLICK_SPEED, "%.0f deg/s")) {
                    settings.yawFlickSpeed = yawTurnCapBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_YAW_TURN_RATE);
            });
            ImGui.endTable();
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

        ThemeManager.sectionSpacing();
        sectionHeader("Path and gizmos");
        renderColor("subtick path", settings.subtickPath, flags);
        renderColor("yaw arrows", settings.yawArrow, flags);
        renderColor("yaw gizmo circle", settings.yawGizmoCircle, flags);
        renderColor("yaw gizmo direction", settings.yawGizmoDirection, flags);

        ThemeManager.sectionSpacing();
        sectionHeader("Hitbox");
        renderColor("hitbox default", settings.hitboxDefault, flags);
        renderColor("hitbox selected", settings.hitboxSelected, flags);
    }

    private void renderColor(String label, float[] color, int flags) {
        ImGui.colorEdit4(label, color, flags);
        tooltipForLastItem(TT_COLOR_GENERIC);
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

    private void checkboxRow(String label, String id, boolean current, String tooltip,
                             java.util.function.Consumer<Boolean> setter) {
        row(label, () -> {
            if (Controls.checkbox(id, current)) {
                setter.accept(!current);
                onChanged.run();
            }
            tooltipForLastItem(tooltip);
        });
    }

    private static void tooltipForLastItem(String text) {
        if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(text);
    }
}
