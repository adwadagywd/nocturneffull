package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import dev.nocturne.util.RenderUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    private final NumSetting strength = add(new NumSetting("Strength", 0.6, 0.05, 1.0, 0.05));
    private final NumSetting fov = add(new NumSetting("FOV", 120, 10, 360, 5));
    private final NumSetting range = add(new NumSetting("Range", 6.0, 2.0, 12.0, 0.5));
    private final ModeSetting aimAt = add(new ModeSetting("Aim at", "Chest", "Head", "Chest", "Feet"));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Angle", "Angle", "Distance", "Health"));
    private final BoolSetting sticky = add(new BoolSetting("Lock on target", true));
    private final BoolSetting vertical = add(new BoolSetting("Vertical", true));
    private final BoolSetting holdClick = add(new BoolSetting("Hold LMB only", false));
    private final BoolSetting weaponOnly = add(new BoolSetting("Weapon only", false));
    private final BoolSetting visibleOnly = add(new BoolSetting("Visible only", true));
    private final TargetSettings targets = new TargetSettings(this, true, false, false, false, false);

    private long last;
    private LivingEntity locked;

    public AimAssist() {
        super("AimAssist", "Keeps your aim on the nearest enemy (Strength 1.0 = hard lock)", Category.COMBAT);
    }

    @Override protected void onEnable() { last = 0; locked = null; }
    @Override protected void onDisable() { locked = null; }

    private boolean valid(PlayerEntity p, LivingEntity e, double rangeMul) {
        return e.isAlive() && targets.testCombat(e) && p.distanceTo(e) <= range.get() * rangeMul
                && (!visibleOnly.get() || p.canSee(e));
    }

    /** {yaw difference, pitch difference} from the player's current look to the target point */
    private float[] angles(PlayerEntity p, Vec3d eye, Entity e, float td) {
        Vec3d base = e.getLerpedPos(td);
        Vec3d t = switch (aimAt.get()) {
            case "Head" -> base.add(0, e.getStandingEyeHeight(), 0);
            case "Feet" -> base.add(0, 0.25, 0);
            default -> base.add(0, e.getHeight() * 0.65, 0);
        };
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{MathHelper.wrapDegrees(yaw - p.getYaw()), pitch - p.getPitch()};
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;

        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null || Combat.blocked()) return;
        if (holdClick.get() && !mc.options.attackKey.isPressed()) return;
        if (weaponOnly.get() && !Combat.isWeapon(p.getMainHandStack())) return;

        float td = RenderUtil.tickDelta;
        Vec3d eye = p.getLerpedPos(td).add(0, p.getEyeY() - p.getY(), 0);

        float[] ang = null;
        // keep the current target until it dies / leaves range (no flicking between players)
        if (sticky.get() && locked != null) {
            if (valid(p, locked, 1.15)) ang = angles(p, eye, locked, td);
            else locked = null;
        }
        if (ang == null) {
            double bestScore = Double.MAX_VALUE;
            LivingEntity best = null;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || !valid(p, le, 1.0)) continue;
                float[] a = angles(p, eye, e, td);
                double angle = Math.hypot(a[0], a[1]);
                if (angle > fov.get() / 2.0) continue;
                double score = switch (priority.get()) {
                    case "Distance" -> p.distanceTo(e);
                    case "Health" -> le.getHealth();
                    default -> angle;
                };
                if (score < bestScore) { bestScore = score; best = le; ang = a; }
            }
            if (best == null) return;
            locked = sticky.get() ? best : null;
        }

        float s = strength.getFloat();
        float f = s >= 0.995f ? 1f : 1f - (float) Math.exp(-s * 25.0 * dt);
        float stepYaw = ang[0] * f;
        float stepPitch = vertical.get() ? ang[1] * f : 0f;
        // changeLookDirection keeps prevYaw/prevPitch in sync => no camera judder
        p.changeLookDirection(stepYaw / 0.15, stepPitch / 0.15);
    }
}
