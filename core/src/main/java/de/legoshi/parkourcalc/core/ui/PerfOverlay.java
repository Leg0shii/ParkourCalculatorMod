package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager.HAlign;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableRowFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

public final class PerfOverlay implements RenderInterface {

    private static final String WINDOW_ID = "###perf-overlay";
    private static final String WINDOW_TITLE = "Perf";

    @Override
    public void render(ImGuiIO io) {
        ThemeManager.pushHeaderChrome();
        if (!ImGui.begin(WINDOW_ID, ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.end();
            ThemeManager.popHeaderChrome();
            return;
        }
        ThemeManager.drawModalTitle(WINDOW_TITLE);
        ThemeManager.popHeaderChrome();

        long frameNs = Perf.getFrameDurationNs();
        if (frameNs > 0) {
            ImGui.text(String.format(Locale.US, "Frame: %.2f ms (%.0f fps)",
                    frameNs / 1_000_000.0, 1e9 / frameNs));
        }
        ImGui.text("Boxes drawn/frame: " + Perf.getBoxesLastFrame());
        if (Controls.secondaryButton("Reset max")) {
            Perf.resetMax();
        }
        ThemeManager.paddedSeparator();

        // Measure max observed widths per frame so the columns grow with the data
        // and never clip. Hardcoded data widths under-allocated as soon as any
        // section's microsecond value reached 5 digits.
        List<Perf.Sample> rows = Perf.snapshot();
        float maxSectionW = 0f, maxLastW = 0f, maxEmaW = 0f, maxMaxW = 0f, maxNW = 0f;
        for (Perf.Sample s : rows) {
            maxSectionW = Math.max(maxSectionW, ImGui.calcTextSize(s.name).x);
            maxLastW = Math.max(maxLastW, ImGui.calcTextSize(usFmt(s.lastNs)).x);
            maxEmaW = Math.max(maxEmaW, ImGui.calcTextSize(usFmt(s.emaNs)).x);
            maxMaxW = Math.max(maxMaxW, ImGui.calcTextSize(usFmt(s.maxNs)).x);
            maxNW = Math.max(maxNW, ImGui.calcTextSize(Integer.toString(s.callsLastFrame)).x);
        }

        if (ThemeManager.beginStandardTable("perf-table", 5)) {
            int fixed = ImGuiTableColumnFlags.WidthFixed;
            // Section: same 2*cellPadX padding as the numeric helper, plus the
            // leftmost column's leading-dummy reservation (scrollbarSlack - cellPad)
            // so the column allocation matches what tableLeftmostCellPad consumes.
            float cellPad = ImGui.getStyle().getCellPadding().x;
            float leadingInset = Math.max(0f, ThemeManager.tableScrollbarSlack() - cellPad);
            ImGui.tableSetupColumn("Section", fixed,
                    ThemeManager.tableNumericColumnWidth("Section", maxSectionW) + leadingInset);
            ImGui.tableSetupColumn("last us", fixed,
                    ThemeManager.tableNumericColumnWidth("last us", maxLastW));
            ImGui.tableSetupColumn("ema us", fixed,
                    ThemeManager.tableNumericColumnWidth("ema us", maxEmaW));
            ImGui.tableSetupColumn("max us", fixed,
                    ThemeManager.tableNumericColumnWidth("max us", maxMaxW));
            ImGui.tableSetupColumn("n/frame", fixed,
                    ThemeManager.tableRightmostColumnWidth("n/frame", maxNW, ThemeManager.tableScrollbarSlack()));
            renderHeader();

            int rowIndex = 0;
            for (Perf.Sample s : rows) {
                ImGui.tableNextRow();
                ThemeManager.paintTableRowBg(rowIndex++);

                ImGui.tableNextColumn();
                ThemeManager.tableLeftmostCellPad();
                ThemeManager.textLeft(s.name);

                ImGui.tableNextColumn();
                ThemeManager.textRight(usFmt(s.lastNs));

                ImGui.tableNextColumn();
                ThemeManager.textRight(usFmt(s.emaNs));

                ImGui.tableNextColumn();
                ThemeManager.textRight(usFmt(s.maxNs));

                ImGui.tableNextColumn();
                ThemeManager.textRight(Integer.toString(s.callsLastFrame));
                ThemeManager.tableRightmostCellTrailingPad();
            }
            ThemeManager.endStandardTable();
        }

        ImGui.end();
    }

    private static void renderHeader() {
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();

        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader("Section", HAlign.LEFT);

        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeaderRight("last us");
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeaderRight("ema us");
        ImGui.tableSetColumnIndex(3);
        ThemeManager.tableHeaderRight("max us");
        ImGui.tableSetColumnIndex(4);
        ThemeManager.tableHeaderRight("n/frame");
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private static String usFmt(long ns) {
        return String.format(Locale.US, "%.1f", ns / 1000.0);
    }
}
