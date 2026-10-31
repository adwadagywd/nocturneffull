package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.DropLock;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional: DropLock - inventory clicks that would throw out an item from a locked hotbar slot are swallowed. */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void nocturne$dropLock(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (DropLock.blocksClick(slotId, actionType, player)) ci.cancel();
    }
}
