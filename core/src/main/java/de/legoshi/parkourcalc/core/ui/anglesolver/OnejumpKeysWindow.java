package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.TurnTiming;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

public final class OnejumpKeysWindow implements RenderInterface {

    private static final String WINDOW_ID = "###onejumpKeys";
    private static final String TITLE = "Onejump Keys";
    private static final float WIN_W = 720f;
    private static final float WIN_H = 210f;
    private static final float MIN_W = 300f;
    private static final float MIN_H = 120f;
    private static final float PAD = 8f;
    private static final float CELL_GAP = 1.5f;
    private static final float STRIP_H = 4f;
    private static final float STRIP_GAP = 3f;

    private final TurnProfileController controller;
    private final AttemptTracker tracker;
    private final Settings settings;
    private final Runnable onSettingsChanged;
    private final ImBoolean open = new ImBoolean(false);

    public OnejumpKeysWindow(TurnProfileController controller, AttemptTracker tracker, Settings settings,
                             Runnable onSettingsChanged) {
        this.controller = controller;
        this.tracker = tracker;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
    }

    @Override
    public void render(ImGuiIO io) {
        render(io, false);
    }

    @Override
    public void renderDetached(ImGuiIO io) {
        render(io, true);
    }

    private void render(ImGuiIO io, boolean gridOnly) {
        open.set(settings.viewOnejumpKeys);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean visible;
        if (gridOnly) {
            visible = ImGui.begin(WINDOW_ID, open, flags | ImGuiWindowFlags.NoTitleBar);
        } else {
            ThemeManager.pushHeaderChrome();
            visible = ImGui.begin(WINDOW_ID, open, flags);
            if (visible) ThemeManager.drawModalTitle(TITLE);
            ThemeManager.popHeaderChrome();
        }
        if (visible) body(scale);
        ImGui.end();
        if (settings.viewOnejumpKeys != open.get()) {
            settings.viewOnejumpKeys = open.get();
            onSettingsChanged.run();
        }
    }

    private static final String[] HOW_TO = {
            "No keys to check. Mark \"Keys\" in the input table for those keys you want checked.",
            "Hint: Shift click a key to make it optional on that tick."};

    private void body(float scale) {
        TurnProfileController.Current cur = controller.current();
        float w = Math.max(80f, ImGui.getContentRegionAvail().x);
        float h = Math.max(40f, ImGui.getContentRegionAvail().y);
        ImVec2 origin = ImGui.getCursorScreenPos();
        float x0 = origin.x, y0 = origin.y;
        ImGui.invisibleButton("##onejumpKeyGrid", w, h);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x0, y0, x0 + w, y0 + h, ThemeManager.bgDarkColor(), 0f);
        if (cur == null || cur.n == 0) {
            float lineH = ImGui.getTextLineHeightWithSpacing();
            float y = y0 + (h - lineH * HOW_TO.length) * 0.5f;
            for (String line : HOW_TO) {
                ImVec2 ts = ImGui.calcTextSize(line);
                dl.addText(x0 + (w - ts.x) * 0.5f, y, ThemeManager.textDimColor(), line);
                y += lineH;
            }
            return;
        }
        TurnAttempt you = tracker.shownAttempt();
        int n = cur.n;
        int lead = cur.leadKeys.length;
        int cols = lead + n;
        float pad = PAD * scale;
        float lineH = ImGui.getTextLineHeight();
        float labelW = ImGui.calcTextSize("Snk").x + pad;
        float gridX = x0 + pad + labelW;
        float gridW = w - pad * 2f - labelW;
        float gridY = y0 + pad;
        boolean timing = settings.onejumpTurnTiming;
        float stripH = timing ? STRIP_H * scale : 0f;
        float stripGap = timing ? STRIP_GAP * scale : 0f;
        float gridH = h - pad * 2f - lineH - stripH - stripGap;
        float cellW = gridW / cols;
        float rowH = gridH / TurnReference.LABELS.length;
        float gap = CELL_GAP * scale;
        int failT = you != null && you.inputFailure ? you.failTick - cur.startTick : -1;
        int turnFailT = you != null && you.turnFailure ? you.failTick - cur.startTick : -1;
        int reached = you == null ? -1 : you.turnFailure ? turnFailT - 1
                : you == tracker.live() ? you.recorded - 2 : you.recorded - 1;
        int hoverT = Integer.MIN_VALUE;
        float mx = ImGui.getMousePosX();
        if (hovered && mx >= gridX && mx < gridX + gridW) hoverT = Math.min(cols - 1, (int) ((mx - gridX) / cellW)) - lead;
        boolean leadPassed = you != null && reached >= 0;
        int unchecked = ThemeManager.textDimTintColor(0.45f);
        int uncheckedPassed = ThemeManager.textDimTintColor(0.75f);
        int pending = tracker.live() == null ? tracker.pendingTicks() : -1;
        int pendingCols = pending < 0 ? 0 : Math.min(pending + 1, cols);
        for (int c = 0; c < pendingCols; c++) {
            float cx0 = gridX + c * cellW;
            dl.addRectFilled(cx0 + gap, gridY + gridH - gap, cx0 + cellW - gap, gridY + gridH,
                    ThemeManager.textDimTintColor(0.9f), 0f);
        }

