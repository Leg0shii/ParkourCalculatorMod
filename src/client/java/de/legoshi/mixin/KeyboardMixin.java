package de.legoshi.mixin;

import de.legoshi.ParkourCalculatorClient;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Unique
    private static final Map<Integer, Integer> KEY_MAP = buildKeyMap();

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (!ParkourCalculatorClient.isUiFocused()) {
            return;
        }

        int glfwKey = input.key();

        // Allow toggle and escape keys to pass through
        if (glfwKey == GLFW.GLFW_KEY_L || glfwKey == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }

        // Forward key event to ImGui
        Integer imguiKey = KEY_MAP.get(glfwKey);
        if (imguiKey != null) {
            boolean pressed = action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;
            ImGui.getIO().addKeyEvent(imguiKey, pressed);
        }

        // Update modifier states
        int modifiers = input.modifiers();
        ImGui.getIO().setKeyCtrl((modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
        ImGui.getIO().setKeyShift((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
        ImGui.getIO().setKeyAlt((modifiers & GLFW.GLFW_MOD_ALT) != 0);
        ImGui.getIO().setKeySuper((modifiers & GLFW.GLFW_MOD_SUPER) != 0);

        ci.cancel();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        if (ParkourCalculatorClient.isUiFocused()) {
            ImGui.getIO().addInputCharacter(input.codepoint());
            ci.cancel();
        }
    }

    @Unique
    private static Map<Integer, Integer> buildKeyMap() {
        Map<Integer, Integer> map = new HashMap<>();

        // Navigation
        map.put(GLFW.GLFW_KEY_TAB, ImGuiKey.Tab);
        map.put(GLFW.GLFW_KEY_LEFT, ImGuiKey.LeftArrow);
        map.put(GLFW.GLFW_KEY_RIGHT, ImGuiKey.RightArrow);
        map.put(GLFW.GLFW_KEY_UP, ImGuiKey.UpArrow);
        map.put(GLFW.GLFW_KEY_DOWN, ImGuiKey.DownArrow);
        map.put(GLFW.GLFW_KEY_PAGE_UP, ImGuiKey.PageUp);
        map.put(GLFW.GLFW_KEY_PAGE_DOWN, ImGuiKey.PageDown);
        map.put(GLFW.GLFW_KEY_HOME, ImGuiKey.Home);
        map.put(GLFW.GLFW_KEY_END, ImGuiKey.End);

        // Editing
        map.put(GLFW.GLFW_KEY_INSERT, ImGuiKey.Insert);
        map.put(GLFW.GLFW_KEY_DELETE, ImGuiKey.Delete);
        map.put(GLFW.GLFW_KEY_BACKSPACE, ImGuiKey.Backspace);
        map.put(GLFW.GLFW_KEY_SPACE, ImGuiKey.Space);
        map.put(GLFW.GLFW_KEY_ENTER, ImGuiKey.Enter);
        map.put(GLFW.GLFW_KEY_ESCAPE, ImGuiKey.Escape);

        // Punctuation
        map.put(GLFW.GLFW_KEY_APOSTROPHE, ImGuiKey.Apostrophe);
        map.put(GLFW.GLFW_KEY_COMMA, ImGuiKey.Comma);
        map.put(GLFW.GLFW_KEY_MINUS, ImGuiKey.Minus);
        map.put(GLFW.GLFW_KEY_PERIOD, ImGuiKey.Period);
        map.put(GLFW.GLFW_KEY_SLASH, ImGuiKey.Slash);
        map.put(GLFW.GLFW_KEY_SEMICOLON, ImGuiKey.Semicolon);
        map.put(GLFW.GLFW_KEY_EQUAL, ImGuiKey.Equal);
        map.put(GLFW.GLFW_KEY_LEFT_BRACKET, ImGuiKey.LeftBracket);
        map.put(GLFW.GLFW_KEY_BACKSLASH, ImGuiKey.Backslash);
        map.put(GLFW.GLFW_KEY_RIGHT_BRACKET, ImGuiKey.RightBracket);
        map.put(GLFW.GLFW_KEY_GRAVE_ACCENT, ImGuiKey.GraveAccent);

        // Letters A-Z
        int[] letterKeys = {
                GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_D,
                GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H,
                GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L,
                GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P,
                GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_T,
                GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_X,
                GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_Z
        };
        int[] imguiLetters = {
                ImGuiKey.A, ImGuiKey.B, ImGuiKey.C, ImGuiKey.D,
                ImGuiKey.E, ImGuiKey.F, ImGuiKey.G, ImGuiKey.H,
                ImGuiKey.I, ImGuiKey.J, ImGuiKey.K, ImGuiKey.L,
                ImGuiKey.M, ImGuiKey.N, ImGuiKey.O, ImGuiKey.P,
                ImGuiKey.Q, ImGuiKey.R, ImGuiKey.S, ImGuiKey.T,
                ImGuiKey.U, ImGuiKey.V, ImGuiKey.W, ImGuiKey.X,
                ImGuiKey.Y, ImGuiKey.Z
        };
        for (int i = 0; i < letterKeys.length; i++) {
            map.put(letterKeys[i], imguiLetters[i]);
        }

        // Numbers 0-9
        int[] numberKeys = {
                GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
                GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7,
                GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9
        };
        int[] imguiNumbers = {
                ImGuiKey._0, ImGuiKey._1, ImGuiKey._2, ImGuiKey._3,
                ImGuiKey._4, ImGuiKey._5, ImGuiKey._6, ImGuiKey._7,
                ImGuiKey._8, ImGuiKey._9
        };
        for (int i = 0; i < numberKeys.length; i++) {
            map.put(numberKeys[i], imguiNumbers[i]);
        }

        return map;
    }
}