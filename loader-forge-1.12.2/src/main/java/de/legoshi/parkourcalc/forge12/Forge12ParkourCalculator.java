package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.forge.common.Lwjgl2ImGuiHost;
import de.legoshi.parkourcalc.forge12.render.Forge12WorldOverlayRenderer;
import de.legoshi.parkourcalc.forge12.sim.Forge12Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;

@Mod(modid = Forge12ParkourCalculator.MODID, version = Forge12ParkourCalculator.VERSION, clientSideOnly = true, acceptableRemoteVersions = "*")
public class Forge12ParkourCalculator {

    public static final String MODID = "parkourcalculator";
    public static final String VERSION = "1.0.0";

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final InputData inputData = new InputData();
    private final OverlayManager overlayManager = new OverlayManager();
    private final Lwjgl2ImGuiHost imguiHost = new Lwjgl2ImGuiHost(overlayManager);
    private final Simulator simulator = new Forge12Simulator();
    private final SimulationRunner runner = new SimulationRunner(simulator);
    private final BoxController boxController = new BoxController();
    private final Forge12WorldOverlayRenderer worldRenderer = new Forge12WorldOverlayRenderer(boxController);

    private KeyBinding toggleKeyBinding;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        InputOverlay inputOverlay = new InputOverlay(inputData, this::runSimulation, this::setStartToPlayer);
        overlayManager.register("TAS Inputs", inputOverlay);

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
        overlayManager.setControlPanelOpen(true);
        mc.displayGuiScreen(new ParkourCalcGuiScreen(
                toggleKeyBinding.getKeyCode(),
                imguiHost,
                () -> overlayManager.setControlPanelOpen(false)
        ));
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        worldRenderer.render(event.getPartialTicks());
    }

    private void runSimulation() {
        try {
            List<Vec3dCore> path = runner.simulate(inputData);
            boxController.clearAll();
            for (Vec3dCore p : path) {
                boxController.add(p);
            }
            if (!path.isEmpty()) {
                Vec3dCore last = path.get(path.size() - 1);
                LOG.info("Simulated {} ticks; final position ({}, {}, {})",
                        path.size() - 1, last.x, last.y, last.z);
            }
        } catch (IllegalStateException ignored) {
            // Player/world not loaded yet; nothing to simulate.
        }
    }

    private void setStartToPlayer() {
        runner.setStartFromPlayer();
        runSimulation();
    }
}
