package de.legoshi;

import de.legoshi.ui.TickInputScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParkourCalculatorClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("ParkourCalc");

    private final MovementSimulator movementSimulator = new MovementSimulator();
    private final BoxController boxController = new BoxController(movementSimulator);
    private final TickInputScreen tickInputScreen = new TickInputScreen(boxController, movementSimulator);

    private static final KeyBinding UI_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key.parkourcalc.ui",
                    GLFW.GLFW_KEY_L,
                    "category.parkourcalc"
            )
    );

    @Override
    public void onInitializeClient() {
        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> boxController.dragFrame(ctx.camera()));
        WorldRenderEvents.LAST.register(ctx -> boxController.renderAll(ctx.matrixStack(), ctx.consumers()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (UI_KEY.wasPressed()) {
                client.setScreen(tickInputScreen);
            }
        });

        ClientPreAttackCallback.EVENT.register(this::onClick);
    }

    private boolean onClick(MinecraftClient client, PlayerEntity player, int clicks) {
        if (clicks != 0) return false;
        Camera cam = client.gameRenderer.getCamera();
        Vec3d start = cam.getPos();
        Vec3d dir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d end = start.add(dir.multiply(128));

        return boxController.pick(start, end).map(hit -> {
            if (hit.equals(boxController.getFirst())) {
                boxController.startDrag(hit);
            }

            return true;
        }).orElse(false);
    }
}