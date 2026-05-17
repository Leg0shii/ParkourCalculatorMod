package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import de.legoshi.parkourcalc.fabric.render.FabricWorldOverlayRenderer;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
import imgui.ImGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class FabricParkourCalculator implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";

    public static KeyBinding toggleKeyBinding;

    private static final Application application = new Application(
            new FabricSimulator(),
            new FabricMinecraftAccess()
    );
    private static final FabricWorldOverlayRenderer worldRenderer =
            new FabricWorldOverlayRenderer(application.getBoxController(), application.getSettings(), application.getSelection());

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

        application.registerInputOverlay();
        application.registerSettingsOverlay();
        application.registerFileBrowserOverlay();
        application.initSettingsStorage(
                FabricLoader.getInstance().getConfigDir().resolve("parkourcalculator.json")
        );
        application.setSaveStore(new FileSystemSaveStore(
                FabricLoader.getInstance().getGameDir().resolve("parkourcalculator"),
                modVersion(),
                SharedConstants.getGameVersion().name(),
                FabricWorldDescriptors::current
        ));

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
        boolean imguiWantsKeys = application.isControlPanelOpen() && ImGui.getIO().getWantTextInput();
        if (toggled && client.currentScreen == null && !imguiWantsKeys) {
            setOverlayOpen(!application.isControlPanelOpen());
        }

        long window = client.getWindow().getHandle();
        if (escapeKey.justPressed(window, GLFW.GLFW_KEY_ESCAPE) && application.isControlPanelOpen() && !imguiWantsKeys) {
            if (!application.getSelection().isEmpty()) {
                application.getSelection().clear();
            } else {
                setOverlayOpen(false);
            }
        }
    }

    private static void setOverlayOpen(boolean open) {
        MinecraftClient client = MinecraftClient.getInstance();
        application.setControlPanelOpen(open);

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
        application.tickDrag();
        worldRenderer.render(positionMatrix);
    }

    /** Called from InGameHudMixin to render ImGui overlays. */
    public static void onHudRender() {
        if (!application.isReady()) return;
        ImGuiImpl.beginImGuiRendering();
        application.getOverlayManager().render(ImGui.getIO());
        ImGuiImpl.endImGuiRendering();
    }

    public static boolean isUiFocused() {
        return application.isControlPanelOpen();
    }

    public static boolean shouldSuppressLeftClick() {
        return application.shouldSuppressLeftClick();
    }

    public static Settings getSettings() {
        return application.getSettings();
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
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
