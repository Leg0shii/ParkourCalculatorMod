package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

/** Read-only inspector for the single currently-selected tick. */
public final class TickInfoPanel implements RenderInterface {

    private static final String WINDOW_TITLE = "Tick Info";
    private static final String TABLE_ID = "tick-info-table";
    private static final String PLACEHOLDER_SELECT_ONE = "Select a single tick.";
    private static final String PLACEHOLDER_OUT_OF_RANGE = "No tick data (resimulating).";
    private static final String NA = "n/a";

    private static final String FMT_NUM = "%12.5f";
    private static final String NUM_SAMPLE = "-99999.99999";
    private static final String FMT_NUM_SINGLE = "%.5f";

    private static final String COL_FIELD = "Field";
    private static final String COL_X = "X";
    private static final String COL_Y = "Y";
    private static final String COL_Z = "Z";

    private final BoxController boxController;
    private final SelectionManager selection;
    private int rowCounter;

    public TickInfoPanel(BoxController boxController, SelectionManager selection) {
        this.boxController = boxController;
        this.selection = selection;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!ImGui.begin(WINDOW_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.end();
            return;
        }

        if (selection.size() != 1) {
            ImGui.text(PLACEHOLDER_SELECT_ONE);
            ImGui.end();
            return;
        }

        int idx = selection.getSelected().iterator().next();
        List<TickState> states = boxController.getStates();
        if (idx < 0 || idx >= states.size()) {
            ImGui.text(PLACEHOLDER_OUT_OF_RANGE);
            ImGui.end();
            return;
        }

        TickState cur = states.get(idx);
        TickState prev = idx > 0 ? states.get(idx - 1) : null;
        TickState prev2 = idx > 1 ? states.get(idx - 2) : null;

        renderTable(idx, cur, prev, prev2);
        ImGui.end();
    }

    private void renderTable(int idx, TickState cur, TickState prev, TickState prev2) {
        if (!ThemeManager.beginStandardKeyValueTable(TABLE_ID, 4, 0, 0f, 0f)) {
            return;
        }
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float cellPad = ImGui.getStyle().getCellPadding().x;
        float labelDataW = ImGui.calcTextSize("Collision angle (deg)").x + 2f * cellPad;
        float numW = ImGui.calcTextSize(NUM_SAMPLE).x;
        ImGui.tableSetupColumn(COL_FIELD, fixed,
                ThemeManager.tableLeftmostColumnWidth(COL_FIELD, labelDataW));
        ImGui.tableSetupColumn(COL_X, fixed, ThemeManager.tableColumnWidth(COL_X, numW));
        ImGui.tableSetupColumn(COL_Y, fixed, ThemeManager.tableColumnWidth(COL_Y, numW));
        ImGui.tableSetupColumn(COL_Z, fixed, ThemeManager.tableRightmostColumnWidth(COL_Z, numW, ThemeManager.tableScrollbarSlack()));

        rowCounter = 0;

        rowInt("Tick", idx, "Index of this tick in the simulated path (0 = start).");
        rowNum("Facing (deg)", cur.yaw,
                "Entity yaw in degrees, MC convention: 0 = +Z, increases CW looking down.");

        double speedH = Math.sqrt(cur.velocity.x * cur.velocity.x + cur.velocity.z * cur.velocity.z);
        double speedT = Math.sqrt(cur.velocity.x * cur.velocity.x
                + cur.velocity.y * cur.velocity.y
                + cur.velocity.z * cur.velocity.z);
        rowNum("Speed (H)", speedH,
                "Horizontal speed: sqrt(vx^2 + vz^2). From post-tick velocity (blocks/tick).");
        rowNum("Speed (T)", speedT,
                "Total speed: sqrt(vx^2 + vy^2 + vz^2). From post-tick velocity (blocks/tick).");

        rowTriple("Position", cur.position.x, cur.position.y, cur.position.z,
                "Entity position at end of tick (world coords). Anchor corner of the rendered tick box.");
        rowTriple("Velocity", cur.velocity.x, cur.velocity.y, cur.velocity.z,
                "Post-tick motionX/Y/Z (after MC's per-axis collision clamp). May read 0 on an axis where a wall was hit.");

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dy = cur.position.y - prev.position.y;
            double dz = cur.position.z - prev.position.z;
            rowTriple("Delta pos", dx, dy, dz,
                    "Position change from previous tick: position(i) - position(i-1). Actually-applied displacement.");
            rowXZ("Post motion (XZ)", dx, dz,
                    "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Velocity on collision-clamp ticks.");
        } else {
            rowNa("Delta pos",
                    "Position change from previous tick: position(i) - position(i-1). Actually-applied displacement.");
            rowNa("Post motion (XZ)",
                    "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Velocity on collision-clamp ticks.");
        }

