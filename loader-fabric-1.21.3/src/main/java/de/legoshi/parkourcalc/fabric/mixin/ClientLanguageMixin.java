package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricLang;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {

    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void pkc$overlayModTranslation(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String value = FabricLang.get(key);
        if (value != null) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void pkc$overlayModHas(String key, CallbackInfoReturnable<Boolean> cir) {
        if (FabricLang.get(key) != null) {
            cir.setReturnValue(true);
        }
    }
}
