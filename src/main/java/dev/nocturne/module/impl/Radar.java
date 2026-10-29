package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Friends;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

/** Round radar with a rotating scan line; north is always the way you look. */
public class Radar extends Module {
    private final NumSetting size = add(new NumSetting("Size", 96, 60, 180, 2));
    private final NumSetting range = add(new NumSetting("Range", 40, 10, 150, 5));
    private final NumSetting offX = add(new NumSetting("Offset X", 10, 0, 800, 1));
    private final NumSetting offY = add(new NumSetting("Offset Y", 120, 0, 600, 1));
    private final BoolSetting sweep = add(new BoolSetting("Sweep animation", true));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting hostile = add(new BoolSetting("Hostile mobs", true));
    private final BoolSetting passive = add(new BoolSetting("Passive mobs", false));
    private final BoolSetting items = add(new BoolSetting("Items", false));

    public Radar() { super("Radar", "Round radar showing players and mobs around you", Category.CLIENT); }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;
        Draw.alpha = 1f;
        int S = size.getInt(), x = offX.getInt(), y = offY.getInt();
        int cx = x + S / 2, cy = y + S / 2;
        double R = S / 2.0 - 4, rng = range.get();
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        double yaw = Math.toRadians(cam.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);

        Draw.shadow(ctx, x, y, S, S, S / 2f);
        Draw.panel(ctx, x, y, S, S, S / 2f, 0xD00B0B0F, Theme.border());
        Draw.ring(ctx, cx, cy, R * 0.5, 1, 0x28FFFFFF, 1f);
        Draw.ring(ctx, cx, cy, R, 1, 0x38FFFFFF, 1f);
        Draw.rect(ctx, cx, y + 6, 1, S - 12, 0x18FFFFFF);
        Draw.rect(ctx, x + 6, cy, S - 12, 1, 0x18FFFFFF);

        if (sweep.get()) {
            MatrixStack m = ctx.getMatrices();
            double ang = (System.currentTimeMillis() % 3200L) / 3200.0 * 360.0;
            for (int k = 0; k < 7; k++) {
                m.push();
                m.translate(cx, cy, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (ang - k * 3.5)));
                Draw.rect(ctx, 0, 0, (int) R, 1, Theme.accentA(Math.max(6, 0x58 - k * 12)));
                m.pop();
            }
        }

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            int color;
            if (e instanceof PlayerEntity) {
                if (!players.get()) continue;
                color = Friends.isFriend(e) ? 0xFF3DDC97 : 0xFFFF4D6D;
            } else if (e instanceof Monster) {
                if (!hostile.get()) continue;
                color = 0xFFFF9F1C;
            } else if (e instanceof MobEntity) {
                if (!passive.get()) continue;
                color = 0xFF57E389;
            } else if (e instanceof ItemEntity) {
                if (!items.get()) continue;
                color = 0xFF4CC9F0;
            } else continue;

            double dx = e.getX() - cam.getX(), dz = e.getZ() - cam.getZ();
            double fwd = dx * fx + dz * fz, rgt = dx * rx + dz * rz;
            double dist = Math.hypot(fwd, rgt);
            boolean clamped = dist > rng;
            double k = (clamped ? rng : dist) / rng * R / Math.max(dist, 0.0001);
            int px = (int) Math.round(cx + rgt * k), py = (int) Math.round(cy - fwd * k);
            Draw.round(ctx, px - 2, py - 2, 5, 5, 2.5f, clamped ? Draw.scaleA(color, 0.45f) : color);
        }
        Draw.round(ctx, cx - 3, cy - 3, 7, 7, 3.5f, Theme.accent());
    }
}
