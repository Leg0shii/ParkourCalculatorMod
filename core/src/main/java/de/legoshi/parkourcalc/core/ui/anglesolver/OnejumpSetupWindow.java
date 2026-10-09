package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.LandingForecast;
import de.legoshi.parkourcalc.core.PracticeMacro;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnProfileDocument;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.TurnTiming;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OnejumpSetupWindow implements RenderInterface {

    private static final String WINDOW_ID = "###onejumpSetup";
    private static final String TITLE = "Onejump Overview";
    private static final String POPUP_CLEAR = "###onejumpClear";
    private static final float WIN_W = 760f;
    private static final float WIN_H = 680f;
    private static final float MIN_W = 520f;
    private static final float MIN_H = 300f;
    private static final int MIN_TABLE_ROWS = 4;
    private static final int PAGE_SIZE = 50;
    private static final int LATEST = 5;
    private static final String[] DOTS = {"", ".", "..", "..."};

    private final TurnProfileController controller;
    private final AttemptTracker tracker;
    private final Settings settings;
    private final Runnable onSettingsChanged;
    private final ImBoolean open = new ImBoolean(false);
    private boolean openClearModal;
    private int attemptsPage;
    private TurnTiming.Onset onsetCache;
    private int onsetVersion = -1;
    private int onsetTick = -1;
    private int onsetLimit = -1;

    public OnejumpSetupWindow(TurnProfileController controller, AttemptTracker tracker, Settings settings,
                              Runnable onSettingsChanged) {
        this.controller = controller;
        this.tracker = tracker;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewOnejumpSetup);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, open, ImGuiWindowFlags.NoCollapse);
        if (visible) ThemeManager.drawModalTitle(TITLE);
        ThemeManager.popHeaderChrome();
        if (visible) body(scale);
        ImGui.end();
        if (settings.viewOnejumpSetup != open.get()) {
            settings.viewOnejumpSetup = open.get();
            onSettingsChanged.run();
        }
    }

    private void body(float scale) {
        TurnProfileController.Current cur = controller.current();
        TurnProfileDocument doc = controller.document();
        List<TurnAttempt> all = doc.attempts();
        if (Controls.beginTabBar("##onejump_tabs")) {
            if (Controls.beginTab("Overview")) {
                ThemeManager.sectionSpacing();
                overview(doc, cur, scale);
                Controls.endTab();
            }
            if (Controls.beginTab("Favourites")) {
                ThemeManager.sectionSpacing();
                List<TurnAttempt> favourites = doc.favourites();
                ImGui.textDisabled(favourites.size() + " favourites");
                float tableH = Math.max(minTableH(scale), ImGui.getContentRegionAvail().y - ThemeManager.sectionSpacingHeight());
                attemptsTable("##attemptsFav", favourites, tableH, false);
                Controls.endTab();
            }
            if (Controls.beginTab("Attempts")) {
                ThemeManager.sectionSpacing();
                int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                attemptsPage = Math.max(0, Math.min(attemptsPage, pages - 1));
                int from = all.size() - attemptsPage * PAGE_SIZE;
                List<TurnAttempt> page = new ArrayList<TurnAttempt>();
                for (int i = from - 1; i >= 0 && i >= from - PAGE_SIZE; i--) page.add(all.get(i));
                if (attemptsPage == 0) Controls.disabledButton("<");
                else if (Controls.secondaryButton("<")) attemptsPage--;
                ImGui.sameLine();
                ImGui.alignTextToFramePadding();
                ImGui.text("Page " + (attemptsPage + 1) + " of " + pages);
                ImGui.sameLine();
                if (attemptsPage >= pages - 1) Controls.disabledButton(">");
                else if (Controls.secondaryButton(">")) attemptsPage++;
                ImGui.sameLine();
                ImGui.alignTextToFramePadding();
                ImGui.textDisabled(all.size() + " attempts");
                float tableH = Math.max(minTableH(scale), ImGui.getContentRegionAvail().y - ThemeManager.sectionSpacingHeight() * 2f
                        - ImGui.getTextLineHeightWithSpacing() - Controls.buttonHeight());
                attemptsTable("##attemptsAll", page, tableH, false);
                dangerZone();
                Controls.endTab();
            }
            if (Controls.beginTab("Help")) {
                ThemeManager.sectionSpacing();
                help();
                Controls.endTab();
            }
            Controls.endTabBar();
        }
        clearModal();
    }

    private static final String[] OVERVIEW_LABELS = {"TAS", "Landing", "Reference", "Live offset", "Attempts", "Landed", "Input failures",
            "Landing chance", "Closest", "Missed by", "Failed at", "Turn onset", "Replay (inputs)", "Replay (turn)", "Top 10",
            "Latest"};

    private void overview(TurnProfileDocument doc, TurnProfileController.Current cur, float scale) {
        TurnProfileDocument.Stats st = doc.stats();
        String name = controller.name();
        float labelW = 0f;
        Fonts.pushBold();
        for (String l : OVERVIEW_LABELS) labelW = Math.max(labelW, ImGui.calcTextSize(l).x);
        Fonts.popBold();
        labelW += ThemeManager.SM * scale;
        checkButton();
        String storeError = controller.storeError();
        overviewRow("TAS", name == null ? "unsaved, attempts are not kept" : storeError == null ? name
                : name + "  (attempts file unreadable, nothing is written: " + storeError + ")", labelW,
                name == null || storeError != null);
        TurnReference.Landing tasLanding = controller.tasLanding();
        int lb = controller.landRow();
        overviewRow("Landing", tasLanding != null ? tasLanding.label(0) + (lb >= 0 ? "  (LB)" : "  (last constraint, no LB marked)")
                : lb >= 0 ? "tick " + (lb + 1) + " is marked LB but has no X or Z constraint"
                : "no landing constraint, mark LB on the landing tick and press B on the block", labelW,
                tasLanding == null);
        TurnReference.Landing landing = cur == null ? null : cur.landing;
        String reference = cur != null
                ? "ticks " + (cur.tasTick(0) + 1) + " to " + (cur.tasTick(cur.n - 1) + 1)
                        + (landing == null ? ", no landing constraint" : "")
                : "none";
        overviewRow("Reference", reference, labelW, cur == null || landing == null);
        checkRows(labelW);
        overviewRow("Attempts", Integer.toString(st.attempts), labelW, false);
        overviewRow("Input failures", Integer.toString(st.inputFailures), labelW, false);
        String failed = LandingForecast.failedSummary(st.failedAt, st.failedTotal, cur == null ? 0 : cur.tasTick(0));
        overviewRow("Failed at", failed == null ? "-" : failed, labelW, failed == null);
        int[] bands = st.missBands;
        boolean anyBand = bands[0] + bands[1] + bands[2] + bands[3] > 0;
        overviewRow("Missed by", String.format(Locale.ROOT, "e-2 %d   e-3 %d   e-4 %d   e-5 %d", bands[0], bands[1],
                bands[2], bands[3]), labelW, !anyBand);
        overviewRow("Closest", st.hasClosest() ? TurnAttempt.signedMargin(st.closest) : "-", labelW, !st.hasClosest());
        overviewRow("Landed", Integer.toString(st.landings), labelW, false);
        overviewRow("Landing chance", landingChance(cur), labelW, cur == null || cur.attempts == null);
        if (settings.onejumpTurnTiming && cur != null) {
            int mainTurn = TurnTiming.mainTurnTick(cur);
            TurnTiming.Onset onset = mainTurn < 0 ? null
                    : onset(doc, cur.startTick + mainTurn, Math.max(1, settings.onejumpSpreadAttempts));
            overviewRow("Turn onset", onset == null ? "-" : String.format(Locale.ROOT, "%s into tick %d  (%d attempts)",
                    TurnTiming.ms(onset.median), mainTurn + 1, onset.attempts), labelW, onset == null);
        }
        if (st.mouseAttempts > 0) overviewRow(PracticeMacro.LABEL_INPUTS, practiceText(st.mouseAttempts, st.mouseClears), labelW, false);
        if (st.inputAttempts > 0) overviewRow(PracticeMacro.LABEL_TURN, practiceText(st.inputAttempts, st.inputClears), labelW, false);
        ThemeManager.sectionSpacing();
        Fonts.pushBold();
        ImGui.text("Top " + TurnProfileDocument.TOP);
        Fonts.popBold();
        List<TurnAttempt> top = doc.top();
        attemptsTable("##attemptsTop", top, listHeight(top, scale), true);
        ThemeManager.sectionSpacing();
        Fonts.pushBold();
        ImGui.text("Latest");
        Fonts.popBold();
        List<TurnAttempt> all = doc.attempts();
        List<TurnAttempt> latest = new ArrayList<TurnAttempt>();
        for (int i = all.size() - 1; i >= 0 && latest.size() < LATEST; i--) latest.add(all.get(i));
        attemptsTable("##attemptsLatest", latest, listHeight(latest, scale), false);
    }

    private void checkButton() {
        if (Controls.secondaryButton("Check TAS")) controller.check();
        TooltipUtil.onHover("Checks that the TAS is complete and lands. The live offset, the landing chance and the solved offsets run only on a checked TAS, and any edit needs a new check.");
        ThemeManager.sectionSpacing();
    }

    private void checkRows(float labelW) {
        boolean checked = controller.isChecked();
        TurnProfileController.SetupCheck check = controller.lastCheck();
        float startX = ImGui.getCursorPosX();
        Fonts.pushBold();
        ImGui.text("Live offset");
        Fonts.popBold();
        ImGui.sameLine();
        ImGui.setCursorPosX(startX + labelW);
        boolean current = controller.lastCheckCurrent();
        if (checked) {
            ThemeManager.pushTextColor(ThemeManager.okColor());
            ImGui.text("on");
            ThemeManager.popTextColor();
        } else if (check == null || !current) {
            ImGui.textDisabled(check == null ? "off, press Check TAS" : "off, the TAS changed since the check");
        } else {
            TurnProfileController.SetupCheck.Item firstFailed = null;
            for (TurnProfileController.SetupCheck.Item item : check.items) {
                if (!item.ok) {
                    firstFailed = item;
                    break;
                }
            }
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.text("off: " + (firstFailed == null ? "the check failed" : firstFailed.label.toLowerCase(Locale.ROOT)));
            ThemeManager.popTextColor();
        }
        if (check != null && current && ImGui.isItemHovered()) checkTooltip(check);
    }

    private static void checkTooltip(TurnProfileController.SetupCheck check) {
        ImGui.beginTooltip();
        for (TurnProfileController.SetupCheck.Item item : check.items) {
            ThemeManager.pushTextColor(item.ok ? ThemeManager.okColor() : ThemeManager.dangerColor());
            ImGui.text(item.ok ? "ok" : "!!");
            ThemeManager.popTextColor();
            ImGui.sameLine();
            ImGui.text(item.label);
            ImGui.sameLine();
            ImGui.textDisabled(item.detail);
        }
        ImGui.endTooltip();
    }

    private static final String[][] HELP = {
            {"What it is",
             "The onejump tracks your attempts on one jump. The TAS of that jump is the reference: every attempt is "
             + "compared to its rows from the first key on and judged on the landing tick, where the offset is measured. "
             + "With a checked TAS the Turn Profile also shows a live offset during the attempt."},
            {"1  The TAS",
             "Build the complete TAS of the jump, by hand or with the solver: every row from the first key you press to "
             + "the landing tick, with the keys you will press. The landing tick is the first tick before the player is on "
             + "the ground again; it carries the landing constraint. The attempt starts on the first keyed row of the TAS."},
            {"2  Landing constraint and walls",
             "Mark LB on the landing tick in the input table, the first tick before the player is on the ground again. "
             + "With that row selected, look at the landing block and press B. A wall you have to clear gets a "
             + "constraint the same way: select the tick where you pass it, look at its face, press B."},
            {"3  Facings",
             "Solve them in the Angle Solver window, Fast or Optimize, or type the yaw of each row yourself."},
            {"4  Flag the ticks",
             "In the input table, mark Keys on the ticks whose keys are checked, Face on the turn ticks and LB on the landing tick. Right click "
             + "on Face sets Still: the attempt fails as soon as you turn on that tick. Shift click on a key cell makes that "
             + "key optional on that tick."},
            {"5  Check TAS",
             "Press Check TAS in the Overview. It first applies the state of every flagged tick from the simulation, "
             + "like Apply state in the Angle Solver. Then it checks that the ticks are flagged, a landing constraint "
             + "follows them, the rows reach it, the simulation meets every constraint, the solver replays the rows and "
             + "meets the landing constraint with the same positions as the simulation, and the first flagged tick is "
             + "on the ground. The live offset, the landing chance and the solved offsets run only while the check "
             + "holds. Any edit of the TAS needs a new check."},
            {"6  Practice",
             "Right click resets. The first key after the reset starts the attempt, the rows play tick by tick, the "
             + "landing tick judges it. The attempts table shows the offset and what failed, the Turn Profile your facing "
             + "against the reference, the Keys window your keys per tick."},
            {"7  Preferences",
             "Preferences > Onejump: practice replay, rated dots, turn timing, offset label and hover, what happens on a "
             + "wrong key, and how many attempts feed the landing chance."},
    };

    private void help() {
        ImGui.beginChild("##onejumpHelp", 0f, 0f, false);
        float wrap = ImGui.getContentRegionAvail().x;
        for (String[] section : HELP) {
            Fonts.pushBold();
            ImGui.text(section[0]);
            Fonts.popBold();
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrap);
            ImGui.textUnformatted(section[1]);
            ImGui.popTextWrapPos();
            ThemeManager.sectionSpacing();
        }
        ImGui.endChild();
    }

    private TurnTiming.Onset onset(TurnProfileDocument doc, int tick, int limit) {
        int version = doc.version();
        if (version != onsetVersion || tick != onsetTick || limit != onsetLimit) {
            onsetCache = TurnTiming.onset(doc.attempts(), tick, limit);
            onsetVersion = version;
            onsetTick = tick;
            onsetLimit = limit;
        }
        return onsetCache;
    }

    private static float listHeight(List<TurnAttempt> list, float scale) {
        return ThemeManager.tableHeaderRowHeight() + ThemeManager.tableRowHeight() * Math.max(1, list.size()) + 4f * scale;
    }

    private String landingChance(TurnProfileController.Current cur) {
        if (cur == null) return "-";
        if (!controller.isChecked()) return "press Check TAS";
        if (!cur.canRate()) return cur.pathLands() ? "needs a landing constraint and a TAS path" : "the reference path does not meet the TAS constraints";
        AttemptSampler.Stats rs = cur.attempts;
        if (rs == null) return controller.isRating() ? "sampling" : "-";
        int used = controller.ratedSpread();
        if (used == 0) return "no attempts yet, the reference itself " + (rs.rate() > 0.0 ? "lands" : "misses");
        return String.format(Locale.ROOT, "%s of tries  (%s, spread of your last %d attempts, %s samples)",
                oneIn(rs.rate()), pct(rs.rate()), used, compact(rs.attempts));
    }

    private static String practiceText(int attempts, int clears) {
        return String.format(Locale.ROOT, "%d   (%d landed)", attempts, clears);
    }

    private static void overviewRow(String label, String value, float labelW, boolean dim) {
        float startX = ImGui.getCursorPosX();
        Fonts.pushBold();
        ImGui.text(label);
        Fonts.popBold();
        ImGui.sameLine();
        ImGui.setCursorPosX(startX + labelW);
        if (dim) ImGui.textDisabled(value);
        else ImGui.text(value);
    }

    private static float minTableH(float scale) {
        return ThemeManager.tableHeaderRowHeight() + ThemeManager.tableRowHeight() * MIN_TABLE_ROWS + 4f * scale;
    }

    private static String oneIn(double rate) {
        if (rate <= 0.0) return "none";
        if (rate >= 0.5) return String.format(Locale.ROOT, "%.0f%%", rate * 100.0);
        return String.format(Locale.ROOT, "1 in %s", compact((long) Math.round(1.0 / rate)));
    }

    private static String pct(double rate) {
        if (rate <= 0.0) return "0%";
        if (rate < 0.001) return String.format(Locale.ROOT, "%.3f%%", rate * 100.0);
        return String.format(Locale.ROOT, "%.1f%%", rate * 100.0);
    }

    private static String compact(long v) {
        if (v >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", v / 1e6);
        if (v >= 10_000L) return String.format(Locale.ROOT, "%dk", v / 1000L);
        return Long.toString(v);
    }

    private void attemptsTable(String id, List<TurnAttempt> list, float h, boolean ranked) {
        float rowH = ThemeManager.tableRowHeight();
        int extra = ranked ? 1 : 0;
        if (!ThemeManager.beginStandardClickableRowsTable(id, 4 + extra, 0, 0f, h)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float numW = ImGui.calcTextSize("999999").x;
        float landW = ImGui.calcTextSize("-99.99999").x;
        float starW = ImGui.calcTextSize("Fav").x;
        if (ranked) ImGui.tableSetupColumn("#", fixed, ThemeManager.tableLeftmostColumnWidth("#", ImGui.calcTextSize("99").x));
        ImGui.tableSetupColumn("Attempt", fixed, ranked ? ThemeManager.tableNumericColumnWidth("Attempt", numW)
                : ThemeManager.tableLeftmostColumnWidth("Attempt", numW));
        ImGui.tableSetupColumn("Fav", fixed, ThemeManager.tableNumericColumnWidth("Fav", starW));
        ImGui.tableSetupColumn("Offset", fixed, ThemeManager.tableNumericColumnWidth("Offset", landW));
        ImGui.tableSetupColumn("Info", ImGuiTableColumnFlags.WidthStretch, 0f);
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        if (ranked) {
            ThemeManager.tableHeaderRight("#");
            ImGui.tableSetColumnIndex(1);
        }
        ThemeManager.tableHeaderRight("Attempt");
        ImGui.tableSetColumnIndex(1 + extra);
        ThemeManager.tableHeaderCentered("Fav");
        TooltipUtil.onHover("Click to keep an attempt in the Favourites list of the Overview.");
        ImGui.tableSetColumnIndex(2 + extra);
        ThemeManager.tableHeaderRight("Offset");
        ImGui.tableSetColumnIndex(3 + extra);
        ThemeManager.tableHeader("Info");
        ThemeManager.tableRightmostCellTrailingPad();
        if (list.isEmpty()) {
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(0);
            ImGui.tableSetColumnIndex(0);
            ThemeManager.tableLeftmostCellPad();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(ranked ? "no attempts yet" : "none");
            ThemeManager.endStandardTable();
            return;
        }
        int selectedNumber = controller.selectedNumber();
        float hitH = rowH - ImGui.getStyle().getItemSpacing().y;
        for (int i = 0; i < list.size(); i++) {
            TurnAttempt a = list.get(i);
            boolean selected = a.number == selectedNumber;
            boolean macroRow = a.isMacro();
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(i);
            if (macroRow) ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.tableSetColumnIndex(0);
            ThemeManager.tableLeftmostCellPad();
            ImGui.alignTextToFramePadding();
            String first = ranked ? Integer.toString(i + 1) : Integer.toString(a.ordinal);
            if (ThemeManager.rightAlignedSelectable(id + i, first, selected,
                    ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap)) {
                controller.select(selected ? -1 : a.number);
            }
            if (ranked) {
                ImGui.tableSetColumnIndex(1);
                ThemeManager.textRight(Integer.toString(a.ordinal));
            }
            ImGui.tableSetColumnIndex(1 + extra);
            favouriteCell(id + "fav" + i, a, hitH, rowH);
            ImGui.tableSetColumnIndex(2 + extra);
            if (a.hasMargin()) {
                if (!macroRow) ThemeManager.pushTextColor(a.landed ? ThemeManager.okColor() : ThemeManager.textMutedColor());
                ThemeManager.textRight(TurnAttempt.signedMargin(a.margin));
                if (!macroRow) ThemeManager.popTextColor();
            } else {
                if (!macroRow) ThemeManager.pushTextColor(ThemeManager.textDimColor());
                ThemeManager.textRight("-");
                if (!macroRow) ThemeManager.popTextColor();
            }
            ImGui.tableSetColumnIndex(3 + extra);
            String cell = info(a);
            if (!cell.isEmpty()) ThemeManager.textLeft(cell);
            if (macroRow) {
                ThemeManager.popTextColor();
                if (!cell.isEmpty()) ImGui.sameLine();
                else ImGui.alignTextToFramePadding();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.text("(?)");
                ThemeManager.popTextColor();
                TooltipUtil.onHover(a.macro == PracticeMacro.INPUTS ? PracticeMacro.LABEL_INPUTS : PracticeMacro.LABEL_TURN);
            }
        }
        ThemeManager.endStandardTable();
    }

    private String info(TurnAttempt a) {
        if (a.inputFailure) {
            return "T" + (a.tasTick(a.failTick) + 1) + " Inputs: " + TurnReference.describe(a.failKeys) + ", expected "
                    + TurnReference.describe(a.expectedKeys);
        }
        if (a.turnFailure) return "T" + (a.tasTick(a.failTick) + 1) + " Preturn: " + turn(a.failTurn);
        if (controller.isDeepChecking(a)) return "solving" + DOTS[(int) (ImGui.getTime() * 3.0) % DOTS.length];
        if (a.judged() && !a.landed && a.failedTick() >= 0) {
            int t = a.failedTick();
            TurnProfileController.Current cur = controller.current();
            double err = cur == null ? Double.NaN : a.errorAt(cur, t);
            return "T" + (a.tasTick(t) + 1) + " Turn" + (Double.isNaN(err) ? "" : ": " + turn(err));
        }
        return "";
    }

    private String turn(double deg) {
        TurnProfileController.Current cur = controller.current();
        return TurnAttempt.turnText(deg, cur == null ? 0.0 : cur.pixelDeg);
    }

    private void favouriteCell(String id, TurnAttempt a, float hitH, float rowH) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        ImGui.alignTextToFramePadding();
        if (ImGui.selectable("##" + id, false, 0, 0f, hitH)) controller.document().setFavourite(a, !a.favourite);
        TooltipUtil.onHover(a.favourite ? "Remove from favourites" : "Add to favourites");
        String mark = "*";
        ImVec2 size = ImGui.calcTextSize(mark);
        float tx = origin.x + (cellW - size.x) * 0.5f;
        float ty = origin.y + (rowH - 2f * ImGui.getStyle().getCellPadding().y - size.y) * 0.5f;
        int col = a.favourite ? ThemeManager.warningColor() : ThemeManager.textDimColor();
        ImGui.getWindowDrawList().addText(tx, ty, col, mark);
    }

    private void dangerZone() {
        ThemeManager.sectionSpacing();
        ImGui.textDisabled("Danger zone");
        int count = controller.document().attempts().size();
        if (count == 0) {
            Controls.disabledButton("Clear attempts");
        } else if (Controls.dangerButton("Clear attempts")) {
            openClearModal = true;
        }
        TooltipUtil.onHover("Deletes every attempt. The reference stays.");
    }

    private void clearModal() {
        if (openClearModal) {
            ImGui.openPopup(POPUP_CLEAR);
            openClearModal = false;
        }
        if (!Modal.begin("Clear attempts", POPUP_CLEAR)) return;
        int count = controller.document().attempts().size();
        String name = controller.name();
        ImGui.text("Delete all " + count + " attempts of '" + (name != null ? name : "this TAS") + "'?");
        ImGui.textDisabled("The reference stays.");
        Modal.footerSeparator();
        if (Controls.dangerButton("Delete")) {
            controller.clearAttempts();
            tracker.reset();
            controller.select(-1);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Modal.footerButton("Cancel")) ImGui.closeCurrentPopup();
        Modal.end();
    }
}
