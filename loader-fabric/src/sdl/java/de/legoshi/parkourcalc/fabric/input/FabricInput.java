package de.legoshi.parkourcalc.fabric.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public final class FabricInput {

    public static final int UNRESOLVED_KEY = 0;

    private FabricInput() {
    }

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    public static boolean isCtrlDown() {
        return isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean isShiftDown() {
        return isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static boolean isAltDown() {
        return isKeyDown(InputConstants.KEY_LALT) || isKeyDown(InputConstants.KEY_RALT);
    }

    public static boolean isMouseDown(int button) {
        return (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0;
    }

    public static double cursorX() {
        return cursorPos(true);
    }

    public static double cursorY() {
        return cursorPos(false);
    }

    private static double cursorPos(boolean wantX) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            SDLMouse.SDL_GetMouseState(x, y);
            return wantX ? x.get(0) : y.get(0);
        }
    }

    public static int keyTyping(char letter, int fallback) {
        for (int scancode = InputConstants.KEY_A; scancode <= InputConstants.KEY_Z; scancode++) {
            int keycode = SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) 0, false);
            if (keycode == letter) return scancode;
        }
        return fallback;
    }
}
