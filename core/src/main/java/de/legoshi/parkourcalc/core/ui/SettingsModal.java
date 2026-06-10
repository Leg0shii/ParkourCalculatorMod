package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.type.ImInt;

import java.util.function.Consumer;

/** Tabbed Preferences modal. */
public final class SettingsModal {

    private static final String POPUP_ID = "###settings_modal";
    private static final String CLOSE_BTN = "Close";
    private static final String RESET_BTN = "Reset All";
    private static final float CONTROL_COL_EMS = 12f;
    private static final float LABEL_COL_EMS = 18f; // fixed so the control column lines up across every subsection table
    private static final float MODAL_MAX_WIDTH_EMS = 36f;

    private static final String TT_UI_SCALE = "Multiplier applied to all ImGui widgets and fonts. 1.5x is the default for 1080p.";
    private static final String TT_SCROLLBAR_SIZE = "Thickness of scrollbars: the width of vertical bars and the height of horizontal ones. Scales with UI Scale.";
    private static final String TT_SCROLLBAR_GRAB = "Minimum length of the draggable scrollbar grab. Also applies to slider grab handles. Scales with UI Scale.";
    private static final String TT_YAW_ARROWS = "Draws an arrow at each tick's position showing the facing angle that frame.";
    private static final String TT_HITBOX = "Draws the player's hitbox at the currently selected tick.";
    private static final String TT_FULL_HITBOX = "Draws hitboxes for every tick in the TAS, not just the active one. Heavy on long TASes.";
    private static final String TT_SUBTICK = "Renders the interpolated path between adjacent ticks, exposing collision moments inside a tick.";
    private static final String TT_POTION_COLS = "Adds Speed and Jump Boost amplifier columns to the input table.";
    private static final String TT_GROUND_HIGHLIGHT = "Tints input rows whose simulated tick ended on the ground. Color is editable in Render Colors.";
    private static final String TT_YAW_TURN_RATE = "Caps how fast the macro rotates the camera during playback (deg per second).";
    private static final String TT_PATH_DIST = "Maximum world distance for the simulated path overlay.";
    private static final String TT_PATH_UNLIMITED = "Disables the distance cap. Heavy on long TASes.";
    private static final String TT_COLOR_GENERIC = "Color used for this overlay. Alpha applies in-world.";
    private static final String TT_KEEP_INPUT_TABLE = "Keeps the input table window drawn as a display-only overlay even when the main UI is closed. It cannot be edited while closed.";
    private static final String TT_KEEP_TICK_INFO = "Keeps the Tick Info window drawn even when the main UI is closed.";
    private static final String TT_KEEP_BOXES_PLAYBACK = "Keeps the tick-box path overlay drawn in-world while playback is running, instead of hiding it.";
    private static final String TT_AUTO_APPLY = "Applies a feasible Angle Solver solution to the input rows the moment the solve finishes, skipping the Apply confirmation.";
    private static final String TT_AUTO_SAVE = "Saves the open TAS automatically while it has unsaved changes, at most every 30 seconds. Needs a named save (use Save As once); Ctrl+S still saves instantly.";

    private final Settings settings;
    private final Runnable onChanged;

    private final ImInt scaleIndexBuf = new ImInt();
    private final float[] yawTurnCapBuf = new float[1];
    private final int[] pathRenderDistanceBuf = new int[1];
    private final float[] scrollbarSizeBuf = new float[1];
    private final float[] scrollbarGrabBuf = new float[1];
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