        int muted = ThemeManager.textMutedColor();
        int dim = ThemeManager.textDimColor();
        for (int k = 0; k < TurnReference.LABELS.length; k++) {
            float ry = gridY + k * rowH;
            dl.addText(x0 + pad, ry + (rowH - lineH) * 0.5f, muted, TurnReference.LABELS[k]);
        }
        for (int t = -lead; t < 0; t++) {
            float cx0 = gridX + (t + lead) * cellW;
            if (t == hoverT) dl.addRectFilled(cx0, gridY, cx0 + cellW, gridY + gridH, ThemeManager.selectedTintColor(0.15f), 0f);
            if (leadPassed) {
                dl.addRectFilled(cx0 + gap, gridY + gridH - gap, cx0 + cellW - gap, gridY + gridH,
                        ThemeManager.okTintColor(0.7f), 0f);
            }
            int expected = cur.leadKeys[t + lead];
            for (int k = 0; k < TurnReference.LABELS.length; k++) {
                if ((expected & TurnReference.LABEL_BITS[k]) == 0) continue;
                float ry = gridY + k * rowH;
                dl.addRectFilled(cx0 + gap, ry + gap, cx0 + cellW - gap, ry + rowH - gap,
                        leadPassed ? uncheckedPassed : unchecked, 2f * scale);
            }
        }
        if (lead > 0) {
            float bx = gridX + lead * cellW;
            dl.addLine(bx, gridY, bx, gridY + gridH, ThemeManager.textDimColor(), 1f * scale);
        }
        for (int t = 0; t < n; t++) {
            float cx0 = gridX + (t + lead) * cellW;
            boolean checked = cur.checkKeys[t];
            int abs = cur.startTick + t;
            boolean bad = you != null && t <= reached && you.keysFailedAt(abs);
            boolean passed = you != null && t <= reached && !bad;
            boolean matched = passed && checked && you.keysRecordedAt(abs);
            if (passed && !checked) {
                dl.addRectFilled(cx0 + gap, gridY + gridH - gap, cx0 + cellW - gap, gridY + gridH,
                        ThemeManager.okTintColor(0.7f), 0f);
            }
            if (t == turnFailT) dl.addRectFilled(cx0, gridY, cx0 + cellW, gridY + gridH, ThemeManager.dangerTintColor(0.2f), 0f);
            if (t == hoverT) dl.addRectFilled(cx0, gridY, cx0 + cellW, gridY + gridH, ThemeManager.selectedTintColor(0.15f), 0f);
            int expected = cur.keys[t];
            int pressed = bad ? you.pressedKeysAt(abs) : expected;
            for (int k = 0; k < TurnReference.LABELS.length; k++) {
                float ry = gridY + k * rowH;
                float ax = cx0 + gap, bx = cx0 + cellW - gap, ay = ry + gap, by = ry + rowH - gap;
                boolean exp = (expected & TurnReference.LABEL_BITS[k]) != 0;
                boolean got = (pressed & TurnReference.LABEL_BITS[k]) != 0;
                if (checked && (cur.optionalKeys[t] & TurnReference.LABEL_BITS[k]) != 0) {
                    dl.addRect(ax, ay, bx, by, ThemeManager.textDimColor(), 2f * scale, 0, 1f * scale);
                    continue;
                }
                if (bad && exp != got) {
                    if (got) dl.addRectFilled(ax, ay, bx, by, ThemeManager.dangerColor(), 2f * scale);
                    else dl.addRect(ax, ay, bx, by, ThemeManager.dangerColor(), 2f * scale, 0, 1.5f * scale);
                    continue;
                }
                if (!exp) continue;
                int col = !checked ? (passed ? uncheckedPassed : unchecked) : matched ? ThemeManager.okTintColor(0.85f)
                        : bad ? ThemeManager.textDimColor() : passed ? uncheckedPassed : ThemeManager.accentTintColor(0.55f);
                dl.addRectFilled(ax, ay, bx, by, col, 2f * scale);
            }
        }
        if (timing) {
            float sy = gridY + gridH + stripGap;
            boolean timed = you != null && you.hasTiming();
            for (int t = -lead; t < n; t++) {
                float ax = gridX + (t + lead) * cellW + gap, bx = gridX + (t + lead + 1) * cellW - gap;
                dl.addRectFilled(ax, sy, bx, sy + stripH, ThemeManager.bgTintColor(0.9f), 1f * scale);
                if (!timed || t < 0) continue;
                float on = you.turnStartAt(cur.startTick + t);
                if (Float.isNaN(on)) continue;
                float off = you.turnEndAt(cur.startTick + t);
                float w0 = bx - ax;
                dl.addRectFilled(ax + on * w0, sy, Math.max(ax + on * w0 + 1f * scale, ax + off * w0), sy + stripH,
                        ThemeManager.accentTintColor(0.9f), 1f * scale);
            }
        }
        int labelEvery = Math.max(1, (int) Math.ceil(ImGui.calcTextSize("000").x * 1.4f / Math.max(1f, cellW)));
        for (int t = -lead; t < n; t++) {
            if ((t + lead) % labelEvery != 0 && t != n - 1) continue;
            String lbl = Integer.toString(cur.tasTick(t) + 1);
            float cx = gridX + (t + lead + 0.5f) * cellW;
            dl.addText(cx - ImGui.calcTextSize(lbl).x * 0.5f, gridY + gridH + stripGap + stripH + 2f * scale,
                    t >= 0 && cur.jumpTicks[t] ? ThemeManager.peachTintColor(0.9f) : dim, lbl);
        }
        if (hoverT != Integer.MIN_VALUE && hoverT < 0) {
            ImGui.beginTooltip();
            ImGui.text("tick " + (cur.tasTick(hoverT) + 1) + "  " + TurnReference.describe(cur.leadKeys[hoverT + lead])
                    + "  (before the reference, not checked)");
            if (leadPassed) ImGui.textDisabled("passed");
            ImGui.endTooltip();
        }
        if (hoverT >= 0) {
            int t = hoverT;
            ImGui.beginTooltip();
            ImGui.text("tick " + (cur.tasTick(t) + 1) + "  " + TurnReference.describe(cur.keys[t])
                    + (cur.checkKeys[t] ? "" : "  (not checked)") + (cur.still[t] ? "  still" : "")
                    + (cur.checkKeys[t] && cur.optionalKeys[t] != 0 ? "  optional " + TurnReference.describe(cur.optionalKeys[t]) : ""));
            if (t == turnFailT) {
                ImGui.pushStyleColor(ImGuiCol.Text, ThemeManager.dangerColor());
                ImGui.text(you.verdict);
                ImGui.popStyleColor();
            }
            if (you != null && t <= reached && you.keysFailedAt(cur.startTick + t)) {
                ImGui.pushStyleColor(ImGuiCol.Text, ThemeManager.dangerColor());
                ImGui.text("pressed " + TurnReference.describe(you.pressedKeysAt(cur.startTick + t)));
                ImGui.popStyleColor();
            } else if (you != null && t <= reached && cur.checkKeys[t] && you.keysRecordedAt(cur.startTick + t)) {
                ImGui.pushStyleColor(ImGuiCol.Text, ThemeManager.okColor());
                ImGui.text("matched");
                ImGui.popStyleColor();
            } else if (you != null && t <= reached) {
                ImGui.textDisabled(cur.checkKeys[t] ? "passed, keys not checked" : "passed");
            }
            if (timing && you != null && you.hasTiming()) {
                float on = you.turnStartAt(cur.startTick + t);
                ImGui.textDisabled(Float.isNaN(on) ? "no turn in this tick" : "turn " + TurnTiming.ms(on) + " to "
                        + TurnTiming.ms(you.turnEndAt(cur.startTick + t)) + " into the tick");
            }
            ImGui.endTooltip();
        }
    }
}
