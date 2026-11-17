package de.legoshi.parkourcalc.preview;

import imgui.ImGuiIO;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

final class PreviewInput {

    private final long window;
    private boolean scripted;
    float mouseX = -Float.MAX_VALUE;
    float mouseY = -Float.MAX_VALUE;
    final boolean[] buttons = new boolean[3];
    boolean ctrl;
    boolean shift;
    boolean alt;
    float wheel;
    final Set<Integer> keys = new HashSet<>();
    final StringBuilder chars = new StringBuilder();

    PreviewInput(long window, boolean scripted) {
        this.window = window;
        this.scripted = scripted;
    }

    boolean isScripted() {
        return scripted;
    }

    void setScripted(boolean scripted) {
        this.scripted = scripted;
    }

    void applyTo(ImGuiIO io) {
        if (!scripted) return;
        io.setMousePos(mouseX, mouseY);
        for (int i = 0; i < buttons.length; i++) io.setMouseDown(i, buttons[i]);
        for (int i = 3; i < 5; i++) io.setMouseDown(i, false);
        io.setKeyCtrl(ctrl);
        io.setKeyShift(shift);
        io.setKeyAlt(alt);
        io.setKeySuper(false);
        for (int k = 0; k < 512; k++) io.setKeysDown(k, keys.contains(k));
        io.setMouseWheel(wheel);
        wheel = 0f;
        if (chars.length() > 0) {
            io.addInputCharactersUTF8(chars.toString());
            chars.setLength(0);
        }
    }

    boolean mouseDown(int button) {
        if (scripted) return buttons[button];
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    double cursorX() {
        if (scripted) return mouseX;
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        return x[0];
    }

    double cursorY() {
        if (scripted) return mouseY;
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        return y[0];
    }

    boolean ctrlDown() {
        if (scripted) return ctrl;
        return live(GLFW.GLFW_KEY_LEFT_CONTROL) || live(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    boolean shiftDown() {
        if (scripted) return shift;
        return live(GLFW.GLFW_KEY_LEFT_SHIFT) || live(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    boolean altDown() {
        if (scripted) return alt;
        return live(GLFW.GLFW_KEY_LEFT_ALT) || live(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    boolean keyDown(int glfwKey) {
        if (scripted) return keys.contains(glfwKey);
        return live(glfwKey);
    }

    private boolean live(int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }
}
