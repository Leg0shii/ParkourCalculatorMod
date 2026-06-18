package de.legoshi.parkourcalc.fabric.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {

    @Accessor("timesPressed") int pkc$getTimesPressed();

    @Accessor("timesPressed") void pkc$setTimesPressed(int timesPressed);
}
