package de.legoshi.parkourcalc.forge.core.lwjgl2;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** LWJGL 2 input polling shared by the Forge 1.8.9 and 1.12.2 MinecraftAccess impls. */
public final class Lwjgl2InputState {

    public static boolean isMousePressedLeft() {
        return Mouse.isButtonDown(0);
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
