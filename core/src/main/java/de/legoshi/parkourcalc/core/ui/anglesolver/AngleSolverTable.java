package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.Potion;
import de.legoshi.parkourcalc.core.anglesolver.PotionDose;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.IntSupplier;

/**
 * The Angle Solver's table-side UI: the gutter chevron / start-landing flags, the inline
 * "Constraints / state" column (draggable constraint chips + peach state-override chips),
 * the start/landing inset bars, and the per-row editor drawer. InputOverlay owns the table
 * and calls into this class only when the solver is active.
 */
public final class AngleSolverTable {

    private static final String[] INPUTS = {"Keep", "Force 45"};

    private final AngleSolverState state;
    private final Settings settings;
    private final SelectionManager selection;
    private final IntSupplier rowCount;

    private int expandedRow = -1;

    // Drawer height: measured from the prior frame's content so bottom padding matches the top.
    private int measuredTick = -1;
    private float measuredContentH = -1f;

    // Constraint picked by clicking its chip: opens that tick's drawer and highlights the editor row.
    private int selectedConstraintTick = -1;
    private int selectedConstraintIndex = -1;

    // State facet picked by clicking its chip: opens the drawer and highlights the matching editor row.
    private int selectedStateTick = -1;
    private DragKind selectedStateKind;
    private Potion selectedStatePotion;

    // Chip drag (manual): tracked across frames while a chip is held. A dragged chip is either a
    // constraint (dragIndex into the tick's list) or one facet of the tick's state override.
    private enum DragKind { CONSTRAINT, STATE_INPUTS, STATE_SLIP, STATE_ADD, STATE_REMOVE }

    private boolean dragging;
    private DragKind dragKind = DragKind.CONSTRAINT;
    private int dragTick = -1;
    private int dragIndex = -1;
    private Potion dragPotion;
    private String dragLabel = "";
    private boolean dragAlt;
    private int dropTick = -1; // cell under the cursor during drag; one frame lagged for the highlight

    private final Map<Integer, float[]> constraintCellRects = new HashMap<>();
    private final Map<Integer, float[]> stateCellRects = new HashMap<>();

    // Per-row grown height (max of the constraint/state cells), carried a frame so single-line columns center.
    private final Map<Integer, Float> rowContentH = new HashMap<>();

    // Full-row gutter rect carried out to InputOverlay (the chevron is the last item drawn there).
    private float gMinX, gMinY, gMaxX, gMaxY;

    private final ImString numBuf = new ImString(32);
    private final ImInt slipBuf = new ImInt();
    private final ImInt doseCombo = new ImInt();
    private final ImInt levelBuf = new ImInt();
    private final ImInt fieldCombo = new ImInt();
    private final ImInt opCombo = new ImInt();
    private final String[] slipItems = Slipperiness.comboItems();
    private static final String[] FIELD_ITEMS = buildFieldItems();
    private static final Constraint.Op[] SCALAR_OPS =
            {Constraint.Op.GT, Constraint.Op.LT, Constraint.Op.GE, Constraint.Op.LE, Constraint.Op.EQ};
    // One combo carries the form: the scalar comparisons, then the four range brackets (lo/hi
    // inclusivity encoded as bit 2 / bit 1 of the offset past SCALAR_OPS, matching opItemIndex).
    private static final String[] OP_ITEMS = {">", "<", ">=", "<=", "=", "( )", "( ]", "[ )", "[ ]"};

