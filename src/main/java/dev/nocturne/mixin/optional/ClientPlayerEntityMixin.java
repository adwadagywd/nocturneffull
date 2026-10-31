package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.AntiTrap;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional: AntiTrap's "Block sign editor" - the sign text screen simply never opens. */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "openEditSignScreen", at = @At("HEAD"), cancellable = true)
    private void nocturne$noSignGui(SignBlockEntity sign, boolean front, CallbackInfo ci) {
        if (AntiTrap.blocksSignEditor()) ci.cancel();
    }
}
