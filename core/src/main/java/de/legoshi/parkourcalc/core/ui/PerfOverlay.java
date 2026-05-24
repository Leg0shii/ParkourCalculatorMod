package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiTableRowFlags;
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

        if (ImGui.beginTable("perf-table", 5, ThemeManager.standardTableFlags())) {
            int fixed = imgui.flag.ImGuiTableColumnFlags.WidthFixed;
            // Data-width estimates picked to cover typical Perf samples; the
            // helper takes max(boldHeader, data) so headers never clip even
            // when data is tiny ("0" / "1" in n/frame).
            ImGui.tableSetupColumn("Section", fixed,
                    ThemeManager.tableLeftmostColumnWidth("Section", 200));
            ImGui.tableSetupColumn("last us", fixed,
                    ThemeManager.tableColumnWidth("last us", 80));
            ImGui.tableSetupColumn("ema us", fixed,
                    ThemeManager.tableColumnWidth("ema us", 80));
            ImGui.tableSetupColumn("max us", fixed,
                    ThemeManager.tableColumnWidth("max us", 80));
            ImGui.tableSetupColumn("n/frame", fixed,
                    ThemeManager.tableRightmostColumnWidth("n/frame", 50));
            renderHeader("Section", "last us", "ema us", "max us", "n/frame");

            List<Perf.Sample> rows = Perf.snapshot();
            int rowIndex = 0;
            for (Perf.Sample s : rows) {
                ImGui.tableNextRow();
                ThemeManager.paintTableRowBg(rowIndex++);
                firstCell(s.name);
                cell(usFmt(s.lastNs));
                cell(usFmt(s.emaNs));
                cell(usFmt(s.maxNs));
                lastCell(Integer.toString(s.callsLastFrame));
            }
            ImGui.endTable();
        }

        ImGui.end();
    }

    private static void renderHeader(String... labels) {
        ImGui.tableNextRow(ImGuiTableRowFlags.Headers);
        ThemeManager.paintTableHeader();
        int last = labels.length - 1;
        for (int i = 0; i < labels.length; i++) {
            ImGui.tableSetColumnIndex(i);
            if (i == 0) ThemeManager.emitTableLeftmostCellPad();
            ThemeManager.tableHeader(labels[i]);
            if (i == last) ThemeManager.emitTableRightmostCellTrailingPad();
        }
    }

    private static void firstCell(String text) {
        ImGui.tableNextColumn();
        ThemeManager.emitTableLeftmostCellPad();
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    private static void cell(String text) {
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    private static void lastCell(String text) {
        cell(text);
        ThemeManager.emitTableRightmostCellTrailingPad();
    }

    private static String usFmt(long ns) {
        return String.format(Locale.US, "%.1f", ns / 1000.0);
    }
}
