package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
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
    private static final float MODAL_MIN_WIDTH_EMS = 38f;
    private static final float MODAL_MAX_WIDTH_EMS = 44f;

    private static final String TT_UI_SCALE = "Multiplier applied to all ImGui widgets and fonts. 1.5x is the default for 1080p.";
    private static final String TT_SCROLLBAR_SIZE = "Thickness of scrollbars: the width of vertical bars and the height of horizontal ones. Scales with UI Scale.";
    private static final String TT_SCROLLBAR_GRAB = "Minimum length of the draggable scrollbar grab. Also applies to slider grab handles. Scales with UI Scale.";
    private static final String TT_YAW_ARROWS = "Draws an arrow at each tick's position showing the facing angle that frame.";
    private static final String TT_ARROW_MODE = "Which facing arrow to draw: the flat yaw arrow, or one arrow combining yaw and pitch into the actual look direction.";
    private static final String TT_HIT_DISTANCE = "Draws a line from each tick's eye position along that tick's look direction, out to block reach (4.5). The line changes color when it hits a block within reach, showing where a click on that tick would land, e.g. on a button.";
    private static final String TT_HIT_DISTANCE_SELECTED = "Limits the hit distance line to the currently selected ticks. Selected-tick lines re-cast every frame, so they also track world edits instantly.";
    private static final String TT_HIT_DISTANCE_GAME_REACH = "Uses the current game mode's block reach for the hit distance line (5.0 in creative) instead of the survival reach of 4.5.";
    private static final String TT_HITBOX = "Draws the player's hitbox at the currently selected tick.";
    private static final String TT_FULL_HITBOX = "Draws hitboxes for every tick in the TAS, not just the active one. Heavy on long TASes.";
    private static final String TT_SUBTICK = "Renders the interpolated path between adjacent ticks, exposing collision moments inside a tick.";
    private static final String TT_COL_SPEED_AMP = "Adds a per-tick Speed potion amplifier column to the input table.";
    private static final String TT_COL_JUMP_BOOST_AMP = "Adds a per-tick Jump Boost potion amplifier column to the input table.";
    private static final String TT_COL_HOTBAR = "Adds a per-tick hotbar slot column (1-9). During playback the held slot switches on ticks that set one; empty keeps the previous slot.";
    private static final String TT_COL_TELEPORT = "Adds a per-tick teleport column. When enabled on a tick, the player teleports to the given X/Y/Z at the start of that tick, before its inputs, in both the simulation and the replay.";
    private static final String TT_CONSTRAINTS = "Draws Angle Solver position constraints (X/Z) as translucent plates at the tick they apply to. The front plate sits on the constraint; the back fades out toward the open/free direction. Only visible while the Angle Solver is open.";
    private static final String TT_C_EXPAND = "Grow each plate outward by the player hitbox half-width (0.3) so the plate covers your hitbox: your hitbox fits inside the plate exactly when the tick is valid.";
    private static final String TT_PRESSURE_PLATE_FULL = "When adding a pressure plate constraint (Ctrl+B while looking at a pressure plate), use the full block footprint from .0 to 1.0 on X and Z instead of the version's inset interaction hitbox. Legacy versions inset the interaction box by 0.125, modern by 0.0625.";
    private static final String TT_C_DIM = "Size in blocks. Width is across the constraint, height is vertical, length is along the plate (front depth from the boundary / back reach behind it).";
    private static final String TT_GROUND_HIGHLIGHT = "Tints input rows whose simulated tick ended on the ground. Color is editable in Colors.";
    private static final String TT_COL_W = "W (forward) is always shown and can't be hidden.";
    private static final String TT_COL_A = "Strafe-left (A) column.";
    private static final String TT_COL_S = "Backward (S) column.";
    private static final String TT_COL_D = "Strafe-right (D) column.";
    private static final String TT_COL_SPRINT = "Sprint (Ctrl) column.";
    private static final String TT_COL_SNEAK = "Sneak (Shift) column.";
    private static final String TT_COL_JUMP = "Jump (Space) column.";
    private static final String TT_COL_YAW = "Yaw angle column.";
    private static final String TT_COL_PITCH = "Pitch angle column.";
    private static final String TT_COL_LMB = "Left click / attack column.";
    private static final String TT_COL_RMB = "Right click / use column.";
    private static final String TT_COL_CLOSE_INVENTORY = "Adds a per-tick column that closes the open inventory or container screen on that tick during playback, like pressing Esc in it.";
    private static final String TT_YAW_TURN_RATE = "Caps how fast the macro rotates the camera during playback (deg per second).";
    private static final String TT_PATH_DIST = "Maximum world distance for the simulated path overlay.";
    private static final String TT_PATH_UNLIMITED = "Disables the distance cap. Heavy on long TASes.";
    private static final String TT_SHOW_PATH = "Draws the simulated path (tick boxes, gizmos and constraints) in-world. Also toggled by the Toggle Path hotkey.";
    private static final String TT_SAVE_DEBUG_VALUES = "Writes the per-tick simulation state (position, velocity, ground and collision flags) into the TAS file on every save.";
    private static final String TT_COLOR_GENERIC = "Color used for this overlay. Alpha applies in-world.";
    private static final String TT_KEEP_INPUT_TABLE = "Keeps the input table window drawn as a display-only overlay even when the main UI is closed. It cannot be edited while closed.";
    private static final String TT_KEEP_TICK_INFO = "Keeps the Tick Info window drawn even when the main UI is closed.";
    private static final String TT_UNDO_REDO_WITHOUT_UI = "Ctrl+Z / Ctrl+Y (or Ctrl+Shift+Z) undo and redo TAS edits even while the main UI is closed. Disabled while a Minecraft screen such as chat or the inventory is open.";
    private static final String TT_HUD_MESSAGE_COUNT = "How many messages the notification stack shows at once. Older messages drop off the bottom.";
    private static final String TT_HUD_MESSAGE_SCALE = "Text size of the notification stack, as a multiplier of the UI font size. The window grows with the text.";
    private static final String TT_HUD_MESSAGE_COLOR = "Text color for normal notifications. Warnings keep their own color. While the main UI is open the window stays visible so it can be dragged; closed, it only appears while messages are showing.";
    private static final String TT_HUD_MESSAGE_ORDER = "Downwards: new messages appear at the top and the window grows downward. Upwards: new messages appear at the bottom and the window grows upward from its bottom edge, like chat.";
    private static final String TT_KEEP_BOXES_PLAYBACK = "Keeps the tick-box path overlay drawn in-world while playback is running, instead of hiding it.";
    private static final String TT_DISABLE_FLIGHT_PLAYBACK = "Prevents double-tapping jump from starting creative flight while playback is running (singleplayer). Flight behaves normally again once playback ends.";
    private static final String TT_LOCKSTEP_REPLAY = "Ticks the integrated server in lockstep with the client while playback runs (singleplayer only): each client tick releases exactly one server tick and waits for its packets, so server effects like damage boosts and lagbacks land on the same tick every replay instead of occasionally arriving a tick late and desyncing the run.";
    private static final String TT_SOLVER_PRECISION = "Decimal places for Angle Solver stats: solved yaws, objective values, constraint chips, and the constraint value editor.";
    private static final String TT_EXPERIMENTAL_BLOCK_CAPTURE = "Enables in-world block capture: tag blocks by role with hotkeys (M momentum, N collision, K land, Delete clears). The hotkeys are registered at startup, so turning this on or off only takes effect after a game restart.";
    private static final String TT_PAIRED_SIMULATION = "Runs the simulation as a lockstep client-server pair (singleplayer only): a server-side player processes the simulated movement through the real server code, so server rulings like fall damage, velocity packets, rubber-banding and block interactions show up in the simulated path and in the server event log.";
    private static final String TT_PAIRED_SIMULATION_UNSUPPORTED = "Paired simulation is not available on this loader yet.";
    private static final String TT_PAIRED_DAMAGE = "When checked, the server rules damage as vanilla does: fall and bounce damage hit the paired entity and the real player during replay, including slime damage boosts. Uncheck to model a server that cancels all damage: no damage rulings or damage velocity boosts anywhere, while movement corrections (lagbacks) still apply. Changing this resimulates the file.";
    private static final String TT_PAIRED_DAMAGE_DISABLED = "Only applies while the paired client-server sim is enabled.";

    private static final String PAIRED_CONFIRM_POPUP_ID = "###paired_sim_confirm";
    private static final String PAIRED_CONFIRM_TITLE = "Paired simulation";

    private final Settings settings;
    private final Runnable onChanged;
    private final TickInfoStatsEditor tickInfoStatsEditor;

    private boolean pairedSimulationSupported;
    private Runnable onPairedSimulationApplied;
    private boolean openPairedConfirm;
    private boolean pairedConfirmTarget;

    private static final String[] ARROW_MODE_LABELS = {"Yaw", "Combined"};
    private static final String[] HUD_MESSAGE_ORDER_LABELS = {"Downwards", "Upwards"};

    private static final String TT_REPLAY_START_DELAY = "Waits this many ticks after playback starts before the macro's inputs begin. The player holds at the start while the delay counts down; 0 starts immediately.";
    private final ImInt scaleIndexBuf = new ImInt();
    private final int[] replayStartDelayBuf = new int[1];
    private final ImInt arrowModeBuf = new ImInt();
    private final float[] yawTurnCapBuf = new float[1];
    private final int[] pathRenderDistanceBuf = new int[1];
    private final float[] scrollbarSizeBuf = new float[1];
    private final float[] scrollbarGrabBuf = new float[1];
    private final int[] solverPrecisionBuf = new int[1];
    private final float[] constraintDimBuf = new float[1];
    private final int[] hudMessageCountBuf = new int[1];
    private final float[] hudMessageScaleBuf = new float[1];
    private final ImInt hudMessageOrderBuf = new ImInt();
    private final String[] scaleLabels;

    private boolean openRequested;
    private Runnable onMacroModeChanged = () -> { };
    private final ImInt macroModeBuf = new ImInt();
    private final int[] macroDelayBuf = new int[1];
    private final int[] spreadBuf = new int[1];
    private final int[] samplesBuf = new int[1];
    private static final String[] MACRO_MODES = {"Off", de.legoshi.parkourcalc.core.PracticeMacro.LABEL_INPUTS,
            de.legoshi.parkourcalc.core.PracticeMacro.LABEL_TURN};
    private static final String TT_MACRO_MODE = "A client-side replica runs the jump; your player stays put. Replay (inputs): the replica presses the keys, you turn. Replay (turn): the replica turns, you press the keys. Starts after the reset click plus the delay.";
    private static final String TT_MACRO_DELAY = "Time between the reset click and the practice replay starting.";
    private static final String TT_STOP_KEYS_ON_FAIL = "After the first wrong key the keys are no longer checked or recorded. The attempt still runs to the landing tick and is judged on it.";
    private static final String TT_STOP_TURN_ON_FAIL = "The first wrong key ends the attempt with a keys verdict. Nothing after it is tracked or judged.";
    private static final String TT_RATED_DOTS = "Draws the simulated tries of the landing chance as dots in the Turn Profile.";
    private static final String TT_TURN_TIMING = "Records where inside each tick your mouse started and stopped moving. Draws your attempt as the real trace in the Turn Profile, a timing strip under each tick in Onejump Keys and the Turn onset stat in the Onejump Setup overview.";
    private static final String TT_OFFSET_LIVE = "Shows the best landing offset still reachable from the current tick above the Turn Profile, updated every tick of the attempt.";
    private static final String TT_OFFSET_HOVER = "Shows the offset still reachable from a tick in the Turn Profile tooltip. The forecast is only computed while this or the offset label is on.";
    private static final String TT_SPREAD = "How many of your latest attempts feed the per-tick facing spread that the landing chance is sampled from.";
    private static final String TT_SAMPLES = "How many tries are simulated for the landing chance after each attempt.";

    public SettingsModal(Settings settings, Runnable onChanged) {
        this.settings = settings;
        this.onChanged = onChanged;
        this.tickInfoStatsEditor = new TickInfoStatsEditor(settings, onChanged);
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

    public void setOnejumpHook(Runnable onMacroModeChanged) {
        this.onMacroModeChanged = onMacroModeChanged;
    }

    public void setPairedSimulationHook(boolean supported, Runnable onApplied) {
        this.pairedSimulationSupported = supported;
        this.onPairedSimulationApplied = onApplied;
    }

    /** active=false means the main UI is closed; dismiss the modal so it doesn't linger as a frozen, uncloseable ghost. */
    public void render(boolean active) {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        // Cap width so the auto-resize popup can't balloon; it still hugs content below the cap.
        if (ImGui.isPopupOpen(POPUP_ID)) {
            float em = ImGui.getFontSize();
            ImGui.setNextWindowSizeConstraints(em * MODAL_MIN_WIDTH_EMS, 0f, em * MODAL_MAX_WIDTH_EMS, Float.MAX_VALUE);
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
            if (Controls.beginTab("Simulation")) {
                renderSimulation();
                Controls.endTab();
            }
            if (Controls.beginTab("Overlays")) {
                renderOverlays();
                Controls.endTab();
            }
            if (Controls.beginTab("Constraints")) {
                renderConstraints();
                Controls.endTab();
            }
            if (Controls.beginTab("Input Table")) {
                renderInputTable();
                Controls.endTab();
            }
            if (Controls.beginTab("Stats")) {
                renderStats();
                Controls.endTab();
            }
            if (Controls.beginTab("Colors")) {
                renderColors();
                Controls.endTab();
            }
            if (Controls.beginTab("Onejump")) {
                renderOnejump();
                Controls.endTab();
            }
            Controls.endTabBar();
        }

        renderPairedSimConfirm();

        Modal.footerSeparator();
        if (Controls.secondaryButton(RESET_BTN)) {
            boolean pairedBefore = settings.pairedSimulation;
            boolean pairedDamageBefore = settings.pairedDamage;
            settings.reset();
            ThemeManager.setScrollbarMetrics(settings.scrollbarSize, settings.scrollbarGrabMinSize);
            ConstraintText.statsPrecision = settings.solverStatsPrecision;
            onChanged.run();
            boolean pairedChanged = pairedBefore != settings.pairedSimulation
                    || pairedDamageBefore != settings.pairedDamage;
            if (pairedChanged && onPairedSimulationApplied != null) {
                onPairedSimulationApplied.run();
            }
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
        sectionHeader("Outside the UI");
        if (beginLayoutTable("##settings_panels")) {
            checkboxRow("Input table", "##keep_input_table", settings.keepInputTableOpen, TT_KEEP_INPUT_TABLE, v -> settings.keepInputTableOpen = v);
            checkboxRow("Tick Info", "##keep_tick_info", settings.keepTickInfoOpen, TT_KEEP_TICK_INFO, v -> settings.keepTickInfoOpen = v);
            checkboxRow("Undo/redo hotkeys", "##undo_redo_without_ui", settings.undoRedoWithoutUi, TT_UNDO_REDO_WITHOUT_UI, v -> settings.undoRedoWithoutUi = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Notifications");
        if (beginLayoutTable("##settings_hud_messages")) {
            row("Max messages", () -> {
                hudMessageCountBuf[0] = settings.hudMessageCount;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##hud_message_count", hudMessageCountBuf,
                        Settings.MIN_HUD_MESSAGE_COUNT, Settings.MAX_HUD_MESSAGE_COUNT, "%d messages")) {
                    settings.hudMessageCount = hudMessageCountBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_HUD_MESSAGE_COUNT);
            });
            row("Text size", () -> {
                hudMessageScaleBuf[0] = settings.hudMessageScale;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderFloat("##hud_message_scale", hudMessageScaleBuf,
                        Settings.MIN_HUD_MESSAGE_SCALE, Settings.MAX_HUD_MESSAGE_SCALE, "%.1fx")) {
                    settings.hudMessageScale = hudMessageScaleBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_HUD_MESSAGE_SCALE);
            });
            row("Text color", () -> {
                ImGui.colorEdit4("##hud_message_color", settings.hudMessageColor,
                        ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoDragDrop);
                tooltipForLastItem(TT_HUD_MESSAGE_COLOR);
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
            });
            hudMessageOrderBuf.set(settings.hudMessageOrder);
            row("Stacking", () -> {
                ImGui.setNextItemWidth(-1);
                if (Controls.combo("##hud_message_order", hudMessageOrderBuf, HUD_MESSAGE_ORDER_LABELS)) {
                    settings.hudMessageOrder = hudMessageOrderBuf.get();
                    onChanged.run();
                }
                tooltipForLastItem(TT_HUD_MESSAGE_ORDER);
            });
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Advanced");
        if (beginLayoutTable("##settings_advanced")) {
            checkboxRow("Save debug values", "##save_debug_values", settings.saveDebugValues, TT_SAVE_DEBUG_VALUES, v -> settings.saveDebugValues = v);
            checkboxRow("Block capture (restart required)", "##experimental_block_capture", settings.experimentalBlockCapture, TT_EXPERIMENTAL_BLOCK_CAPTURE, v -> settings.experimentalBlockCapture = v);
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderSimulation() {
        ThemeManager.sectionSpacing();
        sectionHeader("Simulation");
        if (beginLayoutTable("##settings_simulation")) {
            if (pairedSimulationSupported) {
                row("Paired client-server sim", () -> {
                    if (Controls.checkbox("##paired_simulation", settings.pairedSimulation)) {
                        pairedConfirmTarget = !settings.pairedSimulation;
                        openPairedConfirm = true;
                    }
                    tooltipForLastItem(TT_PAIRED_SIMULATION);
                });
            } else {
                disabledCheckboxRow("Paired client-server sim", "##paired_simulation", settings.pairedSimulation, TT_PAIRED_SIMULATION_UNSUPPORTED);
            }
            if (pairedSimulationSupported && settings.pairedSimulation) {
                row("Server damage", () -> {
                    if (Controls.checkbox("##paired_damage", settings.pairedDamage)) {
                        settings.pairedDamage = !settings.pairedDamage;
                        onChanged.run();
                        if (onPairedSimulationApplied != null) {
                            onPairedSimulationApplied.run();
                        }
                    }
                    tooltipForLastItem(TT_PAIRED_DAMAGE);
                });
            } else {
                disabledCheckboxRow("Server damage", "##paired_damage", settings.pairedDamage, TT_PAIRED_DAMAGE_DISABLED);
            }
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Replay");
        if (beginLayoutTable("##settings_replay")) {
            row("Max yaw turn rate", () -> {
                yawTurnCapBuf[0] = settings.yawFlickSpeed;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderFloat("##yaw_turn_cap", yawTurnCapBuf, Settings.MIN_YAW_FLICK_SPEED, Settings.MAX_YAW_FLICK_SPEED, "%.0f deg/s")) {
                    settings.yawFlickSpeed = yawTurnCapBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_YAW_TURN_RATE);
            });
            row("Delay before replay", () -> {
                replayStartDelayBuf[0] = settings.replayStartDelayTicks;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##replay_start_delay", replayStartDelayBuf, Settings.MIN_REPLAY_START_DELAY_TICKS, Settings.MAX_REPLAY_START_DELAY_TICKS, "%d ticks")) {
                    settings.replayStartDelayTicks = replayStartDelayBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_REPLAY_START_DELAY);
            });
            checkboxRow("Lockstep replay", "##lockstep_replay", settings.lockstepReplay, TT_LOCKSTEP_REPLAY, v -> settings.lockstepReplay = v);
            checkboxRow("Disable creative flight", "##disable_flight_playback", settings.disableFlightDuringPlayback, TT_DISABLE_FLIGHT_PLAYBACK, v -> settings.disableFlightDuringPlayback = v);
            checkboxRow("Keep tick boxes shown", "##keep_boxes_playback", settings.keepBoxesDuringPlayback, TT_KEEP_BOXES_PLAYBACK, v -> settings.keepBoxesDuringPlayback = v);
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderOnejump() {
        ThemeManager.sectionSpacing();
        sectionHeader("Practice replay");
        if (beginLayoutTable("##settings_onejump_macro")) {
            row("Mode", () -> {
                macroModeBuf.set(Math.max(0, Math.min(2, settings.onejumpMacroMode)));
                if (Controls.combo("##onejump_macro_mode", macroModeBuf, MACRO_MODES, ImGui.getContentRegionAvail().x)) {
                    settings.onejumpMacroMode = macroModeBuf.get();
                    onMacroModeChanged.run();
                    onChanged.run();
                }
                tooltipForLastItem(TT_MACRO_MODE);
            });
            row("Delay", () -> {
                macroDelayBuf[0] = settings.onejumpMacroDelayMs;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##onejump_macro_delay", macroDelayBuf, 0, 5000, "%d ms")) {
                    settings.onejumpMacroDelayMs = macroDelayBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_MACRO_DELAY);
            });
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("On a wrong key");
        if (beginLayoutTable("##settings_onejump_fail")) {
            checkboxRow("Stop checking keys", "##onejump_stop_keys", settings.onejumpStopKeysOnFail, TT_STOP_KEYS_ON_FAIL, v -> settings.onejumpStopKeysOnFail = v);
            checkboxRow("Stop the attempt", "##onejump_stop_turn", settings.onejumpStopTurnOnFail, TT_STOP_TURN_ON_FAIL, v -> settings.onejumpStopTurnOnFail = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Attempt display");
        if (beginLayoutTable("##settings_onejump_profile")) {
            checkboxRow("Rated tries as dots", "##onejump_rated_dots", settings.turnProfileShowRating, TT_RATED_DOTS, v -> settings.turnProfileShowRating = v);
            checkboxRow("Turn timing", "##onejump_turn_timing", settings.onejumpTurnTiming, TT_TURN_TIMING, v -> settings.onejumpTurnTiming = v);
            checkboxRow("Offset label", "##onejump_offset_live", settings.onejumpOffsetLive, TT_OFFSET_LIVE, v -> settings.onejumpOffsetLive = v);
            checkboxRow("Offset on hover", "##onejump_offset_hover", settings.onejumpOffsetHover, TT_OFFSET_HOVER, v -> settings.onejumpOffsetHover = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Landing chance");
        if (beginLayoutTable("##settings_onejump_chance")) {
            row("Attempts used", () -> {
                spreadBuf[0] = settings.onejumpSpreadAttempts;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##onejump_spread", spreadBuf, 50, 5000, "%d attempts")) {
                    settings.onejumpSpreadAttempts = spreadBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_SPREAD);
            });
            row("Samples", () -> {
                samplesBuf[0] = settings.turnProfileAttempts;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##onejump_samples", samplesBuf, 1000, 200000, "%d tries")) {
                    settings.turnProfileAttempts = samplesBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_SAMPLES);
            });
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderPairedSimConfirm() {
        if (openPairedConfirm) {
            ImGui.openPopup(PAIRED_CONFIRM_POPUP_ID);
            openPairedConfirm = false;
        }
        if (!Modal.begin(PAIRED_CONFIRM_TITLE, PAIRED_CONFIRM_POPUP_ID)) return;
        ImGui.text("WARNING: this resimulates your file from the beginning.");
        ImGui.spacing();
        if (pairedConfirmTarget) {
            ImGui.text("The simulation gains a server-side player that can interact with");
            ImGui.text("and modify the world: block interactions the TAS performs, damage");
            ImGui.text("side effects, and every other consequence of the current file.");
        } else {
            ImGui.text("World changes made by the paired simulation are reverted before");
            ImGui.text("the resimulation, so the world rewinds to its unmodified state.");
        }
        Modal.footerSeparator();
        if (Controls.dangerButton(pairedConfirmTarget ? "Enable" : "Disable")) {
            settings.pairedSimulation = pairedConfirmTarget;
            onChanged.run();
            ImGui.closeCurrentPopup();
            if (onPairedSimulationApplied != null) {
                onPairedSimulationApplied.run();
            }
        }
        ImGui.sameLine();
        if (Modal.footerButton("Cancel")) ImGui.closeCurrentPopup();
        Modal.end();
    }

    private void renderOverlays() {
        ThemeManager.sectionSpacing();
        sectionHeader("In-world overlays");
        if (beginLayoutTable("##settings_overlays")) {
            checkboxRow("Show path", "##show_path", settings.showPath, TT_SHOW_PATH, v -> settings.showPath = v);
            checkboxRow("Show facing arrows", "##show_yaw_arrows", settings.showYawArrows, TT_YAW_ARROWS, v -> settings.showYawArrows = v);
            arrowModeBuf.set(settings.arrowMode);
            row("Arrow type", () -> {
                ImGui.setNextItemWidth(-1);
                if (Controls.combo("##arrow_mode", arrowModeBuf, ARROW_MODE_LABELS)) {
                    settings.arrowMode = arrowModeBuf.get();
                    onChanged.run();
                }
                tooltipForLastItem(TT_ARROW_MODE);
            });
            checkboxRow("Show hit distance lines", "##show_hit_distance", settings.showHitDistanceLines, TT_HIT_DISTANCE, v -> settings.showHitDistanceLines = v);
            checkboxRow("Hit distance for selected ticks only", "##hit_distance_selected", settings.hitDistanceSelectedOnly, TT_HIT_DISTANCE_SELECTED, v -> settings.hitDistanceSelectedOnly = v);
            checkboxRow("Hit distance uses game mode reach", "##hit_distance_game_reach", settings.hitDistanceGameReach, TT_HIT_DISTANCE_GAME_REACH, v -> settings.hitDistanceGameReach = v);
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
    }

    private void renderConstraints() {
        ThemeManager.sectionSpacing();
        sectionHeader("Shape");
        if (beginLayoutTable("##settings_constraint_shape")) {
            checkboxRow("Show constraints", "##show_constraints", settings.showConstraints, TT_CONSTRAINTS, v -> settings.showConstraints = v);
            checkboxRow("Expand by player hitbox", "##c_expand", settings.constraintExpandByHitbox, TT_C_EXPAND, v -> settings.constraintExpandByHitbox = v);
            checkboxRow("Pressure plate full block", "##pressure_plate_full", settings.pressurePlateFullBlock, TT_PRESSURE_PLATE_FULL, v -> settings.pressurePlateFullBlock = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Plates (front on the constraint, back is the fade tail)");
        if (beginLayoutTable("##settings_constraint_plates")) {
            constraintDimRow("Front width", "##c_fw", settings.constraintFrontWidth, Settings.CONSTRAINT_MAX_WIDTH, v -> settings.constraintFrontWidth = v);
            constraintDimRow("Front height", "##c_fh", settings.constraintFrontHeight, Settings.CONSTRAINT_MAX_HEIGHT, v -> settings.constraintFrontHeight = v);
            constraintDimRow("Front length", "##c_fl", settings.constraintFrontLength, Settings.CONSTRAINT_MAX_FRONT_LENGTH, v -> settings.constraintFrontLength = v);
            constraintDimRow("Back width", "##c_bw", settings.constraintBackWidth, Settings.CONSTRAINT_MAX_WIDTH, v -> settings.constraintBackWidth = v);
            constraintDimRow("Back height", "##c_bh", settings.constraintBackHeight, Settings.CONSTRAINT_MAX_HEIGHT, v -> settings.constraintBackHeight = v);
            constraintDimRow("Back length", "##c_bl", settings.constraintBackLength, Settings.CONSTRAINT_MAX_BACK_LENGTH, v -> settings.constraintBackLength = v);
            ThemeManager.endStandardFormTable();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Colors");
        int flags = ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoDragDrop;
        renderColor("front", settings.constraintFill, flags);
        renderColor("satisfied outline", settings.constraintOutline, flags);
        renderColor("selected highlight", settings.constraintHighlight, flags);
        renderColor("back", settings.constraintBack, flags);
    }

    private void constraintDimRow(String label, String id, float value, float max, Consumer<Float> setter) {
        row(label, () -> {
            constraintDimBuf[0] = value;
            ImGui.setNextItemWidth(-1);
            if (Controls.sliderFloat(id, constraintDimBuf, Settings.CONSTRAINT_MIN_DIM, max, "%.2f")) {
                setter.accept(constraintDimBuf[0]);
            }
            if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
            tooltipForLastItem(TT_C_DIM);
        });
    }

    private void renderInputTable() {
        ThemeManager.sectionSpacing();
        sectionHeader("Columns and rows");
        if (beginLayoutTable("##settings_columns")) {
            disabledCheckboxRow("Forward (W)", "##col_w", true, TT_COL_W);
            checkboxRow("Strafe left (A)", "##col_a", settings.showColA, TT_COL_A, v -> settings.showColA = v);
            checkboxRow("Backward (S)", "##col_s", settings.showColS, TT_COL_S, v -> settings.showColS = v);
            checkboxRow("Strafe right (D)", "##col_d", settings.showColD, TT_COL_D, v -> settings.showColD = v);
            checkboxRow("Sprint", "##col_sprint", settings.showColSprint, TT_COL_SPRINT, v -> settings.showColSprint = v);
            checkboxRow("Sneak", "##col_sneak", settings.showColSneak, TT_COL_SNEAK, v -> settings.showColSneak = v);
            checkboxRow("Jump", "##col_jump", settings.showColJump, TT_COL_JUMP, v -> settings.showColJump = v);
            checkboxRow("Yaw", "##col_yaw", settings.showColYaw, TT_COL_YAW, v -> settings.showColYaw = v);
            checkboxRow("Pitch", "##col_pitch", settings.showColPitch, TT_COL_PITCH, v -> settings.showColPitch = v);
            checkboxRow("Left click (LMB)", "##col_lmb", settings.showColLeftClick, TT_COL_LMB, v -> settings.showColLeftClick = v);
            checkboxRow("Right click (RMB)", "##col_rmb", settings.showColRightClick, TT_COL_RMB, v -> settings.showColRightClick = v);
            checkboxRow("Close inventory", "##col_close_inv", settings.showColCloseInventory, TT_COL_CLOSE_INVENTORY, v -> settings.showColCloseInventory = v);
            checkboxRow("Speed", "##show_speed", settings.showColSpeed, TT_COL_SPEED_AMP, v -> settings.showColSpeed = v);
            checkboxRow("Jump Boost", "##show_jump_boost", settings.showColJumpBoost, TT_COL_JUMP_BOOST_AMP, v -> settings.showColJumpBoost = v);
            checkboxRow("Hotbar slot", "##show_hotbar", settings.showColHotbar, TT_COL_HOTBAR, v -> settings.showColHotbar = v);
            checkboxRow("Teleport", "##show_teleport", settings.showColTeleport, TT_COL_TELEPORT, v -> settings.showColTeleport = v);
            checkboxRow("Highlight on-ground ticks", "##highlight_on_ground", settings.highlightOnGroundRows, TT_GROUND_HIGHLIGHT, v -> settings.highlightOnGroundRows = v);
            ThemeManager.endStandardFormTable();
        }
    }

    private void renderStats() {
        ThemeManager.sectionSpacing();
        sectionHeader("Stats");
        if (beginLayoutTable("##settings_stats")) {
            row("Angle Solver decimal places", () -> {
                solverPrecisionBuf[0] = settings.solverStatsPrecision;
                ImGui.setNextItemWidth(-1);
                if (Controls.sliderInt("##solver_stats_precision", solverPrecisionBuf,
                        Settings.MIN_STAT_PRECISION, Settings.MAX_STAT_PRECISION, "%d decimals")) {
                    settings.solverStatsPrecision = solverPrecisionBuf[0];
                    ConstraintText.statsPrecision = solverPrecisionBuf[0];
                }
                if (ImGui.isItemDeactivatedAfterEdit()) onChanged.run();
                tooltipForLastItem(TT_SOLVER_PRECISION);
            });
            ThemeManager.endStandardFormTable();
        }
        ThemeManager.sectionSpacing();
        ImGui.textDisabled("Tick Info stats: toggle, set decimals, and drag to reorder.");
        ThemeManager.sectionSpacing();
        tickInfoStatsEditor.render();
    }

    private void renderColors() {
        ThemeManager.sectionSpacing();
        sectionHeader("Tick boxes");
        int flags = ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoDragDrop;
        renderColor("tick box default", settings.tickDefault, flags);
        renderColor("tick box selected", settings.tickSelected, flags);
        renderColor("tick box in-air", settings.tickAir, flags);
        renderColor("tick box sneak", settings.tickSneak, flags);
        renderColor("tick box wall", settings.tickWall, flags);
        renderColor("tick box soft collision", settings.tickSoftCollision, flags);
        renderColor("tick box solver start", settings.tickSolverStart, flags);
        renderColor("tick box solver goal", settings.tickSolverGoal, flags);
        renderColor("tick box desync", settings.tickDeviation, flags);
        renderColor("on-ground row tint", settings.tickGroundHighlight, flags);

        ThemeManager.sectionSpacing();
        sectionHeader("Path and gizmos");
        renderColor("subtick path", settings.subtickPath, flags);
        renderColor("yaw arrows", settings.yawArrow, flags);
        renderColor("combined arrows", settings.pitchArrow, flags);
        renderColor("hit distance line", settings.hitDistanceLine, flags);
        renderColor("hit distance line (in reach)", settings.hitDistanceLineHit, flags);
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

    private void disabledCheckboxRow(String label, String id, boolean current, String tooltip) {
        row(label, () -> {
            ImGui.beginDisabled(true);
            Controls.checkbox(id, current);
            ImGui.endDisabled();
            tooltipForLastItem(tooltip);
        });
    }

    private static void tooltipForLastItem(String text) {
        TooltipUtil.onHover(text);
    }
}
