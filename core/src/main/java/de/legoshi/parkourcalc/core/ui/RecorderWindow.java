package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.io.OsSystemBridge;
import de.legoshi.parkourcalc.core.record.HumanRecorder;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.nio.file.Path;
import java.util.Locale;

public final class RecorderWindow implements RenderInterface {

    private static final String WINDOW_ID = "Recorder";
    private static final float WIN_W = 420f;
    private static final float WIN_H = 170f;

    private final HumanRecorder recorder;
    private final Settings settings;
    private final Runnable toggle;
    private final Runnable onSettingsChanged;
    private final OsSystemBridge systemBridge;
    private final ImBoolean open = new ImBoolean(false);

    public RecorderWindow(HumanRecorder recorder, Settings settings, Runnable toggle, Runnable onSettingsChanged,
                          OsSystemBridge systemBridge) {
        this.recorder = recorder;
        this.settings = settings;
        this.toggle = toggle;
        this.onSettingsChanged = onSettingsChanged;
        this.systemBridge = systemBridge;
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewRecorder);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW_ID, open, ImGuiWindowFlags.NoCollapse)) {
            body();
        }
        ImGui.end();
        if (settings.viewRecorder != open.get()) {
            settings.viewRecorder = open.get();
            onSettingsChanged.run();
        }
    }

    private void body() {
        boolean rec = recorder.isRecording();
        if (rec) {
            if (Controls.dangerButton("Stop recording")) toggle.run();
            ImGui.sameLine();
            ImGui.text(String.format(Locale.ROOT, "%.1f s   %d ticks   %d mouse moves   %d key and button events",
                    recorder.elapsedMs() / 1000.0, recorder.tickCount(), recorder.mouseCount(), recorder.keyCount()));
        } else {
            if (Controls.primaryButton("Start recording")) toggle.run();
        }
        ImGui.spacing();
        ImGui.textWrapped("Records every game tick (position, yaw, pitch, keys) and every raw mouse move, click and key event "
                + "with timestamps, written to disk as you play. Start, close this UI, do the jump a couple of hundred times "
                + "including the failed attempts, then open the UI again and stop.");
        ImGui.spacing();
        Path last = recorder.lastFile();
        String err = recorder.lastError();
        if (err != null) {
            ImGui.textColored(0.95f, 0.5f, 0.5f, 1f, "could not write: " + err);
        } else if (last != null) {
            ImGui.text("saved " + last.getFileName());
        }
        Path dir = recorder.directory();
        if (dir != null && Controls.secondaryButton("Open recordings folder")) {
            systemBridge.openFolder(dir);
        }
    }
}
