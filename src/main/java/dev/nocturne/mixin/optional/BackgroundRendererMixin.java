package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.WorldFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional: replaces the Fog object the game computed. Written against class/method names only
 * (no compile-time Fog type) - if it ever fails to apply, only WorldFog stops working.
 */
@Mixin(targets = "net.minecraft.client.render.BackgroundRenderer")
public class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void nocturne$fog(CallbackInfoReturnable<Object> cir) {
        Object fog = cir.getReturnValue();
        if (fog != null) cir.setReturnValue(WorldFog.apply(fog));
    }
}
