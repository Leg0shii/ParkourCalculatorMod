package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;

/** Form-control helpers: every interactive control draws the shared focus ring and routes through ThemeManager state colors. */
public final class Controls {

    private static final float DEFAULT_NUM_WIDTH = 90.0f;
    private static final float FOCUS_RING_THICKNESS = 2.0f;
    private static final float FOCUS_RING_ROUNDING = 3.0f;
    private static final float BUTTON_PAD_X = 10.0f; // one knob: every button's horizontal padding
    private static final float BUTTON_PAD_Y = 6.0f;  // one knob: every button's vertical padding

    private Controls() {}

    // ---- text / numeric inputs -------------------------------------------------

    public static boolean inputText(String label, ImString holder, float width) {
        return inputText(label, holder, width, 0);
    }

    public static boolean inputText(String label, ImString holder, float width, int flags) {
        pushInputFrameHeight();
        beginLabeled(label, width);
        boolean changed = ImGui.inputText(idFor(label), holder, flags);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    /** Compact text input sized to a tick-table row; skips the button-height padding the standalone inputs use. */
    public static boolean tableInputText(String label, ImString holder, float width) {
        beginLabeled(label, width);
        boolean changed = ImGui.inputText(idFor(label), holder, 0);
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean tableInputText(String label, ImString holder, float width, int flags, ImGuiInputTextCallback callback) {
        beginLabeled(label, width);
        boolean changed = ImGui.inputText(idFor(label), holder, flags, callback);
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean inputTextHint(String label, String hint, ImString holder, float width) {
        return inputTextHint(label, hint, holder, width, 0);
    }

    public static boolean inputTextHint(String label, String hint, ImString holder, float width, int flags) {
        pushInputFrameHeight();
        beginLabeled(label, width);
        boolean changed = ImGui.inputTextWithHint(idFor(label), hint, holder, flags);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean inputInt(String label, ImInt holder, float width) {
        pushInputFrameHeight();
        beginLabeled(label, width <= 0 ? DEFAULT_NUM_WIDTH : width);
        boolean changed = ImGui.inputInt(idFor(label), holder);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean inputFloat(String label, ImFloat holder, float width, String fmt) {
        pushInputFrameHeight();
        beginLabeled(label, width <= 0 ? DEFAULT_NUM_WIDTH : width);
        boolean changed = ImGui.inputFloat(idFor(label), holder, 0f, 0f, fmt);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    // ---- sliders ---------------------------------------------------------------

    public static boolean sliderFloat(String label, ImFloat ref, float lo, float hi, String fmt) {
        float[] tmp = {ref.get()};
        boolean changed = sliderFloat(label, tmp, lo, hi, fmt);
        if (changed) ref.set(tmp[0]);
        return changed;
    }

    public static boolean sliderFloat(String label, float[] ref, float lo, float hi, String fmt) {
        pushInputFrameHeight();
        beginLabeled(label, 0);
        boolean changed = ImGui.sliderFloat(idFor(label), ref, lo, hi, fmt);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean sliderInt(String label, ImInt ref, int lo, int hi, String fmt) {
        int[] tmp = {ref.get()};
        boolean changed = sliderInt(label, tmp, lo, hi, fmt);
        if (changed) ref.set(tmp[0]);
        return changed;
    }

    public static boolean sliderInt(String label, int[] ref, int lo, int hi, String fmt) {
        pushInputFrameHeight();
        beginLabeled(label, 0);
        boolean changed = ImGui.sliderInt(idFor(label), ref, lo, hi, fmt);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    // ---- checkbox / combo ------------------------------------------------------

    public static boolean checkbox(String label, ImBoolean ref) {
        pushInputFrameHeight();
        boolean changed = ImGui.checkbox(label, ref);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean checkbox(String label, boolean currentValue) {
        pushInputFrameHeight();
        boolean clicked = ImGui.checkbox(label, currentValue);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return clicked;
    }

    public static boolean combo(String label, ImInt selectedIdx, String[] items) {
        return combo(label, selectedIdx, items, 0);
    }

    public static boolean combo(String label, ImInt selectedIdx, String[] items, float width) {
        pushInputFrameHeight();
        beginLabeled(label, width);
        boolean changed = ImGui.combo(idFor(label), selectedIdx, items);
        popInputFrameHeight();
        drawFocusRingIfActive();
        return changed;
    }

    /** Compact combo sized to a tick-table row; skips the button-height padding the standalone combos use. */
    public static boolean tableCombo(String label, ImInt selectedIdx, String[] items, float width) {
        beginLabeled(label, width);
        boolean changed = ImGui.combo(idFor(label), selectedIdx, items);
        drawFocusRingIfActive();
        return changed;
    }

    // ---- buttons ---------------------------------------------------------------

    public static boolean primaryButton(String label) {
        ThemeManager.pushPrimaryButton();
        boolean clicked = styledButton(label);
        ThemeManager.popPrimaryButton();
        return clicked;
    }

    public static boolean secondaryButton(String label) {
        return styledButton(label);
    }

    public static boolean dangerButton(String label) {
        ThemeManager.pushDangerButton();
        boolean clicked = styledButton(label);
        ThemeManager.popDangerButton();
        return clicked;
    }

    /** Non-interactive button styled as a muted frame; the blanket disabled-alpha is locally cancelled so the frame stays readable. */
    public static void disabledButton(String label) {
        ThemeManager.pushDisabledButton();
        ImGui.beginDisabled(true);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, BUTTON_PAD_X * ThemeManager.uiScale(), BUTTON_PAD_Y * ThemeManager.uiScale());
        ImGui.button(label);
        ImGui.popStyleVar(2);
        ImGui.endDisabled();
        ThemeManager.popDisabledButton();
    }

    /** On-screen width of a styled button for this label, including the shared BUTTON_PAD_X padding (which the default FramePadding underestimates). */
    public static float buttonWidth(String label) {
        return ImGui.calcTextSize(label).x + 2f * BUTTON_PAD_X * ThemeManager.uiScale();
    }

    /** Advances the cursor so the next styled button of this label sits flush against the content's right edge. */
    public static void cursorToRightAlignedButton(String label) {
        float avail = ImGui.getContentRegionAvail().x;
        float w = buttonWidth(label);
        if (avail > w) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - w);
    }

    /** Gives standalone inputs the button's vertical FramePadding so they match button height; horizontal padding is left at the style default. */
    private static void pushInputFrameHeight() {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePadding().x, BUTTON_PAD_Y * ThemeManager.uiScale());
    }

    private static void popInputFrameHeight() {
        ImGui.popStyleVar();
    }

    /** Every button routes through here so they all inherit BUTTON_PAD_* (FramePadding is shared with inputs, so it's pushed only around buttons). */
    private static boolean styledButton(String label) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, BUTTON_PAD_X * ThemeManager.uiScale(), BUTTON_PAD_Y * ThemeManager.uiScale());
        boolean clicked = ImGui.button(label);
        ImGui.popStyleVar();
        drawFocusRingIfActive();
        return clicked;
    }

    // ---- tabs ------------------------------------------------------------------

    public static boolean beginTabBar(String id) {
        // ImGui captures FramePadding here to reserve the bar height; match the per-tab padding so labels stay centered.
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, BUTTON_PAD_X * ThemeManager.uiScale(), BUTTON_PAD_Y * ThemeManager.uiScale());
        boolean open = ImGui.beginTabBar(id);
        ImGui.popStyleVar();
        return open;
    }

    public static void endTabBar() {
        ImGui.endTabBar();
    }

    public static boolean beginTab(String label) {
        // No custom focus ring: ImGui marks the active tab as focused, so a ring would show on every click.
        // Keyboard nav still highlights via ImGuiCol.NavHighlight (= FOCUS).
        // Match button sizing: ImGui measures the tab from FramePadding at submit, so push it only around the call.
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, BUTTON_PAD_X * ThemeManager.uiScale(), BUTTON_PAD_Y * ThemeManager.uiScale());
        boolean open = ImGui.beginTabItem(label);
        ImGui.popStyleVar();
        return open;
    }

    public static void endTab() {
        ImGui.endTabItem();
    }

    // ---- hyperlink -------------------------------------------------------------

    /** Accent-colored text that underlines and shows a hand cursor on hover; returns true on click. */
    public static boolean hyperlink(String text) {
        ThemeManager.pushTextColor(ThemeManager.accentColor());
        ImGui.text(text);
        ThemeManager.popTextColor();

        ImVec2 min = ImGui.getItemRectMin();
        ImVec2 max = ImGui.getItemRectMax();
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            ImGui.getWindowDrawList().addLine(min.x, max.y, max.x, max.y, ThemeManager.accentColor());
        }
        return ImGui.isItemClicked(0);
    }

    // ---- layout helper for label-control pairs --------------------------------

    public static void labelCell(String label) {
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
    }

    // ---- internals -------------------------------------------------------------

    private static boolean hasVisibleLabel(String label) {
        return label != null && !label.isEmpty() && !label.startsWith("##");
    }

    private static String idFor(String label) {
        if (label == null || label.isEmpty()) return "##unnamed";
        if (label.startsWith("##")) return label;
        return "##" + label;
    }

    private static void beginLabeled(String label, float width) {
        if (hasVisibleLabel(label)) {
            ImGui.alignTextToFramePadding();
            ImGui.text(label);
            ImGui.sameLine();
        }
        if (width > 0) ImGui.setNextItemWidth(width);
    }

    private static void drawFocusRingIfActive() {
        if (!ImGui.isItemActive() && !ImGui.isItemFocused()) return;
        ImVec2 min = ImGui.getItemRectMin();
        ImVec2 max = ImGui.getItemRectMax();
        ImGui.getWindowDrawList().addRect(
                min.x + 1.0f, min.y + 1.0f, max.x - 1.0f, max.y - 1.0f,
                ThemeManager.focusColor(),
                FOCUS_RING_ROUNDING,
                0,
                FOCUS_RING_THICKNESS
        );
    }
}
