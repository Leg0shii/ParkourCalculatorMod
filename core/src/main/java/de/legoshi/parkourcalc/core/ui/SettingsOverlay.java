package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

public final class SettingsOverlay implements RenderInterface {

    private final Settings settings;
    private final Runnable onChanged;
    private final ImInt scaleIndexBuf = new ImInt();
    private final String[] scaleLabels;

    public SettingsOverlay(Settings settings, Runnable onChanged) {
        this.settings = settings;
        this.onChanged = onChanged;
        this.scaleLabels = buildScaleLabels();
    }

    private static String[] buildScaleLabels() {
        String[] labels = new String[Settings.PRESET_SCALES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Settings.PRESET_SCALES[i] + "x";
        }
        return labels;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!ImGui.begin("Settings", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.end();
            return;
        }

        renderToggles();
        ImGui.separator();
        renderScale();
        ImGui.separator();
        renderColors();
        ImGui.separator();

        if (ImGui.button("Reset all")) {
            settings.reset();
            onChanged.run();
        }

        ImGui.end();
    }

    private void renderToggles() {
        if (ImGui.checkbox("Show yaw arrows", settings.showYawArrows)) {
            settings.showYawArrows = !settings.showYawArrows;
            onChanged.run();
        }
        if (ImGui.checkbox("Show Hitbox", settings.showHitbox)) {
            settings.showHitbox = !settings.showHitbox;
            onChanged.run();
        }
        if (ImGui.checkbox("Show Full Hitbox", settings.showFullHitbox)) {
            settings.showFullHitbox = !settings.showFullHitbox;
            onChanged.run();
        }
        if (ImGui.checkbox("Subtick Visualization", settings.showSubtick)) {
            settings.showSubtick = !settings.showSubtick;
            onChanged.run();
        }
    }

    private void renderScale() {
        scaleIndexBuf.set(settings.scaleIndex);
        ImGui.text("UI Scale");
        ImGui.sameLine();
        if (ImGui.combo("##ui_scale", scaleIndexBuf, scaleLabels)) {
            settings.scaleIndex = scaleIndexBuf.get();
            onChanged.run();
        }
    }

    private void renderColors() {
        ImGui.text("Render Colors");
        int flags = ImGuiColorEditFlags.NoInputs;
        renderColor("tick box default", settings.tickDefault, flags);
        renderColor("tick box selected", settings.tickSelected, flags);
        renderColor("tick box in-air", settings.tickAir, flags);
        renderColor("tick box sneak", settings.tickSneak, flags);
        renderColor("subtick path", settings.subtickPath, flags);
        renderColor("yaw arrows", settings.yawArrow, flags);
        renderColor("yaw gizmo circle", settings.yawGizmoCircle, flags);
        renderColor("yaw gizmo direction", settings.yawGizmoDirection, flags);
        renderColor("hitbox default", settings.hitboxDefault, flags);
        renderColor("hitbox selected", settings.hitboxSelected, flags);
    }

    private void renderColor(String label, float[] color, int flags) {
        ImGui.colorEdit4(label, color, flags);
        if (ImGui.isItemDeactivatedAfterEdit()) {
            onChanged.run();
        }
    }
}
