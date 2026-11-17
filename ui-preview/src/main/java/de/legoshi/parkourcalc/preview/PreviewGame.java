package de.legoshi.parkourcalc.preview;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class PreviewGame implements MinecraftAccess {

    private static final List<String> BLOCK_IDS = Arrays.asList(
            "minecraft:air", "minecraft:stone", "minecraft:oak_planks", "minecraft:glass",
            "minecraft:ladder", "minecraft:oak_trapdoor", "minecraft:oak_slab", "minecraft:player_head");

    private PreviewInput input;
    private Vec3dCore playerPosition = new Vec3dCore(0.5, 64.0, 0.5);
    private float playerYaw;
    private float mouseSensitivity = 0.5f;

    void setInput(PreviewInput input) {
        this.input = input;
    }

    void setPlayerPosition(Vec3dCore pos, float yaw) {
        playerPosition = pos;
        playerYaw = yaw;
    }

    @Override
    public Vec3dCore getPlayerPosition() {
        return playerPosition;
    }

    @Override
    public float getPlayerYaw() {
        return playerYaw;
    }

    @Override
    public float getPlayerPitch() {
        return 0f;
    }

    @Override
    public Vec3dCore getEyePosition() {
        return new Vec3dCore(playerPosition.x, playerPosition.y + getEyeHeight(false), playerPosition.z);
    }

    @Override
    public Vec3dCore getLookDirection() {
        return getLookVector(playerYaw, 0f);
    }

    @Override
    public double getInsideBlockInset() {
        return 0.001;
    }

    @Override
    public String describeBlockState(int x, int y, int z) {
        return "minecraft:air";
    }

    @Override
    public String getHeldBlock() {
        return "minecraft:stone";
    }

    @Override
    public List<String> blockIds() {
        return BLOCK_IDS;
    }

    @Override
    public String defaultBlockState(String id) {
        return id;
    }

    @Override
    public Map<String, List<String>> blockStateOptions(String id) {
        if (id == null || !id.contains("trapdoor")) return Collections.emptyMap();
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("facing", Arrays.asList("north", "south", "west", "east"));
        options.put("half", Arrays.asList("bottom", "top"));
        options.put("open", Arrays.asList("false", "true"));
        return options;
    }

    @Override
    public boolean isMousePressedLeft() {
        return input != null && input.mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean isMousePressedRight() {
        return input != null && input.mouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    @Override
    public double getCursorScreenX() {
        return input == null ? 0 : input.cursorX();
    }

    @Override
    public double getCursorScreenY() {
        return input == null ? 0 : input.cursorY();
    }

    @Override
    public boolean isCtrlDown() {
        return input != null && input.ctrlDown();
    }

    @Override
    public boolean isAltDown() {
        return input != null && input.altDown();
    }

    @Override
    public boolean isShiftDown() {
        return input != null && input.shiftDown();
    }

    @Override
    public boolean isSaveChordDown() {
        return isCtrlDown() && key(GLFW.GLFW_KEY_S);
    }

    @Override
    public boolean isUndoChordDown() {
        return isCtrlDown() && !isShiftDown() && key(GLFW.GLFW_KEY_Z);
    }

    @Override
    public boolean isRedoChordDown() {
        return isCtrlDown() && (key(GLFW.GLFW_KEY_Y) || isShiftDown() && key(GLFW.GLFW_KEY_Z));
    }

    @Override
    public boolean isCopyChordDown() {
        return isCtrlDown() && key(GLFW.GLFW_KEY_C);
    }

    @Override
    public boolean isPasteChordDown() {
        return isCtrlDown() && key(GLFW.GLFW_KEY_V);
    }

    private boolean key(int glfwKey) {
        return input != null && input.keyDown(glfwKey);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    @Override
    public void setMouseSensitivity(float sensitivity) {
        mouseSensitivity = sensitivity;
    }

    @Override
    public boolean isSinglePlayer() {
        return true;
    }

    @Override
    public String getRerunSimulationKeyName() {
        return "J";
    }

    @Override
    public <T> T runOnServerThread(Supplier<T> task) {
        return task.get();
    }
}
