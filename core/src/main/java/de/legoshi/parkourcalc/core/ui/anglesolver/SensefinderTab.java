package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratfinderWindow.Ending;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;

import java.util.List;
import java.util.Locale;

public final class SensefinderTab {

    public interface Host {
        boolean isBusy();

        Ending ending();

        String stage();

        double fraction();

        double elapsedSeconds();

        String outcome();

        List<SenseFinder.Candidate> results();

        SenseFinder.Candidate selected();

        SenseFinder.Turn turn();

        int startTick();

        boolean hasLine();

        String lineText();

        boolean lineStale();

        String goalWallLabel();

        SenseFinder.Mode rankMode();

        void setRankMode(SenseFinder.Mode mode);

        int fromPercent();

        void setFromPercent(int percent);

        void start();

        void cancel();

        void select(SenseFinder.Candidate candidate);
    }

    private static final String TABLE_ID = "##sensefinder_senses";
    private static final String COL_RANK = "#";
    private static final String COL_SENSE = "Sense";
    private static final String COL_MARGIN = "Margin";
    private static final String COL_OFFSET = "Offset";
    private static final String COL_PIXELS = "Pixels";
    private static final int DETAIL_ROWS = 6;
    private static final int TURN_LINES = 3;
    private static final String SEARCH_TIP = "Sweep the mouse sensitivity from the slider value up to 200% for the"
            + " selected line. Every angle of its turn is rounded to whole mouse pixels at that sense and the rounded"
            + " path is forwarded byte-exact; a sense is listed when the rounded turn still lands. Click one to write"
            + " its pixel-exact facings into the TAS.";
    private static final String CANCEL_TIP = "Stop now. Nothing is listed until the sweep finishes.";
    private static final String FROM_TIP = "Lowest sense to sweep. The sweep always ends at 200%.";
    private static final String MARGIN_TIP = "How far the first angle's listed pixel count sits inside its facing"
            + " window, in degrees: the smaller of the room below and above before the line misses, with every later"
            + " facing carried along. A sense changes only where its pixel grid falls in that window, so this is the"
            + " robustness the sense buys against anything that is not a whole pixel. The detail row has every"
            + " angle.\n\nClick to sort: largest first-angle margin first, then the second angle, and so on; offset"
            + " breaks ties.";
    private static final String OFFSET_TIP = "Landing offset past the goal wall on the objective axis, in blocks,"
            + " with every angle at its listed pixels.\n\nClick to sort furthest first; ties fall back to the"
            + " sense order.";
    private static final String SENSE_TIP = "Mouse sensitivity as the options screen shows it. The sweep lists the"
            + " value with the best offset inside the range where these pixel counts land.\n\nClick to sort highest"
            + " first: a coarser pixel grid snaps more of a human's attempts onto the one count that lands.";
    private static final String PIXELS_TIP = "Mouse pixels to move per angle of the turn, in tick order, signed"
            + " like the facing change.";
    private static final String ROW_TIP = "Click to write this sense's pixel-exact facings into the TAS.";

    private final Host host;
    private final Runnable close;
    private final int[] fromBuf = new int[1];

    public SensefinderTab(Host host, Runnable close) {
        this.host = host;
        this.close = close;
    }

    public void render() {
        renderLine();
        renderTable();
        renderSelected();
        ThemeManager.sectionSpacing();
        renderProgress();
        renderActions();
    }

    private void renderLine() {
        Fonts.pushBold();
        ImGui.text("Senses");
        Fonts.popBold();
        ImGui.sameLine();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        String line = host.lineText();
        if (line == null) ImGui.text(host.hasLine() ? "for the selected line" : "select a line in Lines first");
        else ImGui.text(line + (host.lineStale() ? "  (the selected line changed)" : ""));
        ThemeManager.popTextColor();
    }

