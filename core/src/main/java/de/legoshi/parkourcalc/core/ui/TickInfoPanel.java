package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

public final class TickInfoPanel implements RenderInterface {

    private static final String WINDOW_ID = "###tick-info";
    private static final String WINDOW_TITLE = "Tick Info";
    private static final String TABLE_ID = "tick-info-table";
    private static final String PLACEHOLDER_SELECT_ONE = "Select a single tick.";
    private static final String PLACEHOLDER_OUT_OF_RANGE = "No tick data (resimulating).";
    private static final String PLACEHOLDER_NO_STATS = "No stats enabled. Right-click to configure.";
    private static final String NA = "n/a";

    private static final String COL_FIELD = "Field";
    private static final String COL_X = "X";
    private static final String COL_Y = "Y";
    private static final String COL_Z = "Z";

    private final BoxController boxController;
    private final InputData inputData;
    private final SelectionManager selection;
    private final Settings settings;
    private final SimulationRunner runner;
    private int rowCounter;

    private int sampleDecimals = -1;
    private String numSample = "";

    public TickInfoPanel(BoxController boxController, InputData inputData, SelectionManager selection, Settings settings, SimulationRunner runner) {
        this.boxController = boxController;
        this.inputData = inputData;
        this.selection = selection;
        this.settings = settings;
        this.runner = runner;
    }

    private float effectivePitch(int idx) {
        float p = runner.getStartPitch();
        int n = Math.min(idx + 1, inputData.size());
        for (int i = 0; i < n; i++) {
            p = PlaybackController.applyPitch(p, inputData.get(i));
        }
        return p;
    }

    private static int clampPrecision(int p) {
        return Math.min(Math.max(p, Settings.MIN_STAT_PRECISION), Settings.MAX_STAT_PRECISION);
    }

    private static String fmtNum(int decimals) {
        int d = clampPrecision(decimals);
        return "%" + (7 + d) + "." + d + "f";
    }

    private static String fmtNumSingle(int decimals) {
        int d = clampPrecision(decimals);
        return "%." + d + "f";
    }

    private void rebuildColumnSample(int widestDecimals) {
        int d = clampPrecision(widestDecimals);
        if (d == sampleDecimals) return;
        sampleDecimals = d;
        StringBuilder sample = new StringBuilder("-99999.");
        for (int i = 0; i < d; i++) sample.append('9');
        numSample = sample.toString();
    }

    private int widestEnabledDecimals() {
        int widest = Settings.MIN_STAT_PRECISION;
        TickInfoConfig config = settings.tickInfoStats;
        if (config == null || config.stats == null) return widest;
        for (TickInfoStatSetting setting : config.stats) {
            if (setting != null && setting.enabled && setting.decimals > widest) {
                widest = setting.decimals;
            }
        }
        return clampPrecision(widest);
    }