        if (prev != null && prev2 != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            double pdx = prev.position.x - prev2.position.x;
            double pdz = prev.position.z - prev2.position.z;
            rowXZ("Acceleration (XZ)", dx - pdx, dz - pdz,
                    "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1)).");
        } else {
            rowNa("Acceleration (XZ)",
                    "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1)).");
        }

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            if (dx * dx + dz * dz < 1.0e-18) {
                rowNa("Move angle (deg)",
                        "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
            } else {
                rowNum("Move angle (deg)", Math.toDegrees(Math.atan2(-dx, dz)),
                        "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
            }
        } else {
            rowNa("Move angle (deg)",
                    "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
        }

        rowBool("On ground", cur.onGround,
                "Entity onGround flag at end of tick.");
        rowBool("Sneaking", cur.sneaking,
                "Sneak input active during this tick.");
        rowBool("Collision", cur.wallCollision,
                "Horizontal collision occurred this tick (MC horizontalCollision).");

        if (cur.wallCollision) {
            rowBool("Soft collision", cur.softCollision,
                    "1.21.10 only: grazing wall hit that does NOT break sprint (Entity.collidedSoftly). Always false on 1.8.9/1.12.2.");
        } else {
            rowNa("Soft collision",
                    "1.21.10 only: grazing wall hit that does NOT break sprint. n/a when no horizontal collision is happening.");
        }

        if (Double.isNaN(cur.collisionAngleDegrees)) {
            rowNa("Collision angle (deg)",
                    "1.21.10 only: angle between intended motion (forwardSpeed/sidewaysSpeed rotated by yaw) and post-collision motion. n/a on 1.8.9/1.12.2 or off-collision ticks.");
        } else {
            rowNum("Collision angle (deg)", cur.collisionAngleDegrees,
                    "1.21.10 only: angle between intended motion and post-collision motion. MC keeps sprint when this is below ~8 deg (0.13962634 rad).");
        }

        ThemeManager.endStandardTable();
    }

    private void labelCell(String label, String tooltip) {
        ImGui.tableNextRow();
        ThemeManager.paintTableRowBg(rowCounter++);
        ImGui.tableNextColumn();
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ThemeManager.textLeft(label);
        ThemeManager.popTextColor();
        if (ImGui.isItemHovered()) {
            TooltipUtil.wrappedTooltip(tooltip);
        }
    }

    private void rowTriple(String label, double x, double y, double z, String tooltip) {
        labelCell(label, tooltip);
        numCell(x);
        numCell(y);
        numCell(z);
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void rowXZ(String label, double x, double z, String tooltip) {
        labelCell(label, tooltip);
        numCell(x);
        emptyCell();
        numCell(z);
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void rowNum(String label, double v, String tooltip) {
        labelCell(label, tooltip);
        centerSingleValueInMiddleColumn(String.format(Locale.US, FMT_NUM_SINGLE, v));
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

    private static void numCell(double v) {
        ImGui.tableNextColumn();
        ThemeManager.textCenter(String.format(Locale.US, FMT_NUM, v));
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