    private void renderTable() {
        List<SenseFinder.Candidate> results = host.results();
        SenseFinder.Candidate selected = host.selected();
        float reserve = selectedHeight(selected != null) + footerHeight();
        float tableH = Math.max(ImGui.getTextLineHeightWithSpacing() * 4f, ImGui.getContentRegionAvail().y - reserve);
        if (!ThemeManager.beginStandardClickableRowsTable(TABLE_ID, 5, 0, 0f, tableH)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float rankW = ImGui.calcTextSize("999").x;
        float senseW = ImGui.calcTextSize("199.9999%").x;
        float marginW = ImGui.calcTextSize("10.0000\u00b0").x;
        float offW = ImGui.calcTextSize("+" + ConstraintText.fixedStat(99.0)).x;
        float mark = ImGui.getFontSize() * 0.7f;
        ImGui.tableSetupColumn(COL_RANK, fixed, ThemeManager.tableLeftmostColumnWidth(COL_RANK, rankW));
        ImGui.tableSetupColumn(COL_SENSE, fixed, ThemeManager.tableNumericColumnWidth(COL_SENSE, senseW));
        ImGui.tableSetupColumn(COL_MARGIN, fixed, ThemeManager.tableNumericColumnWidth(COL_MARGIN, marginW) + mark);
        ImGui.tableSetupColumn(COL_OFFSET, fixed, ThemeManager.tableNumericColumnWidth(COL_OFFSET, offW) + mark);
        ImGui.tableSetupColumn(COL_PIXELS, ImGuiTableColumnFlags.WidthStretch, 0f);
        renderHeader();
        float rowH = ThemeManager.tableRowHeight();
        for (int i = 0; i < results.size(); i++) {
            SenseFinder.Candidate c = results.get(i);
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(i);
            ImGui.tableSetColumnIndex(0);
            ThemeManager.tableLeftmostCellPad();
            ImGui.alignTextToFramePadding();
            if (ImGui.selectable((i + 1) + "##sense" + i, c == selected, ImGuiSelectableFlags.SpanAllColumns)) {
                host.select(c);
            }
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(ROW_TIP);
            ImGui.tableSetColumnIndex(1);
            ThemeManager.textRight(percentText(c.sens));
            ImGui.tableSetColumnIndex(2);
            ThemeManager.textRight(StratfinderWindow.degreesText(c.margin(0)));
            ImGui.tableSetColumnIndex(3);
            ThemeManager.textRight(offsetText(c.offset));
            ImGui.tableSetColumnIndex(4);
            ThemeManager.textLeft(pixelsText(c.pixels));
        }
        ThemeManager.endStandardTable();
    }

    private void renderHeader() {
        SenseFinder.Mode mode = host.rankMode();
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader(COL_RANK);
        ImGui.tableSetColumnIndex(1);
        if (ThemeManager.tableSortHeader(COL_SENSE, ThemeManager.HAlign.RIGHT, mode == SenseFinder.Mode.SENSE)) {
            host.setRankMode(SenseFinder.Mode.SENSE);
        }
        TooltipUtil.onHover(SENSE_TIP);
        ImGui.tableSetColumnIndex(2);
        if (ThemeManager.tableSortHeader(COL_MARGIN, ThemeManager.HAlign.RIGHT, mode == SenseFinder.Mode.MARGIN)) {
            host.setRankMode(SenseFinder.Mode.MARGIN);
        }
        TooltipUtil.onHover(MARGIN_TIP);
        ImGui.tableSetColumnIndex(3);
        if (ThemeManager.tableSortHeader(COL_OFFSET, ThemeManager.HAlign.RIGHT, mode == SenseFinder.Mode.FURTHEST)) {
            host.setRankMode(SenseFinder.Mode.FURTHEST);
        }
        TooltipUtil.onHover(OFFSET_TIP);
        ImGui.tableSetColumnIndex(4);
        ThemeManager.tableHeader(COL_PIXELS);
        TooltipUtil.onHover(PIXELS_TIP);
    }

    private void renderSelected() {
        SenseFinder.Candidate c = host.selected();
        SenseFinder.Turn turn = host.turn();
        if (c == null || turn == null) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.alignTextToFramePadding();
            ImGui.text(host.results().isEmpty() ? "Senses you can apply appear here." : "Click a sense to apply and view it.");
            ThemeManager.popTextColor();
            return;
        }
        String wall = host.goalWallLabel();
        String offset = offsetText(c.offset) + (wall != null ? "  vs " + wall : "");
        String range = percentText(c.sensLo) + " .. " + percentText(c.sensHi);
        String pixel = String.format(Locale.ROOT, "%.5f° per mouse pixel", c.pixelDeg);
        if (ThemeManager.beginStandardFormTable("##sense_selected", 2)) {
            ImGui.tableSetupColumn("##sense_detail_label", ImGuiTableColumnFlags.WidthFixed, 0f);
            ImGui.tableSetupColumn("##sense_detail_value", ImGuiTableColumnFlags.WidthStretch, 0f);
            senseRow(c.sens);
            detailRow("Pixel", pixel);
            detailRow("Window", windowText(c, 0, host.startTick()));
            detailRow("Turn", turnText(turn, c, host.startTick()));
            detailRow("Offset", offset);
            detailRow("Range", range);
            ThemeManager.endStandardFormTable();
        }
    }

