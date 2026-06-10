package de.legoshi.parkourcalc.forge.core.lwjgl2;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** LWJGL 2 input polling shared by the Forge 1.8.9 and 1.12.2 MinecraftAccess impls. */
public final class Lwjgl2InputState {

    public static boolean isMousePressedLeft() {
        return Mouse.isButtonDown(0);
    }

    public static boolean isMousePressedRight() {
        return Mouse.isButtonDown(1);
    }

    public static double getCursorScreenX() {
        return Mouse.getX();
    }

    // LWJGL 2 mouse Y is bottom-up; flip to top-down so callers see GLFW-style coords.
    public static double getCursorScreenY() {
        return Display.getHeight() - Mouse.getY();
    }

    public static boolean isSaveChordDown() {
        return isCtrlDown() && Keyboard.isKeyDown(Keyboard.KEY_S);
    }

    public static boolean isCtrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    public static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private Lwjgl2InputState() {
    }
}
