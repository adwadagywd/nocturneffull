package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.AntiTrap;
import dev.nocturne.module.impl.Hitboxes;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional: makes cobwebs invisible to the crosshair ray (client thread only), so the entity ray is no longer
 * stopped by the web the player is standing in. Matches both getOutlineShape overloads by name.
 */
@Mixin(targets = "net.minecraft.block.AbstractBlock$AbstractBlockState")
public class AbstractBlockStateMixin {
    @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
    private void nocturne$webs(CallbackInfoReturnable<VoxelShape> cir) {
        AbstractBlock.AbstractBlockState self = (AbstractBlock.AbstractBlockState) (Object) this;
        if (Hitboxes.ignoreWebs(self) || AntiTrap.ignoresBlock(self)) cir.setReturnValue(VoxelShapes.empty());
    }
}
