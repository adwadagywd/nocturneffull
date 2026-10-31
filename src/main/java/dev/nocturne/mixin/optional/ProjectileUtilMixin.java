package dev.nocturne.mixin.optional;

import dev.nocturne.module.impl.AntiTrap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

/** Optional: the crosshair entity ray skips everything AntiTrap hides (this is what makes it "have no hitbox"). */
@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
    @ModifyVariable(method = "raycast", at = @At("HEAD"), argsOnly = true)
    private static Predicate<Entity> nocturne$antiTrap(Predicate<Entity> predicate) {
        return AntiTrap.active() ? predicate.and(e -> !AntiTrap.hides(e)) : predicate;
    }
}
