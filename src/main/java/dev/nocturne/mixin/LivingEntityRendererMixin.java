package dev.nocturne.mixin;

import dev.nocturne.module.impl.Nametags;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    // Hides the vanilla nametag when our Nametags module replaces it.
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void nocturne$hideLabel(LivingEntity entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
        if (Nametags.hidesVanilla(entity)) cir.setReturnValue(false);
    }
}
