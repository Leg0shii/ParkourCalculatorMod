package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;

/** Read-only inspector for the single currently-selected tick. */
public final class TickInfoPanel implements RenderInterface {

    public interface MoveTickHandler {
        SimulationRunner.MoveTickResult move(int tickIndex, Vec3dCore target);
    }

    private static final String WINDOW_ID = "###tick-info";
    private static final String WINDOW_TITLE = "Tick Info";
    private static final String TABLE_ID = "tick-info-table";
    private static final String PLACEHOLDER_SELECT_ONE = "Select a single tick.";
    private static final String PLACEHOLDER_OUT_OF_RANGE = "No tick data (resimulating).";
    private static final String NA = "n/a";

    private static final String COL_FIELD = "Field";
    private static final String COL_X = "X";
    private static final String COL_Y = "Y";
    private static final String COL_Z = "Z";

    private static final String MOVE_SECTION = "Move tick";
    private static final String MOVE_BUTTON = "Move tick here";
    private static final String MOVE_TOOLTIP =
            "Shifts the start position so this tick lands on the given coordinates. The whole path moves with "
            + "it, so the move is kept only if collisions don't divert this tick away from the target.";
    private static final String MSG_APPLIED = "Moved. Start shifted by (%s, %s, %s).";
    private static final String MSG_COLLISION = "Tick no longer valid: a collision changed the path. Move reverted.";
    private static final String MSG_TICK_LOST = "Tick no longer valid: it disappears after the move. Move reverted.";
    private static final String MSG_BAD_INPUT = "Enter valid numbers for X, Y and Z.";
    private static final String COORD_HINT = "0.0";

    private final BoxController boxController;
    private final SelectionManager selection;
    private final Settings settings;
    private final MoveTickHandler moveTickHandler;
    private int rowCounter;

    private int fmtPrecision = -1;
    private String fmtNum;
    private String fmtNumSingle;
    private String numSample;

    private final ImString moveX = new ImString(32);
    private final ImString moveY = new ImString(32);
    private final ImString moveZ = new ImString(32);
    private int prefilledIndex = -1;
    private double prefilledTickX, prefilledTickY, prefilledTickZ;
    private int statusIndex = -1;
    private String statusMessage;
    private int statusColor;

    public TickInfoPanel(BoxController boxController, SelectionManager selection, Settings settings, MoveTickHandler moveTickHandler) {
        this.boxController = boxController;
        this.selection = selection;
        this.settings = settings;
        this.moveTickHandler = moveTickHandler;
    }

    private void rebuildFormats() {
        int p = Math.min(Math.max(settings.tickInfoPrecision, Settings.MIN_STAT_PRECISION), Settings.MAX_STAT_PRECISION);
        if (p == fmtPrecision) return;
        fmtPrecision = p;
        fmtNum = "%" + (7 + p) + "." + p + "f";
        fmtNumSingle = "%." + p + "f";
        StringBuilder sample = new StringBuilder("-99999.");
        for (int i = 0; i < p; i++) sample.append('9');
        numSample = sample.toString();
    }

