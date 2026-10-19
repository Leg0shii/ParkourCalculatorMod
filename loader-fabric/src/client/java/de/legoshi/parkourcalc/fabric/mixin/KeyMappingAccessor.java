package de.legoshi.parkourcalc.fabric.mixin;

//? if <26.1
//import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if <26.1
//import java.util.Map;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount") int pkc$getClickCount();

    @Accessor("clickCount") void pkc$setClickCount(int timesPressed);

    //? if <26.1 {
    /*@Accessor("key") InputConstants.Key pkc$getKey();

    @Accessor("CATEGORY_SORT_ORDER")
    static Map<String, Integer> pkc$getCategorySortOrder() {
        throw new AssertionError();
    }
    *///?}
}