    private void senseRow(float sens) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.alignTextToFramePadding();
        ImGui.text("Sense");
        ThemeManager.popTextColor();
        ImGui.tableNextColumn();
        String raw = String.format(Locale.ROOT, "%.8f", sens);
        ImGui.alignTextToFramePadding();
        ImGui.text(percentText(sens) + "   options.txt " + raw);
        ImGui.sameLine();
        if (Controls.secondaryButton("Copy")) ImGui.setClipboardText(raw);
        TooltipUtil.onHover("Copy the options.txt value of this sense (mouseSensitivity).");
    }

    private static void detailRow(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(label);
        ThemeManager.popTextColor();
        ImGui.tableNextColumn();
        ImGui.textWrapped(value);
    }

    private void renderProgress() {
        boolean busy = host.isBusy();
        float frac = (float) host.fraction();
        String label;
        int fill;
        if (busy) {
            label = String.format(Locale.ROOT, "%d%%   %.1f s", Math.round(frac * 100f), host.elapsedSeconds());
            fill = ThemeManager.accentTintColor(0.35f);
        } else {
            Ending ending = host.ending();
            String outcome = host.outcome();
            if (ending == Ending.NONE) {
                label = outcome == null || outcome.isEmpty() ? "" : outcome;
                frac = 0f;
                fill = 0;
            } else {
                double elapsed = host.elapsedSeconds();
                label = outcome + (elapsed > 0.0 ? String.format(Locale.ROOT, " after %.1f s", elapsed) : "");
                frac = 1f;
                fill = ending == Ending.FOUND ? ThemeManager.okTintColor(0.30f)
                        : ending == Ending.CANCELLED ? ThemeManager.warningTintColor(0.30f)
                        : ThemeManager.dangerTintColor(0.30f);
            }
        }
        SolverWidgets.progressStrip("##sense_progress", frac, fill, label);
        String stage = host.stage();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.textWrapped(busy && stage != null && !stage.isEmpty() ? stage : " ");
        ThemeManager.popTextColor();
    }

    private void renderActions() {
        boolean busy = host.isBusy();
        if (busy) {
            if (Controls.secondaryButton("Cancel")) host.cancel();
            TooltipUtil.onHover(CANCEL_TIP);
        } else {
            boolean can = host.hasLine();
            if (!can) ImGui.beginDisabled(true);
            if (Controls.primaryButton("Search")) host.start();
            if (!can) ImGui.endDisabled();
            TooltipUtil.onHover(SEARCH_TIP);
        }
        ImGui.sameLine();
        float sliderW = ImGui.getContentRegionAvail().x - Controls.buttonWidth("Close")
                - ImGui.getStyle().getItemSpacingX();
        fromBuf[0] = host.fromPercent();
        Controls.pushInputFrameHeight();
        ImGui.setNextItemWidth(sliderW);
        if (busy) ImGui.beginDisabled(true);
        boolean changed = Controls.sliderInt("##sense_from", fromBuf, 1, 200, "from %d%%");
        if (busy) ImGui.endDisabled();
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(FROM_TIP);
        if (changed) host.setFromPercent(fromBuf[0]);
        ImGui.sameLine();
        if (Controls.secondaryButton("Close")) close.run();
    }

    private static float selectedHeight(boolean hasSelection) {
        float spacing = ImGui.getStyle().getItemSpacingY();
        if (!hasSelection) return ImGui.getFrameHeight() + spacing;
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        return (DETAIL_ROWS + TURN_LINES - 1) * (ImGui.getTextLineHeightWithSpacing() + 2f * cellPadY) + spacing;
    }

    private static float footerHeight() {
        float spacing = ImGui.getStyle().getItemSpacingY();
        return ThemeManager.sectionSpacingHeight() + spacing
                + ImGui.getFrameHeight() + spacing
                + ImGui.getTextLineHeight() + spacing
                + Controls.buttonHeight() + spacing;
    }

    static String percentText(float sens) {
        return String.format(Locale.ROOT, "%.4f%%", SenseFinder.percent(sens));
    }

    private static String offsetText(double offset) {
        if (Double.isNaN(offset)) return "-";
        return (offset >= 0 ? "+" : "") + ConstraintText.fixedStat(offset);
    }

    static String pixelsText(int[] pixels) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < pixels.length; k++) {
            if (k > 0) sb.append(' ');
            sb.append(pixels[k]);
        }
        return sb.toString();
    }

    static String windowText(SenseFinder.Candidate c, int angle, int startTick) {
        return String.format(Locale.ROOT, "-%.4f\u00b0 (%s) .. +%.4f\u00b0 (%s)", c.below[angle],
                constraintText(c.belowBy[angle], startTick), c.above[angle], constraintText(c.aboveBy[angle], startTick));
    }

    static String constraintText(JumpConstraint c, int startTick) {
        if (c == null) return "cap";
        String tick = "T" + (startTick + c.t1 + 1) + (c.t2 != null ? "-T" + (startTick + c.t2 + 1) : "");
        if (c.t2 != null) return c.mode + " " + tick;
        String cmp = c.cmp == JumpConstraint.Cmp.GE ? " >= " : c.cmp == JumpConstraint.Cmp.LE ? " <= " : " = ";
        return c.mode + cmp + ConstraintText.fixedStat(c.rhs) + " " + tick;
    }

    static String turnText(SenseFinder.Turn turn, SenseFinder.Candidate c, int startTick) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < turn.angles(); k++) {
            if (k > 0) sb.append("   ");
            sb.append('T').append(startTick + turn.turnTicks[k] + 1).append(' ')
                    .append(String.format(Locale.ROOT, "%+.2f°", turn.deltas[k]))
                    .append(" = ").append(c.pixels[k]).append(" px")
                    .append(String.format(Locale.ROOT, " (-%.4f .. +%.4f\u00b0)", c.below[k], c.above[k]));
        }
        return sb.toString();
    }
}