    /** active=false means the main UI is closed; dismiss the modal so it doesn't linger as a frozen, uncloseable ghost. */
    public void render(boolean active) {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        // Cap width so the auto-resize popup can't balloon; it still hugs content below the cap.
        if (ImGui.isPopupOpen(POPUP_ID)) {
            ImGui.setNextWindowSizeConstraints(0f, 0f, ImGui.getFontSize() * MODAL_MAX_WIDTH_EMS, Float.MAX_VALUE);
        }
        if (!Modal.begin("Preferences", POPUP_ID)) {
            return;
        }
        if (!active) {
            ImGui.closeCurrentPopup();
            Modal.end();
            return;
        }

        if (Controls.beginTabBar("##settings_tabs")) {
            if (Controls.beginTab("General")) {
                renderGeneral();
                Controls.endTab();
            }
            if (Controls.beginTab("Visualization")) {
                renderVisualization();
                Controls.endTab();
            }
            if (Controls.beginTab("Playback")) {
                renderPlayback();
                Controls.endTab();
            }
            if (Controls.beginTab("Render Colors")) {
                renderColors();
                Controls.endTab();
            }
            Controls.endTabBar();
        }

        Modal.footerSeparator();
        if (Controls.secondaryButton(RESET_BTN)) {
            settings.reset();
            ThemeManager.setScrollbarMetrics(settings.scrollbarSize, settings.scrollbarGrabMinSize);
            onChanged.run();
        }
        ImGui.sameLine();
        if (Modal.footerButton(CLOSE_BTN)) ImGui.closeCurrentPopup();
        Modal.end();
    }

