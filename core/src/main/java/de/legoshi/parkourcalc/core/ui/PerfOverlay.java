package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

public final class PerfOverlay implements RenderInterface {

    private static final String WINDOW_TITLE = "Perf##perf-overlay";

    @Override
    public void render(ImGuiIO io) {
        if (!ImGui.begin(WINDOW_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.end();
            return;
        }

        long frameNs = Perf.getFrameDurationNs();
        if (frameNs > 0) {
            ImGui.text(String.format(Locale.US, "Frame: %.2f ms (%.0f fps)",
                    frameNs / 1_000_000.0, 1e9 / frameNs));
        }
        ImGui.text("Boxes drawn/frame: " + Perf.getBoxesLastFrame());
        if (ImGui.button("Reset max")) {
            Perf.resetMax();
        }
        ImGui.separator();

        int flags = ImGuiTableFlags.RowBg | ImGuiTableFlags.Borders | ImGuiTableFlags.SizingFixedFit;
        if (ImGui.beginTable("perf-table", 5, flags)) {
            ImGui.tableSetupColumn("Section");
            ImGui.tableSetupColumn("last us");
            ImGui.tableSetupColumn("ema us");
            ImGui.tableSetupColumn("max us");
            ImGui.tableSetupColumn("n/frame");
            ImGui.tableHeadersRow();

            List<Perf.Sample> rows = Perf.snapshot();
            for (Perf.Sample s : rows) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.text(s.name);
                ImGui.tableNextColumn();
                ImGui.text(usFmt(s.lastNs));
                ImGui.tableNextColumn();
                ImGui.text(usFmt(s.emaNs));
                ImGui.tableNextColumn();
                ImGui.text(usFmt(s.maxNs));
                ImGui.tableNextColumn();
                ImGui.text(Integer.toString(s.callsLastFrame));
            }
            ImGui.endTable();
        }

        ImGui.end();
    }

    private static String usFmt(long ns) {
        return String.format(Locale.US, "%.1f", ns / 1000.0);
    }
}
