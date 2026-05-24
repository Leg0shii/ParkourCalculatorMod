package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;

/** Form-control helpers. See docs/UI_REDESIGN.md "Visual quality contract". */
public final class Controls {

    private static final float DEFAULT_NUM_WIDTH = 90.0f;
    private static final float FOCUS_RING_THICKNESS = 2.0f;
    private static final float FOCUS_RING_ROUNDING = 3.0f;

    private Controls() {}

    // ---- text / numeric inputs -------------------------------------------------

    public static boolean inputText(String label, ImString holder, float width) {
        return inputText(label, holder, width, 0);
    }

    public static boolean inputText(String label, ImString holder, float width, int flags) {
        beginLabeled(label, width);
        boolean changed = ImGui.inputText(idFor(label), holder, flags);
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean inputTextHint(String label, String hint, ImString holder, float width) {
        return inputTextHint(label, hint, holder, width, 0);
    }

    public static boolean inputTextHint(String label, String hint, ImString holder, float width, int flags) {
        beginLabeled(label, width);
        boolean changed = ImGui.inputTextWithHint(idFor(label), hint, holder, flags);
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean inputInt(String label, ImInt holder, float width) {
        beginLabeled(label, width <= 0 ? DEFAULT_NUM_WIDTH : width);
        boolean changed = ImGui.inputInt(idFor(label), holder);
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean inputFloat(String label, ImFloat holder, float width, String fmt) {
        beginLabeled(label, width <= 0 ? DEFAULT_NUM_WIDTH : width);
        boolean changed = ImGui.inputFloat(idFor(label), holder, 0f, 0f, fmt);
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
        beginLabeled(label, 0);
        boolean changed = ImGui.sliderFloat(idFor(label), ref, lo, hi, fmt);
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
        beginLabeled(label, 0);
        boolean changed = ImGui.sliderInt(idFor(label), ref, lo, hi, fmt);
        drawFocusRingIfActive();
        return changed;
    }

    // ---- checkbox / combo ------------------------------------------------------

    public static boolean checkbox(String label, ImBoolean ref) {
        boolean changed = ImGui.checkbox(label, ref);
        drawFocusRingIfActive();
        return changed;
    }

    public static boolean checkbox(String label, boolean currentValue) {
        boolean clicked = ImGui.checkbox(label, currentValue);
        drawFocusRingIfActive();
        return clicked;
    }

    public static boolean combo(String label, ImInt selectedIdx, String[] items) {
        return combo(label, selectedIdx, items, 0);
    }

    public static boolean combo(String label, ImInt selectedIdx, String[] items, float width) {
        beginLabeled(label, width);
        boolean changed = ImGui.combo(idFor(label), selectedIdx, items);
        drawFocusRingIfActive();
        return changed;
    }

    // ---- buttons ---------------------------------------------------------------

    public static boolean primaryButton(String label) {
        ThemeManager.pushPrimaryButton();
        boolean clicked = ImGui.button(label);
        ThemeManager.popPrimaryButton();
        return clicked;
    }

    public static boolean secondaryButton(String label) {
        return ImGui.button(label);
    }

    public static boolean dangerButton(String label) {
        ThemeManager.pushDangerButton();
        boolean clicked = ImGui.button(label);
        ThemeManager.popDangerButton();
        return clicked;
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
