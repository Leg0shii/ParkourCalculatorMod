package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Thin Fabric adapter: feeds GLFW mouse state and camera ray to BoxDragController. */
public final class FabricBoxDragController {

    private final BoxDragController controller;
    private final BooleanSupplier uiFocused;

    public FabricBoxDragController(BoxController boxController, BooleanSupplier uiFocused,
                                   Consumer<Vec3dCore> onPositionChange) {
        this.controller = new BoxDragController(boxController, onPositionChange);
        this.uiFocused = uiFocused;
    }

    public void tick(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        long window = client.getWindow().getHandle();
        boolean mousePressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        controller.tick(originOf(camera), directionOf(camera), mousePressed, uiFocused.getAsBoolean());
    }

    public boolean isDragging() {
        return controller.isDragging();
    }

    public boolean shouldSuppressLeftClick(MinecraftClient client) {
        if (uiFocused.getAsBoolean()) return false;
        if (controller.isDragging()) return true;
        Camera camera = client.gameRenderer.getCamera();
        return controller.isCursorOverStartBox(originOf(camera), directionOf(camera));
    }

    private static Vec3dCore originOf(Camera camera) {
        Vec3d p = camera.getPos();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    private static Vec3dCore directionOf(Camera camera) {
        Vec3d d = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        return new Vec3dCore(d.x, d.y, d.z);
    }
}
