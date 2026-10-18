package de.legoshi.parkourcalc.fabric.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

public final class FabricInput {

    public static final int UNRESOLVED_KEY = GLFW.GLFW_KEY_UNKNOWN;

    private FabricInput() {
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    public static boolean isKeyDown(int key) {
        return GLFW.glfwGetKey(window(), key) == GLFW.GLFW_PRESS;
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
        return GLFW.glfwGetMouseButton(window(), button) == GLFW.GLFW_PRESS;
    }

    public static double cursorX() {
        return cursorPos(true);
    }

    public static double cursorY() {
        return cursorPos(false);
    }

    private static double cursorPos(boolean wantX) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window(), x, y);
            return wantX ? x.get(0) : y.get(0);
        }
    }

    public static int keyTyping(char letter, int fallback) {
        for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && name.length() == 1 && Character.toLowerCase(name.charAt(0)) == letter) return key;
        }
        return fallback;
    }
}
