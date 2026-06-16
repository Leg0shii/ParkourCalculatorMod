package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

public final class StartStateTable {

    private static final int PRECISION = 5;

    private final SimulationRunner runner;
    private final Runnable reSimulate;

    private boolean expanded;
    private float measuredContentH = -1f;

    private final Map<String, ImString> fieldBufs = new HashMap<>();
    private String activeField;
    private ImDrawList drawerDrawList;

    public StartStateTable(SimulationRunner runner, Runnable reSimulate) {
        this.runner = runner;
        this.reSimulate = reSimulate;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public void setExpanded(boolean value) {
        expanded = value;
    }

    public ImDrawList drawerDrawList() {
        return drawerDrawList;
    }

    public float drawerHeight() {
        float s = ThemeManager.uiScale();
        float fhs = ImGui.getFrameHeightWithSpacing();
        float inputRow = fhs + 8f * s;
        float spacing = ThemeManager.SM * s;
        float sectionHead = 2f * spacing + fhs;
        float pad = 2f * ThemeManager.LG * s;
        return pad
                + sectionHead + 3f * inputRow
                + sectionHead + 3f * inputRow
                + sectionHead + inputRow
                + fhs;
    }

    public void renderDrawer(float width) {
        float s = ThemeManager.uiScale();
        float pad = ThemeManager.LG * s;
        float h = measuredContentH > 0f ? measuredContentH : drawerHeight();

        ThemeManager.pushDrawerChildBg();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.beginChild("##start_drawer", width, h, false, ImGuiWindowFlags.NoScrollbar);

        ImGui.setCursorPos(pad, pad);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f);
        ImGui.beginChild("##start_drawer_body", width - 2f * pad, h - 2f * pad, false, ImGuiWindowFlags.NoScrollbar);

        Vec3dCore pos = runner.getStartPosition();
        sectionHeader("Position");
        vectorEditor("pos", pos, (x, y, z) -> {
            runner.setStartPosition(new Vec3dCore(x, y, z));
            reSimulate.run();
        });

        ThemeManager.sectionSpacing();
        Vec3dCore vel = runner.getStartVelocity();
        sectionHeader("Velocity");
        vectorEditor("vel", vel, (x, y, z) -> {
            runner.setStartVelocity(new Vec3dCore(x, y, z));
            reSimulate.run();
        });

        ThemeManager.sectionSpacing();
        sectionHeader("Rotation");
        rotationEditor(runner.getStartYaw());

        float spacingY = ImGui.getStyle().getItemSpacing().y;
        float bodyH = ImGui.getCursorPosY() - spacingY;

        ImGui.endChild();
        ImGui.popStyleColor();
        drawerDrawList = ImGui.getWindowDrawList();
        ImGui.endChild();
        ImGui.popStyleVar();
        ThemeManager.popDrawerChildBg();

        measuredContentH = bodyH + 2f * pad;
    }

    private void sectionHeader(String title) {
        ImGui.textDisabled(title);
        ThemeManager.bottomPaddedSeparator();
    }

    private interface Vec3Apply {
        void apply(double x, double y, double z);
    }

    private void vectorEditor(String id, Vec3dCore v, Vec3Apply apply) {
        if (!ThemeManager.beginStandardFormTable("##" + id, 2)) return;
        ImGui.tableSetupColumn("l", ImGuiTableColumnFlags.WidthFixed, axisLabelWidth());
        ImGui.tableSetupColumn("v", ImGuiTableColumnFlags.WidthStretch);
        Controls.pushInputFrameHeight();
        axisRow(id, "X", v.x, nx -> apply.apply(nx, v.y, v.z));
        axisRow(id, "Y", v.y, ny -> apply.apply(v.x, ny, v.z));
        axisRow(id, "Z", v.z, nz -> apply.apply(v.x, v.y, nz));
        Controls.popInputFrameHeight();
        ThemeManager.endStandardFormTable();
    }

    private void rotationEditor(float yaw) {
        if (!ThemeManager.beginStandardFormTable("##rot", 2)) return;
        ImGui.tableSetupColumn("l", ImGuiTableColumnFlags.WidthFixed, axisLabelWidth());
        ImGui.tableSetupColumn("v", ImGuiTableColumnFlags.WidthStretch);
        Controls.pushInputFrameHeight();
        axisRow("rot", "Yaw", yaw, value -> {
            runner.setStartYaw((float) value);
            reSimulate.run();
        });
        Controls.popInputFrameHeight();
        ThemeManager.endStandardFormTable();
    }

    private void axisRow(String group, String label, double value, DoubleConsumer apply) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        Controls.labelCell(label);
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        numberField("##" + group + label, value, apply);
    }

    private void numberField(String id, double value, DoubleConsumer apply) {
        ImString buf = fieldBufs.get(id);
        if (buf == null) {
            buf = new ImString(32);
            fieldBufs.put(id, buf);
        }
        if (!id.equals(activeField)) {
            buf.set(String.format(Locale.ROOT, "%." + PRECISION + "f", value));
        }
        ImGui.inputText(id, buf, ImGuiInputTextFlags.CharsDecimal);
        if (ImGui.isItemActivated()) {
            activeField = id;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            try {
                apply.accept(Double.parseDouble(buf.get().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (ImGui.isItemDeactivated() && id.equals(activeField)) {
            activeField = null;
        }
    }

    private float axisLabelWidth() {
        float max = 0f;
        for (String l : new String[]{"X", "Y", "Z", "Yaw"}) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * ThemeManager.uiScale();
    }
}