    @Override
    public void render(ImGuiIO io) {
        if (!ThemeManager.beginPanel(WINDOW_ID, WINDOW_TITLE,
                ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize)) {
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
        // Facing is an input applied DURING this tick: it lands in states[idx+1].yaw, not the
        // pre-tick facing carried in (states[idx].yaw). Matches the box's outgoing yaw arrow.
        float appliedYaw = idx + 1 < states.size() ? states.get(idx + 1).yaw : cur.yaw;

        renderTable(idx, cur, prev, prev2, appliedYaw);
        renderMoveSection(idx, cur);
        ImGui.end();
    }

    private void renderMoveSection(int idx, TickState cur) {
        if (moveTickHandler == null) return;

        boolean tickChanged = idx != prefilledIndex;
        boolean tickShifted = !tickChanged
                && (cur.position.x != prefilledTickX || cur.position.y != prefilledTickY || cur.position.z != prefilledTickZ);
        if (tickChanged || tickShifted) {
            seedMoveInputs(idx, cur.position);
            if (tickChanged) clearStatus();
        }

        ThemeManager.sectionSpacing();
        ImGui.textDisabled(MOVE_SECTION);
        TooltipUtil.onHover(MOVE_TOOLTIP);
        ThemeManager.bottomPaddedSeparator();

        float fieldW = ImGui.calcTextSize(numSample).x + 2f * ImGui.getStyle().getFramePadding().x;
        int flags = ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.AutoSelectAll | ImGuiInputTextFlags.EnterReturnsTrue;
        boolean submit = false;
        submit |= Controls.inputTextHint("X", COORD_HINT, moveX, fieldW, flags);
        ImGui.sameLine();
        submit |= Controls.inputTextHint("Y", COORD_HINT, moveY, fieldW, flags);
        ImGui.sameLine();
        submit |= Controls.inputTextHint("Z", COORD_HINT, moveZ, fieldW, flags);

        ThemeManager.sectionSpacing();
        if (Controls.primaryButton(MOVE_BUTTON)) submit = true;
        TooltipUtil.onHover(MOVE_TOOLTIP);

        if (submit) applyMove(idx);

        if (statusMessage != null && statusIndex == idx) {
            ThemeManager.sectionSpacing();
            ThemeManager.pushTextColor(statusColor);
            ImGui.pushTextWrapPos(0f);
            ImGui.textWrapped(statusMessage);
            ImGui.popTextWrapPos();
            ThemeManager.popTextColor();
        }
    }

    private void applyMove(int idx) {
        Double x = parse(moveX);
        Double y = parse(moveY);
        Double z = parse(moveZ);
        if (x == null || y == null || z == null) {
            setStatus(idx, MSG_BAD_INPUT, ThemeManager.dangerColor());
            return;
        }
        SimulationRunner.MoveTickResult result = moveTickHandler.move(idx, new Vec3dCore(x, y, z));
        switch (result.status) {
            case APPLIED:
                setStatus(
                        idx,
                        String.format(
                                Locale.US,
                                MSG_APPLIED,
                                String.format(Locale.US, fmtNumSingle, result.offset.x),
                                String.format(Locale.US, fmtNumSingle, result.offset.y),
                                String.format(Locale.US, fmtNumSingle, result.offset.z)
                        ),
                        ThemeManager.okColor()
                );
                break;
            case COLLISION_CHANGED_PATH:
                setStatus(idx, MSG_COLLISION, ThemeManager.dangerColor());
                break;
            case TICK_LOST:
                setStatus(idx, MSG_TICK_LOST, ThemeManager.dangerColor());
                break;
            default:
                setStatus(idx, MSG_TICK_LOST, ThemeManager.dangerColor());
                break;
        }
    }

    private void seedMoveInputs(int idx, Vec3dCore pos) {
        prefilledIndex = idx;
        prefilledTickX = pos.x;
        prefilledTickY = pos.y;
        prefilledTickZ = pos.z;
        moveX.set(String.format(Locale.US, fmtNumSingle, pos.x));
        moveY.set(String.format(Locale.US, fmtNumSingle, pos.y));
        moveZ.set(String.format(Locale.US, fmtNumSingle, pos.z));
    }

    private static Double parse(ImString holder) {
        String text = holder.get().trim();
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setStatus(int idx, String message, int color) {
        statusIndex = idx;
        statusMessage = message;
        statusColor = color;
    }

    private void clearStatus() {
        statusIndex = -1;
        statusMessage = null;
    }

    private void renderTable(int idx, TickState cur, TickState prev, TickState prev2, float appliedYaw) {
        rebuildFormats();
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

        rowInt("Tick", idx + 1, "Tick number (1-based), matching the input table's Tick column.");
        rowNum("Yaw", appliedYaw, "Facing applied during this tick (drives this tick's movement). MC convention: 0 = +Z, increases CW looking down.");

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            rowNum("Speed (XZ)", Math.sqrt(dx * dx + dz * dz),"Horizontal magnitude of actual displacement this tick, sqrt(dx^2 + dz^2), blocks/tick.");
        } else {
            rowNa("Speed (XZ)", "Horizontal magnitude of actual displacement this tick, sqrt(dx^2 + dz^2), blocks/tick.");
        }

        double motionXZ = Math.sqrt(cur.velocity.x * cur.velocity.x + cur.velocity.z * cur.velocity.z);
        double motionXYZ = Math.sqrt(cur.velocity.x * cur.velocity.x + cur.velocity.y * cur.velocity.y + cur.velocity.z * cur.velocity.z);
        rowNum("Motion (XZ)", motionXZ, "Horizontal magnitude of post-tick velocity, sqrt(vx^2 + vz^2), blocks/tick. Differs from Speed on collision ticks.");
        rowNum("Motion (XYZ)", motionXYZ, "Total magnitude of post-tick velocity, sqrt(vx^2 + vy^2 + vz^2), blocks/tick.");

        rowTriple("Position", cur.position.x, cur.position.y, cur.position.z, "Entity position entering this tick, before this tick's input is applied (the previous tick's end position). This is the point a constraint placed on this tick is tested against; this tick's input shows on the next tick. World coords; anchor corner of the rendered tick box.");
        rowTriple("Motion", cur.velocity.x, cur.velocity.y, cur.velocity.z, "Post-tick motionX/Y/Z (after MC's per-axis collision clamp). May read 0 on an axis where a wall was hit.");

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dy = cur.position.y - prev.position.y;
            double dz = cur.position.z - prev.position.z;
            rowTriple("Speed", dx, dy, dz, "Position(i) - position(i-1), the actual displacement vector this tick.");
            rowXZ("Post motion (XZ)", dx, dz, "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Motion on collision-clamp ticks.");
        } else {
            rowNa("Speed", "Position(i) - position(i-1), the actual displacement vector this tick.");
            rowNa("Post motion (XZ)", "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Motion on collision-clamp ticks.");
        }

        if (prev != null && prev2 != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            double pdx = prev.position.x - prev2.position.x;
            double pdz = prev.position.z - prev2.position.z;
            rowXZ("Acceleration (XZ)", dx - pdx, dz - pdz, "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1)).");
        } else {
            rowNa("Acceleration (XZ)", "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1)).");
        }

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            if (dx * dx + dz * dz < 1.0e-18) {
                rowNa("Speed (angle)", "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
            } else {
                rowNum("Speed (angle)", Math.toDegrees(Math.atan2(-dx, dz)), "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
            }
        } else {
            rowNa("Speed (angle)", "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
        }

        rowBool("On ground", cur.onGround, "Entity onGround flag at end of tick.");
        rowBool("Sneaking", cur.sneaking, "Sneak input active during this tick.");
        rowBool("Collision", cur.wallCollision, "Horizontal collision occurred this tick (MC horizontalCollision).");

        if (cur.wallCollision) {
            rowBool("Soft collision", cur.softCollision, "1.21.10 only: grazing wall hit that does NOT break sprint (Entity.collidedSoftly). Always false on 1.8.9/1.12.2.");
        } else {
            rowNa("Soft collision", "1.21.10 only: grazing wall hit that does NOT break sprint. n/a when no horizontal collision is happening.");
        }

        if (Double.isNaN(cur.collisionAngleDegrees)) {
            rowNa("Collision angle (deg)", "1.21.10 only: angle between intended motion (forwardSpeed/sidewaysSpeed rotated by yaw) and post-collision motion. n/a on 1.8.9/1.12.2 or off-collision ticks.");
        } else {
            rowNum("Collision angle (deg)", cur.collisionAngleDegrees, "1.21.10 only: angle between intended motion and post-collision motion. MC keeps sprint when this is below ~8 deg (0.13962634 rad).");
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
        TooltipUtil.onHover(tooltip);
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
        centerSingleValueInMiddleColumn(String.format(Locale.US, fmtNumSingle, v));
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

    private void numCell(double v) {
        ImGui.tableNextColumn();
        ThemeManager.textCenter(String.format(Locale.US, fmtNum, v));
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
