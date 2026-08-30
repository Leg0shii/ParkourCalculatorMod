package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.io.OsSystemBridge;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import de.legoshi.parkourcalc.fabric.render.FabricHudOverlayRenderer;
import de.legoshi.parkourcalc.fabric.render.FabricWorldOverlayRenderer;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public class FabricParkourCalculator implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";

    public static KeyMapping toggleKeyBinding;
    public static KeyMapping deselectKeyBinding;
    public static KeyMapping playbackKeyBinding;
    public static KeyMapping landingConstraintsKeyBinding;
    public static KeyMapping removeConstraintsKeyBinding;
    public static KeyMapping extendPathAndSolveKeyBinding;
    private static KeyMapping applySurfaceStateKeyBinding;
    private static KeyMapping solveKeyBinding;
    private static KeyMapping solverStartTickKeyBinding;
    private static KeyMapping solverEndTickKeyBinding;
    private static KeyMapping rerunSimulationKeyBinding;
    private static KeyMapping captureMomentumBlockKeyBinding;
    private static KeyMapping captureCollisionBlockKeyBinding;
    private static KeyMapping captureLandBlockKeyBinding;
    private static KeyMapping clearBlocksKeyBinding;
    private static boolean blockCaptureEnabled;

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

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        toggleKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.toggle_ui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                category
        ));
        deselectKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.deselect_all",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                category
        ));
        playbackKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.toggle_playback",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category
        ));
        landingConstraintsKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.add_landing_constraints",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                category
        ));
        removeConstraintsKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.remove_selected_constraints",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                category
        ));
        extendPathAndSolveKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.extend_path_and_solve_to_block",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                category
        ));
        applySurfaceStateKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.apply_surface_state",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        ));
        solveKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.solve",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                category
        ));
        solverStartTickKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.set_solver_start",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                category
        ));
        solverEndTickKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.set_solver_end",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category
        ));
        rerunSimulationKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.parkourcalculator.rerun_simulation",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                category
        ));

        application.initSettingsStorage(
                FabricLoader.getInstance().getConfigDir().resolve("parkourcalculator.json")
        );
        blockCaptureEnabled = application.getSettings().experimentalBlockCapture;
        if (blockCaptureEnabled) {
            captureMomentumBlockKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.parkourcalculator.capture_momentum_block", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, category));
            captureCollisionBlockKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.parkourcalculator.capture_collision_block", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
            captureLandBlockKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.parkourcalculator.capture_land_block", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
            clearBlocksKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.parkourcalculator.clear_blocks", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, category));
        }

        application.setModVersion(modVersion());
        OsSystemBridge.setPlatformOpeners(
                p -> Util.getPlatform().openPath(p),
                u -> Util.getPlatform().openUri(u)
        );
        application.setFilePicker(new FabricFilePicker());
        application.setSaveStore(new FileSystemSaveStore(
                FabricLoader.getInstance().getGameDir().resolve("parkourcalculator"),
                modVersion(),
                SharedConstants.getCurrentVersion().name(),
                FabricWorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);
        application.setBlockPicker(new FabricBlockPicker());
        application.setupUi();

        ClientTickEvents.END_CLIENT_TICK.register(FabricParkourCalculator::handleInput);
        ClientTickEvents.START_CLIENT_TICK.register(FabricParkourCalculator::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(FabricParkourCalculator::onEndTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> application.onWorldChange());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> application.onWorldChange());
        ServerLifecycleEvents.SERVER_STOPPING.register(simulator::onServerStopping);

        LevelRenderEvents.AFTER_SOLID_FEATURES.register(FabricParkourCalculator::renderWorldOverlayBeforeTranslucent);
        LevelRenderEvents.COLLECT_SUBMITS.register(FabricParkourCalculator::onCollectSubmits);
    }

    private static boolean wasPlaybackRunning = false;

    private static void onStartTick(Minecraft client) {
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
                de.legoshi.parkourcalc.fabric.sim.paired.RestartSettle.clear();
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
            de.legoshi.parkourcalc.fabric.sim.paired.RestartSettle.clear();
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

    private static void onEndTick(Minecraft client) {
        // Restore visual yaw after MC physics so render frames don't briefly show
        // the snap value the physics tick used.
        application.postTickPlayback();
        ReplayLockstep.clientBarrierPostTick();
    }

    public static void syncFrozenPlayerToServer() {
        playbackBridge.syncFrozenPlayerToServer();
    }

    public static java.util.List<de.legoshi.parkourcalc.core.sim.TickState> simStates() {
        return application.getBoxController().getStates();
    }

    public static de.legoshi.parkourcalc.core.sim.Checkpoint simCheckpoint(int index) {
        return application.getCheckpoint(index);
    }

    private static void handleInput(Minecraft client) {
        if (client.getWindow() == null) return;

        // Drain queued presses; only act when no MC screen owns input. Prevents
        // typing the bound key in chat from toggling the UI.
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
        boolean extendPathAndSolvePressed = false;
        while (extendPathAndSolveKeyBinding.consumeClick()) {
            extendPathAndSolvePressed = true;
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
        boolean rerunSimulationPressed = false;
        while (rerunSimulationKeyBinding.consumeClick()) {
            rerunSimulationPressed = true;
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
        boolean canDispatch = client.gui.screen() == null && !imguiWantsKeys;
        boolean chordFree = canDispatch && !isCtrlHeld(client);

        if (toggled && chordFree) {
            setOverlayOpen(!application.isControlPanelOpen());
        }
        if (deselectPressed && chordFree) {
            application.getSelection().clear();
        }
        if (playbackPressed && chordFree) {
            togglePlayback();
        }
        if (landingConstraintsPressed && canDispatch) {
            long window = client.getWindow().handle();
            boolean enter = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean remove = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            application.onConstraintKey(enter, remove);
        }
        if (removeConstraintsPressed && chordFree) {
            application.removeSelectedConstraints();
        }
        if (extendPathAndSolvePressed && chordFree) {
            triggerExtendPathAndSolve(client);
        }
        if (applySurfaceStatePressed && chordFree) {
            application.applyPathSurfaceState();
        }
        if (solvePressed && chordFree) {
            application.solveAngleSolver();
        }
        if (solverStartPressed && chordFree) {
            application.setSolverStartTickFromSelection();
        }
        if (solverEndPressed && chordFree) {
            application.setSolverLandingTickFromSelection();
        }
        if (rerunSimulationPressed && chordFree) {
            application.runSimulation();
        }
        if (captureMomentum && chordFree) {
            application.captureAngleSolverBlock(BlockSelection.Kind.MOMENTUM);
        }
        if (captureCollision && chordFree) {
            application.captureAngleSolverBlock(BlockSelection.Kind.COLLISION);
        }
        if (captureLand && chordFree) {
            application.captureAngleSolverBlock(BlockSelection.Kind.LAND);
        }
        if (clearBlocks && chordFree) {
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
        if (isCtrlHeld(client) && glfwKey != boundKey(landingConstraintsKeyBinding)) return false;
        if (glfwKey == boundKey(deselectKeyBinding)) {
            application.getSelection().clear();
            return true;
        }
        if (glfwKey == boundKey(playbackKeyBinding)) {
            togglePlayback();
            return true;
        }
        if (glfwKey == boundKey(landingConstraintsKeyBinding)) {
            long window = client.getWindow().handle();
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
        if (glfwKey == boundKey(extendPathAndSolveKeyBinding)) {
            triggerExtendPathAndSolve(client);
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
        return KeyMappingHelper.getBoundKeyOf(mapping).getValue();
    }

    private static boolean isCtrlHeld(Minecraft client) {
        long window = client.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
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
        // Mixins stop forwarding events once the overlay loses focus, so a key/modifier still held
        // at close never gets its release. Flush everything so nothing sticks across reopen.
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

    /**
     * Fires after solid features, before translucent terrain draws, so boxes depth-test
     * only against opaque geometry and stay visible through water, lava, and stained/tinted glass.
     */
    public static void renderWorldOverlayBeforeTranslucent(LevelRenderContext context) {
        if (context != null) {
            onWorldRender(context);
        }
    }

    public static void onWorldRender(LevelRenderContext context) {
        application.tickDrag();
        if (application.isPlaybackRunning()) {
            application.renderPlayback();
            if (application.getSettings().keepBoxesDuringPlayback) {
                worldRenderer.render(context);
            }
            return;
        }
        worldRenderer.render(context);
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        if (application.isPlaybackRunning() && !application.getSettings().keepBoxesDuringPlayback) {
            return;
        }
        worldRenderer.submitGizmo(context);
    }

    /** Called from InGameHudMixin to queue the MACRO badge into the GUI state. */
    public static void onHudRender(GuiGraphicsExtractor context) {
        if (!application.isReady()) return;
        if (application.isPlaybackRunning()) {
            hudRenderer.render(context, application.getPlayback().teleportNoticeAlpha());
        }
    }

    /** Called by GameRendererMixin after guiRenderer.render(); ImGui draws above the rasterized HUD. */
    public static void onGuiRendered() {
        if (!application.isReady()) return;
        // Pinned panels are hidden while any blocking screen (pause, inventory, chat) is open.
        boolean allowDetached = Minecraft.getInstance().gui.screen() == null;
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
        // A vanilla screen (e.g. pause on tab-out) must take input precedence over ImGui.
        return application.isControlPanelOpen() && Minecraft.getInstance().gui.screen() == null;
    }

    public static boolean isControlPanelOpen() {
        return application.isControlPanelOpen();
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

    private static void triggerExtendPathAndSolve(Minecraft client) {
        if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
            double targetX = pos.getX() + 0.5;
            double targetY = pos.getY() + 1.0;
            if (client.level != null) {
                net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
                net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(client.level, pos);
                if (!shape.isEmpty()) {
                    targetY = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                }
            }
            double targetZ = pos.getZ() + 0.5;
            application.extendPathAndSolveToBlock(targetX, targetY, targetZ);
        }
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
