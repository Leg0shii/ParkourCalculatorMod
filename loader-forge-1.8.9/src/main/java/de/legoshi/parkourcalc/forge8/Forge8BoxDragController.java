package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Forge 1.8.9 adapter: feeds LWJGL2 mouse state and view-entity ray to BoxDragController. */
public final class Forge8BoxDragController {

    private final BoxDragController controller;
    private final BooleanSupplier uiFocused;

    public Forge8BoxDragController(BoxController boxController, BooleanSupplier uiFocused,
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
        Vec3 p = view.getPositionEyes(1.0F);
        return new Vec3dCore(p.xCoord, p.yCoord, p.zCoord);
    }

    private static Vec3dCore directionOf(Entity view) {
        Vec3 d = view.getLook(1.0F);
        return new Vec3dCore(d.xCoord, d.yCoord, d.zCoord);
    }
}
