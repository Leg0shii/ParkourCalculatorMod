package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
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

@Mod(modid = Forge8ParkourCalculator.MODID, version = Forge8ParkourCalculator.VERSION, clientSideOnly = true, acceptableRemoteVersions = "*")
public class Forge8ParkourCalculator {

    public static final String MODID = "parkourcalculator";
    public static final String VERSION = "1.0.0";

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final Application application = new Application(
            new Forge8Simulator(),
            new Forge8MinecraftAccess()
    );
    private final Lwjgl2ImGuiHost imguiHost = new Lwjgl2ImGuiHost(application.getOverlayManager(), application.getSettings());
    private final Forge8WorldOverlayRenderer worldRenderer = new Forge8WorldOverlayRenderer(
            application.getBoxController(),
            application.getSettings(),
            application.getSelection(),
            application.getYawGizmo());
    private final Forge8HudOverlayRenderer hudRenderer = new Forge8HudOverlayRenderer();
    private final Forge8PlaybackBridge playbackBridge = new Forge8PlaybackBridge();

    private KeyBinding toggleKeyBinding;
    private Path configPath;
    private Path saveDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configPath = new File(event.getModConfigurationDirectory(), "parkourcalculator.json").toPath();
        saveDir = new File(Minecraft.getMinecraft().mcDataDir, "parkourcalculator").toPath();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        application.registerInputOverlay();
        application.registerSettingsOverlay();
        application.registerFileBrowserOverlay();
        application.registerTickInfoOverlay();
        application.initSettingsStorage(configPath);
        application.setSaveStore(new FileSystemSaveStore(
                saveDir,
                modVersion(),
                MinecraftForge.MC_VERSION,
                Forge8WorldDescriptors::current
        ));
        application.setPlaybackBridge(playbackBridge);

        toggleKeyBinding = new KeyBinding("key.parkourcalculator.toggle_ui", Keyboard.KEY_K, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(toggleKeyBinding);

        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("ParkourCalculator init complete; K registered as toggle.");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        application.tickPlayback();
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

        // Drain queued presses; only open when no MC screen owns input. Close path lives in the GuiScreen.
        boolean toggled = false;
        while (toggleKeyBinding.isPressed()) {
            toggled = true;
        }
        if (toggled && mc.currentScreen == null) {
            openOverlay(mc);
        }
        if (application.isReady()) {
            imguiHost.renderFrame(mc.displayWidth, mc.displayHeight);
        }
    }

    private void openOverlay(Minecraft mc) {
        application.setControlPanelOpen(true);
        mc.displayGuiScreen(new ParkourCalcGuiScreen(
                toggleKeyBinding.getKeyCode(),
                imguiHost,
                application.getSelection(),
                () -> application.setControlPanelOpen(false)
        ));
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        application.tickDrag();
        if (application.isPlaybackRunning()) return;
        worldRenderer.render(event.partialTicks);
    }

    // Mirror in Forge12ParkourCalculator; differs only in MouseEvent.button vs getButton().
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseEvent(MouseEvent event) {
        if (event.button == 0 && application.shouldSuppressLeftClick()) {
            event.setCanceled(true);
        }
        if (event.button == 1 && application.shouldSuppressRightClick()) {
            event.setCanceled(true);
        }
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
        return container != null ? container.getVersion() : VERSION;
    }
}
