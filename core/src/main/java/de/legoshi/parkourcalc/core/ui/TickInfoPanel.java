package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.sim.TickState;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

/**
 * Read-only inspector for the single currently-selected tick. Hidden as a placeholder when
 * 0 or >1 ticks are selected. Reads the same TickState list BoxController owns, so values
 * refresh in place after every runSimulation() without explicit listener wiring.
 */
public final class TickInfoPanel implements RenderInterface {

    private static final String WINDOW_TITLE = "Tick Info";
    private static final String TABLE_ID = "tick-info-table";
    private static final String PLACEHOLDER_SELECT_ONE = "Select a single tick.";
    private static final String PLACEHOLDER_OUT_OF_RANGE = "No tick data (resimulating).";
    private static final String NA = "n/a";

    // Triples and pairs use %10.5f so values right-align in the panel's monospace font
    // (JetBrainsMono is the project font). Speed inlines both H and T on one row.
    private static final String FMT_TRIPLE = "%10.5f  %10.5f  %10.5f";
    private static final String FMT_PAIR = "%10.5f  %10.5f";
    private static final String FMT_SPEED = "H %.4f  T %.4f";
    private static final String FMT_ANGLE = "%.3f";

    // Tighter than ImGui defaults so the panel doesn't push other overlays around.
    private static final float CELL_PAD_X = 4.0f;
    private static final float CELL_PAD_Y = 1.0f;
    private static final float ITEM_SPACING_X = 4.0f;
    private static final float ITEM_SPACING_Y = 1.0f;

    private static final float LABEL_R = 0.65f;
    private static final float LABEL_G = 0.65f;
    private static final float LABEL_B = 0.65f;
    private static final float LABEL_A = 1.0f;

    private final BoxController boxController;
    private final SelectionManager selection;

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

        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, CELL_PAD_X, CELL_PAD_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ITEM_SPACING_X, ITEM_SPACING_Y);
        try {
            renderTable(idx, cur, prev, prev2);
        } finally {
            ImGui.popStyleVar(2);
        }
        ImGui.end();
    }

    private void renderTable(int idx, TickState cur, TickState prev, TickState prev2) {
        if (!ImGui.beginTable(TABLE_ID, 2, ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.RowBg)) {
            return;
        }

        row("Tick", Integer.toString(idx),
                "Index of this tick in the simulated path (0 = start).");
        row("Facing", String.format(Locale.US, FMT_ANGLE, cur.yaw),
                "Entity yaw in degrees, MC convention: 0 = +Z, increases CW looking down.");

        double speedH = Math.sqrt(cur.velocity.x * cur.velocity.x + cur.velocity.z * cur.velocity.z);
        double speedT = Math.sqrt(cur.velocity.x * cur.velocity.x
                + cur.velocity.y * cur.velocity.y
                + cur.velocity.z * cur.velocity.z);
        row("Speed", String.format(Locale.US, FMT_SPEED, speedH, speedT),
                "H = sqrt(vx^2 + vz^2); T = sqrt(vx^2 + vy^2 + vz^2). Both from post-tick velocity (blocks/tick).");

        row("Position",
                String.format(Locale.US, FMT_TRIPLE, cur.position.x, cur.position.y, cur.position.z),
                "Entity position at end of tick, world coordinates (blocks).");
        row("Velocity",
                String.format(Locale.US, FMT_TRIPLE, cur.velocity.x, cur.velocity.y, cur.velocity.z),
                "Post-tick motionX/Y/Z (after MC's per-axis collision clamp). May read 0 on an axis where a wall was hit.");

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dy = cur.position.y - prev.position.y;
            double dz = cur.position.z - prev.position.z;
            row("Delta Pos", String.format(Locale.US, FMT_TRIPLE, dx, dy, dz),
                    "Position change from previous tick: position(i) - position(i-1). Actually-applied displacement.");
        } else {
            row("Delta Pos", NA,
                    "Position change from previous tick: position(i) - position(i-1). Actually-applied displacement.");
        }

        row("On ground", Boolean.toString(cur.onGround),
                "Entity onGround flag at end of tick.");
        row("Sneaking", Boolean.toString(cur.sneaking),
                "Sneak input active during this tick.");

        row("Collision", Boolean.toString(cur.wallCollision),
                "Horizontal collision occurred this tick (MC horizontalCollision).");
        row("Soft collision", Boolean.toString(cur.softCollision),
                "1.21.10 only: grazing wall hit that does NOT break sprint (Entity.collidedSoftly). Always false on 1.8.9/1.12.2.");
        String angleStr = Double.isNaN(cur.collisionAngleDegrees)
                ? NA
                : String.format(Locale.US, FMT_ANGLE, cur.collisionAngleDegrees);
        row("Collision angle (deg)", angleStr,
                "1.21.10 only: angle between intended motion (forwardSpeed/sidewaysSpeed rotated by yaw) and post-collision motion. MC keeps sprint when this is below ~8 deg (0.13962634 rad). Off-collision ticks read ~0; n/a on 1.8.9/1.12.2 (any horizontal collision breaks sprint there).");

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            row("Post motion (XZ)", String.format(Locale.US, FMT_PAIR, dx, dz),
                    "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Velocity on collision-clamp ticks.");
        } else {
            row("Post motion (XZ)", NA,
                    "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Velocity on collision-clamp ticks.");
        }

        if (prev != null && prev2 != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            double pdx = prev.position.x - prev2.position.x;
            double pdz = prev.position.z - prev2.position.z;
            row("Acceleration (XZ)", String.format(Locale.US, FMT_PAIR, dx - pdx, dz - pdz),
                    "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1)).");
        } else {
            row("Acceleration (XZ)", NA,
                    "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1)).");
        }

        if (prev != null) {
            double dx = cur.position.x - prev.position.x;
            double dz = cur.position.z - prev.position.z;
            if (dx * dx + dz * dz < 1.0e-18) {
                row("Angle (deg)", NA,
                        "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
            } else {
                double angle = Math.toDegrees(Math.atan2(-dx, dz));
                row("Angle (deg)", String.format(Locale.US, FMT_ANGLE, angle),
                        "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
            }
        } else {
            row("Angle (deg)", NA,
                    "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz)).");
        }

        ImGui.endTable();
    }

    private void row(String label, String value, String tooltip) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.pushStyleColor(ImGuiCol.Text, LABEL_R, LABEL_G, LABEL_B, LABEL_A);
        ImGui.text(label);
        ImGui.popStyleColor();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        ImGui.tableNextColumn();
        ImGui.text(value);
    }
}