    private void renderGeneral() {
        ThemeManager.sectionSpacing();
        sectionHeader("Interface");
        if (beginLayoutTable("##settings_general")) {
            scaleIndexBuf.set(settings.scaleIndex);
            row("UI Scale", () -> {
                ImGui.setNextItemWidth(-1);
                if (Controls.combo("##ui_scale", scaleIndexBuf, scaleLabels)) {
                    settings.scaleIndex = scaleIndexBuf.get();
                    onChanged.run();
                }
                tooltipForLastItem(TT_UI_SCALE);
            });
            row("Scrollbar thickness", () -> {
                scrollbarSizeBuf[0] = settings.scrollbarSize;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderFloat("##scrollbar_size", scrollbarSizeBuf, Settings.MIN_SCROLLBAR_SIZE, Settings.MAX_SCROLLBAR_SIZE, "%.0f px")) {
                    settings.scrollbarSize = scrollbarSizeBuf[0];
                    ThemeManager.setScrollbarMetrics(settings.scrollbarSize, settings.scrollbarGrabMinSize);
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_SCROLLBAR_SIZE);
            });
            row("Scrollbar grab length", () -> {
                scrollbarGrabBuf[0] = settings.scrollbarGrabMinSize;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderFloat("##scrollbar_grab", scrollbarGrabBuf, Settings.MIN_SCROLLBAR_GRAB_MIN_SIZE, Settings.MAX_SCROLLBAR_GRAB_MIN_SIZE, "%.0f px")) {
                    settings.scrollbarGrabMinSize = scrollbarGrabBuf[0];
                    ThemeManager.setScrollbarMetrics(settings.scrollbarSize, settings.scrollbarGrabMinSize);
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_SCROLLBAR_GRAB);
            });
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Saving");
        if (beginLayoutTable("##settings_saving")) {
            checkboxRow("Auto-save", "##auto_save", settings.autoSave, TT_AUTO_SAVE, v -> settings.autoSave = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Keep open when UI is closed");
        if (beginLayoutTable("##settings_panels")) {
            checkboxRow("Input table", "##keep_input_table", settings.keepInputTableOpen, TT_KEEP_INPUT_TABLE, v -> settings.keepInputTableOpen = v);
            checkboxRow("Tick Info", "##keep_tick_info", settings.keepTickInfoOpen, TT_KEEP_TICK_INFO, v -> settings.keepTickInfoOpen = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Angle Solver");
        if (beginLayoutTable("##settings_angle_solver")) {
            checkboxRow("Auto-apply solutions", "##auto_apply_solve", settings.autoApplySolve, TT_AUTO_APPLY, v -> settings.autoApplySolve = v);
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderVisualization() {
        ThemeManager.sectionSpacing();
        sectionHeader("In-world overlays");
        if (beginLayoutTable("##settings_overlays")) {
            checkboxRow("Show yaw arrows", "##show_yaw_arrows", settings.showYawArrows, TT_YAW_ARROWS, v -> settings.showYawArrows = v);
            checkboxRow("Show hitbox", "##show_hitbox", settings.showHitbox, TT_HITBOX, v -> settings.showHitbox = v);
            checkboxRow("Show full hitbox", "##show_full_hitbox", settings.showFullHitbox, TT_FULL_HITBOX, v -> settings.showFullHitbox = v);
            checkboxRow("Subtick visualization", "##show_subtick", settings.showSubtick, TT_SUBTICK, v -> settings.showSubtick = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Path");
        if (beginLayoutTable("##settings_path")) {
            row("Path render distance", () -> {
                pathRenderDistanceBuf[0] = settings.pathRenderDistance;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##path_render_distance", pathRenderDistanceBuf, Settings.MIN_PATH_RENDER_DISTANCE, Settings.MAX_PATH_RENDER_DISTANCE, "%d blocks")) {
                    settings.pathRenderDistance = pathRenderDistanceBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_PATH_DIST);
            });
            checkboxRow("Unlimited path render distance", "##unlimited_path", settings.unlimitedPathRender, TT_PATH_UNLIMITED, v -> settings.unlimitedPathRender = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Editor table");
        if (beginLayoutTable("##settings_editor")) {
            checkboxRow("Show potion effect columns", "##show_potion", settings.showPotionColumns, TT_POTION_COLS, v -> settings.showPotionColumns = v);
            checkboxRow("Highlight on-ground ticks", "##highlight_on_ground", settings.highlightOnGroundRows, TT_GROUND_HIGHLIGHT, v -> settings.highlightOnGroundRows = v);
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderPlayback() {
        ThemeManager.sectionSpacing();
        sectionHeader("Camera and turning");
        if (beginLayoutTable("##settings_playback")) {
            row("Max yaw turn rate", () -> {
                yawTurnCapBuf[0] = settings.yawFlickSpeed;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderFloat("##yaw_turn_cap", yawTurnCapBuf, Settings.MIN_YAW_FLICK_SPEED, Settings.MAX_YAW_FLICK_SPEED, "%.0f deg/s")) {
                    settings.yawFlickSpeed = yawTurnCapBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_YAW_TURN_RATE);
            });
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Overlays");
        if (beginLayoutTable("##settings_playback_overlays")) {
            checkboxRow("Keep tick boxes shown", "##keep_boxes_playback", settings.keepBoxesDuringPlayback, TT_KEEP_BOXES_PLAYBACK, v -> settings.keepBoxesDuringPlayback = v);
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderColors() {
        ThemeManager.sectionSpacing();
        sectionHeader("Tick boxes");
        int flags = ImGuiColorEditFlags.NoInputs;
        renderColor("tick box default", settings.tickDefault, flags);
        renderColor("tick box selected", settings.tickSelected, flags);
        renderColor("tick box in-air", settings.tickAir, flags);
        renderColor("tick box sneak", settings.tickSneak, flags);
        renderColor("tick box wall", settings.tickWall, flags);
        renderColor("tick box soft collision", settings.tickSoftCollision, flags);
        renderColor("on-ground row tint", settings.tickGroundHighlight, flags);

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
        ThemeManager.bottomPaddedSeparator();
    }

    private boolean beginLayoutTable(String id) {
        // Fixed-fit columns hug content so the modal can't balloon; stretch columns have no finite width in an auto-resize popup.
        if (!ThemeManager.beginStandardFormTable(id, 2)) return false;
        ImGui.tableSetupColumn("##label", ImGuiTableColumnFlags.WidthFixed, ImGui.getFontSize() * LABEL_COL_EMS);
        ImGui.tableSetupColumn("##control", ImGuiTableColumnFlags.WidthFixed, ImGui.getFontSize() * CONTROL_COL_EMS);
        return true;
    }

    private void row(String label, Runnable controlBody) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        Controls.labelCell(label);
        ImGui.tableNextColumn();
        controlBody.run();
    }

    private void checkboxRow(String label, String id, boolean current, String tooltip, Consumer<Boolean> setter) {
        row(label, () -> {
            if (Controls.checkbox(id, current)) {
                setter.accept(!current);
                onChanged.run();
            }
            tooltipForLastItem(tooltip);
        });
    }

    private static void tooltipForLastItem(String text) {
        TooltipUtil.onHover(text);
    }
}
