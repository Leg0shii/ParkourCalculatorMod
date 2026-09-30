package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.PracticeMacro;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnProfileDocument;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OnejumpSetupWindow implements RenderInterface {

    private static final String WINDOW_ID = "###onejumpSetup";
    private static final String TITLE = "Onejump Setup";
    private static final String POPUP_CLEAR = "###onejumpClear";
    private static final String POPUP_NEW = "###onejumpNew";
    private static final String POPUP_DELETE = "###onejumpDelete";
    private static final String[] FORM_LABELS = {"Onejump", "From TAS", "Landing", "X", "Z"};
    private static final float WIN_W = 760f;
    private static final float WIN_H = 680f;
    private static final float MIN_W = 520f;
    private static final float MIN_H = 300f;
    private static final int MIN_TABLE_ROWS = 4;
    private static final int LIST_LIMIT = 200;

    private final TurnProfileController controller;
    private final AttemptTracker tracker;
    private final Settings settings;
    private final Supplier<Float> sensitivity;
    private final Runnable onSettingsChanged;
    private final PracticeMacro macro;
    private final InputOverlay referenceTable;
    private final ImInt macroPick = new ImInt(0);
    private final int[] macroDelay = new int[1];
    private final ImBoolean open = new ImBoolean(false);
    private final int[] inputsPct = new int[1];
    private final ImInt importFrom = new ImInt(1);
    private final ImInt importTo = new ImInt(1);
    private final ImInt landingPick = new ImInt(0);
    private final ImInt onejumpPick = new ImInt(0);
    private final ImString newName = new ImString(64);
    private final Map<String, ImString> buffers = new HashMap<String, ImString>();
    private String activeId;
    private boolean importInit;
    private boolean openClearModal;
    private boolean openNewModal;
    private boolean openDeleteModal;

    public OnejumpSetupWindow(TurnProfileController controller, AttemptTracker tracker, Settings settings,
                              MinecraftAccess mc, Supplier<Float> sensitivity, Runnable onSettingsChanged,
                              PracticeMacro macro) {
        this.controller = controller;
        this.tracker = tracker;
        this.settings = settings;
        this.sensitivity = sensitivity;
        this.onSettingsChanged = onSettingsChanged;
        this.macro = macro;
        final TurnReference ref = controller.document().reference();
        referenceTable = new InputOverlay(ref.data(), settings, new SelectionManager(mc), i -> controller.referenceChanged(),
                () -> { }, null, null, null, null);
        referenceTable.setRowFlags(
                new InputOverlay.RowFlag("Keys", "Check the keys of this tick against the attempt.", ref::checkKeys,
                        r -> ref.setCheckKeys(r, !ref.checkKeys(r))),
                new InputOverlay.RowFlag("Face", "Check the facing of this tick and show it in the Onejump graph.",
                        ref::checkYaw, r -> ref.setCheckYaw(r, !ref.checkYaw(r))));
        referenceTable.setShortcutsEnabled(() -> ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows));
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

    private float labelColumnWidth(float scale) {
        float max = 0f;
        for (String l : FORM_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    private void body(float scale) {
        controller.sync();
        TurnProfileController.Current cur = controller.current();
        TurnProfileDocument doc = controller.document();
        List<TurnAttempt> all = doc.attempts();
        float labelW = labelColumnWidth(scale);
        if (controller.name() == null) {
            emptyState(scale);
            newModal();
            return;
        }
        float lineH = ImGui.getTextLineHeightWithSpacing();
        if (Controls.beginTabBar("##onejump_tabs")) {
            if (Controls.beginTab("Overview")) {
                ThemeManager.sectionSpacing();
                overview(doc, scale);
                Controls.endTab();
            }
            if (Controls.beginTab("Jump")) {
                ThemeManager.sectionSpacing();
                float tableH = Math.max(minTableH(scale), ImGui.getContentRegionAvail().y - lineH * 5f
                        - ThemeManager.sectionSpacingHeight() * 2f);
                reference(labelW, scale, tableH);
                Controls.endTab();
            }
            if (Controls.beginTab("Attempts")) {
                ThemeManager.sectionSpacing();
                List<TurnAttempt> newest = new ArrayList<TurnAttempt>();
                for (int i = all.size() - 1; i >= 0 && newest.size() < LIST_LIMIT; i--) newest.add(all.get(i));
                ImGui.textDisabled(all.size() > LIST_LIMIT ? "latest " + LIST_LIMIT + " of " + all.size() : all.size() + " stored");
                float tableH = Math.max(minTableH(scale), ImGui.getContentRegionAvail().y - ThemeManager.sectionSpacingHeight());
                attemptsTable("##attemptsAll", newest, tableH, false);
                Controls.endTab();
            }
            if (Controls.beginTab("Settings")) {
                ThemeManager.sectionSpacing();
                settingsSection(cur, scale);
                Controls.endTab();
            }
            Controls.endTabBar();
        }
        clearModal();
        newModal();
        deleteModal();
    }

    private void emptyState(float scale) {
        String hint = "No onejump loaded";
        float availH = ImGui.getContentRegionAvail().y;
        float availW = ImGui.getContentRegionAvail().x;
        float comboW = ImGui.calcTextSize("a rather long onejump name").x + ImGui.getFrameHeight() + 24f * scale;
        float rowW = comboW + ImGui.getStyle().getItemSpacing().x + Controls.buttonWidth("New");
        float blockH = ImGui.getTextLineHeight() + ThemeManager.sectionSpacingHeight() + Controls.buttonHeight();
        ImGui.setCursorPosY(ImGui.getCursorPosY() + Math.max(0f, (availH - blockH) * 0.5f));
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ThemeManager.textCenter(hint);
        ThemeManager.popTextColor();
        ThemeManager.sectionSpacing();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0f, (availW - rowW) * 0.5f));
        pickerControls(comboW);
    }

    private static final String[] OVERVIEW_LABELS = {"Name", "Attempts", "Landed", "Input failures", "Closest",
            "Mouse practice", "Input practice", "Top 10"};

    private void overview(TurnProfileDocument doc, float scale) {
        TurnProfileDocument.Stats st = doc.stats();
        String name = controller.name();
        float labelW = 0f;
        Fonts.pushBold();
        for (String l : OVERVIEW_LABELS) labelW = Math.max(labelW, ImGui.calcTextSize(l).x);
        Fonts.popBold();
        labelW += ThemeManager.SM * scale;
        overviewRow("Name", name != null ? name : "none", labelW, name == null);
        overviewRow("Attempts", Integer.toString(st.attempts), labelW, false);
        overviewRow("Landed", Integer.toString(st.landings), labelW, false);
        overviewRow("Input failures", Integer.toString(st.inputFailures), labelW, false);
        overviewRow("Closest", st.hasClosest() ? TurnAttempt.signedMargin(st.closest) : "-", labelW, !st.hasClosest());
        overviewRow("Mouse practice", practiceText(st.mouseAttempts, st.mouseClears, PracticeMacro.LABEL_INPUTS), labelW,
                st.mouseAttempts == 0);
        overviewRow("Input practice", practiceText(st.inputAttempts, st.inputClears, PracticeMacro.LABEL_TURN), labelW,
                st.inputAttempts == 0);
        ThemeManager.sectionSpacing();
        Fonts.pushBold();
        ImGui.text("Top " + TurnProfileDocument.TOP);
        Fonts.popBold();
        List<TurnAttempt> top = doc.top();
        float topH = ThemeManager.tableHeaderRowHeight() + ThemeManager.tableRowHeight() * Math.max(1, top.size()) + 4f * scale;
        attemptsTable("##attemptsTop", top, topH, true);
    }

    private static String practiceText(int attempts, int clears, String macro) {
        if (attempts == 0) return "none  (" + macro + ")";
        return String.format(Locale.ROOT, "%d attempts   %d clears  (%s)", attempts, clears, macro);
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

    private void onejumpRow(float labelW, float scale) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Onejump", labelW);
        Controls.popInputFrameHeight();
        pickerControls(ImGui.calcTextSize("a rather long onejump name").x + ImGui.getFrameHeight() + 24f * scale);
    }

    private void pickerControls(float comboW) {
        List<String> names = controller.names();
        String current = controller.name();
        String[] items = new String[names.size() + 1];
        items[0] = names.isEmpty() ? "none saved" : "choose";
        int selected = 0;
        for (int i = 0; i < names.size(); i++) {
            items[i + 1] = names.get(i);
            if (names.get(i).equals(current)) selected = i + 1;
        }
        onejumpPick.set(selected);
        if (Controls.combo("##onejumpPick", onejumpPick, items, comboW) && onejumpPick.get() > 0) {
            controller.open(names.get(onejumpPick.get() - 1));
            importInit = false;
        }
        TooltipUtil.onHover("One file per onejump in parkourcalculator-onejump.");
        ImGui.sameLine();
        if (Controls.secondaryButton("New")) {
            newName.set("");
            openNewModal = true;
        }
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
        if (!ThemeManager.beginStandardClickableRowsTable(id, 3 + extra, 0, 0f, h)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float numW = ImGui.calcTextSize("999999").x;
        float landW = ImGui.calcTextSize("-99.99999").x;
        if (ranked) ImGui.tableSetupColumn("#", fixed, ThemeManager.tableLeftmostColumnWidth("#", ImGui.calcTextSize("99").x));
        ImGui.tableSetupColumn("attempt", fixed, ranked ? ThemeManager.tableNumericColumnWidth("attempt", numW)
                : ThemeManager.tableLeftmostColumnWidth("attempt", numW));
        ImGui.tableSetupColumn("landing", fixed, ThemeManager.tableNumericColumnWidth("landing", landW));
        ImGui.tableSetupColumn("keys", ImGuiTableColumnFlags.WidthStretch, 0f);
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        if (ranked) {
            ThemeManager.tableHeaderRight("#");
            ImGui.tableSetColumnIndex(1);
        }
        ThemeManager.tableHeaderRight("attempt");
        ImGui.tableSetColumnIndex(1 + extra);
        ThemeManager.tableHeaderRight("landing");
        ImGui.tableSetColumnIndex(2 + extra);
        ThemeManager.tableHeader("keys");
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
        for (int i = 0; i < list.size(); i++) {
            TurnAttempt a = list.get(i);
            boolean selected = a.number == selectedNumber;
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(i);
            ImGui.tableSetColumnIndex(0);
            ThemeManager.tableLeftmostCellPad();
            ImGui.alignTextToFramePadding();
            String first = ranked ? Integer.toString(i + 1) : Integer.toString(a.number);
            if (ThemeManager.rightAlignedSelectable(id + i, first, selected, ImGuiSelectableFlags.SpanAllColumns)) {
                controller.select(selected ? -1 : a.number);
            }
            if (ranked) {
                ImGui.tableSetColumnIndex(1);
                ThemeManager.textRight(Integer.toString(a.number));
            }
            ImGui.tableSetColumnIndex(1 + extra);
            if (a.hasMargin()) {
                ThemeManager.pushTextColor(a.landed ? ThemeManager.okColor() : ThemeManager.textMutedColor());
                ThemeManager.textRight(TurnAttempt.signedMargin(a.margin));
                ThemeManager.popTextColor();
            } else {
                ThemeManager.pushTextColor(ThemeManager.textDimColor());
                ThemeManager.textRight("-");
                ThemeManager.popTextColor();
            }
            ImGui.tableSetColumnIndex(2 + extra);
            String cell = a.inputFailure ? (a.failTick + 1) + ": " + TurnReference.describe(a.failKeys) + ", expected "
                    + TurnReference.describe(a.expectedKeys) : "";
            if (a.isMacro()) {
                String tag = a.macro == 1 ? PracticeMacro.LABEL_INPUTS : PracticeMacro.LABEL_TURN;
                ThemeManager.pushTextColor(ThemeManager.warningColor());
                ThemeManager.textLeft(cell.isEmpty() ? tag : tag + "   " + cell);
                ThemeManager.popTextColor();
            } else if (!cell.isEmpty()) {
                ThemeManager.textLeft(cell);
            }
        }
        ThemeManager.endStandardTable();
    }

    private void reference(float labelW, float scale, float tableH) {
        onejumpRow(labelW, scale);
        TurnReference ref = controller.document().reference();
        float tickW = ImGui.calcTextSize("99999").x + 14f * scale;
        float numW = ImGui.calcTextSize("-99999.00000").x + 14f * scale;
        if (!importInit && ref.tasFirstTick() >= 0 && !ref.isEmpty()) {
            importFrom.set(ref.tasFirstTick() + 1);
            importTo.set(ref.tasFirstTick() + ref.size());
            importInit = true;
        }
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("From TAS", labelW);
        ImGui.text("tick");
        Controls.popInputFrameHeight();
        ImGui.sameLine();
        textCell("importFrom", Integer.toString(importFrom.get()), tickW, s -> importFrom.set(parseInt(s, importFrom.get())));
        word("to");
        textCell("importTo", Integer.toString(importTo.get()), tickW, s -> importTo.set(parseInt(s, importTo.get())));
        ImGui.sameLine();
        if (Controls.secondaryButton("Import")) {
            if (controller.importFromTas(importFrom.get() - 1, importTo.get() - 1)) importInit = true;
        }
        TooltipUtil.onHover("Copies keys and facing of those ticks, and the solver's X and Z constraints as the landing.");
        String err = controller.lastError();
        if (err != null) {
            ImGui.sameLine();
            ThemeManager.pushTextColor(ThemeManager.dangerColor());
            ImGui.alignTextToFramePadding();
            ImGui.text(err);
            ThemeManager.popTextColor();
        }

        TurnReference.Landing landing = ref.landing();
        final int[] tick = {landing == null ? ref.size() + 1 : landing.tick + 1};
        final double[] b = landing == null ? new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN}
                : new double[] {landing.xLo, landing.xHi, landing.zLo, landing.zHi};
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Landing", labelW);
        ImGui.text("tick");
        Controls.popInputFrameHeight();
        ImGui.sameLine();
        boolean changed = textCell("landingTick", Integer.toString(tick[0]), tickW, s -> tick[0] = parseInt(s, tick[0]));
        List<TurnReference.Landing> options = controller.solverLandings();
        if (!options.isEmpty()) {
            ImGui.sameLine();
            String[] labels = new String[options.size() + 1];
            labels[0] = "from solver";
            for (int i = 0; i < options.size(); i++) labels[i + 1] = options.get(i).label();
            landingPick.set(0);
            float pickW = ImGui.calcTextSize("from solver").x + ImGui.getFrameHeight() + 24f * scale;
            if (Controls.combo("##landingPick", landingPick, labels, pickW) && landingPick.get() > 0) {
                controller.setLanding(options.get(landingPick.get() - 1));
            }
        }
        if (landing == null || landing.isEmpty()) {
            ImGui.sameLine();
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.alignTextToFramePadding();
            ImGui.text("no landing box: attempts are not judged");
            ThemeManager.popTextColor();
        }
        TooltipUtil.onHover("The tick whose position is checked against the box.");
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("X", labelW);
        Controls.popInputFrameHeight();
        changed |= textCell("landingXLo", bound(b[0]), numW, s -> b[0] = parseBound(s));
        word("to");
        changed |= textCell("landingXHi", bound(b[1]), numW, s -> b[1] = parseBound(s));
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Z", labelW);
        Controls.popInputFrameHeight();
        changed |= textCell("landingZLo", bound(b[2]), numW, s -> b[2] = parseBound(s));
        word("to");
        changed |= textCell("landingZHi", bound(b[3]), numW, s -> b[3] = parseBound(s));
        TooltipUtil.onHover("Empty = no limit.");
        if (changed) controller.setLanding(new TurnReference.Landing(Math.max(0, tick[0] - 1), b[0], b[1], b[2], b[3]));

        ThemeManager.sectionSpacing();
        ImGui.beginChild("##referenceTable", 0f, tableH, false, ImGuiWindowFlags.NoScrollbar);
        referenceTable.renderBody();
        ImGui.endChild();
    }

    private static void word(String text) {
        ImGui.sameLine();
        Controls.pushInputFrameHeight();
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
        Controls.popInputFrameHeight();
        ImGui.sameLine();
    }

    private static String bound(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.5f", v);
    }

    private static double parseBound(String s) {
        return s.trim().isEmpty() ? Double.NaN : parseDouble(s, Double.NaN);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean textCell(String id, String value, float width, Consumer<String> commit) {
        ImString buf = buffers.get(id);
        if (buf == null) {
            buf = new ImString(64);
            buffers.put(id, buf);
        }
        if (!id.equals(activeId)) buf.set(value);
        Controls.pushInputFrameHeight();
        Controls.tableInputText("##" + id, buf, width, ImGuiInputTextFlags.AutoSelectAll, null);
        Controls.popInputFrameHeight();
        boolean changed = false;
        if (ImGui.isItemActive()) activeId = id;
        if (ImGui.isItemDeactivatedAfterEdit()) {
            commit.accept(buf.get().trim());
            changed = true;
        }
        if (ImGui.isItemDeactivated() && id.equals(activeId)) activeId = null;
        return changed;
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final String[] MACRO_MODES = {"Off", PracticeMacro.LABEL_INPUTS, PracticeMacro.LABEL_TURN};

    private void macroSection(float scale) {
        Fonts.pushBold();
        ImGui.text("Practice replay");
        Fonts.popBold();
        macroPick.set(Math.max(0, Math.min(2, settings.onejumpMacroMode)));
        float w = ImGui.calcTextSize(MACRO_MODES[1]).x + ImGui.getFrameHeight() + 32f * scale;
        if (Controls.combo("##macroMode", macroPick, MACRO_MODES, w)) {
            settings.onejumpMacroMode = macroPick.get();
            macro.stop();
            onSettingsChanged.run();
        }
        TooltipUtil.onHover("A client-side replica runs the jump; your player stays put. Replay (inputs): the replica"
                + " presses the keys, you turn. Replay (turn): the replica turns, you press the keys. Starts after the"
                + " reset click plus the delay.");
        ImGui.pushItemWidth(200f * scale);
        macroDelay[0] = settings.onejumpMacroDelayMs;
        if (Controls.sliderInt("##macroDelay", macroDelay, 0, 5000, "delay %d ms")) {
            settings.onejumpMacroDelayMs = macroDelay[0];
            onSettingsChanged.run();
        }
        ImGui.popItemWidth();
        TooltipUtil.onHover("Time between the reset click and the macro starting.");
    }

    private void settingsSection(TurnProfileController.Current cur, float scale) {
        macroSection(scale);
        ThemeManager.sectionSpacing();
        if (Controls.checkbox("My attempts as dots", settings.turnProfileShowAttempts)) {
            settings.turnProfileShowAttempts = !settings.turnProfileShowAttempts;
            onSettingsChanged.run();
        }
        if (Controls.checkbox("Rated tries as dots", settings.turnProfileShowRating)) {
            settings.turnProfileShowRating = !settings.turnProfileShowRating;
            onSettingsChanged.run();
        }
        ImGui.pushItemWidth(150f * scale);
        inputsPct[0] = settings.turnProfileInputsHitPct;
        if (Controls.sliderInt("##inputsHit", inputsPct, 1, 100, "inputs %d%%")) {
            settings.turnProfileInputsHitPct = inputsPct[0];
            onSettingsChanged.run();
        }
        ImGui.popItemWidth();
        TooltipUtil.onHover("Assumed input hit rate, only used while no attempts are recorded.");
        ImGui.sameLine();
        float sens = sensitivity.get();
        ImGui.textDisabled(String.format(Locale.ROOT, "sens %.0f%%   1 px = %.3f°", sens * 200.0, TurnProfile.pixelDeg(sens)));

        boolean canRate = cur != null && cur.canRate();
        if (controller.isRating()) {
            Controls.disabledButton("Rating");
        } else if (!canRate) {
            Controls.disabledButton("Rate difficulty");
            TooltipUtil.onHover("Needs a reference imported from the TAS that meets its constraints.");
        } else if (Controls.secondaryButton("Rate difficulty")) {
            controller.rate();
        }
        if (canRate) TooltipUtil.onHover("Simulates tries with pixel-sized facing errors at your sensitivity.");
        ImGui.sameLine();
        AttemptSampler.Stats rs = cur == null ? null : cur.attempts;
        TurnProfileDocument.Stats st = controller.stats();
        if (rs == null) {
            ImGui.textDisabled(controller.isRating() ? "sampling" : cur != null && !cur.pathLands()
                    ? "the reference path does not meet the TAS constraints" : "not rated");
        } else {
            double turnRate = rs.rate();
            boolean measured = st.attempts > 0;
            double inputs = measured ? 1.0 - st.inputFailures / (double) st.attempts : settings.turnProfileInputsHitPct / 100.0;
            ImGui.text(String.format(Locale.ROOT, "lands %s of tries  (turn %s, inputs %.0f%% %s, %s samples)",
                    oneIn(turnRate * inputs), pct(turnRate), inputs * 100.0, measured ? "from your attempts" : "assumed",
                    compact(rs.attempts)));
        }
        dangerZone();
    }

    private void dangerZone() {
        ThemeManager.sectionSpacing();
        ImGui.textDisabled("Danger zone");
        int count = controller.stats().attempts;
        if (count == 0) {
            Controls.disabledButton("Clear attempts");
        } else if (Controls.dangerButton("Clear attempts")) {
            openClearModal = true;
        }
        TooltipUtil.onHover("Deletes every attempt. The reference stays.");
        ImGui.sameLine();
        if (controller.name() == null) {
            Controls.disabledButton("Delete onejump");
        } else if (Controls.dangerButton("Delete onejump")) {
            openDeleteModal = true;
        }
        TooltipUtil.onHover("Deletes the whole onejump file.");
    }

    private void clearModal() {
        if (openClearModal) {
            ImGui.openPopup(POPUP_CLEAR);
            openClearModal = false;
        }
        if (!Modal.begin("Clear attempts", POPUP_CLEAR)) return;
        int count = controller.stats().attempts;
        String name = controller.name();
        ImGui.text("Delete all " + count + " attempts of onejump '" + (name != null ? name : "?") + "'?");
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

    private void newModal() {
        if (openNewModal) {
            ImGui.openPopup(POPUP_NEW);
            openNewModal = false;
        }
        if (!Modal.begin("New onejump", POPUP_NEW)) return;
        ImGui.text("Name");
        ImGui.sameLine();
        Controls.pushInputFrameHeight();
        boolean enter = ImGui.inputText("##onejumpNewName", newName, ImGuiInputTextFlags.EnterReturnsTrue);
        Controls.popInputFrameHeight();
        ImGui.textDisabled("Starts empty.");
        Modal.footerSeparator();
        String name = newName.get().trim();
        boolean ok = !name.isEmpty();
        if (!ok) Controls.disabledButton("Create");
        else if (Controls.primaryButton("Create") || enter) {
            controller.create(name);
            importInit = false;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Modal.footerButton("Cancel")) ImGui.closeCurrentPopup();
        Modal.end();
    }

    private void deleteModal() {
        if (openDeleteModal) {
            ImGui.openPopup(POPUP_DELETE);
            openDeleteModal = false;
        }
        if (!Modal.begin("Delete onejump", POPUP_DELETE)) return;
        String name = controller.name();
        ImGui.text("Delete onejump '" + (name != null ? name : "?") + "' with all its attempts?");
        ImGui.textDisabled("The file is removed from disk.");
        Modal.footerSeparator();
        if (Controls.dangerButton("Delete")) {
            controller.delete();
            tracker.reset();
            controller.select(-1);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Modal.footerButton("Cancel")) ImGui.closeCurrentPopup();
        Modal.end();
    }
}
