package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.io.OsSystemBridge;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import de.legoshi.parkourcalc.fabric.mixin.KeyMappingAccessor;
import de.legoshi.parkourcalc.fabric.render.FabricHudOverlayRenderer;
import de.legoshi.parkourcalc.fabric.render.FabricWorldOverlayRenderer;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class FabricParkourCalculator implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";
    private static final String KEY_CATEGORY = "key.category.parkourcalculator.general";

    public static KeyMapping toggleKeyBinding;
    public static KeyMapping deselectKeyBinding;
    public static KeyMapping playbackKeyBinding;
    public static KeyMapping landingConstraintsKeyBinding;
    public static KeyMapping removeConstraintsKeyBinding;
    private static KeyMapping applySurfaceStateKeyBinding;
    private static KeyMapping solveKeyBinding;
    private static KeyMapping solverStartTickKeyBinding;
    private static KeyMapping solverEndTickKeyBinding;
    private static KeyMapping captureMomentumBlockKeyBinding;
    private static KeyMapping captureCollisionBlockKeyBinding;
    private static KeyMapping captureLandBlockKeyBinding;
    private static KeyMapping clearBlocksKeyBinding;
    private static boolean blockCaptureEnabled;

    private static final List<KeyMapping> MOD_KEYS = new ArrayList<>();
    private static boolean keysCreated = false;

    private static final FabricSimulator simulator = new FabricSimulator();
    private static final Application application = new Application(
            simulator,
            new FabricMinecraftAccess()
    );
    private static final FabricPlaybackBridge playbackBridge = new FabricPlaybackBridge();
    private static final FabricWorldOverlayRenderer worldRenderer = new FabricWorldOverlayRenderer(
                    application.getBoxController(),
                    application.getSettings(),
                    application.getSelection(),
                    application.getYawGizmo(),
                    application::getAngleSolverState
            );
    private static final FabricHudOverlayRenderer hudRenderer = new FabricHudOverlayRenderer();

    private static KeyMapping key(String name, int glfwKey) {
        KeyMapping km = new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, KEY_CATEGORY);
        MOD_KEYS.add(km);
        return km;
    }

    private static void createKeyBindings() {
        if (keysCreated) return;
        keysCreated = true;
        toggleKeyBinding = key("key.parkourcalculator.toggle_ui", GLFW.GLFW_KEY_G);
        deselectKeyBinding = key("key.parkourcalculator.deselect_all", GLFW.GLFW_KEY_L);
        playbackKeyBinding = key("key.parkourcalculator.toggle_playback", GLFW.GLFW_KEY_P);
        landingConstraintsKeyBinding = key("key.parkourcalculator.add_landing_constraints", GLFW.GLFW_KEY_B);
        removeConstraintsKeyBinding = key("key.parkourcalculator.remove_selected_constraints", GLFW.GLFW_KEY_X);
        applySurfaceStateKeyBinding = key("key.parkourcalculator.apply_surface_state", GLFW.GLFW_KEY_H);
        solveKeyBinding = key("key.parkourcalculator.solve", GLFW.GLFW_KEY_V);
        solverStartTickKeyBinding = key("key.parkourcalculator.set_solver_start", GLFW.GLFW_KEY_I);
        solverEndTickKeyBinding = key("key.parkourcalculator.set_solver_end", GLFW.GLFW_KEY_O);
        if (blockCaptureEnabled) {
            captureMomentumBlockKeyBinding = key("key.parkourcalculator.capture_momentum_block", GLFW.GLFW_KEY_M);
            captureCollisionBlockKeyBinding = key("key.parkourcalculator.capture_collision_block", GLFW.GLFW_KEY_N);
            captureLandBlockKeyBinding = key("key.parkourcalculator.capture_land_block", GLFW.GLFW_KEY_K);
            clearBlocksKeyBinding = key("key.parkourcalculator.clear_blocks", GLFW.GLFW_KEY_DELETE);
        }
    }

    public static List<KeyMapping> collectKeyMappings() {
        createKeyBindings();
        return MOD_KEYS;
    }

    @Override
    public void onInitializeClient() {
        application.initSettingsStorage(
                FabricLoader.getInstance().getConfigDir().resolve("parkourcalculator.json")
        );
        blockCaptureEnabled = application.getSettings().experimentalBlockCapture;
        createKeyBindings();

        application.setModVersion(modVersion());
        OsSystemBridge.setPlatformOpeners(
                p -> Util.getPlatform().openPath(p),
                u -> Util.getPlatform().openUri(u)
        );
        application.setFilePicker(new FabricFilePicker());
        application.setSaveStore(new FileSystemSaveStore(
                FabricLoader.getInstance().getGameDir().resolve("parkourcalculator"),
                modVersion(),
                SharedConstants.getCurrentVersion().getName(),
                FabricWorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);
        application.setBlockPicker(new FabricBlockPicker());
        application.setupUi();
    }

    private static boolean wasPlaybackRunning = false;

    public static void onStartTick() {
        ReplayLockstep.clientBarrierPreTick();
        manageInputLifecycle();
        application.tickPlayback();
    }

    private static void manageInputLifecycle() {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            if (wasPlaybackRunning) {
                application.getPlayback().stop();
                playbackBridge.resetInputOverride();
                playbackBridge.endGhostPlayback();
                ReplayLockstep.disengage();
                wasPlaybackRunning = false;
            }
            return;
        }
        boolean isRunning = application.isPlaybackRunning();
        if (isRunning && !wasPlaybackRunning) {
            playbackBridge.installPlaybackInput(p);
            maybeEngageLockstep();
        } else if (!isRunning && wasPlaybackRunning) {
            playbackBridge.restorePlaybackInput(p);
            playbackBridge.endGhostPlayback();
            ReplayLockstep.disengage();
        }
        wasPlaybackRunning = isRunning;
    }

    private static void maybeEngageLockstep() {
        if (!application.getSettings().lockstepReplay) return;
        if (!playbackBridge.isSingleplayer()) return;
        net.minecraft.server.MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            ReplayLockstep.engage(server);
        }
    }

    public static void onWorldChange() {
        application.onWorldChange();
    }

    public static boolean isGhostPlaybackActive() {
        return playbackBridge.ghostEntity() != null;
    }

    public static boolean shouldForceGroundOnTick0(net.minecraft.client.player.LocalPlayer self) {
        return application.isPlaybackRunning()
                && playbackBridge.ghostEntity() == null
                && self == Minecraft.getInstance().player
                && application.getPlayback().currentTick() == 0;
    }

    public static boolean shouldForceGroundOnGhostTick0(de.legoshi.parkourcalc.fabric.sim.GhostPlayerEntity self) {
        return application.isPlaybackRunning()
                && self == playbackBridge.ghostEntity()
                && application.getPlayback().currentTick() == 0;
    }

    public static boolean firstTickOnGround() {
        return application.getPlayback().firstTickOnGround();
    }

    public static boolean shouldSuppressFallDamage(net.minecraft.world.entity.Entity self) {
        Settings settings = application.getSettings();
        return application.isPlaybackRunning()
                && !(settings.pairedSimulation && settings.pairedDamage)
                && self instanceof net.minecraft.world.entity.player.Player;
    }

    public static boolean shouldPinHealthDuringPlayback(net.minecraft.world.entity.Entity self) {
        if (!application.isPlaybackRunning()) return false;
        if (!application.getSettings().pairedSimulation) return false;
        if (!(self instanceof net.minecraft.world.entity.player.Player)) return false;
        net.minecraft.client.player.LocalPlayer local = Minecraft.getInstance().player;
        return local != null && self.getUUID().equals(local.getUUID());
    }

    public static boolean shouldSuppressDamageDuringPlayback(net.minecraft.world.entity.Entity self) {
        Settings settings = application.getSettings();
        if (!application.isPlaybackRunning()) return false;
        if (settings.pairedSimulation && settings.pairedDamage) return false;
        if (!(self instanceof net.minecraft.world.entity.player.Player)) return false;
        net.minecraft.client.player.LocalPlayer local = Minecraft.getInstance().player;
        return local != null && self.getUUID().equals(local.getUUID());
    }

    public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
        simulator.onServerStopping(server);
    }

    public static java.util.List<de.legoshi.parkourcalc.core.sim.TickState> simStates() {
        return application.getBoxController().getStates();
    }

    public static void onEndTick() {
        application.postTickPlayback();
        ReplayLockstep.clientBarrierPostTick();
    }

    public static void syncFrozenPlayerToServer() {
        playbackBridge.syncFrozenPlayerToServer();
    }

    public static void handleInput() {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return;

        boolean toggled = false;
        while (toggleKeyBinding.consumeClick()) {
            toggled = true;
        }
        boolean deselectPressed = false;
        while (deselectKeyBinding.consumeClick()) {
            deselectPressed = true;
        }
        boolean playbackPressed = false;
        while (playbackKeyBinding.consumeClick()) {
            playbackPressed = true;
        }
        boolean landingConstraintsPressed = false;
        while (landingConstraintsKeyBinding.consumeClick()) {
            landingConstraintsPressed = true;
        }
        boolean removeConstraintsPressed = false;
        while (removeConstraintsKeyBinding.consumeClick()) {
            removeConstraintsPressed = true;
        }
        boolean applySurfaceStatePressed = false;
        while (applySurfaceStateKeyBinding.consumeClick()) {
            applySurfaceStatePressed = true;
        }
        boolean solvePressed = false;
        while (solveKeyBinding.consumeClick()) {
            solvePressed = true;
        }
        boolean solverStartPressed = false;
        while (solverStartTickKeyBinding.consumeClick()) {
            solverStartPressed = true;
        }
        boolean solverEndPressed = false;
        while (solverEndTickKeyBinding.consumeClick()) {
            solverEndPressed = true;
        }
        boolean captureMomentum = false;
        boolean captureCollision = false;
        boolean captureLand = false;
        boolean clearBlocks = false;
        if (blockCaptureEnabled) {
            while (captureMomentumBlockKeyBinding.consumeClick()) {
                captureMomentum = true;
            }
            while (captureCollisionBlockKeyBinding.consumeClick()) {
                captureCollision = true;
            }
            while (captureLandBlockKeyBinding.consumeClick()) {
                captureLand = true;
            }
            while (clearBlocksKeyBinding.consumeClick()) {
                clearBlocks = true;
            }
        }

        boolean imguiWantsKeys = application.isControlPanelOpen() && ImGui.getIO().getWantTextInput();
        boolean canDispatch = client.screen == null && !imguiWantsKeys;

        if (toggled && canDispatch) {
            setOverlayOpen(!application.isControlPanelOpen());
        }
        if (deselectPressed && canDispatch) {
            application.getSelection().clear();
        }
        if (playbackPressed && canDispatch) {
            togglePlayback();
        }
        if (landingConstraintsPressed && canDispatch) {
            long window = client.getWindow().getWindow();
            boolean enter = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean remove = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            application.onConstraintKey(enter, remove);
        }
        if (removeConstraintsPressed && canDispatch) {
            application.removeSelectedConstraints();
        }
        if (applySurfaceStatePressed && canDispatch) {
            application.applyPathSurfaceState();
        }
        if (solvePressed && canDispatch) {
            application.solveAngleSolver();
        }
        if (solverStartPressed && canDispatch) {
            application.setSolverStartTickFromSelection();
        }
        if (solverEndPressed && canDispatch) {
            application.setSolverLandingTickFromSelection();
        }
        if (captureMomentum && canDispatch) {
            application.captureAngleSolverBlock(BlockSelection.Kind.MOMENTUM);
        }
        if (captureCollision && canDispatch) {
            application.captureAngleSolverBlock(BlockSelection.Kind.COLLISION);
        }
        if (captureLand && canDispatch) {
            application.captureAngleSolverBlock(BlockSelection.Kind.LAND);
        }
        if (clearBlocks && canDispatch) {
            application.clearAngleSolverBlocks();
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

    public static boolean dispatchOverlayHotkey(int glfwKey) {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return false;
        if (glfwKey == boundKey(deselectKeyBinding)) {
            application.getSelection().clear();
            return true;
        }
        if (glfwKey == boundKey(playbackKeyBinding)) {
            togglePlayback();
            return true;
        }
        if (glfwKey == boundKey(landingConstraintsKeyBinding)) {
            long window = client.getWindow().getWindow();
            boolean enter = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean remove = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            application.onConstraintKey(enter, remove);
            return true;
        }
        if (glfwKey == boundKey(removeConstraintsKeyBinding)) {
            application.removeSelectedConstraints();
            return true;
        }
        if (glfwKey == boundKey(applySurfaceStateKeyBinding)) {
            application.applyPathSurfaceState();
            return true;
        }
        if (glfwKey == boundKey(solveKeyBinding)) {
            application.solveAngleSolver();
            return true;
        }
        if (glfwKey == boundKey(solverStartTickKeyBinding)) {
            application.setSolverStartTickFromSelection();
            return true;
        }
        if (glfwKey == boundKey(solverEndTickKeyBinding)) {
            application.setSolverLandingTickFromSelection();
            return true;
        }
        return false;
    }

    private static int boundKey(KeyMapping mapping) {
        return ((KeyMappingAccessor) (Object) mapping).pkc$getKey().getValue();
    }

    public static void closeOverlay() {
        if (application.isControlPanelOpen()) {
            setOverlayOpen(false);
        }
    }

    private static void setOverlayOpen(boolean open) {
        Minecraft client = Minecraft.getInstance();
        application.setControlPanelOpen(open);

        if (open) {
            client.mouseHandler.releaseMouse();
        } else {
            client.mouseHandler.grabMouse();
            clearImGuiInputState();
        }
    }

    private static void clearImGuiInputState() {
        ImGuiIO io = ImGui.getIO();
        io.clearInputKeys();
        io.setKeyCtrl(false);
        io.setKeyShift(false);
        io.setKeyAlt(false);
        io.setKeySuper(false);
        for (int i = 0; i < 5; i++) {
            io.setMouseDown(i, false);
        }
    }

    public static void onWorldRenderClassic(PoseStack poseStack, Vec3 camPos) {
        application.tickDrag();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        if (application.isPlaybackRunning()) {
            application.renderPlayback();
            if (application.getSettings().keepBoxesDuringPlayback) {
                worldRenderer.render(poseStack, buffers, camPos);
            }
            return;
        }
        worldRenderer.render(poseStack, buffers, camPos);
    }

    public static void onHudRender(GuiGraphics context) {
        if (!application.isReady()) return;
        if (application.isPlaybackRunning()) {
            hudRenderer.render(context);
        }
    }

    public static void onGuiRendered() {
        if (!application.isReady()) return;
        boolean allowDetached = Minecraft.getInstance().screen == null;
        ImGuiImpl.beginImGuiRendering();
        application.getOverlayManager().render(ImGui.getIO(), allowDetached);
        ImGuiImpl.endImGuiRendering();
    }

    public static boolean isEditingYaw() {
        return application.isEditingYaw();
    }

    public static void navigateYaw(boolean forward) {
        application.navigateYaw(forward);
    }

    public static boolean isUiFocused() {
        return application.isControlPanelOpen() && Minecraft.getInstance().screen == null;
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

    public static void resolveAutoScale(int displayHeightPx) {
        application.resolveAutoScaleIfNeeded(displayHeightPx);
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
