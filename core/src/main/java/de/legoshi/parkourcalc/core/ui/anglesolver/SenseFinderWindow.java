package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiListClipper;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.callback.ImListClipperCallback;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;

public final class SenseFinderWindow implements RenderInterface {

    public interface Host {
        SenseFinder.Turn turn();

        int startTick();

        float gameSense();

        boolean isBusy();

        String stage();

        double fraction();

        double elapsedSeconds();

        String outcome();

        String applied();

        List<SenseFinder.Candidate> results();

        SenseFinder.Candidate gameRow();

        boolean searchedGameSense();

        SenseFinder.Candidate selected();

        SenseFinder.Turn searchedTurn();

        SenseFinder.Window window();

        SenseFinder.Mode rankMode();

        void setRankMode(SenseFinder.Mode mode);

        float minPercent();

        float maxPercent();

        void setRange(float min, float max);

        int firstTick();

        int lastTick();

        void setTicks(int first, int last);

        void start();

        void cancel();

        void select(SenseFinder.Candidate c);

        boolean canApplySense();

        void applySense();
    }

    private static final String WINDOW_ID = "###sensefinder";
    private static final String TITLE = "Sense Finder";
    private static final String TABLE_ID = "##sensefinder_rows";
    private static final float WIN_W = 760f;
    private static final float WIN_H = 520f;
    private static final float MIN_W = 480f;
    private static final float MIN_H = 300f;
    private static final String SEARCH_TIP = "Round the solved turn to whole mouse pixels at every sense in the range"
            + " and list the pixel counts that still land.";
    private static final String TICKS_TIP = "Turn ticks whose turn range is measured. The Margin column sums"
            + " their turn ranges; leave out the last ticks when they are nearly free.";
    private static final String APPLY = "Apply";
    private static final String APPLY_TIP = "Set your game sense to the selected row's sense.";
    private static final String SHOW = "Show";
    private static final String[] VIEWS = {"Range", "Count"};
    private static final String[] VIEW_TIPS = {"Each tick shows its turn range in pixels.",
            "Each tick shows how many pixel counts still make the jump."};
    private static final String COL_SENSE = "Sense";
    private static final String COL_OFFSET = "Offset";
    private static final String COL_MARGIN = "Margin";
    private static final String MARGIN_TIP = "The turn range: the pixel counts you can turn at this tick and still make"
            + " the jump while the rest adapts. Hover a cell for its width in degrees and the row's own turn. Orange is"
            + " the row's narrowest turn range.";

    private final Host host;
    private final Settings settings;
    private final Runnable onSettingsChanged;
    private final ImBoolean open = new ImBoolean(false);
    private final ImString minText = new ImString(16);
    private final ImString maxText = new ImString(16);
    private final ImString firstText = new ImString(16);
    private final ImString lastText = new ImString(16);

