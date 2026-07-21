package de.legoshi.parkourcalc.fabric.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("xLast") void pkc$setXLast(double v);

    @Accessor("yLast") void pkc$setYLast(double v);

    @Accessor("zLast") void pkc$setZLast(double v);

    @Accessor("yRotLast") void pkc$setYRotLast(float v);

    @Accessor("xRotLast") void pkc$setXRotLast(float v);

    @Accessor("positionReminder") void pkc$setPositionReminder(int v);
}
