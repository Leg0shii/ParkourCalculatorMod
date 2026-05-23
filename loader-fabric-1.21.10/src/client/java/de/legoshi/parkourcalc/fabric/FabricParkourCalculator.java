package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import de.legoshi.parkourcalc.fabric.render.FabricHudOverlayRenderer;
import de.legoshi.parkourcalc.fabric.render.FabricWorldOverlayRenderer;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
import imgui.ImGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class FabricParkourCalculator implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";

    public static KeyBinding toggleKeyBinding;
    public static KeyBinding deselectKeyBinding;
    public static KeyBinding playbackKeyBinding;

    private static final Application application = new Application(
            new FabricSimulator(),
            new FabricMinecraftAccess()
    );
    private static final FabricPlaybackBridge playbackBridge = new FabricPlaybackBridge();
    private static final FabricWorldOverlayRenderer worldRenderer =
            new FabricWorldOverlayRenderer(
                    application.getBoxController(),
                    application.getSettings(),
                    application.getSelection(),
                    application.getYawGizmo());
    private static final FabricHudOverlayRenderer hudRenderer = new FabricHudOverlayRenderer();

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.parkourcalculator.toggle_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));
        deselectKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.parkourcalculator.deselect_all",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category
        ));
        playbackKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.parkourcalculator.toggle_playback",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                category
        ));

        application.setModVersion(modVersion());
        application.registerInputOverlay();
        application.registerSettingsOverlay();
        application.registerFileBrowserOverlay();
        application.registerTickInfoOverlay();
        application.initSettingsStorage(
                FabricLoader.getInstance().getConfigDir().resolve("parkourcalculator.json")
        );
        application.setSaveStore(new FileSystemSaveStore(
                FabricLoader.getInstance().getGameDir().resolve("parkourcalculator"),
                modVersion(),
                SharedConstants.getGameVersion().name(),
                FabricWorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);

        ClientTickEvents.END_CLIENT_TICK.register(FabricParkourCalculator::handleInput);
        ClientTickEvents.START_CLIENT_TICK.register(FabricParkourCalculator::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(FabricParkourCalculator::onEndTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> application.onWorldChange());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> application.onWorldChange());
    }

    private static void onStartTick(MinecraftClient client) {
        application.tickPlayback();
    }

    private static void onEndTick(MinecraftClient client) {
        // Restore visual yaw after MC physics so render frames don't briefly show
        // the snap value the physics tick used.
        application.postTickPlayback();
    }

    private static void handleInput(MinecraftClient client) {
        if (client.getWindow() == null) return;

        // Drain queued presses; only act when no MC screen owns input. Prevents
        // typing the bound key in chat from toggling the UI.
        boolean toggled = false;
        while (toggleKeyBinding.wasPressed()) {
            toggled = true;
        }
        boolean deselectPressed = false;
        while (deselectKeyBinding.wasPressed()) {
            deselectPressed = true;
        }
        boolean playbackPressed = false;
        while (playbackKeyBinding.wasPressed()) {
            playbackPressed = true;
        }

        boolean imguiWantsKeys = application.isControlPanelOpen() && ImGui.getIO().getWantTextInput();
        boolean canDispatch = client.currentScreen == null && !imguiWantsKeys;

        if (toggled && canDispatch) {
            setOverlayOpen(!application.isControlPanelOpen());
        }
        if (deselectPressed && canDispatch) {
            application.getSelection().clear();
        }
        if (playbackPressed && canDispatch) {
            togglePlayback();
        }
    }

    private static void togglePlayback() {
        PlaybackController pc = application.getPlayback();
        if (pc.isRunning()) {
            pc.stop();
        } else if (pc.canStart()) {
            pc.start();
        }
    }

    public static void closeOverlay() {
        if (application.isControlPanelOpen()) {
            setOverlayOpen(false);
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
        if (application.isPlaybackRunning()) {
            application.renderPlayback();
            return;
        }
        worldRenderer.render(positionMatrix);
    }

    /** Called from InGameHudMixin to render ImGui overlays. */
    public static void onHudRender(DrawContext context) {
        if (!application.isReady()) return;
        if (application.isPlaybackRunning()) {
            hudRenderer.render(context);
        }
        ImGuiImpl.beginImGuiRendering();
        application.getOverlayManager().render(ImGui.getIO());
        ImGuiImpl.endImGuiRendering();
    }

    public static boolean isUiFocused() {
        // A vanilla screen (e.g. pause on tab-out) must take input precedence over ImGui.
        return application.isControlPanelOpen() && MinecraftClient.getInstance().currentScreen == null;
    }

    public static boolean shouldSuppressLeftClick() {
        return application.shouldSuppressLeftClick();
    }

    public static boolean shouldSuppressRightClick() {
        return application.shouldSuppressRightClick();
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
}
