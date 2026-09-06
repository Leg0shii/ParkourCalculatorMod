package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
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
    private static final String COPY_TELEPORT_LABEL = "Copy teleport command";

    private final SimulationRunner runner;
    private final Runnable reSimulate;
    private final Runnable copyTeleport;

    private boolean expanded;
    private float measuredContentH = -1f;
    private StartResumeState shownResume;

    private final Map<String, ImString> fieldBufs = new HashMap<>();
    private String activeField;
    private ImDrawList drawerDrawList;

    public StartStateTable(SimulationRunner runner, Runnable reSimulate, Runnable copyTeleport) {
        this.runner = runner;
        this.reSimulate = reSimulate;
        this.copyTeleport = copyTeleport;
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
                + sectionHead + 2f * inputRow
                + spacing + inputRow
                + sectionHead + 9f * inputRow
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
        rotationEditor(runner.getStartYaw(), runner.getStartPitch());

        ThemeManager.sectionSpacing();
        if (Controls.secondaryButton(COPY_TELEPORT_LABEL, ImGui.getContentRegionAvail().x) && copyTeleport != null) {
            copyTeleport.run();
        }

        ThemeManager.sectionSpacing();
        sectionHeader("Resume state");
        resumeEditor();

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

    private void rotationEditor(float yaw, float pitch) {
        if (!ThemeManager.beginStandardFormTable("##rot", 2)) return;
        ImGui.tableSetupColumn("l", ImGuiTableColumnFlags.WidthFixed, axisLabelWidth());
        ImGui.tableSetupColumn("v", ImGuiTableColumnFlags.WidthStretch);
        Controls.pushInputFrameHeight();
        axisRow("rot", "Yaw", yaw, value -> {
            runner.setStartYaw((float) value);
            reSimulate.run();
        });
        axisRow("rot", "Pitch", pitch, value -> {
            runner.setStartPitch((float) value);
            reSimulate.run();
        });
        Controls.popInputFrameHeight();
        ThemeManager.endStandardFormTable();
    }

    private void resumeEditor() {
        StartResumeState pinned = runner.getStartResumeState();
        if (pinned != null) {
            shownResume = pinned;
        } else {
            StartResumeState derived = runner.describeResumeAt(0);
            shownResume = derived != null ? derived : new StartResumeState();
        }
        StartResumeState resume = shownResume;

        if (!ThemeManager.beginStandardFormTable("##resume", 2)) return;
        ImGui.tableSetupColumn("l", ImGuiTableColumnFlags.WidthFixed, resumeLabelWidth());
        ImGui.tableSetupColumn("v", ImGuiTableColumnFlags.WidthStretch);
        Controls.pushInputFrameHeight();
        boolRow("On ground", "##res_ground", resume.onGround, v -> resume.onGround = v);
        boolRow("Sprinting", "##res_sprinting", resume.sprinting, v -> resume.sprinting = v);
        boolRow("Wall contact", "##res_wall", resume.wallContact, v -> resume.wallContact = v);
        boolRow("Grazing contact", "##res_soft", resume.softWallContact, v -> resume.softWallContact = v);
        boolRow("Cobweb slow", "##res_web", resume.stuckMultiplier != null,
                v -> resume.stuckMultiplier = v ? new Vec3dCore(0.25, 0.05, 0.25) : null);
        intRow("Jump cooldown", "##res_jumpcd", resume.jumpCooldown, v -> resume.jumpCooldown = clampInt(v, 0, 10));
        intRow("Sprint window", "##res_window", resume.sprintWindow, v -> resume.sprintWindow = clampInt(v, 0, 7));
        heldKeysRows(resume);
        Controls.popInputFrameHeight();
        ThemeManager.endStandardFormTable();
    }

    private void pinShownResume() {
        if (runner.getStartResumeState() == null) {
            runner.setStartResumeState(shownResume);
        }
        reSimulate.run();
    }

    private interface BoolApply {
        void apply(boolean value);
    }

    private interface IntApply {
        void apply(int value);
    }

    private void boolRow(String label, String id, boolean value, BoolApply apply) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        Controls.labelCell(label);
        ImGui.tableNextColumn();
        if (ImGui.checkbox(id, value)) {
            apply.apply(!value);
            pinShownResume();
        }
    }

    private void intRow(String label, String id, int value, IntApply apply) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        Controls.labelCell(label);
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        ImString buf = fieldBufs.get(id);
        if (buf == null) {
            buf = new ImString(8);
            fieldBufs.put(id, buf);
        }
        if (!id.equals(activeField)) {
            buf.set(Integer.toString(value));
        }
        ImGui.inputText(id, buf, ImGuiInputTextFlags.CharsDecimal);
        if (ImGui.isItemActivated()) {
            activeField = id;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            try {
                apply.apply(Integer.parseInt(buf.get().trim()));
                pinShownResume();
            } catch (NumberFormatException ignored) {
            }
        }
        if (ImGui.isItemDeactivated() && id.equals(activeField)) {
            activeField = null;
        }
    }

    private void heldKeysRows(StartResumeState resume) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        Controls.labelCell("Held last tick");
        ImGui.tableNextColumn();
        keyToggle(resume, InputRow.Key.W, "W");
        ImGui.sameLine();
        keyToggle(resume, InputRow.Key.A, "A");
        ImGui.sameLine();
        keyToggle(resume, InputRow.Key.S, "S");
        ImGui.sameLine();
        keyToggle(resume, InputRow.Key.D, "D");
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.tableNextColumn();
        keyToggle(resume, InputRow.Key.JUMP, "Jump");
        ImGui.sameLine();
        keyToggle(resume, InputRow.Key.SNEAK, "Sneak");
        ImGui.sameLine();
        keyToggle(resume, InputRow.Key.SPRINT, "Sprint");
    }

    private void keyToggle(StartResumeState resume, InputRow.Key key, String label) {
        boolean held = resume.heldLastTick.contains(key);
        if (ImGui.checkbox(label + "##res_held_" + key.name(), held)) {
            if (held) {
                resume.heldLastTick.remove(key);
            } else {
                resume.heldLastTick.add(key);
            }
            pinShownResume();
        }
    }

    private static int clampInt(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private float resumeLabelWidth() {
        float max = 0f;
        for (String l : new String[]{"On ground", "Sprinting", "Wall contact", "Grazing contact",
                "Cobweb slow", "Jump cooldown", "Sprint window", "Held last tick"}) {
            max = Math.max(max, ImGui.calcTextSize(l).x);
        }
        return max + ThemeManager.SM * ThemeManager.uiScale();
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
        for (String l : new String[]{"X", "Y", "Z", "Yaw", "Pitch"}) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * ThemeManager.uiScale();
    }
}
