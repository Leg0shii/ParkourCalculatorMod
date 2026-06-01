package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.forge.core.io.OsFilePicker;
import de.legoshi.parkourcalc.forge.core.io.OsSystemBridge;
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
            application.getYawGizmo());
    private final Forge12HudOverlayRenderer hudRenderer = new Forge12HudOverlayRenderer();
    private final Forge12PlaybackBridge playbackBridge = new Forge12PlaybackBridge();

    private KeyBinding toggleKeyBinding;
    private KeyBinding deselectKeyBinding;
    private KeyBinding playbackKeyBinding;
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
        application.setSystemBridge(new OsSystemBridge());
        application.setSaveStore(new FileSystemSaveStore(
                saveDir,
                modVersion(),
                MinecraftForge.MC_VERSION,
                Forge12WorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);
        application.initSettingsStorage(configPath);
        application.setupUi();
        imguiHost.setEditingYawSupplier(application::isEditingYaw);

        toggleKeyBinding = new KeyBinding("key.parkourcalculator.toggle_ui", Keyboard.KEY_G, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(toggleKeyBinding);
        deselectKeyBinding = new KeyBinding("key.parkourcalculator.deselect_all", Keyboard.KEY_L, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(deselectKeyBinding);
        playbackKeyBinding = new KeyBinding("key.parkourcalculator.toggle_playback", Keyboard.KEY_P, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(playbackKeyBinding);

        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("ParkourCalculator init complete. G toggle, L deselect, P playback.");
    }

    private boolean wasPlaybackRunning = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            manageInputLifecycle();
            application.tickPlayback();
        } else {
            application.postTickPlayback();
        }
    }

    private void manageInputLifecycle() {
        net.minecraft.client.entity.EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        boolean isRunning = application.isPlaybackRunning();
        if (isRunning && !wasPlaybackRunning) {
            playbackBridge.installPlaybackInput(p);
        } else if (!isRunning && wasPlaybackRunning) {
            playbackBridge.restorePlaybackInput(p);
        }
        wasPlaybackRunning = isRunning;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!application.isPlaybackRunning()) return;
        net.minecraft.entity.player.EntityPlayer p = event.player;
        if (p != Minecraft.getMinecraft().player) return;
        // Sim runs noClip so its tick 0 sees onGround=true; the real player's warmup
        // moveEntity can flip it false when startPosition isn't directly on a block top.
        if (application.getPlayback().currentTick() == 0) {
            p.onGround = true;
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
        if (!application.isPlaybackRunning()) return;
        hudRenderer.render();
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
        if (mc.currentScreen == null) {
            if (toggled) {
                openOverlay(mc);
            }
            if (deselectPressed) {
                application.getSelection().clear();
            }
            if (playbackPressed) {
                togglePlayback();
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
                () -> application.setControlPanelOpen(false)
        ));
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        application.tickDrag();
        if (application.isPlaybackRunning()) return;
        worldRenderer.render(event.getPartialTicks());
    }

    // Mirror in Forge8ParkourCalculator; differs only in MouseEvent.getButton() vs button.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseEvent(MouseEvent event) {
        if (!event.isButtonstate()) return;
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