    @Override
    public void render(ImGuiIO io) {
        float em = ImGui.getFontSize();
        ImGui.setNextWindowSize(em * 17f, em * 26f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(em * 11f, em * 6f, Float.MAX_VALUE, Float.MAX_VALUE);
        if (!ThemeManager.beginPanel(WINDOW_ID, WINDOW_TITLE, ImGuiWindowFlags.NoCollapse)) {
            return;
        }

        if (selection.size() != 1) {
            ImGui.text(PLACEHOLDER_SELECT_ONE);
            ImGui.end();
            return;
        }

        int pathIndex = selection.getSelected().iterator().next();
        List<TickState> states = boxController.getStates();
        if (pathIndex < 0 || pathIndex >= states.size()) {
            ImGui.text(PLACEHOLDER_OUT_OF_RANGE);
            ImGui.end();
            return;
        }

        boolean isStart = pathIndex == 0;
        int idx = SelectionManager.boxIndexForSelection(pathIndex);
        TickState cur = states.get(idx);
        TickState prev = idx > 0 ? states.get(idx - 1) : null;
        TickState prev2 = idx > 1 ? states.get(idx - 2) : null;
        float appliedYaw = isStart ? cur.yaw : (idx + 1 < states.size() ? states.get(idx + 1).yaw : cur.yaw);
        float appliedPitch = isStart ? runner.getStartPitch() : effectivePitch(idx);

        renderTable(idx, cur, prev, prev2, appliedYaw, appliedPitch, isStart);
        ImGui.end();
    }

    private void rowText(String label, String text, String tooltip) {
        labelCell(label, tooltip);
        centerSingleValueInMiddleColumn(text);
    }

    private void renderTable(int idx, TickState cur, TickState prev, TickState prev2, float appliedYaw, float appliedPitch, boolean isStart) {
        List<TickInfoStat> enabled = settings.tickInfoStats.enabledInOrder();
        if (enabled.isEmpty()) {
            ImGui.text(PLACEHOLDER_NO_STATS);
            return;
        }

        rebuildColumnSample(widestEnabledDecimals());
        if (!ThemeManager.beginStandardKeyValueTable(TABLE_ID, 4, 0, 0f, 0f)) {
            return;
        }
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float cellPad = ImGui.getStyle().getCellPadding().x;
        float labelDataW = ImGui.calcTextSize("Collision angle (deg)").x + 2f * cellPad;
        float numW = ImGui.calcTextSize(numSample).x;
        ImGui.tableSetupColumn(COL_FIELD, fixed, ThemeManager.tableLeftmostColumnWidth(COL_FIELD, labelDataW));
        ImGui.tableSetupColumn(COL_X, fixed, ThemeManager.tableColumnWidth(COL_X, numW));
        ImGui.tableSetupColumn(COL_Y, fixed, ThemeManager.tableColumnWidth(COL_Y, numW));
        ImGui.tableSetupColumn(COL_Z, fixed, ThemeManager.tableRightmostColumnWidth(COL_Z, numW, ThemeManager.tableFixedScrollbarSlack()));

        rowCounter = 0;
        for (TickInfoStat stat : enabled) {
            int decimals = decimalsFor(stat);
            renderStatRow(stat, decimals, idx, cur, prev, prev2, appliedYaw, appliedPitch, isStart);
        }

        ThemeManager.endStandardTable();
    }

    private int decimalsFor(TickInfoStat stat) {
        TickInfoStatSetting setting = settings.tickInfoStats.find(stat);
        int d = setting != null ? setting.decimals : Settings.defaultTickInfoPrecision();
        return clampPrecision(d);
    }

    private void renderStatRow(TickInfoStat stat, int decimals, int idx,
                               TickState cur, TickState prev, TickState prev2, float appliedYaw, float appliedPitch, boolean isStart) {
        String tip = stat.tooltip();
        String naTip = stat.naTooltip();
        switch (stat) {
            case TICK:
                if (isStart) {
                    rowText(stat.label(), "Start", "The simulation's start state: the seed the run launches from.");
                } else {
                    rowInt(stat.label(), idx + 1, tip);
                }
                break;
            case YAW:
                rowNum(stat.label(), appliedYaw, decimals, tip);
                break;
            case PITCH:
                rowNum(stat.label(), appliedPitch, decimals, tip);
                break;
            case SPEED_XZ:
                if (prev != null) {
                    double dx = cur.position.x - prev.position.x;
                    double dz = cur.position.z - prev.position.z;
                    rowNum(stat.label(), Math.sqrt(dx * dx + dz * dz), decimals, tip);
                } else {
                    rowNa(stat.label(), naTip);
                }
                break;
            case MOTION_XZ:
                rowNum(stat.label(),
                        Math.sqrt(cur.velocity.x * cur.velocity.x + cur.velocity.z * cur.velocity.z), decimals, tip);
                break;
            case MOTION_XYZ:
                rowNum(stat.label(),
                        Math.sqrt(cur.velocity.x * cur.velocity.x + cur.velocity.y * cur.velocity.y + cur.velocity.z * cur.velocity.z),
                        decimals, tip);
                break;
            case POSITION:
                rowTriple(stat.label(), cur.position.x, cur.position.y, cur.position.z, decimals, tip);
                break;
            case MOTION:
                rowTriple(stat.label(), cur.velocity.x, cur.velocity.y, cur.velocity.z, decimals, tip);
                break;
            case SPEED:
                if (prev != null) {
                    rowTriple(stat.label(),
                            cur.position.x - prev.position.x,
                            cur.position.y - prev.position.y,
                            cur.position.z - prev.position.z, decimals, tip);
                } else {
                    rowNa(stat.label(), naTip);
                }
                break;
            case POST_MOTION_XZ:
                if (prev != null) {
                    rowXZ(stat.label(), cur.position.x - prev.position.x, cur.position.z - prev.position.z, decimals, tip);
                } else {
                    rowNa(stat.label(), naTip);
                }
                break;
            case ACCELERATION_XZ:
                if (prev != null && prev2 != null) {
                    double dx = cur.position.x - prev.position.x;
                    double dz = cur.position.z - prev.position.z;
                    double pdx = prev.position.x - prev2.position.x;
                    double pdz = prev.position.z - prev2.position.z;
                    rowXZ(stat.label(), dx - pdx, dz - pdz, decimals, tip);
                } else {
                    rowNa(stat.label(), naTip);
                }
                break;
            case SPEED_ANGLE:
                if (prev != null) {
                    double dx = cur.position.x - prev.position.x;
                    double dz = cur.position.z - prev.position.z;
                    if (dx * dx + dz * dz < 1.0e-18) {
                        rowNa(stat.label(), naTip);
                    } else {
                        rowNum(stat.label(), Math.toDegrees(Math.atan2(-dx, dz)), decimals, tip);
                    }
                } else {
                    rowNa(stat.label(), naTip);
                }
                break;
            case ON_GROUND:
                rowBool(stat.label(), cur.onGround, tip);
                break;
            case SNEAKING:
                rowBool(stat.label(), cur.sneaking, tip);
                break;
            case COLLISION:
                rowBool(stat.label(), cur.wallCollision, tip);
                break;
            case SOFT_COLLISION:
                if (cur.wallCollision) {
                    rowBool(stat.label(), cur.softCollision, tip);
                } else {
                    rowNa(stat.label(), naTip);
                }
                break;
            case COLLISION_ANGLE:
                if (Double.isNaN(cur.collisionAngleDegrees)) {
                    rowNa(stat.label(), naTip);
                } else {
                    rowNum(stat.label(), cur.collisionAngleDegrees, decimals, tip);
                }
                break;
            default:
                rowNa(stat.label(), naTip);
                break;
        }
    }

    private void labelCell(String label, String tooltip) {
        ImGui.tableNextRow();
        ThemeManager.paintTableRowBg(rowCounter++);
        ImGui.tableNextColumn();
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ThemeManager.textLeft(label);
        ThemeManager.popTextColor();
        TooltipUtil.onHover(tooltip);
    }

    private void rowTriple(String label, double x, double y, double z, int decimals, String tooltip) {
        labelCell(label, tooltip);
        numCell(x, decimals);
        numCell(y, decimals);
        numCell(z, decimals);
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void rowXZ(String label, double x, double z, int decimals, String tooltip) {
        labelCell(label, tooltip);
        numCell(x, decimals);
        emptyCell();
        numCell(z, decimals);
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void rowNum(String label, double v, int decimals, String tooltip) {
        labelCell(label, tooltip);
        centerSingleValueInMiddleColumn(String.format(Locale.US, fmtNumSingle(decimals), v));
    }

    private void rowInt(String label, int v, String tooltip) {
        labelCell(label, tooltip);
        centerSingleValueInMiddleColumn(Integer.toString(v));
    }

    private void rowBool(String label, boolean v, String tooltip) {
        labelCell(label, tooltip);
        int color = v ? ThemeManager.okColor() : ThemeManager.dangerColor();
        ThemeManager.pushTextColor(color);
        centerSingleValueInMiddleColumn(Boolean.toString(v));
        ThemeManager.popTextColor();
    }

    private void rowNa(String label, String tooltip) {
        labelCell(label, tooltip);
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        centerSingleValueInMiddleColumn(NA);
        ThemeManager.popTextColor();
    }

    private void numCell(double v, int decimals) {
        ImGui.tableNextColumn();
        ThemeManager.textCenter(String.format(Locale.US, fmtNum(decimals), v));
    }

    private static void emptyCell() {
        ImGui.tableNextColumn();
    }

    private void centerSingleValueInMiddleColumn(String text) {
        ImGui.tableNextColumn();
        ImGui.tableNextColumn();
        ThemeManager.textCenter(text);
        ImGui.tableNextColumn();
        ThemeManager.tableRightmostCellTrailingPad();
    }
}
