package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.forge.core.io.OsFilePicker;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2ImGuiHost;
import de.legoshi.parkourcalc.forge8.render.Forge8HudOverlayRenderer;
import de.legoshi.parkourcalc.forge8.render.Forge8WorldOverlayRenderer;
import de.legoshi.parkourcalc.forge8.sim.Forge8Simulator;
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

@Mod(modid = Forge8ParkourCalculator.MODID, clientSideOnly = true, acceptableRemoteVersions = "*")
@SuppressWarnings("DuplicatedCode")
public class Forge8ParkourCalculator {

    public static final String MODID = "parkourcalculator";

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final Application application = new Application(
            new Forge8Simulator(),
            new Forge8MinecraftAccess()
    );
    private final Lwjgl2ImGuiHost imguiHost = new Lwjgl2ImGuiHost(
            application.getOverlayManager(),
            application.getSettings(),
            application::resolveAutoScaleIfNeeded,
            () -> Minecraft.getMinecraft().currentScreen instanceof ParkourCalcGuiScreen
    );
    private final Forge8WorldOverlayRenderer worldRenderer = new Forge8WorldOverlayRenderer(
            application.getBoxController(),
            application.getSettings(),
            application.getSelection(),
            application.getYawGizmo()
    );
    private final Forge8HudOverlayRenderer hudRenderer = new Forge8HudOverlayRenderer();
    private final Forge8PlaybackBridge playbackBridge = new Forge8PlaybackBridge();

    private KeyBinding toggleKeyBinding;
    private KeyBinding deselectKeyBinding;
    private KeyBinding playbackKeyBinding;
    private Path configPath;
    private Path saveDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configPath = new File(event.getModConfigurationDirectory(), "parkourcalculator.json").toPath();
        saveDir = new File(Minecraft.getMinecraft().mcDataDir, "parkourcalculator").toPath();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        application.setModVersion(modVersion());
        application.setFilePicker(new OsFilePicker());
        application.setSaveStore(new FileSystemSaveStore(
                saveDir,
                modVersion(),
                MinecraftForge.MC_VERSION,
                Forge8WorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);
        application.initSettingsStorage(configPath);
        application.setupUi();
        imguiHost.setEditingYawSupplier(application::isEditingYaw);
        imguiHost.setAllowDetachedSupplier(() -> Minecraft.getMinecraft().currentScreen == null);

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
        net.minecraft.client.entity.EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
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
        if (p != Minecraft.getMinecraft().thePlayer) return;
        if (application.getPlayback().currentTick() == 0) {
            p.onGround = application.getPlayback().firstTickOnGround();
            p.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!application.isPlaybackRunning()) return;
        if (event.source != net.minecraft.util.DamageSource.fall) return;
        if (!(event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer)) return;
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
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;
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
        if (application.isPlaybackRunning() && !application.getSettings().keepBoxesDuringPlayback) return;
        worldRenderer.render(event.partialTicks);
    }

    // Mirror in Forge12ParkourCalculator; differs only in MouseEvent.button vs getButton().
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseEvent(MouseEvent event) {
        if (!event.buttonstate) return;
        if (event.button == 0 && application.shouldSuppressLeftClick()) {
            event.setCanceled(true);
        }
        if (event.button == 1 && application.shouldSuppressRightClick()) {
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
