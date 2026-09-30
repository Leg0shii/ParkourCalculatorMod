package de.legoshi.parkourcalc.fabric.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("accumulatedDX")
    double pkc$accumulatedDX();

    @Accessor("accumulatedDY")
    double pkc$accumulatedDY();

    @Accessor("accumulatedDX")
    void pkc$setAccumulatedDX(double value);

    @Accessor("accumulatedDY")
    void pkc$setAccumulatedDY(double value);
}
