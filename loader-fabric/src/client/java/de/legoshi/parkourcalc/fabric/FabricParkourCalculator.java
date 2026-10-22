package de.legoshi.parkourcalc.fabric;

//? if >=26.1
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
//? if >=26.1 {
import com.mojang.renderpearl.api.commands.RenderPass;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.anglesolver.noturn.PoolScreenAccelerator;
import de.legoshi.parkourcalc.gpu.OpenClPoolScreen;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.io.OsSystemBridge;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import de.legoshi.parkourcalc.fabric.input.FabricInput;
//? if <26.1
//import de.legoshi.parkourcalc.fabric.mixin.KeyMappingAccessor;
import de.legoshi.parkourcalc.fabric.render.FabricHudOverlayRenderer;
import de.legoshi.parkourcalc.fabric.render.FabricWorldOverlayRenderer;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.fabricmc.api.ClientModInitializer;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
//? if <26.1
//import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
*///?}

//? if >=26.1 {
import java.net.URI;
//?} else {
/*import java.util.ArrayList;
import java.util.List;
*///?}

public class FabricParkourCalculator implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";
    //? if <26.1
    //private static final String KEY_CATEGORY = "key.category.parkourcalculator.general";

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
    private static KeyMapping solverGoalFromLookKeyBinding;
    private static KeyMapping rerunSimulationKeyBinding;
    private static KeyMapping togglePathKeyBinding;
    private static KeyMapping copyTeleportKeyBinding;
    private static KeyMapping captureMomentumBlockKeyBinding;
    private static KeyMapping captureCollisionBlockKeyBinding;
    private static KeyMapping captureLandBlockKeyBinding;
    private static KeyMapping clearBlocksKeyBinding;
    private static boolean blockCaptureEnabled;

    //? if <26.1 {
    /*private static final List<KeyMapping> MOD_KEYS = new ArrayList<>();
    private static boolean keysCreated = false;
    *///?}

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

    //? if >=26.1 {
    private static KeyMapping register(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, key, category));
    }
    //?} else {
    /*private static KeyMapping key(String name, int key) {
        KeyMapping km = new KeyMapping(name, InputConstants.Type.KEYSYM, key, KEY_CATEGORY);
        MOD_KEYS.add(km);
        return km;
    }

    private static void createKeyBindings() {
        if (keysCreated) return;
        keysCreated = true;
        toggleKeyBinding = key("key.parkourcalculator.toggle_ui", InputConstants.KEY_G);
        deselectKeyBinding = key("key.parkourcalculator.deselect_all", InputConstants.KEY_L);
        playbackKeyBinding = key("key.parkourcalculator.toggle_playback", InputConstants.KEY_P);
        landingConstraintsKeyBinding = key("key.parkourcalculator.add_landing_constraints", InputConstants.KEY_B);
        removeConstraintsKeyBinding = key("key.parkourcalculator.remove_selected_constraints", InputConstants.KEY_X);
        extendPathAndSolveKeyBinding = key("key.parkourcalculator.extend_path_and_solve_to_block", InputConstants.KEY_U);
        applySurfaceStateKeyBinding = key("key.parkourcalculator.apply_surface_state", InputConstants.KEY_H);
        solveKeyBinding = key("key.parkourcalculator.solve", InputConstants.KEY_V);
        solverStartTickKeyBinding = key("key.parkourcalculator.set_solver_start", InputConstants.KEY_I);
        solverEndTickKeyBinding = key("key.parkourcalculator.set_solver_end", InputConstants.KEY_O);
        solverGoalFromLookKeyBinding = key("key.parkourcalculator.set_solver_goal_from_look", InputConstants.KEY_Z);
        rerunSimulationKeyBinding = key("key.parkourcalculator.rerun_simulation", InputConstants.KEY_J);
        togglePathKeyBinding = key("key.parkourcalculator.toggle_path", InputConstants.KEY_Y);
        copyTeleportKeyBinding = key("key.parkourcalculator.copy_teleport", InputConstants.KEY_K);
        if (blockCaptureEnabled) {
            captureMomentumBlockKeyBinding = key("key.parkourcalculator.capture_momentum_block", InputConstants.KEY_M);
            captureCollisionBlockKeyBinding = key("key.parkourcalculator.capture_collision_block", InputConstants.KEY_N);
            captureLandBlockKeyBinding = key("key.parkourcalculator.capture_land_block", InputConstants.KEY_K);
            clearBlocksKeyBinding = key("key.parkourcalculator.clear_blocks", InputConstants.KEY_DELETE);
        }
    }

    public static List<KeyMapping> collectKeyMappings() {
        createKeyBindings();
        return MOD_KEYS;
    }
    *///?}

    @Override
    public void onInitializeClient() {
        //? if >=26.1 {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        toggleKeyBinding = register("key.parkourcalculator.toggle_ui", InputConstants.KEY_G, category);
        deselectKeyBinding = register("key.parkourcalculator.deselect_all", InputConstants.KEY_L, category);
        playbackKeyBinding = register("key.parkourcalculator.toggle_playback", InputConstants.KEY_P, category);
        landingConstraintsKeyBinding = register("key.parkourcalculator.add_landing_constraints", InputConstants.KEY_B, category);
        removeConstraintsKeyBinding = register("key.parkourcalculator.remove_selected_constraints", InputConstants.KEY_X, category);
        extendPathAndSolveKeyBinding = register("key.parkourcalculator.extend_path_and_solve_to_block", InputConstants.KEY_U, category);
        applySurfaceStateKeyBinding = register("key.parkourcalculator.apply_surface_state", InputConstants.KEY_H, category);
        solveKeyBinding = register("key.parkourcalculator.solve", InputConstants.KEY_V, category);
        solverStartTickKeyBinding = register("key.parkourcalculator.set_solver_start", InputConstants.KEY_I, category);
        solverEndTickKeyBinding = register("key.parkourcalculator.set_solver_end", InputConstants.KEY_O, category);
        solverGoalFromLookKeyBinding = register("key.parkourcalculator.set_solver_goal_from_look", InputConstants.KEY_Z, category);
        rerunSimulationKeyBinding = register("key.parkourcalculator.rerun_simulation", InputConstants.KEY_J, category);
        togglePathKeyBinding = register("key.parkourcalculator.toggle_path", InputConstants.KEY_Y, category);
        copyTeleportKeyBinding = register("key.parkourcalculator.copy_teleport", InputConstants.KEY_K, category);
        //?}

        PoolScreenAccelerator.register(new OpenClPoolScreen.Factory());
        application.initSettingsStorage(
                FabricLoader.getInstance().getConfigDir().resolve("parkourcalculator.json")
        );
        blockCaptureEnabled = application.getSettings().experimentalBlockCapture;
        //? if >=26.1 {
        if (blockCaptureEnabled) {
            captureMomentumBlockKeyBinding = register("key.parkourcalculator.capture_momentum_block", InputConstants.KEY_M, category);
            captureCollisionBlockKeyBinding = register("key.parkourcalculator.capture_collision_block", InputConstants.KEY_N, category);
            captureLandBlockKeyBinding = register("key.parkourcalculator.capture_land_block", InputConstants.KEY_K, category);
            clearBlocksKeyBinding = register("key.parkourcalculator.clear_blocks", InputConstants.KEY_DELETE, category);
        }
        //?} else {
        /*createKeyBindings();
        *///?}

        application.setModVersion(modVersion());
        OsSystemBridge.setPlatformOpeners(
                //? if >=26.1 {
                Blaze3D::openPath,
                u -> Blaze3D.openUri(URI.create(u))
                //?} else {
                /*p -> Util.getPlatform().openFile(p.toFile()),
                u -> Util.getPlatform().openUri(u)
                *///?}
        );
        application.setFilePicker(new FabricFilePicker());
        application.setSaveStore(new FileSystemSaveStore(
                FabricLoader.getInstance().getGameDir().resolve("parkourcalculator"),
                modVersion(),
                //? if >=26.1 {
                SharedConstants.getCurrentVersion().name(),
                //?} else {
                /*SharedConstants.getCurrentVersion().getName(),
                *///?}
                FabricWorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);
        application.getRecorder().setKeyCodes("glfw");
        application.setBlockPicker(new FabricBlockPicker());
        application.setPhysicsVariant(de.legoshi.parkourcalc.fabric.sim.BpkModBridge.physicsVariant());
        application.setupUi();

        //? if >=26.1 {
        ClientTickEvents.END_CLIENT_TICK.register(client -> handleInput());
        ClientTickEvents.START_CLIENT_TICK.register(client -> onStartTick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> onEndTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onWorldChange());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onWorldChange();
            announceBpkMod();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(simulator::onServerStopping);

        LevelRenderEvents.COLLECT_SUBMITS.register(FabricParkourCalculator::onCollectSubmits);
        //?} else {
        /*Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            client.options.load();
        }
        *///?}
    }

    private static boolean wasPlaybackRunning = false;

    private static void announceBpkMod() {
        if (!de.legoshi.parkourcalc.fabric.sim.BpkModBridge.present()) return;
        if (de.legoshi.parkourcalc.fabric.sim.BpkModBridge.broken()) {
            application.pushHudMessage("BPKMod found but its config could not be read: the simulation follows vanilla sprint and strafe rules");
            return;
        }
        de.legoshi.parkourcalc.fabric.sim.BpkModBridge.Rules rules = de.legoshi.parkourcalc.fabric.sim.BpkModBridge.rules();
        application.pushHudMessage("BPKMod: " + rules.describe());
        if (!rules.physicsVariant().equals(application.getPhysicsVariant())) {
            application.pushHudMessage("BPKMod config changed since launch: restart the game so the solver follows it");
        }
    }

    public static String rerunSimulationKeyName() {
        return rerunSimulationKeyBinding == null ? null : rerunSimulationKeyBinding.getTranslatedKeyMessage().getString();
    }

    private static Screen currentScreen(Minecraft client) {
        //? if >=26.1 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
        *///?}
    }

    public static void onStartTick() {
        ReplayLockstep.clientBarrierPreTick();
        manageInputLifecycle();
        application.tickPlayback();
        recordTickStart(Minecraft.getInstance());
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

    public static void onWorldChange() {
        application.onWorldChange();
    }

    //? if <26.1 {
    /*public static void onServerStopping(MinecraftServer server) {
        simulator.onServerStopping(server);
    }
    *///?}

    public static boolean isGhostPlaybackActive() {
        return playbackBridge.ghostEntity() != null && !playbackBridge.replicaMouseFree();
    }

    public static boolean hasReplica() {
        return playbackBridge != null && playbackBridge.ghostEntity() != null;
    }

    public static boolean turnReplica(double accumulatedDX, double accumulatedDY) {
        return playbackBridge != null && playbackBridge.turnReplica(accumulatedDX, accumulatedDY);
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

    public static void onEndTick() {
        // Restore visual yaw after MC physics so render frames don't briefly show
        // the snap value the physics tick used.
        application.postTickPlayback();
        ReplayLockstep.clientBarrierPostTick();
        recordTickEnd(Minecraft.getInstance());
    }

    private static void recordTickStart(Minecraft client) {
        net.minecraft.client.player.LocalPlayer p = client.player;
        if (p == null) return;
        if (playbackBridge.replicaActive()) {
            client.hitResult = null;
            client.options.keyAttack.setDown(false);
            client.options.keyUse.setDown(false);
        }
        net.minecraft.world.entity.player.Player g = playbackBridge.replicaActive() ? playbackBridge.ghostEntity() : p;
        net.minecraft.world.phys.Vec3 pos = g.position();
        net.minecraft.world.phys.Vec3 vel = g.getDeltaMovement();
        application.getAttemptTracker().tickStart(pos.x, pos.y, pos.z, vel.x, vel.z, g.getYRot(), g.onGround(),
                System.nanoTime());
        de.legoshi.parkourcalc.core.record.HumanRecorder r = application.getRecorder();
        if (!r.isRecording()) return;
        net.minecraft.world.phys.Vec3 own = p.position();
        r.tickStart(own.x, own.y, own.z, p.getYRot(), p.getXRot(), p.onGround());
    }

    private static void recordTickEnd(Minecraft client) {
        net.minecraft.client.player.LocalPlayer p = client.player;
        if (p == null) return;
        //? if >=1.21.2 {
        var k = p.input.keyPresses;
        boolean forward = k.forward(), left = k.left(), backward = k.backward(), right = k.right();
        boolean jump = k.jump(), shift = k.shift(), sprint = k.sprint();
        //?} else {
        /*boolean forward = p.input.up, left = p.input.left, backward = p.input.down, right = p.input.right;
        boolean jump = p.input.jumping, shift = p.input.shiftKeyDown, sprint = client.options.keySprint.isDown();
        *///?}
        if (playbackBridge.replicaActive()) {
            application.getAttemptTracker().tickEnd(playbackBridge.getCurrentRow());
        } else {
            application.getAttemptTracker().tickEnd(forward, left, backward, right, jump, shift,
                    sprint || p.isSprinting());
        }
        de.legoshi.parkourcalc.core.record.HumanRecorder r = application.getRecorder();
        if (!r.isRecording()) return;
        r.tickEnd(forward, left, backward, right, jump, shift, sprint, p.isSprinting());
    }

    public static void recordMouse(double dx, double dy) {
        de.legoshi.parkourcalc.core.record.HumanRecorder r = application.getRecorder();
        if (!r.isRecording() || isUiFocused()) return;
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        r.mouse(0L, dx, dy, p.getYRot(), p.getXRot());
    }

    public static void recordFrame() {
        if (isUiFocused()) return;
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        net.minecraft.world.entity.player.Player g = playbackBridge.replicaActive() ? playbackBridge.ghostEntity() : p;
        application.getAttemptTracker().frame(g.getYRot(), System.nanoTime());
        de.legoshi.parkourcalc.core.record.HumanRecorder r = application.getRecorder();
        if (!r.isRecording()) return;
        r.frame(p.getYRot(), p.getXRot());
    }

    public static void recordButton(int button, int action) {
        if (action != InputConstants.PRESS && action != InputConstants.RELEASE) return;
        if (isUiFocused() || currentScreen(Minecraft.getInstance()) != null) return;
        application.getAttemptTracker().mouseButton(button, action == InputConstants.PRESS);
        de.legoshi.parkourcalc.core.record.HumanRecorder r = application.getRecorder();
        if (r.isRecording()) r.button(0L, button, action == InputConstants.PRESS);
    }

    public static boolean swallowsEditChord(int key) {
        return currentScreen(Minecraft.getInstance()) == null && getSettings().undoRedoWithoutUi
                && FabricMinecraftAccess.isEditChordPress(key);
    }

    public static void recordKey(int key, int action) {
        if (action != InputConstants.PRESS && action != InputConstants.RELEASE) return;
        if (isUiFocused() || currentScreen(Minecraft.getInstance()) != null) return;
        de.legoshi.parkourcalc.core.record.HumanRecorder r = application.getRecorder();
        if (r.isRecording()) r.key(0L, key, action == InputConstants.PRESS);
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

    public static void handleInput() {
        Minecraft client = Minecraft.getInstance();
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
        boolean solverGoalFromLookPressed = false;
        while (solverGoalFromLookKeyBinding.consumeClick()) {
            solverGoalFromLookPressed = true;
        }
        boolean rerunSimulationPressed = false;
        while (rerunSimulationKeyBinding.consumeClick()) {
            rerunSimulationPressed = true;
        }
        boolean togglePathPressed = false;
        while (togglePathKeyBinding.consumeClick()) {
            togglePathPressed = true;
        }
        boolean copyTeleportPressed = false;
        while (copyTeleportKeyBinding.consumeClick()) {
            copyTeleportPressed = true;
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
        boolean canDispatch = currentScreen(client) == null && !imguiWantsKeys;
        boolean chordFree = canDispatch && !FabricInput.isCtrlDown();

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
            boolean enter = FabricInput.isShiftDown();
            boolean remove = FabricInput.isCtrlDown();
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
        if (solverGoalFromLookPressed && chordFree) {
            application.setSolverGoalFromLook(FabricInput.isShiftDown());
        }
        if (rerunSimulationPressed && chordFree) {
            application.runSimulation();
        }
        if (togglePathPressed && chordFree) {
            application.toggleShowPath();
        }
        if (copyTeleportPressed && chordFree) {
            application.copyTeleportCommand();
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

    public static boolean dispatchOverlayHotkey(int key) {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return false;
        if (FabricInput.isCtrlDown() && key != boundKey(landingConstraintsKeyBinding)) return false;
        if (key == boundKey(deselectKeyBinding)) {
            application.getSelection().clear();
            return true;
        }
        if (key == boundKey(playbackKeyBinding)) {
            togglePlayback();
            return true;
        }
        if (key == boundKey(landingConstraintsKeyBinding)) {
            boolean enter = FabricInput.isShiftDown();
            boolean remove = FabricInput.isCtrlDown();
            application.onConstraintKey(enter, remove);
            return true;
        }
        if (key == boundKey(removeConstraintsKeyBinding)) {
            application.removeSelectedConstraints();
            return true;
        }
        if (key == boundKey(extendPathAndSolveKeyBinding)) {
            triggerExtendPathAndSolve(client);
            return true;
        }
        if (key == boundKey(applySurfaceStateKeyBinding)) {
            application.applyPathSurfaceState();
            return true;
        }
        if (key == boundKey(solveKeyBinding)) {
            application.solveAngleSolver();
            return true;
        }
        if (key == boundKey(solverStartTickKeyBinding)) {
            application.setSolverStartTickFromSelection();
            return true;
        }
        if (key == boundKey(solverEndTickKeyBinding)) {
            application.setSolverLandingTickFromSelection();
            return true;
        }
        if (key == boundKey(solverGoalFromLookKeyBinding)) {
            application.setSolverGoalFromLook(FabricInput.isShiftDown());
            return true;
        }
        if (key == boundKey(togglePathKeyBinding)) {
            application.toggleShowPath();
            return true;
        }
        if (key == boundKey(copyTeleportKeyBinding)) {
            application.copyTeleportCommand();
            return true;
        }
        return false;
    }

    private static int boundKey(KeyMapping mapping) {
        //? if >=26.1 {
        return KeyMappingHelper.getBoundKeyOf(mapping).getValue();
        //?} else {
        /*return ((KeyMappingAccessor) (Object) mapping).pkc$getKey().getValue();
        *///?}
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

    //? if >=26.1 {
    /**
     * Fires after opaque terrain and before the solid features draw, while vanilla still needs the
     * pass itself, so boxes depth-test only against opaque geometry and stay visible through water,
     * lava, and stained/tinted glass.
     */
    public static void renderWorldOverlayBeforeTranslucent(RenderPass pass) {
        if (pass == null) return;
        if (shouldDrawPath()) {
            worldRenderer.draw(pass);
        }
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        application.tickDrag();
        if (application.isPlaybackRunning()) {
            application.renderPlayback();
        }
        if (!shouldDrawPath()) {
            return;
        }
        worldRenderer.prepare(context);
        worldRenderer.submitGizmo(context);
    }
    //?} else {
    /*public static void onWorldRenderClassic(PoseStack poseStack, Vec3 camPos) {
        application.tickDrag();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        if (application.isPlaybackRunning()) {
            application.renderPlayback();
        }
        if (shouldDrawPath()) {
            worldRenderer.render(poseStack, buffers, camPos);
        }
    }
    *///?}

    private static boolean shouldDrawPath() {
        if (!application.getSettings().showPath) return false;
        return !application.isPlaybackRunning() || application.getSettings().keepBoxesDuringPlayback;
    }

    /** Called from InGameHudMixin to queue the MACRO badge into the GUI state. */
    //? if >=26.1 {
    public static void onHudRender(GuiGraphicsExtractor context) {
    //?} else {
    /*public static void onHudRender(GuiGraphics context) {
    *///?}
        if (!application.isReady()) return;
        String label = application.hudBadgeLabel();
        if (label != null) hudRenderer.render(context, label, application.hudBadgeAlpha());
    }

    /** Called by GameRendererMixin after guiRenderer.render(); ImGui draws above the rasterized HUD. */
    public static void onGuiRendered() {
        if (!application.isReady()) return;
        // Pinned panels are hidden while any blocking screen (pause, inventory, chat) is open.
        boolean allowDetached = currentScreen(Minecraft.getInstance()) == null;
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
        return application.isControlPanelOpen() && currentScreen(Minecraft.getInstance()) == null;
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
