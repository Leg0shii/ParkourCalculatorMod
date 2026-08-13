package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.forge.core.io.OsFilePicker;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2ImGuiHost;
import de.legoshi.parkourcalc.forge12.render.Forge12HudOverlayRenderer;
import de.legoshi.parkourcalc.forge12.render.Forge12WorldOverlayRenderer;
import de.legoshi.parkourcalc.forge12.sim.Forge12Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.nio.file.Path;

@Mod(modid = Forge12ParkourCalculator.MODID, clientSideOnly = true, acceptableRemoteVersions = "*")
@SuppressWarnings("DuplicatedCode")
public class Forge12ParkourCalculator {

    public static final String MODID = "parkourcalculator";

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final Application application = new Application(
            new Forge12Simulator(),
            new Forge12MinecraftAccess()
    );
    private final Lwjgl2ImGuiHost imguiHost = new Lwjgl2ImGuiHost(
            application.getOverlayManager(),
            application.getSettings(),
            application::resolveAutoScaleIfNeeded,
            () -> Minecraft.getMinecraft().currentScreen instanceof ParkourCalcGuiScreen);
    private final Forge12WorldOverlayRenderer worldRenderer = new Forge12WorldOverlayRenderer(
            application.getBoxController(),
            application.getSettings(),
            application.getSelection(),
            application.getYawGizmo(),
            application::getAngleSolverState);
    private final Forge12HudOverlayRenderer hudRenderer = new Forge12HudOverlayRenderer();
    private final Forge12PlaybackBridge playbackBridge = new Forge12PlaybackBridge();

