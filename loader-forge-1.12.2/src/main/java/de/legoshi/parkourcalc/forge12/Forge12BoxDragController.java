package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Forge 1.12.2 adapter: feeds LWJGL2 mouse state and view-entity ray to BoxDragController. */
public final class Forge12BoxDragController {

    private final BoxDragController controller;
    private final BooleanSupplier uiFocused;

    public Forge12BoxDragController(BoxController boxController, BooleanSupplier uiFocused,
                                    Consumer<Vec3dCore> onPositionChange) {
        this.controller = new BoxDragController(boxController, onPositionChange);
        this.uiFocused = uiFocused;
    }

    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        boolean mousePressed = Mouse.isButtonDown(0);
        controller.tick(originOf(view), directionOf(view), mousePressed, uiFocused.getAsBoolean());
    }

    public boolean shouldSuppressLeftClick() {
        if (uiFocused.getAsBoolean()) return false;
        if (controller.isDragging()) return true;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return false;
        return controller.isCursorOverStartBox(originOf(view), directionOf(view));
    }

    private static Vec3dCore originOf(Entity view) {
        Vec3d p = view.getPositionEyes(1.0F);
        return new Vec3dCore(p.x, p.y, p.z);
    }

    private static Vec3dCore directionOf(Entity view) {
        Vec3d d = view.getLook(1.0F);
        return new Vec3dCore(d.x, d.y, d.z);
    }
}
