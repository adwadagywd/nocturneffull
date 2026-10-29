package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.Crosshair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional: hides the vanilla crosshair while the custom one is on. */
@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud")
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nocturne$crosshair(CallbackInfo ci) {
        if (Crosshair.hidesVanilla()) ci.cancel();
    }
}
