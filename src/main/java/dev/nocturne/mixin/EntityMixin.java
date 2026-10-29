package dev.nocturne.mixin;

import dev.nocturne.module.impl.Hitboxes;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    // Crosshair/attack raycast uses boundingBox.expand(getTargetingMargin()), so this grows the *hit* area only.
    @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true)
    private void nocturne$margin(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.getWorld().isClient() || Hitboxes.INSTANCE == null) return;
        float m = Hitboxes.INSTANCE.margin(self);
        if (m > 0f) cir.setReturnValue(m);
    }
}
