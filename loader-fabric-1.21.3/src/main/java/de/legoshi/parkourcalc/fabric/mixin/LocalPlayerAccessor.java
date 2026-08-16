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

    @Accessor("xLast") double pkc$getXLast();

    @Accessor("yLast") double pkc$getYLast();

    @Accessor("zLast") double pkc$getZLast();

    @Accessor("positionReminder") int pkc$getPositionReminder();

    @Accessor("lastOnGround") boolean pkc$getLastOnGround();

    @Accessor("lastOnGround") void pkc$setLastOnGround(boolean v);

    @Accessor("lastHorizontalCollision") boolean pkc$getLastHorizontalCollision();

    @Accessor("lastHorizontalCollision") void pkc$setLastHorizontalCollision(boolean v);
}
