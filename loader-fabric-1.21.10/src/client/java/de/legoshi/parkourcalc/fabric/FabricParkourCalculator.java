package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import de.legoshi.parkourcalc.fabric.render.FabricWorldOverlayRenderer;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
import imgui.ImGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FabricParkourCalculator implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";

    public static KeyBinding toggleKeyBinding;

    private static final InputData inputData = new InputData();
    private static final Simulator simulator = new FabricSimulator();
    private static final SimulationRunner runner = new SimulationRunner(simulator);
    private static final BoxController boxController = new BoxController();
    private static final OverlayManager overlayManager = new OverlayManager();

    private static final FabricWorldOverlayRenderer worldRenderer = new FabricWorldOverlayRenderer(boxController);
    private static final FabricBoxDragController dragController = new FabricBoxDragController(
            boxController,
            FabricParkourCalculator::isUiFocused,
            FabricParkourCalculator::handleStartPositionChange
    );

    private static final KeyState escapeKey = new KeyState();

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.parkourcalculator.toggle_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));

        InputOverlay inputOverlay = new InputOverlay(
                inputData,
                FabricParkourCalculator::runSimulation,
                FabricParkourCalculator::setStartToPlayerPosition
        );

        overlayManager.register("TAS Inputs", inputOverlay);

        ClientTickEvents.END_CLIENT_TICK.register(FabricParkourCalculator::handleInput);
    }

    private static void handleInput(MinecraftClient client) {
        if (client.getWindow() == null) return;

        // Drain queued presses; only act when no MC screen owns input. Prevents
        // typing the bound key in chat from toggling the UI.
        boolean toggled = false;
        while (toggleKeyBinding.wasPressed()) {
            toggled = true;
        }
        if (toggled && client.currentScreen == null) {
            setOverlayOpen(!overlayManager.isControlPanelOpen());
        }

        long window = client.getWindow().getHandle();
        if (escapeKey.justPressed(window, GLFW.GLFW_KEY_ESCAPE) && overlayManager.isControlPanelOpen()) {
            setOverlayOpen(false);
        }
    }

    private static void setOverlayOpen(boolean open) {
        MinecraftClient client = MinecraftClient.getInstance();
        overlayManager.setControlPanelOpen(open);

        if (open) {
            client.mouse.unlockCursor();
        } else {
            client.mouse.lockCursor();
            clearImGuiInputState();
        }
    }

    private static void clearImGuiInputState() {
        ImGui.getIO().clearInputKeys();
        for (int i = 0; i < 5; i++) {
            ImGui.getIO().setMouseDown(i, false);
        }
    }

    /** Called from WorldRendererMixin to render world overlays. */
    public static void onWorldRender(Matrix4f positionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        dragController.tick(client);
        worldRenderer.render(positionMatrix);
    }

    /** Called from InGameHudMixin to render ImGui overlays. */
    public static void onHudRender() {
        ImGuiImpl.beginImGuiRendering();
        overlayManager.render(ImGui.getIO());
        ImGuiImpl.endImGuiRendering();
    }

    public static boolean isUiFocused() {
        return overlayManager.isControlPanelOpen();
    }

    public static boolean shouldSuppressLeftClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        return dragController.shouldSuppressLeftClick(client);
    }

    private static void runSimulation() {
        List<Vec3dCore> path = runner.simulate(inputData);
        boxController.clearAll();
        for (Vec3dCore p : path) {
            boxController.add(p);
        }
    }

    private static void setStartToPlayerPosition() {
        runner.setStartFromPlayer();
    }

    private static void handleStartPositionChange(Vec3dCore pos) {
        runner.setStartPosition(pos);
        runSimulation();
    }

    private static class KeyState {
        private boolean wasPressed = false;

        boolean justPressed(long window, int key) {
            boolean isPressed = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean justPressed = isPressed && !wasPressed;
            wasPressed = isPressed;
            return justPressed;
        }
    }
}
