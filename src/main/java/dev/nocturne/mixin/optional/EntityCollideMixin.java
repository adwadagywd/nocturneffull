package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.AntiTrap;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional: you walk straight through entities AntiTrap hides (client side). */
@Mixin(Entity.class)
public class EntityCollideMixin {
    @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
    private void nocturne$antiTrap(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (AntiTrap.hides(other)) cir.setReturnValue(false);
    }
}
