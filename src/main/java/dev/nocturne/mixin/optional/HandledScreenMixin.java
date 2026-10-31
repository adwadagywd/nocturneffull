package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.CustomItemEsp;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional: middle-click (the scroll wheel button) on an item in any container screen adds it to CustomItemESP. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nocturne$middleClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 2 && CustomItemEsp.onMiddleClick(this.focusedSlot)) cir.setReturnValue(true);
    }
}
