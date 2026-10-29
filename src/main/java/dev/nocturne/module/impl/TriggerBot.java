package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.TargetSettings;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Attacks when your crosshair is on a target.
 *  Normal     - hits as soon as the weapon cooldown is ready.
 *  Smart      - on the ground it hits normally; in the air (jumping) it WAITS for the fall so the hit is a critical.
 *  Only crits - never hits unless it is a critical.
 */
public class TriggerBot extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Smart", "Normal", "Smart", "Only crits"));
    private final NumSetting smartWait = add(new NumSetting("Smart wait (ticks)", 12, 2, 30, 1));
    private final NumSetting minCooldown = add(new NumSetting("Min cooldown", 0.92, 0.5, 1.0, 0.01));
    private final NumSetting range = add(new NumSetting("Range", 3.0, 2.0, 6.0, 0.1));
    private final BoolSetting weaponOnly = add(new BoolSetting("Weapon only", true));
    private final BoolSetting holdClick = add(new BoolSetting("Only while clicking", false));
    private final BoolSetting pauseUsing = add(new BoolSetting("Not while using item", true));
    private final TargetSettings targets = new TargetSettings(this, true, true, false, false, false);

    private int waited;

    public TriggerBot() {
        super("TriggerBot", "Attacks when your crosshair is on a target - Smart mode waits for crits in the air", Category.COMBAT);
        smartWait.showIf(() -> mode.is("Smart"));
    }

    @Override protected void onEnable() { waited = 0; }

    @Override
    public void onTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null || Combat.blocked()) return;
        boolean airborne = !p.isOnGround() && !p.isTouchingWater() && !p.isClimbing() && !p.hasVehicle();
        if (!airborne) waited = 0;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;

        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isAlive() || !targets.testCombat(e)) return;
        if (p.distanceTo(e) > range.get()) return;
        if (weaponOnly.get() && !Combat.isWeapon(p.getMainHandStack())) return;
        if (pauseUsing.get() && p.isUsingItem()) return;
        if (holdClick.get() && !mc.options.attackKey.isPressed()) return;
        if (p.getAttackCooldownProgress(0.5f) < minCooldown.get()) return;

        if (mode.is("Only crits")) {
            if (!Combat.canCrit(p)) return;
        } else if (mode.is("Smart") && airborne) {
            if (Combat.canCrit(p)) {
                // falling and not sprinting: this hit is a crit - go
            } else if (p.isSprinting()) {
                // a sprinting hit can never be a crit in vanilla, so do not wait for one
            } else if (++waited <= smartWait.getInt()) {
                return; // still rising: wait for the fall
            }
        }

        mc.interactionManager.attackEntity(p, e);
        p.swingHand(Hand.MAIN_HAND);
        waited = 0;
    }
}