    public SenseFinderWindow(Host host, Settings settings, Runnable onSettingsChanged) {
        this.host = host;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
        minText.set(percentText((double) host.minPercent()));
        maxText.set(percentText((double) host.maxPercent()));
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewSenseFinder);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, open, flags);
        if (visible) ThemeManager.drawModalTitle(TITLE);
        ThemeManager.popHeaderChrome();
        if (visible) body();
        ImGui.end();
        if (settings.viewSenseFinder != open.get()) {
            settings.viewSenseFinder = open.get();
            onSettingsChanged.run();
        }
    }

    private void body() {
        SenseFinder.Turn turn = host.turn();
        boolean busy = host.isBusy();
        if (turn == null && host.results().isEmpty() && !busy) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.textWrapped("Solve a jump with a turn in the Angle Solver first.");
            ThemeManager.popTextColor();
        }
        controls(turn, busy);
        if (busy) {
            progress();
            return;
        }
        String outcome = host.outcome();
        if (outcome != null && !outcome.isEmpty() && host.results().isEmpty()) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.text(outcome);
            ThemeManager.popTextColor();
        }
        if (host.searchedTurn() == null || host.results().isEmpty()) return;
        float footer = ImGui.getFrameHeightWithSpacing();
        rows(host.searchedTurn(), Math.max(ImGui.getTextLineHeight() * 6f, ImGui.getContentRegionAvail().y - footer));
        String applied = host.applied();
        ImGui.alignTextToFramePadding();
        ThemeManager.pushTextColor(applied.isEmpty() ? ThemeManager.textDimColor() : ThemeManager.okColor());
        ImGui.text(applied.isEmpty() ? (outcome == null ? "" : outcome) : applied);
        ThemeManager.popTextColor();
        ImGui.sameLine();
        Controls.cursorToRightAlignedButton(APPLY);
        if (host.canApplySense()) {
            if (Controls.primaryButton(APPLY)) host.applySense();
            TooltipUtil.onHover(APPLY_TIP);
        } else {
            Controls.disabledButton(APPLY);
        }
    }

    private String defaultFirst(SenseFinder.Turn turn) {
        return turn == null ? "" : Integer.toString(host.startTick() + turn.jumpTick() + 1);
    }

    private String defaultLast(SenseFinder.Turn turn) {
        return turn == null ? "" : Integer.toString(host.startTick() + turn.lastMainTurnTick() + 1);
    }

    private void controls(SenseFinder.Turn turn, boolean busy) {
        boolean ready = turn != null;
        ImGuiStyle style = ImGui.getStyle();
        float fieldW = ImGui.calcTextSize("200.00%").x + 2f * style.getFramePaddingX();
        float labelW = Math.max(ImGui.calcTextSize("Sense").x, Math.max(ImGui.calcTextSize("Ticks").x,
                ImGui.calcTextSize(SHOW).x)) + style.getItemSpacingX();
        float pairW = 2f * fieldW + ImGui.calcTextSize("to").x + 2f * style.getItemSpacingX();

        SolverWidgets.rowLabel("Sense", labelW);
        if (busy) ImGui.beginDisabled(true);
        boolean changed = Controls.decimalField("##sense_min", minText, fieldW);
        to();
        changed |= Controls.decimalField("##sense_max", maxText, fieldW);
        if (busy) ImGui.endDisabled();
        if (changed) host.setRange(parse(minText.get(), host.minPercent()), parse(maxText.get(), host.maxPercent()));

        SolverWidgets.rowLabel("Ticks", labelW);
        TooltipUtil.onHover(TICKS_TIP);
        if (busy) ImGui.beginDisabled(true);
        int digits = ImGuiInputTextFlags.CharsDecimal;
        boolean ticksChanged = Controls.inputTextHint("##sense_first", defaultFirst(turn), firstText, fieldW, digits);
        to();
        ticksChanged |= Controls.inputTextHint("##sense_last", defaultLast(turn), lastText, fieldW, digits);
        if (busy) ImGui.endDisabled();
        if (ticksChanged) {
            host.setTicks((int) parse(firstText.get(), 0f), (int) parse(lastText.get(), 0f));
        }

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(SHOW, labelW);
        int view = SolverWidgets.segmented("##sense_view", VIEWS, VIEW_TIPS, settings.senseFinderCountView ? 1 : 0,
                pairW);
        Controls.popInputFrameHeight();
        if (view >= 0 && (view == 1) != settings.senseFinderCountView) {
            settings.senseFinderCountView = view == 1;
            onSettingsChanged.run();
        }
        ImGui.sameLine();
        String label = busy ? "Cancel" : "Search";
        Controls.cursorToRightAlignedButton(label);
        if (busy) {
            if (Controls.secondaryButton(label)) host.cancel();
        } else if (ready) {
            if (Controls.primaryButton(label)) host.start();
            TooltipUtil.onHover(SEARCH_TIP);
        } else {
            Controls.disabledButton(label);
        }
    }

    private static void to() {
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text("to");
        ThemeManager.popTextColor();
        ImGui.sameLine();
    }

    private void progress() {
        String label = String.format(Locale.ROOT, "%d%%   %.1f s", Math.round(host.fraction() * 100.0), host.elapsedSeconds());
        SolverWidgets.progressStrip("##sense_progress", (float) host.fraction(), ThemeManager.accentTintColor(0.35f), label);
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        String stage = host.stage();
        ImGui.textWrapped(stage == null || stage.isEmpty() ? " " : stage);
        ThemeManager.popTextColor();
    }

    private void rows(SenseFinder.Turn turn, float height) {
        int ticks = turn.angles();
        int cols = 4 + ticks;
        int flags = ImGuiTableFlags.ScrollX;
        if (!ThemeManager.beginStandardClickableRowsTable(TABLE_ID, cols, flags, 0f, height)) return;
        ImGui.tableSetupScrollFreeze(4, 1);
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float rankW = ImGui.calcTextSize("999").x;
        float senseW = ImGui.calcTextSize("199.99%").x;
        float offW = ImGui.calcTextSize("+" + ConstraintText.fixedStat(9.0)).x;
        float marginW = ImGui.calcTextSize("99.999°").x;
        float tickW = ImGui.calcTextSize("[-9999, -9999]").x + 2f * ImGui.getStyle().getCellPaddingX();
        float mark = ImGui.getFontSize() * 0.7f;
        ImGui.tableSetupColumn("#", fixed, ThemeManager.tableLeftmostColumnWidth("#", rankW));
        ImGui.tableSetupColumn(COL_SENSE, fixed, ThemeManager.tableNumericColumnWidth(COL_SENSE, senseW) + mark);
        ImGui.tableSetupColumn(COL_OFFSET, fixed, ThemeManager.tableNumericColumnWidth(COL_OFFSET, offW) + mark);
        ImGui.tableSetupColumn(COL_MARGIN, fixed, ThemeManager.tableNumericColumnWidth(COL_MARGIN, marginW) + mark);
        int startTick = host.startTick();
        for (int k = 0; k < ticks; k++) {
            ImGui.tableSetupColumn("T" + (startTick + turn.turnTicks[k] + 1), fixed, tickW);
        }
        header(ticks);
        float rowH = ImGui.getTextLineHeight();
        SenseFinder.Candidate game = host.gameRow();
        if (host.searchedGameSense()) {
            if (game == null) gameMissRow();
            else row(turn, game, "G", -1, rowH, true);
        }
        List<SenseFinder.Candidate> list = host.results();
        ImGuiListClipper.forEach(list.size(), new ImListClipperCallback() {
            @Override
            public void accept(int i) {
                row(turn, list.get(i), Integer.toString(i + 1), i, rowH, false);
            }
        });
        ThemeManager.endStandardTable();
    }

    private void header(int ticks) {
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader("#");
        SenseFinder.Mode mode = host.rankMode();
        ImGui.tableSetColumnIndex(1);
        if (ThemeManager.tableSortHeader(COL_SENSE, ThemeManager.HAlign.RIGHT, mode == SenseFinder.Mode.SENSE)) {
            host.setRankMode(SenseFinder.Mode.SENSE);
        }
        TooltipUtil.onHover(SenseFinder.Mode.SENSE.hint);
        ImGui.tableSetColumnIndex(2);
        if (ThemeManager.tableSortHeader(COL_OFFSET, ThemeManager.HAlign.RIGHT, mode == SenseFinder.Mode.FURTHEST)) {
            host.setRankMode(SenseFinder.Mode.FURTHEST);
        }
        TooltipUtil.onHover(SenseFinder.Mode.FURTHEST.hint);
        ImGui.tableSetColumnIndex(3);
        if (ThemeManager.tableSortHeader(COL_MARGIN, ThemeManager.HAlign.RIGHT, mode == SenseFinder.Mode.MARGIN)) {
            host.setRankMode(SenseFinder.Mode.MARGIN);
        }
        TooltipUtil.onHover(SenseFinder.Mode.MARGIN.hint);
        for (int k = 0; k < ticks; k++) {
            ImGui.tableSetColumnIndex(4 + k);
            ThemeManager.tableHeader(ImGui.tableGetColumnName(4 + k));
            TooltipUtil.onHover(MARGIN_TIP);
        }
    }

    private void gameMissRow() {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("G");
        ImGui.tableSetColumnIndex(1);
        ThemeManager.textRight(senseText(host.gameSense()) + "%");
        ImGui.tableSetColumnIndex(4);
        ImGui.text("no landing pixels at your sense");
        ThemeManager.popTextColor();
    }

    private void row(SenseFinder.Turn turn, SenseFinder.Candidate c, String rank, int index, float rowH, boolean game) {
        ImGui.tableNextRow();
        if (index >= 0) ThemeManager.paintTableRowBg(index);
        if (c == host.selected()) ThemeManager.paintTableRowTint(ThemeManager.selectedRowBg());
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        if (game) ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        if (ImGui.selectable(rank + "##sense" + rank, false,
                ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap, 0f, rowH)) {
            host.select(c);
        }
        ImGui.tableSetColumnIndex(1);
        ThemeManager.textRight(senseText(c.sens) + "%");
        ImGui.tableSetColumnIndex(2);
        ThemeManager.textRight(offsetText(c.offset));
        ImGui.tableSetColumnIndex(3);
        ThemeManager.textRight(c.weakestTick() < 0 ? "-" : c.marginsComplete() ? marginText(c.totalTarget()) : "...");
        if (game) ThemeManager.popTextColor();
        int weakest = c.weakestTick();
        for (int k = 0; k < c.pixels.length; k++) {
            ImGui.tableSetColumnIndex(4 + k);
            cell(c, k, k == weakest && c.marginsComplete(), settings.senseFinderCountView);
        }
    }

    private static void cell(SenseFinder.Candidate c, int k, boolean weakest, boolean count) {
        if (weakest) ThemeManager.pushTextColor(ThemeManager.warningColor());
        String shown = count ? Integer.toString(c.landingPixels(k)) : rangeText(c.landingRange(k));
        ImGui.text(!c.scoped[k] ? " " : c.hasMargin(k) ? shown : "...");
        if (weakest) ThemeManager.popTextColor();
        if (!ImGui.isItemHovered()) return;
        ImGui.beginTooltip();
        if (ImGui.beginTable("##cell_tip", 2)) {
            if (c.hasMargin(k)) {
                tipHeading("Turn range");
                tipValue("Pixels", rangeText(c.landingRange(k)));
                tipValue("Width", marginText(c.target(k)));
            }
            tipHeading("Turn");
            tipValue("Pixels", Integer.toString(c.pixels[k]));
            tipValue("Degrees", String.format(Locale.ROOT, "%.2f°", c.turnDeg(k)));
            ImGui.endTable();
        }
        ImGui.endTooltip();
    }

    private static void tipHeading(String label) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        Fonts.pushBold();
        ImGui.text(label);
        Fonts.popBold();
    }

    private static void tipValue(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        ImGui.indent();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(label);
        ThemeManager.popTextColor();
        ImGui.unindent();
        ImGui.tableSetColumnIndex(1);
        ImGui.text(value);
    }

    private static String rangeText(long[] range) {
        if (range[0] == range[1]) return Long.toString(range[0]);
        return "[" + range[0] + ", " + range[1] + "]";
    }

    private static String marginText(double target) {
        return String.format(Locale.ROOT, "%.3f°", target);
    }

    private static float parse(String text, float fallback) {
        try {
            return Float.parseFloat(text.replace("%", "").replace("T", "").trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String senseText(float sens) {
        return String.format(Locale.ROOT, "%.2f", SenseFinder.percent(sens));
    }

    private static String percentText(double percent) {
        return String.format(Locale.ROOT, "%.2f", percent);
    }

    private static String offsetText(double offset) {
        if (Double.isNaN(offset)) return "-";
        return (offset >= 0 ? "+" : "") + ConstraintText.fixedStat(offset);
    }
}
