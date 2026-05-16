package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2ImGuiHost;
import de.legoshi.parkourcalc.forge12.render.Forge12WorldOverlayRenderer;
import de.legoshi.parkourcalc.forge12.sim.Forge12Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

@Mod(modid = Forge12ParkourCalculator.MODID, version = Forge12ParkourCalculator.VERSION, clientSideOnly = true, acceptableRemoteVersions = "*")
public class Forge12ParkourCalculator {

    public static final String MODID = "parkourcalculator";
    public static final String VERSION = "1.0.0";

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final Application application = new Application(
            new Forge12Simulator(),
            new Forge12MinecraftAccess()
    );
    private final Lwjgl2ImGuiHost imguiHost = new Lwjgl2ImGuiHost(application.getOverlayManager());
    private final Forge12WorldOverlayRenderer worldRenderer = new Forge12WorldOverlayRenderer(application.getBoxController());

    private KeyBinding toggleKeyBinding;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        application.registerInputOverlay();

        toggleKeyBinding = new KeyBinding("key.parkourcalculator.toggle_ui", Keyboard.KEY_K, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(toggleKeyBinding);

        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("ParkourCalculator init complete; K registered as toggle.");
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
        imguiHost.renderFrame(mc.displayWidth, mc.displayHeight);
    }

    private void openOverlay(Minecraft mc) {
        application.setControlPanelOpen(true);
        mc.displayGuiScreen(new ParkourCalcGuiScreen(
                toggleKeyBinding.getKeyCode(),
                imguiHost,
                () -> application.setControlPanelOpen(false)
        ));
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        application.tickDrag();
        worldRenderer.render(event.getPartialTicks());
    }

    // Mirror in Forge8ParkourCalculator; differs only in MouseEvent.getButton() vs button.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseEvent(MouseEvent event) {
        if (event.getButton() == 0 && application.shouldSuppressLeftClick()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (application.shouldSuppressLeftClick()) {
            event.setCanceled(true);
        }
    }
}