    private KeyBinding toggleKeyBinding;
    private KeyBinding deselectKeyBinding;
    private KeyBinding playbackKeyBinding;
    private KeyBinding landingConstraintsKeyBinding;
    private KeyBinding removeConstraintsKeyBinding;
    private KeyBinding applySurfaceStateKeyBinding;
    private KeyBinding solveKeyBinding;
    private KeyBinding solverStartTickKeyBinding;
    private KeyBinding solverEndTickKeyBinding;
    private KeyBinding captureMomentumBlockKeyBinding;
    private KeyBinding captureCollisionBlockKeyBinding;
    private KeyBinding captureLandBlockKeyBinding;
    private KeyBinding clearBlocksKeyBinding;
    private boolean blockCaptureEnabled;
    private Path configPath;
    private Path saveDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configPath = new File(event.getModConfigurationDirectory(), "parkourcalculator.json").toPath();
        saveDir = new File(Minecraft.getMinecraft().gameDir, "parkourcalculator").toPath();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        application.setModVersion(modVersion());
        application.setFilePicker(new OsFilePicker());
        application.setSaveStore(new FileSystemSaveStore(
                saveDir,
                modVersion(),
                MinecraftForge.MC_VERSION,
                Forge12WorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);
        application.setBlockPicker(new Forge12BlockPicker());
        application.initSettingsStorage(configPath);
        blockCaptureEnabled = application.getSettings().experimentalBlockCapture;
        application.setupUi();
        imguiHost.setEditingYawSupplier(application::isEditingYaw);
        imguiHost.setAllowDetachedSupplier(() -> Minecraft.getMinecraft().currentScreen == null);

        toggleKeyBinding = new KeyBinding("key.parkourcalculator.toggle_ui", Keyboard.KEY_G, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(toggleKeyBinding);
        deselectKeyBinding = new KeyBinding("key.parkourcalculator.deselect_all", Keyboard.KEY_L, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(deselectKeyBinding);
        playbackKeyBinding = new KeyBinding("key.parkourcalculator.toggle_playback", Keyboard.KEY_P, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(playbackKeyBinding);
        landingConstraintsKeyBinding = new KeyBinding("key.parkourcalculator.add_landing_constraints", Keyboard.KEY_B, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(landingConstraintsKeyBinding);
        removeConstraintsKeyBinding = new KeyBinding("key.parkourcalculator.remove_selected_constraints", Keyboard.KEY_X, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(removeConstraintsKeyBinding);
        applySurfaceStateKeyBinding = new KeyBinding("key.parkourcalculator.apply_surface_state", Keyboard.KEY_H, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(applySurfaceStateKeyBinding);
        solveKeyBinding = new KeyBinding("key.parkourcalculator.solve", Keyboard.KEY_V, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(solveKeyBinding);
        solverStartTickKeyBinding = new KeyBinding("key.parkourcalculator.set_solver_start", Keyboard.KEY_I, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(solverStartTickKeyBinding);
        solverEndTickKeyBinding = new KeyBinding("key.parkourcalculator.set_solver_end", Keyboard.KEY_O, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(solverEndTickKeyBinding);
        if (blockCaptureEnabled) {
            captureMomentumBlockKeyBinding = new KeyBinding("key.parkourcalculator.capture_momentum_block", Keyboard.KEY_M, "key.categories.parkourcalculator");
            ClientRegistry.registerKeyBinding(captureMomentumBlockKeyBinding);
            captureCollisionBlockKeyBinding = new KeyBinding("key.parkourcalculator.capture_collision_block", Keyboard.KEY_N, "key.categories.parkourcalculator");
            ClientRegistry.registerKeyBinding(captureCollisionBlockKeyBinding);
            captureLandBlockKeyBinding = new KeyBinding("key.parkourcalculator.capture_land_block", Keyboard.KEY_K, "key.categories.parkourcalculator");
            ClientRegistry.registerKeyBinding(captureLandBlockKeyBinding);
            clearBlocksKeyBinding = new KeyBinding("key.parkourcalculator.clear_blocks", Keyboard.KEY_DELETE, "key.categories.parkourcalculator");
            ClientRegistry.registerKeyBinding(clearBlocksKeyBinding);
        }

        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("ParkourCalculator init complete. G toggle, L deselect, P playback, B landing constraints, X remove constraints,"
                + " H surface state, V solve, I/O solver start/goal tick."
                + (blockCaptureEnabled ? " Block capture ON: M/N/K capture momentum/collision/land block, Delete clear blocks." : ""));
    }

    private boolean wasPlaybackRunning = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            manageInputLifecycle();
            application.tickPlayback();
        } else {
            application.postTickPlayback();
            playbackBridge.syncFrozenPlayerToServer();
        }
    }

    private void manageInputLifecycle() {
        net.minecraft.client.entity.EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) {
            if (wasPlaybackRunning) {
                application.getPlayback().stop();
                playbackBridge.resetInputOverride();
                playbackBridge.endGhostPlayback();
                wasPlaybackRunning = false;
            }
            return;
        }
        boolean isRunning = application.isPlaybackRunning();
        if (isRunning && !wasPlaybackRunning) {
            playbackBridge.installPlaybackInput(p);
        } else if (!isRunning && wasPlaybackRunning) {
            playbackBridge.restorePlaybackInput(p);
            playbackBridge.endGhostPlayback();
        }
        wasPlaybackRunning = isRunning;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!application.isPlaybackRunning()) return;
        net.minecraft.entity.player.EntityPlayer p = event.player;
        net.minecraft.entity.player.EntityPlayer target = playbackBridge.ghostEntity();
        if (target == null) target = Minecraft.getMinecraft().player;
        if (p != target) return;
        if (application.getPlayback().currentTick() == 0) {
            p.onGround = application.getPlayback().firstTickOnGround();
            p.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!application.isPlaybackRunning()) return;
        if (event.getSource() != net.minecraft.util.DamageSource.FALL) return;
        if (!(event.getEntityLiving() instanceof net.minecraft.entity.player.EntityPlayer)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerPlayerTickEnd(TickEvent.PlayerTickEvent event) {
        // Must run AFTER EntityPlayer.onUpdate resets noClip=isSpectator() and BEFORE
        // networkSystem.networkTick where the in-memory channel dispatches C03 inline.
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof net.minecraft.entity.player.EntityPlayerMP)) return;
        if (!application.isPlaybackRunning()) return;
        if (!playbackBridge.isSingleplayer()) return;
        event.player.noClip = true;
    }

    private void togglePlayback() {
        PlaybackController pc = application.getPlayback();
        if (pc.isRunning()) {
            pc.stop();
        } else if (pc.canStart()) {
            pc.start();
        }
    }

    @SubscribeEvent
    public void onHudRender(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;
        if (application.isPlaybackRunning()) {
            hudRenderer.render();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        application.renderPlayback();

        // Drain queued presses; only act when no MC screen owns input. Close path and
        // in-UI handling live in the GuiScreen.
        boolean toggled = false;
        while (toggleKeyBinding.isPressed()) {
            toggled = true;
        }
        boolean deselectPressed = false;
        while (deselectKeyBinding.isPressed()) {
            deselectPressed = true;
        }
        boolean playbackPressed = false;
        while (playbackKeyBinding.isPressed()) {
            playbackPressed = true;
        }
        boolean landingConstraintsPressed = false;
        while (landingConstraintsKeyBinding.isPressed()) {
            landingConstraintsPressed = true;
        }
        boolean removeConstraintsPressed = false;
        while (removeConstraintsKeyBinding.isPressed()) {
            removeConstraintsPressed = true;
        }
        boolean applySurfaceStatePressed = false;
        while (applySurfaceStateKeyBinding.isPressed()) {
            applySurfaceStatePressed = true;
        }
        boolean solvePressed = false;
        while (solveKeyBinding.isPressed()) {
            solvePressed = true;
        }
        boolean solverStartPressed = false;
        while (solverStartTickKeyBinding.isPressed()) {
            solverStartPressed = true;
        }
        boolean solverEndPressed = false;
        while (solverEndTickKeyBinding.isPressed()) {
            solverEndPressed = true;
        }
        boolean captureMomentum = false;
        boolean captureCollision = false;
        boolean captureLand = false;
        boolean clearBlocks = false;
        if (blockCaptureEnabled) {
            while (captureMomentumBlockKeyBinding.isPressed()) {
                captureMomentum = true;
            }
            while (captureCollisionBlockKeyBinding.isPressed()) {
                captureCollision = true;
            }
            while (captureLandBlockKeyBinding.isPressed()) {
                captureLand = true;
            }
            while (clearBlocksKeyBinding.isPressed()) {
                clearBlocks = true;
            }
        }
        if (mc.currentScreen == null) {
            boolean chordFree = !isCtrlHeld();
            if (toggled && chordFree) {
                openOverlay(mc);
            }
            if (deselectPressed && chordFree) {
                application.getSelection().clear();
            }
            if (playbackPressed && chordFree) {
                togglePlayback();
            }
            if (landingConstraintsPressed) {
                boolean enter = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                boolean remove = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                application.onConstraintKey(enter, remove);
            }
            if (removeConstraintsPressed && chordFree) {
                application.removeSelectedConstraints();
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
        if (application.isReady()) {
            imguiHost.renderFrame(mc.displayWidth, mc.displayHeight);
        }
    }

    private void openOverlay(Minecraft mc) {
        application.setControlPanelOpen(true);
        mc.displayGuiScreen(new ParkourCalcGuiScreen(
                toggleKeyBinding.getKeyCode(),
                deselectKeyBinding.getKeyCode(),
                playbackKeyBinding.getKeyCode(),
                imguiHost,
                application.getSelection(),
                application,
                this::togglePlayback,
                this::dispatchHotkey,
                () -> application.setControlPanelOpen(false)
        ));
    }

    private static boolean isCtrlHeld() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private boolean dispatchHotkey(int keyCode) {
        if (isCtrlHeld() && keyCode != landingConstraintsKeyBinding.getKeyCode()) return false;
        if (keyCode == landingConstraintsKeyBinding.getKeyCode()) {
            boolean enter = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            boolean remove = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            application.onConstraintKey(enter, remove);
            return true;
        }
        if (keyCode == removeConstraintsKeyBinding.getKeyCode()) {
            application.removeSelectedConstraints();
            return true;
        }
        if (keyCode == applySurfaceStateKeyBinding.getKeyCode()) {
            application.applyPathSurfaceState();
            return true;
        }
        if (keyCode == solveKeyBinding.getKeyCode()) {
            application.solveAngleSolver();
            return true;
        }
        if (keyCode == solverStartTickKeyBinding.getKeyCode()) {
            application.setSolverStartTickFromSelection();
            return true;
        }
        if (keyCode == solverEndTickKeyBinding.getKeyCode()) {
            application.setSolverLandingTickFromSelection();
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        application.tickDrag();
        if (application.isPlaybackRunning() && !application.getSettings().keepBoxesDuringPlayback) return;
        worldRenderer.render(event.getPartialTicks());
    }

    // Mirror in Forge8ParkourCalculator; differs only in MouseEvent.getButton() vs button.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseEvent(MouseEvent event) {
        if (!event.isButtonstate()) return;
        if (playbackBridge.ghostEntity() != null && event.getButton() >= 0) {
            event.setCanceled(true);
            return;
        }
        if (event.getButton() == 0 && application.shouldSuppressLeftClick()) {
            event.setCanceled(true);
        }
        if (event.getButton() == 1 && application.shouldSuppressRightClick()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        application.onWorldChange();
    }

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        application.onWorldChange();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.isCancelable()) return;
        if (application.shouldSuppressLeftClick() || application.shouldSuppressRightClick()) {
            event.setCanceled(true);
        }
    }

    private static String modVersion() {
        ModContainer container = Loader.instance().getIndexedModList().get(MODID);
        return container != null ? container.getVersion() : "unknown";
    }
}
