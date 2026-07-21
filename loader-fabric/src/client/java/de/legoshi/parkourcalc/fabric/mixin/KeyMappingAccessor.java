package de.legoshi.parkourcalc.fabric.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount") int pkc$getClickCount();

    @Accessor("clickCount") void pkc$setClickCount(int timesPressed);
}
