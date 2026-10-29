package dev.nocturne.util;

import dev.nocturne.module.impl.Freecam;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public final class Combat {
    private Combat() {}

    public static boolean isWeapon(ItemStack s) {
        return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isOf(Items.MACE);
    }

    /** Vanilla critical-hit conditions (note: sprinting cancels crits). */
    public static boolean canCrit(PlayerEntity p) {
        return !p.isOnGround() && p.fallDistance > 0f && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    /** combat helpers stay off while the camera is detached */
    public static boolean blocked() {
        Freecam f = Freecam.INSTANCE;
        return f != null && f.isEnabled();
    }
}
