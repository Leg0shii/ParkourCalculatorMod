package de.legoshi.parkourcalc.fabric.imgui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiKey;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

final class ImGuiSdlPlatform {

    private static final int MOUSE_BUTTONS = 5;
    private static final int SCANCODE_COUNT = 512;
    private static final Object TEXT_INPUT_OWNER = new Object();

    private final boolean[] mouseJustPressed = new boolean[MOUSE_BUTTONS];
    private long lastFrameNanos;

    void init() {
        ImGuiIO io = ImGui.getIO();
        io.setBackendPlatformName("imgui_java_impl_sdl3_minecraft");

        io.setKeyMap(ImGuiKey.Tab, InputConstants.KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, InputConstants.KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, InputConstants.KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, InputConstants.KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, InputConstants.KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, InputConstants.KEY_PAGEUP);
        io.setKeyMap(ImGuiKey.PageDown, InputConstants.KEY_PAGEDOWN);
        io.setKeyMap(ImGuiKey.Home, InputConstants.KEY_HOME);
        io.setKeyMap(ImGuiKey.End, InputConstants.KEY_END);
        io.setKeyMap(ImGuiKey.Insert, InputConstants.KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, InputConstants.KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, InputConstants.KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, InputConstants.KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, InputConstants.KEY_RETURN);
        io.setKeyMap(ImGuiKey.Escape, InputConstants.KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.KeyPadEnter, InputConstants.KEY_NUMPADENTER);
        io.setKeyMap(ImGuiKey.A, InputConstants.KEY_A);
        io.setKeyMap(ImGuiKey.C, InputConstants.KEY_C);
        io.setKeyMap(ImGuiKey.V, InputConstants.KEY_V);
        io.setKeyMap(ImGuiKey.X, InputConstants.KEY_X);
        io.setKeyMap(ImGuiKey.Y, InputConstants.KEY_Y);
        io.setKeyMap(ImGuiKey.Z, InputConstants.KEY_Z);

        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(String text) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
            }
        });
        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                return Minecraft.getInstance().keyboardHandler.getClipboard();
            }
        });
        lastFrameNanos = 0L;
    }

    void newFrame() {
        ImGuiIO io = ImGui.getIO();
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        int screenW = window.getScreenWidth();
        int screenH = window.getScreenHeight();
        io.setDisplaySize(screenW, screenH);
        if (screenW > 0 && screenH > 0) {
            io.setDisplayFramebufferScale((float) window.getWidth() / screenW, (float) window.getHeight() / screenH);
        }

        long now = System.nanoTime();
        io.setDeltaTime(lastFrameNanos > 0L ? Math.max(1e-4f, (now - lastFrameNanos) / 1_000_000_000f) : 1f / 60f);
        lastFrameNanos = now;

        updateMouse(io);
        updateModifiers(io);
        syncTextInput(mc, io);
    }

    private void updateMouse(ImGuiIO io) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            int mask = SDLMouse.SDL_GetMouseState(x, y);
            for (int i = 0; i < MOUSE_BUTTONS; i++) {
                int sdlButton = toSdlButton(i);
                boolean held = (mask & (1 << (sdlButton - 1))) != 0;
                io.setMouseDown(i, mouseJustPressed[i] || held);
                mouseJustPressed[i] = false;
            }
            io.setMousePos(x.get(0), y.get(0));
        }
    }

    private static void updateModifiers(ImGuiIO io) {
        io.setKeyCtrl(InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL));
        io.setKeyShift(InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
        io.setKeyAlt(InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT));
        io.setKeySuper(InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI));
    }

    private static void syncTextInput(Minecraft mc, ImGuiIO io) {
        if (io.getWantTextInput()) {
            mc.textInputManager().startTextInput(TEXT_INPUT_OWNER);
        } else {
            mc.textInputManager().stopTextInput(TEXT_INPUT_OWNER);
        }
    }

    void keyCallback(int scancode, int action) {
        if (scancode < 0 || scancode >= SCANCODE_COUNT) return;
        if (action == InputConstants.PRESS) {
            ImGui.getIO().setKeysDown(scancode, true);
        } else if (action == InputConstants.RELEASE) {
            ImGui.getIO().setKeysDown(scancode, false);
        }
    }

    void charCallback(int codepoint) {
        ImGui.getIO().addInputCharacter(codepoint);
    }

    void mouseButtonCallback(int sdlButton, int action) {
        int button = toImGuiButton(sdlButton);
        if (button < 0) return;
        if (action == InputConstants.PRESS) {
            mouseJustPressed[button] = true;
        }
    }

    void scrollCallback(double xOffset, double yOffset) {
        ImGuiIO io = ImGui.getIO();
        io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
        io.setMouseWheel(io.getMouseWheel() + (float) yOffset);
    }

    void dispose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.textInputManager() != null) {
            mc.textInputManager().stopTextInput(TEXT_INPUT_OWNER);
        }
    }

    private static int toImGuiButton(int sdlButton) {
        switch (sdlButton) {
            case InputConstants.MOUSE_BUTTON_LEFT: return 0;
            case InputConstants.MOUSE_BUTTON_RIGHT: return 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE: return 2;
            case InputConstants.MOUSE_BUTTON_4: return 3;
            case InputConstants.MOUSE_BUTTON_5: return 4;
            default: return -1;
        }
    }

    private static int toSdlButton(int imguiButton) {
        switch (imguiButton) {
            case 0: return InputConstants.MOUSE_BUTTON_LEFT;
            case 1: return InputConstants.MOUSE_BUTTON_RIGHT;
            case 2: return InputConstants.MOUSE_BUTTON_MIDDLE;
            case 3: return InputConstants.MOUSE_BUTTON_4;
            default: return InputConstants.MOUSE_BUTTON_5;
        }
    }
}