    private static String[] buildFieldItems() {
        Constraint.Field[] all = Constraint.Field.values();
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) items[i] = all[i].label;
        return items;
    }

    private static int opItemIndex(Constraint c) {
        if (c.isRange()) return SCALAR_OPS.length + (c.isLoInclusive() ? 2 : 0) + (c.isHiInclusive() ? 1 : 0);
        for (int i = 0; i < SCALAR_OPS.length; i++) if (SCALAR_OPS[i] == c.getOp()) return i;
        return 0;
    }

    public AngleSolverTable(AngleSolverState state, Settings settings, SelectionManager selection,
                            IntSupplier rowCount) {
        this.state = state;
        this.settings = settings;
        this.selection = selection;
        this.rowCount = rowCount;
    }

    public boolean isActive() {
        return settings.viewAngleSolver;
    }

    // ---- row-edit forwarding (gh-89): InputOverlay reports row edits so the per-tick solver data
    // (constraints, overrides, start/landing) follows its rows. Plain delegates to the model.

    public void onRowsInserted(int index, int count) {
        state.onRowsInserted(index, count);
    }

    public void onRowsRemoved(java.util.List<Integer> descendingIndices) {
        state.onRowsRemoved(descendingIndices);
    }

    public void onRowMoved(int from, int to) {
        state.onRowMoved(from, to);
        // Row-keyed drawer UI state follows its row through a drag-move (gh-119).
        expandedRow = AngleSolverState.mapRowMove(expandedRow, from, to);
        measuredTick = AngleSolverState.mapRowMove(measuredTick, from, to);
        selectedConstraintTick = AngleSolverState.mapRowMove(selectedConstraintTick, from, to);
        selectedStateTick = AngleSolverState.mapRowMove(selectedStateTick, from, to);
    }

    public void onRowDuplicated(int sourceIndex) {
        state.onRowDuplicated(sourceIndex);
    }


    public String constraintsColumnHeaderLabel() {
        return "Constraints";
    }

    public String stateColumnHeaderLabel() {
        return "State";
    }

    public float minConstraintsColumnWidth() {
        return 150f * ThemeManager.uiScale();
    }

    public float minStateColumnWidth() {
        return 120f * ThemeManager.uiScale();
    }

    public float gutterExtraWidth() {
        return 34f * ThemeManager.uiScale();
    }

    public boolean isExpanded(int rowIndex) {
        return expandedRow == rowIndex;
    }

    /** Grown height of this row from the prior frame's chip layout, floored at the single-line base. */
    public float rowHeight(int rowIndex, float baseRowH) {
        Float h = rowContentH.get(rowIndex);
        return (h == null || h < baseRowH) ? baseRowH : h;
    }

    public int expandedRow() {
        return expandedRow;
    }

    private void toggleExpanded(int rowIndex) {
        expandedRow = (expandedRow == rowIndex) ? -1 : rowIndex;
    }

    public float gutterMinX() { return gMinX; }
    public float gutterMinY() { return gMinY; }
    public float gutterMaxX() { return gMaxX; }
    public float gutterMaxY() { return gMaxY; }

    // ---- per-frame drag lifecycle ----------------------------------------------

    public void beginRows() {
        constraintCellRects.clear();
        stateCellRects.clear();
    }

    public void endRows() {
        if (!dragging) {
            dropTick = -1;
            return;
        }
        dragAlt = ImGui.getIO().getKeyAlt();
        ImVec2 mouse = ImGui.getMousePos();
        // Only the column the dragged item belongs to is a valid drop target, so it alone highlights.
        Map<Integer, float[]> targets = dragKind == DragKind.CONSTRAINT ? constraintCellRects : stateCellRects;
        int hover = tickAtIn(targets, mouse.x, mouse.y);
        dropTick = hover;
        drawGhost(hover);
        if (ImGui.isMouseReleased(0)) {
            if (hover >= 0) applyDrop(hover);
            dragging = false;
            dragTick = -1;
            dragIndex = -1;
            dragPotion = null;
            dropTick = -1;
        }
    }

    private void applyDrop(int hover) {
        if (dragKind == DragKind.CONSTRAINT) {
            if (dragAlt) state.copyConstraint(dragTick, dragIndex, hover);
            else if (hover != dragTick) state.moveConstraint(dragTick, dragIndex, hover);
            return;
        }
        if (hover == dragTick && !dragAlt) return;
        StateOverride src = state.tickConstraints(dragTick).getOverride();
        StateOverride dst = state.tickConstraints(hover).getOverride();
        switch (dragKind) {
            case STATE_INPUTS:
                dst.setInputs(src.getInputs());
                if (!dragAlt) src.clearInputs();
                break;
            case STATE_SLIP:
                dst.setSlipperiness(src.getSlipperiness());
                if (!dragAlt) src.clearSlipperiness();
                break;
            case STATE_ADD: {
                PotionDose srcDose = src.findAdded(dragPotion);
                int lvl = srcDose != null ? srcDose.level : 1;
                PotionDose dstDose = dst.findAdded(dragPotion);
                if (dstDose == null) dst.getAdded().add(new PotionDose(dragPotion, lvl));
                else dstDose.level = lvl;
                if (!dragAlt) src.removeAdded(dragPotion);
                break;
            }
            case STATE_REMOVE:
                dst.getRemoved().add(dragPotion);
                if (!dragAlt) src.getRemoved().remove(dragPotion);
                break;
            default:
                break;
        }
    }

    private static int tickAtIn(Map<Integer, float[]> rects, float x, float y) {
        for (Map.Entry<Integer, float[]> e : rects.entrySet()) {
            float[] r = e.getValue();
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) return e.getKey();
        }
        return -1;
    }

    private void drawGhost(int hover) {
        ImDrawList dl = ImGui.getForegroundDrawList();
        ImVec2 m = ImGui.getMousePos();
        float s = ThemeManager.uiScale();
        String tail = (hover >= 0)
                ? (dragAlt ? "  copy -> T" + (hover + 1) : "  move -> T" + (hover + 1))
                : "";
        float pad = 6f * s;
        float labelW = ImGui.calcTextSize(dragLabel).x;
        float tailW = ImGui.calcTextSize(tail).x;
        float h = ImGui.getFrameHeight();
        float x = m.x + 12f * s;
        float y = m.y + 4f * s;
        float w = pad + labelW + tailW + pad;
        // A state-facet drag carries the peach chip color; a constraint drag keeps the accent color.
        boolean stateDrag = dragKind != DragKind.CONSTRAINT;
        int family = stateDrag ? ThemeManager.peachColor() : ThemeManager.accentColor();
        dl.addRectFilled(x, y, x + w, y + h, ThemeManager.panelColor(), 3f * s);
        dl.addRect(x, y, x + w, y + h, family, 3f * s, 0, 1f);
        float ty = y + (h - ImGui.getFontSize()) * 0.5f;
        dl.addText(x + pad, ty, ThemeManager.textColor(), dragLabel);
        dl.addText(x + pad + labelW, ty, dragAlt ? ThemeManager.okColor() : family, tail);
    }

    // ---- gutter (chevron + flag + number + selection) --------------------------

    public void renderGutter(int rowIndex, float rowH, Runnable dragDropHook) {
        ThemeManager.tableLeftmostCellPad();
        ImVec2 origin = ImGui.getCursorScreenPos();
        float s = ThemeManager.uiScale();

        boolean sel = selection.isSelected(rowIndex);
        int flags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap;
        if (ThemeManager.rightAlignedSelectable("row" + rowIndex, "", sel, flags)) {
            selection.handleClick(rowIndex);
        }
        ImVec2 selMin = ImGui.getItemRectMin();
        ImVec2 selMax = ImGui.getItemRectMax();
        // The selectable is only one line tall; carry the full grown-row rect so the start/landing
        // inset and hover/selection bounds span the whole multi-line row.
        gMinX = selMin.x; gMinY = selMin.y; gMaxX = selMax.x; gMaxY = selMin.y + rowH;
        // Row drag-drop must attach to the selectable (ImGui targets the last item), so the hook
        // runs here, before the chevron becomes the last item (gh-119).
        if (dragDropHook != null) dragDropHook.run();

        ImDrawList dl = ImGui.getWindowDrawList();
        float chevW = 12f * s;
        float cy = origin.y + rowH * 0.5f;
        ImGui.setCursorScreenPos(origin.x, origin.y);
        boolean chevClicked = ImGui.invisibleButton("chev" + rowIndex, chevW, rowH);
        boolean open = isExpanded(rowIndex);
        int chevCol = open ? ThemeManager.accentColor() : ThemeManager.textDimColor();
        if (open) SolverWidgets.triangleDown(dl, origin.x + chevW * 0.5f, cy, 3.3f * s, chevCol);
        else SolverWidgets.triangleRight(dl, origin.x + chevW * 0.5f, cy, 3.3f * s, chevCol);
        if (chevClicked) toggleExpanded(rowIndex);

        float x = origin.x + chevW;
        if (state.isStart(rowIndex)) x = drawFlag(dl, x, origin.y, rowH, "S", ThemeManager.okColor());
        else if (state.isLanding(rowIndex)) x = drawFlag(dl, x, origin.y, rowH, "G", ThemeManager.dangerColor());

        float ty = origin.y + (rowH - ImGui.getFontSize()) * 0.5f;
        dl.addText(x + 4f * s, ty, ThemeManager.textMutedColor(), String.valueOf(rowIndex + 1));
    }

    private float drawFlag(ImDrawList dl, float x, float top, float rowH, String letter, int color) {
        float s = ThemeManager.uiScale();
        float pad = 3f * s;
        float fontH = ImGui.getFontSize();
        float bw = ImGui.calcTextSize(letter).x + 2f * pad;
        float bh = fontH + pad;
        float by = top + (rowH - bh) * 0.5f;
        dl.addRectFilled(x, by, x + bw, by + bh, color, 2f * s);
        dl.addText(x + pad, by + (bh - fontH) * 0.5f, ThemeManager.bgDarkColor(), letter);
        return x + bw + 4f * s;
    }

    public void drawStartLandingInset(int rowIndex, float minX, float minY, float maxX, float maxY) {
        int col;
        if (state.isStart(rowIndex)) col = ThemeManager.okColor();
        else if (state.isLanding(rowIndex)) col = ThemeManager.dangerColor();
        else return;
        float s = ThemeManager.uiScale();
        ImGui.getWindowDrawList().addRectFilled(minX, minY, minX + 3f * s, maxY, col);
    }

    // ---- constraints / state cell ----------------------------------------------

    // grownRowH is the row's full rendered height (last frame's wrapped content). The drop target rect and
    // drop highlight use it so they span the whole multi-line row, not just this cell's own content line.
    public void renderConstraintsCell(int rowIndex, float grownRowH) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        float baseRowH = ThemeManager.tableRowHeight();

        TickConstraints tc = state.tickConstraintsOrNull(rowIndex);
        final List<Constraint> list = tc == null ? null : tc.getConstraints();
        if (list == null || list.isEmpty()) {
            constraintCellRects.put(rowIndex, cellRect(origin, cellW, grownRowH));
            rowContentH.put(rowIndex, baseRowH); // constraint cell renders first, so it seeds this frame's row height
            drawDropHighlight(rowIndex, origin, cellW, grownRowH, true);
            return;
        }

        int n = list.size();
        float[] chipW = new float[n];
        for (int i = 0; i < n; i++) chipW[i] = constraintChipWidth(list.get(i));
        ChipLayout layout = computeChipLayout(cellW, baseRowH, chipW);

        constraintCellRects.put(rowIndex, cellRect(origin, cellW, grownRowH));
        rowContentH.put(rowIndex, layout.cellH);
        drawDropHighlight(rowIndex, origin, cellW, grownRowH, true);
        placeChips(origin, cellW, grownRowH, layout, i -> renderConstraintChip(rowIndex, i, list.get(i)));
    }

    public void renderStateCell(int rowIndex, float grownRowH) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        float baseRowH = ThemeManager.tableRowHeight();

        TickConstraints tc = state.tickConstraintsOrNull(rowIndex);
        StateOverride ov = tc == null ? null : tc.getOverride();
        if (ov == null || ov.isEmpty()) {
            stateCellRects.put(rowIndex, cellRect(origin, cellW, grownRowH));
            mergeRowContentHeight(rowIndex, baseRowH);
            drawDropHighlight(rowIndex, origin, cellW, grownRowH, false);
            return;
        }

        final List<StateChipSpec> specs = collectStateChips(ov);
        int n = specs.size();
        float[] chipW = new float[n];
        for (int i = 0; i < n; i++) chipW[i] = stateChipWidth(specs.get(i).key, specs.get(i).value);
        ChipLayout layout = computeChipLayout(cellW, baseRowH, chipW);

        stateCellRects.put(rowIndex, cellRect(origin, cellW, grownRowH));
        mergeRowContentHeight(rowIndex, layout.cellH);
        drawDropHighlight(rowIndex, origin, cellW, grownRowH, false);
        placeChips(origin, cellW, grownRowH, layout, i -> {
            StateChipSpec sp = specs.get(i);
            stateChip(rowIndex, sp.kind, sp.potion, sp.key, sp.value, sp.struck);
        });
    }

    // State cell renders after the constraint cell, so it raises (never seeds) the row's grown height.
    private void mergeRowContentHeight(int rowIndex, float cellH) {
        Float cur = rowContentH.get(rowIndex);
        rowContentH.put(rowIndex, cur == null ? cellH : Math.max(cur, cellH));
    }

    // Full cell rect (origin is the cell's content top-left, inset by cell padding): grow back out by the
    // padding so the drop hitbox and its highlight span the whole cell edge-to-edge and top-to-bottom.
    private float[] cellRect(ImVec2 origin, float cellW, float grownRowH) {
        float padX = ImGui.getStyle().getCellPadding().x;
        float padY = ImGui.getStyle().getCellPadding().y;
        float x0 = origin.x - padX;
        float y0 = origin.y - padY;
        return new float[]{x0, y0, origin.x + cellW + padX, y0 + grownRowH};
    }

    private void drawDropHighlight(int rowIndex, ImVec2 origin, float cellW, float grownRowH, boolean constraintColumn) {
        if (!dragging || dropTick != rowIndex) return;
        if (constraintColumn != (dragKind == DragKind.CONSTRAINT)) return;
        float[] r = cellRect(origin, cellW, grownRowH);
        int fill = constraintColumn ? ThemeManager.accentTintColor(0.16f) : ThemeManager.peachTintColor(0.16f);
        int border = constraintColumn ? ThemeManager.accentColor() : ThemeManager.peachColor();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(r[0], r[1], r[2], r[3], fill, 0f);
        dl.addRect(r[0], r[1], r[2], r[3], border, 0f, 0, 1f);
    }

    // ---- chip layout: wrap onto new lines and center each line in the cell -------

    private interface ChipRenderer {
        void render(int index);
    }

    private static final class ChipLayout {
        int[] lineOf;
        float[] lineW;
        float cellH;
        int lines;
    }

    private ChipLayout computeChipLayout(float cellW, float rowH, float[] chipW) {
        float s = ThemeManager.uiScale();
        float gap = 5f * s;
        float lineH = ImGui.getFrameHeight();
        float spacingY = ImGui.getStyle().getItemSpacing().y;
        int n = chipW.length;

        ChipLayout layout = new ChipLayout();
        layout.lineOf = new int[n];
        layout.lineW = new float[Math.max(1, n)];
        int lines = 1;
        float usedX = 0f;
        for (int i = 0; i < n; i++) {
            if (i == 0) usedX = chipW[i];
            else if (usedX + gap + chipW[i] <= cellW) usedX += gap + chipW[i];
            else { lines++; usedX = chipW[i]; }
            layout.lineOf[i] = lines - 1;
            layout.lineW[lines - 1] = usedX;
        }
        layout.lines = lines;
        layout.cellH = Math.max(rowH, lines * lineH + (lines - 1) * spacingY);
        return layout;
    }

    private void placeChips(ImVec2 origin, float cellW, float grownRowH, ChipLayout layout, ChipRenderer renderer) {
        float s = ThemeManager.uiScale();
        float gap = 5f * s;
        float lineH = ImGui.getFrameHeight();
        float spacingY = ImGui.getStyle().getItemSpacing().y;
        // Center this cell's chip block within the (possibly taller) row, so a short column doesn't cling to the top.
        float blockH = layout.lines * lineH + (layout.lines - 1) * spacingY;
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        float yOff = Math.max(0f, (grownRowH - 2f * cellPadY - blockH) * 0.5f);
        int curLine = -1;
        for (int i = 0; i < layout.lineOf.length; i++) {
            int line = layout.lineOf[i];
            if (line != curLine) {
                curLine = line;
                float lx = origin.x + Math.max(0f, (cellW - layout.lineW[line]) * 0.5f);
                float ly = origin.y + yOff + line * (lineH + spacingY);
                ImGui.setCursorScreenPos(lx, ly);
            } else {
                ImGui.sameLine(0, gap);
            }
            renderer.render(i);
        }
    }

    private float constraintChipWidth(Constraint c) {
        float s = ThemeManager.uiScale();
        String field = c.getField().label;
        String op = c.isRange() ? "" : c.getOp().glyph;
        String val = ConstraintText.chip(c);
        float pad = 5f * s;
        float gap = 4f * s;
        float grip = SolverWidgets.gripWidth();
        Fonts.pushBold();
        float fieldW = ImGui.calcTextSize(field).x;
        Fonts.popBold();
        float opW = op.isEmpty() ? 0f : ImGui.calcTextSize(op).x + gap;
        float valW = ImGui.calcTextSize(val).x;
        return pad + grip + gap + fieldW + gap + opW + valW + pad;
    }

    /** Soft drop shadow behind a chip, layered offset rects so it reads as lifted off the row. */
    private void chipDropShadow(ImDrawList dl, ImVec2 mn, ImVec2 mx, float s) {
        for (int k = 1; k <= 3; k++) {
            float o = k * s;
            dl.addRectFilled(mn.x + o, mn.y + o, mx.x + o, mx.y + o, ThemeManager.shadowColor(0.10f), 3f * s + o);
        }
    }

    private void renderConstraintChip(int tick, int index, Constraint c) {
        float s = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        ImDrawList dl = ImGui.getWindowDrawList();

        String field = c.getField().label;
        String op = c.isRange() ? "" : c.getOp().glyph;
        String val = ConstraintText.chip(c);
        float pad = 5f * s;
        float gap = 4f * s;
        float grip = SolverWidgets.gripWidth();
        Fonts.pushBold();
        float fieldW = ImGui.calcTextSize(field).x;
        Fonts.popBold();
        float opW = op.isEmpty() ? 0f : ImGui.calcTextSize(op).x + gap;
        float valW = ImGui.calcTextSize(val).x;
        float w = constraintChipWidth(c);

        ImGui.invisibleButton("chip" + tick + "_" + index, w, h);
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        boolean hover = ImGui.isItemHovered();
        boolean selected = selectedConstraintTick == tick && selectedConstraintIndex == index;

        boolean off = !c.isEnabled();
        chipDropShadow(dl, mn, mx, s);
        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.panelColor(), 3f * s); // opaque base sits above the row highlight
        // A disabled chip reads unmistakably dormant (gh-118): no accent fill, dim border + text,
        // and a strike through the definition.
        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y,
                off ? ThemeManager.bgTintColor(0.25f) : ThemeManager.accentTintColor(selected ? 0.22f : 0.12f), 3f * s);
        int border = off ? ThemeManager.textDimColor()
                : (hover || selected) ? ThemeManager.accentColor() : ThemeManager.accentTintColor(0.55f);
        dl.addRect(mn.x, mn.y, mx.x, mx.y, border, 3f * s, 0, selected ? 1.5f : 1f);

        float cy = mn.y + h * 0.5f;
        float ty = mn.y + (h - ImGui.getFontSize()) * 0.5f;
        float x = mn.x + pad;
        SolverWidgets.gripDots(dl, x + s, cy, ThemeManager.textDimColor());
        x += grip + gap;
        float textStart = x;
        Fonts.pushBold();
        dl.addText(x, ty, off ? ThemeManager.textDimColor() : ThemeManager.accentColor(), field);
        Fonts.popBold();
        x += fieldW + gap;
        if (!op.isEmpty()) {
            dl.addText(x, ty, off ? ThemeManager.textDimColor() : ThemeManager.textMutedColor(), op);
            x += ImGui.calcTextSize(op).x + gap;
        }
        dl.addText(x, ty, off ? ThemeManager.textDimColor() : ThemeManager.textColor(), val);
        if (off) dl.addLine(textStart, cy, x + valW, cy, ThemeManager.textDimColor(), 1f * s);

        boolean isSource = dragging && dragTick == tick && dragIndex == index;
        if (isSource) dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.bgTintColor(0.6f), 3f * s);

        if (!dragging && ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
            dragging = true;
            dragKind = DragKind.CONSTRAINT;
            dragTick = tick;
            dragIndex = index;
            dragPotion = null;
            dragLabel = field + (op.isEmpty() ? " " : " " + op + " ") + val;
        }

        // A plain click (pressed and released on the chip without dragging) opens the tick and selects it.
        if (!dragging && ImGui.isItemDeactivated()) {
            expandedRow = tick;
            selectedConstraintTick = tick;
            selectedConstraintIndex = index;
            clearStateSelection();
        }

        if (ImGui.beginPopupContextItem("chipctx" + tick + "_" + index)) {
            renderChipMenu(tick, index);
            ImGui.endPopup();
        }
    }

    private void renderChipMenu(int tick, int index) {
        Constraint c = state.tickConstraints(tick).getConstraints().get(index);
        if (ImGui.menuItem(c.isEnabled() ? "Disable" : "Enable")) {
            c.setEnabled(!c.isEnabled());
            ImGui.closeCurrentPopup();
        }
        ThemeManager.paddedSeparator();
        if (ImGui.menuItem("Duplicate here")) {
            state.duplicateConstraint(tick, index);
            ImGui.closeCurrentPopup();
        }
        ThemeManager.paddedSeparator();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text("Copy to tick");
        ThemeManager.popTextColor();
        int copyPick = tickGrid("copy", tick);
        if (copyPick >= 0) {
            state.copyConstraint(tick, index, copyPick);
            ImGui.closeCurrentPopup();
        }
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text("Move to tick");
        ThemeManager.popTextColor();
        int movePick = tickGrid("move", tick);
        if (movePick >= 0) {
            state.moveConstraint(tick, index, movePick);
            ImGui.closeCurrentPopup();
        }
        ThemeManager.paddedSeparator();
        ThemeManager.pushTextColor(ThemeManager.dangerColor());
        boolean del = ImGui.menuItem("Delete");
        ThemeManager.popTextColor();
        if (del) {
            state.deleteConstraint(tick, index);
            ImGui.closeCurrentPopup();
        }
    }

    /** Renders a wrapped grid of tick buttons (excluding current). Returns the picked tick, or -1. */
    private int tickGrid(String id, int currentTick) {
        int pick = -1;
        int n = Math.max(1, rowCount.getAsInt());
        ImGui.pushID(id);
        int col = 0;
        for (int t = 0; t < n; t++) {
            if (t == currentTick) continue;
            if (col > 0) ImGui.sameLine();
            if (ImGui.button("T" + (t + 1))) pick = t;
            col++;
            if (col >= 6) col = 0;
        }
        ImGui.popID();
        return pick;
    }

    private static final class StateChipSpec {
        final DragKind kind;
        final Potion potion;
        final String key;
        final String value;
        final boolean struck;

        StateChipSpec(DragKind kind, Potion potion, String key, String value, boolean struck) {
            this.kind = kind;
            this.potion = potion;
            this.key = key;
            this.value = value;
            this.struck = struck;
        }
    }

    private List<StateChipSpec> collectStateChips(StateOverride ov) {
        List<StateChipSpec> specs = new ArrayList<>();
        if (ov.overridesInputs()) {
            specs.add(new StateChipSpec(DragKind.STATE_INPUTS, null, "Inputs", ov.getInputs().label, false));
        }
        if (ov.overridesSlipperiness()) {
            specs.add(new StateChipSpec(DragKind.STATE_SLIP, null, "Slip", ov.getSlipperiness().label, false));
        }
        for (PotionDose d : ov.getAdded()) {
            specs.add(new StateChipSpec(DragKind.STATE_ADD, d.potion, "Potion", "+" + d.potion.label + amp(d.level), false));
        }
        for (Potion p : ov.getRemoved()) {
            specs.add(new StateChipSpec(DragKind.STATE_REMOVE, p, "Potion", p.label, true));
        }
        return specs;
    }

    private float stateChipWidth(String key, String value) {
        float s = ThemeManager.uiScale();
        float pad = 5f * s;
        float gap = 4f * s;
        float grip = SolverWidgets.gripWidth();
        float keyW = ImGui.calcTextSize(key).x;
        Fonts.pushBold();
        float valW = ImGui.calcTextSize(value).x;
        Fonts.popBold();
        return pad + grip + gap + keyW + gap + valW + pad;
    }

    /** Peach state-override chip: a grip + key + value, draggable to move/copy the facet onto another tick (Alt copies). */
    private void stateChip(int tick, DragKind kind, Potion potion, String key, String value, boolean struck) {
        float s = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        ImDrawList dl = ImGui.getWindowDrawList();
        float pad = 5f * s;
        float gap = 4f * s;
        float grip = SolverWidgets.gripWidth();
        float keyW = ImGui.calcTextSize(key).x;
        Fonts.pushBold();
        float valW = ImGui.calcTextSize(value).x;
        Fonts.popBold();
        float w = pad + grip + gap + keyW + gap + valW + pad;

        String id = "schip" + tick + "_" + kind.name() + (potion == null ? "" : potion.name());
        ImGui.invisibleButton(id, w, h);
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        boolean hover = ImGui.isItemHovered();
        boolean selected = isStateSelected(tick, kind, potion);

        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.panelColor(), 3f * s); // opaque base sits above the row highlight
        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.peachTintColor(selected ? 0.22f : 0.12f), 3f * s);
        dl.addRect(mn.x, mn.y, mx.x, mx.y, (hover || selected) ? ThemeManager.peachColor() : ThemeManager.peachTintColor(0.55f), 3f * s, 0, selected ? 1.5f : 1f);

        float cy = mn.y + h * 0.5f;
        float ty = mn.y + (h - ImGui.getFontSize()) * 0.5f;
        float x = mn.x + pad;
        SolverWidgets.gripDots(dl, x + s, cy, ThemeManager.textDimColor());
        x += grip + gap;
        dl.addText(x, ty, ThemeManager.textMutedColor(), key);
        x += keyW + gap;
        Fonts.pushBold();
        dl.addText(x, ty, struck ? ThemeManager.textDimColor() : ThemeManager.peachColor(), value);
        Fonts.popBold();
        if (struck) dl.addLine(x, cy, x + valW, cy, ThemeManager.textDimColor(), 1f);

        boolean isSource = dragging && dragKind == kind && dragTick == tick && dragPotion == potion;
        if (isSource) dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.bgTintColor(0.6f), 3f * s);

        if (!dragging && ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
            dragging = true;
            dragKind = kind;
            dragTick = tick;
            dragIndex = -1;
            dragPotion = potion;
            dragLabel = key + " " + value;
        }

        // A plain click opens the tick and selects this state facet.
        if (!dragging && ImGui.isItemDeactivated()) {
            expandedRow = tick;
            selectedStateTick = tick;
            selectedStateKind = kind;
            selectedStatePotion = potion;
            clearConstraintSelection();
        }
    }

    private boolean isStateSelected(int tick, DragKind kind, Potion potion) {
        return selectedStateTick == tick && selectedStateKind == kind && selectedStatePotion == potion;
    }

    private void clearStateSelection() {
        selectedStateTick = -1;
        selectedStateKind = null;
        selectedStatePotion = null;
    }

    private void clearConstraintSelection() {
        selectedConstraintTick = -1;
        selectedConstraintIndex = -1;
    }

    // ---- editor drawer ----------------------------------------------------------

    public float drawerHeight(int tick) {
        float s = ThemeManager.uiScale();
        float fhs = ImGui.getFrameHeightWithSpacing();
        float inputRow = fhs + 8f * s; // table rows push framePadding.y from XXS(2) to BUTTON_PAD_Y(6)
        float spacing = ThemeManager.SM * s;
        float sectionHead = 2f * spacing + fhs; // sectionSpacing + title + separator
        TickConstraints tc = state.tickConstraintsOrNull(tick);
        int cn = tc == null ? 0 : tc.getConstraints().size();
        int potions = tc == null ? 0 : tc.getOverride().getAdded().size();
        float pad = 2f * ThemeManager.LG * s; // child top + bottom window padding
        float stateRows = (2f + potions + 1f) * inputRow;  // Inputs, Slipperiness, doses, + add
        float constraintRows = (cn + 1f) * inputRow;        // constraint rows + add button
        return pad
                + sectionHead + stateRows
                + sectionHead + constraintRows
                + fhs; // slack
    }

    // The drawer child's own draw list: renders above the table content but below every other
    // window, popup and modal, so InputOverlay's rail/shadow decorations stay inside this window.
    private ImDrawList drawerDrawList;

    public ImDrawList drawerDrawList() {
        return drawerDrawList;
    }

    public void renderDrawer(int tick, float width) {
        float s = ThemeManager.uiScale();
        float pad = ThemeManager.LG * s;
        // The estimate seeds the first frame; thereafter the measured content height sizes the child exactly,
        // so the bottom padding matches the top instead of leaving slack.
        float h = (measuredTick == tick && measuredContentH > 0f) ? measuredContentH : drawerHeight(tick);

        ThemeManager.pushDrawerChildBg();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.beginChild("##solver_drawer", width, h, false, ImGuiWindowFlags.NoScrollbar);

        // Explicit symmetric padding: an inset, transparent inner pane. WindowPadding alone wasn't holding
        // the stretch-column editor tables off the colored edges, so the inner child physically narrows the
        // content region on every side (headers, separators and tables included).
        ImGui.setCursorPos(pad, pad);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f);
        ImGui.beginChild("##solver_drawer_body", width - 2f * pad, h - 2f * pad, false, ImGuiWindowFlags.NoScrollbar);

        sectionHeader("State");
        renderOverrideEditor(tick);

        ThemeManager.sectionSpacing();
        sectionHeader("Constraints");
        renderConstraintEditor(tick);

        float spacingY = ImGui.getStyle().getItemSpacing().y;
        float bodyH = ImGui.getCursorPosY() - spacingY; // inner pane has zero padding, so this is the bare content height

        ImGui.endChild();
        ImGui.popStyleColor();
        drawerDrawList = ImGui.getWindowDrawList();
        ImGui.endChild();
        ImGui.popStyleVar();
        ThemeManager.popDrawerChildBg();

        measuredContentH = bodyH + 2f * pad;
        measuredTick = tick;
    }

    private void sectionHeader(String title) {
        ImGui.textDisabled(title);
        ThemeManager.bottomPaddedSeparator();
    }

    // ---- constraint editor (aligned table: grip | field | op | value | x) ------

    private void renderConstraintEditor(int tick) {
        float s = ThemeManager.uiScale();
        List<Constraint> list = state.tickConstraints(tick).getConstraints();
        int delete = -1;
        if (ThemeManager.beginStandardFormTable("##cons_edit", 6)) {
            ImGui.tableSetupColumn("g", ImGuiTableColumnFlags.WidthFixed, SolverWidgets.gripWidth() + 4f * s + gripLeftPad());
            ImGui.tableSetupColumn("e", ImGuiTableColumnFlags.WidthFixed, ImGui.getFrameHeight());
            ImGui.tableSetupColumn("f", ImGuiTableColumnFlags.WidthFixed, 72f * s);
            ImGui.tableSetupColumn("o", ImGuiTableColumnFlags.WidthFixed, 72f * s);
            ImGui.tableSetupColumn("v", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("x", ImGuiTableColumnFlags.WidthFixed, deleteXWidth());
            Controls.pushInputFrameHeight();
            for (int i = 0; i < list.size(); i++) {
                Constraint c = list.get(i);
                ImGui.pushID(i);
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                if (tick == selectedConstraintTick && i == selectedConstraintIndex) drawSelectedRowHighlight();
                editorGrip(tick, i, c);
                ImGui.tableNextColumn();
                if (ImGui.checkbox("##on", c.isEnabled())) c.setEnabled(!c.isEnabled());
                if (ImGui.isItemHovered()) ImGui.setTooltip(c.isEnabled() ? "Disable (kept, ignored by Solve)" : "Enable");
                boolean dim = !c.isEnabled();
                if (dim) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, ImGui.getStyle().getAlpha() * 0.45f);
                ImGui.tableNextColumn();
                fieldCombo.set(c.getField().ordinal());
                if (Controls.combo("##fld", fieldCombo, FIELD_ITEMS, ImGui.getContentRegionAvail().x)) {
                    c.setField(Constraint.Field.values()[fieldCombo.get()]);
                }
                ImGui.tableNextColumn();
                opCombo.set(opItemIndex(c));
                if (Controls.combo("##op", opCombo, OP_ITEMS, ImGui.getContentRegionAvail().x)) {
                    int v = opCombo.get();
                    if (v < SCALAR_OPS.length) {
                        c.setOp(SCALAR_OPS[v]);
                    } else {
                        c.setOp(Constraint.Op.IN);
                        int b = v - SCALAR_OPS.length;
                        c.setInclusive((b & 2) != 0, (b & 1) != 0);
                    }
                }
                ImGui.tableNextColumn();
                renderConstraintValues(c);
                ImGui.tableNextColumn();
                if (deleteX("del")) delete = i;
                if (dim) ImGui.popStyleVar();
                ImGui.popID();
            }
            Controls.popInputFrameHeight();
            ThemeManager.endStandardFormTable();
        }
        if (delete >= 0) state.deleteConstraint(tick, delete);

        if (Controls.secondaryButton("+ add constraint")) state.addConstraint(tick);
    }

    private void renderConstraintValues(Constraint c) {
        float s = ThemeManager.uiScale();
        float numW = 108f * s;
        if (c.isRange()) {
            ImGui.setNextItemWidth(numW);
            numberField("##lo", c.getLo(), c::setLo);
            ImGui.sameLine();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.alignTextToFramePadding();
            ImGui.text("to");
            ThemeManager.popTextColor();
            ImGui.sameLine();
            ImGui.setNextItemWidth(numW);
            numberField("##hi", c.getHi(), c::setHi);
        } else {
            ImGui.setNextItemWidth(numW);
            numberField("##v", c.getValue(), c::setValue);
        }
    }

    /** Locale-independent replacement for inputDouble, whose native printf renders comma decimals under some C locales (gh-124). */
    private void numberField(String id, double value, DoubleConsumer apply) {
        numBuf.set(String.format(Locale.ROOT, "%.4f", value));
        if (ImGui.inputText(id, numBuf, ImGuiInputTextFlags.CharsDecimal)) {
            try {
                apply.accept(Double.parseDouble(numBuf.get().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** Space between the band's left edge and the grip dots, so the grip isn't crammed against the edge. */
    private float gripLeftPad() {
        return ThemeManager.SM * ThemeManager.uiScale();
    }

    /** Drag handle for an in-drawer constraint row: feeds the same CONSTRAINT drag/drop pipeline as the inline chips. */
    private void editorGrip(int tick, int index, Constraint c) {
        float s = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        ImVec2 p = ImGui.getCursorScreenPos();
        float w = SolverWidgets.gripWidth() + 2f * s + gripLeftPad();
        ImGui.invisibleButton("egrip", w, h);
        boolean hover = ImGui.isItemHovered();
        SolverWidgets.gripDots(ImGui.getWindowDrawList(), p.x + s + gripLeftPad(), p.y + h * 0.5f,
                hover ? ThemeManager.textMutedColor() : ThemeManager.textDimColor());

        if (!dragging && ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
            String op = c.isRange() ? "" : c.getOp().glyph;
            String val = ConstraintText.chip(c);
            dragging = true;
            dragKind = DragKind.CONSTRAINT;
            dragTick = tick;
            dragIndex = index;
            dragPotion = null;
            dragLabel = c.getField().label + (op.isEmpty() ? " " : " " + op + " ") + val;
        }
    }

    private float deleteXWidth() {
        return SolverWidgets.deleteXWidth();
    }

    private boolean deleteX(String id) {
        return SolverWidgets.deleteX(id);
    }

    // ---- state override editor (aligned table: label | control | trailing) -----

    private float overrideLabelWidth() {
        float max = 0f;
        for (String l : new String[]{"Inputs", "Slipperiness", "Potion"}) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * ThemeManager.uiScale();
    }

    private void renderOverrideEditor(int tick) {
        float s = ThemeManager.uiScale();
        StateOverride ov = state.tickConstraints(tick).getOverride();
        if (!ThemeManager.beginStandardFormTable("##ov_edit", 3)) return;
        ImGui.tableSetupColumn("l", ImGuiTableColumnFlags.WidthFixed, overrideLabelWidth() + gripLeftPad());
        ImGui.tableSetupColumn("c", ImGuiTableColumnFlags.WidthFixed, 190f * s);
        ImGui.tableSetupColumn("t", ImGuiTableColumnFlags.WidthStretch);
        Controls.pushInputFrameHeight();

        // Inputs
        ovRowStart("Inputs", isStateSelected(tick, DragKind.STATE_INPUTS, null));
        AngleSolverState.InputMode effInputs = ov.overridesInputs() ? ov.getInputs() : state.getDefaultInputs();
        int sel = SolverWidgets.segmented("ovinputs", INPUTS, effInputs.ordinal(), ImGui.getContentRegionAvail().x);
        if (sel >= 0) {
            AngleSolverState.InputMode chosen = AngleSolverState.InputMode.values()[sel];
            if (chosen == state.getDefaultInputs()) ov.clearInputs();
            else ov.setInputs(chosen);
        }
        ImGui.tableNextColumn();
        overrideTrailing(ov.overridesInputs(), "inherits default (" + state.getDefaultInputs().label + ")", "ovinr", ov::clearInputs);

        // Slipperiness
        ovRowStart("Slipperiness", isStateSelected(tick, DragKind.STATE_SLIP, null));
        slipBuf.set((ov.overridesSlipperiness() ? ov.getSlipperiness() : state.getDefaultSlipperiness()).ordinal());
        if (Controls.combo("##ovslip", slipBuf, slipItems, ImGui.getContentRegionAvail().x)) {
            Slipperiness chosen = Slipperiness.values()[slipBuf.get()];
            if (chosen == state.getDefaultSlipperiness()) ov.clearSlipperiness();
            else ov.setSlipperiness(chosen);
        }
        ImGui.tableNextColumn();
        overrideTrailing(ov.overridesSlipperiness(), "inherits default (" + state.getDefaultSlipperiness().label + ")", "ovslr", ov::clearSlipperiness);

        // Potion: one row per dose, then a + add row below
        renderPotionRows(tick, ov);

        Controls.popInputFrameHeight();
        ThemeManager.endStandardFormTable();
    }

    private void ovRowStart(String label, boolean selected) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        if (selected) drawSelectedRowHighlight();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + gripLeftPad());
        Controls.labelCell(label);
        ImGui.tableNextColumn();
    }

    /** Pill behind a selected editor row, drawn before the row's content so widgets sit on top. */
    private void drawSelectedRowHighlight() {
        float s = ThemeManager.uiScale();
        // Anchor the left edge to the row's first column (the grip) so the grip is always inside the pill;
        // the right edge runs to the window bound to cover the delete-x.
        ImVec2 pos = ImGui.getCursorScreenPos();
        float winX = ImGui.getWindowPos().x;
        float winW = ImGui.getWindowWidth();
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        float left = pos.x - ThemeManager.XS * s;
        float top = pos.y - cellPadY;
        float h = ImGui.getFrameHeight() + cellPadY * 2f;
        ImGui.getWindowDrawList().addRectFilled(
                left, top, winX + winW, top + h, ThemeManager.selectedRowBg(), 3f * s);
    }

    private void renderPotionRows(int tick, StateOverride ov) {
        float s = ThemeManager.uiScale();
        List<PotionDose> doses = ov.getAdded();
        Potion removeP = null;
        for (int i = 0; i < doses.size(); i++) {
            PotionDose d = doses.get(i);
            ImGui.pushID("pd" + i);
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            if (isStateSelected(tick, DragKind.STATE_ADD, d.potion)) drawSelectedRowHighlight();
            if (i == 0) {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + gripLeftPad());
                Controls.labelCell("Potion");
            }
            ImGui.tableNextColumn();
            List<Potion> options = availableOverridePotions(ov, d.potion);
            String[] items = new String[options.size()];
            for (int k = 0; k < items.length; k++) items[k] = options.get(k).label;
            doseCombo.set(Math.max(0, options.indexOf(d.potion)));
            float gap = ImGui.getStyle().getItemSpacing().x;
            float levelW = 56f * s;
            float comboW = Math.max(60f * s, ImGui.getContentRegionAvail().x - levelW - gap);
            if (Controls.combo("##povp", doseCombo, items, comboW)) d.potion = options.get(doseCombo.get());
            ImGui.sameLine();
            levelBuf.set(d.level);
            ImGui.setNextItemWidth(levelW);
            // step 0 hides the +/- buttons, which would otherwise eat the whole narrow field.
            if (ImGui.inputInt("##povl", levelBuf, 0, 0)) d.level = Math.max(1, Math.min(10, levelBuf.get()));
            ImGui.tableNextColumn();
            cursorToRightDeleteX();
            if (deleteX("povx")) removeP = d.potion;
            ImGui.popID();
        }
        if (removeP != null) ov.removeAdded(removeP);

        // + add row
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        if (doses.isEmpty()) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + gripLeftPad());
            Controls.labelCell("Potion");
        }
        ImGui.tableNextColumn();
        Potion next = nextOverridePotion(ov);
        if (next == null) Controls.disabledButton("+ add");
        else if (Controls.secondaryButton("+ add")) ov.getAdded().add(new PotionDose(next, 1));
        ImGui.tableNextColumn();
        if (!ov.overridesPotion()) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.alignTextToFramePadding();
            ImGui.text("inherits default");
            ThemeManager.popTextColor();
        }
    }

    private List<Potion> availableOverridePotions(StateOverride ov, Potion current) {
        List<Potion> out = new ArrayList<>();
        for (Potion p : Potion.values()) {
            if (p == current) { out.add(p); continue; }
            if (state.hasDefaultPotion(p) || ov.hasAdded(p)) continue;
            out.add(p);
        }
        return out;
    }

    private Potion nextOverridePotion(StateOverride ov) {
        for (Potion p : Potion.values()) {
            if (!state.hasDefaultPotion(p) && !ov.hasAdded(p)) return p;
        }
        return null;
    }

    /** Trailing cell: a right-aligned reset × when overriding, else a dim "inherits default" hint. */
    private void overrideTrailing(boolean overriding, String inheritHint, String resetId, Runnable reset) {
        if (overriding) {
            cursorToRightDeleteX();
            if (deleteX(resetId)) reset.run();
        } else {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.alignTextToFramePadding();
            ImGui.text(inheritHint);
            ThemeManager.popTextColor();
        }
    }

    private void cursorToRightDeleteX() {
        float avail = ImGui.getContentRegionAvail().x;
        float w = deleteXWidth();
        if (avail > w) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - w);
    }

    private static String amp(int level) {
        String[] roman = {"", "", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (level <= 1) return "";
        return level < roman.length ? " " + roman[level] : " " + level;
    }
}
