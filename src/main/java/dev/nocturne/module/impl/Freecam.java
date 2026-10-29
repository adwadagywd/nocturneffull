package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** Detached camera. The real player stays exactly where he is (keys, mouse and clicks do not reach him). */
public class Freecam extends Module {
    public static Freecam INSTANCE;

    private final NumSetting speed = add(new NumSetting("Speed", 14, 2, 60, 1));
    private final NumSetting boost = add(new NumSetting("Sprint boost", 2.5, 1, 6, 0.5));
    private final NumSetting smooth = add(new NumSetting("Smoothing", 0.5, 0.0, 1.0, 0.05));
    private final ModeSetting mouse = add(new ModeSetting("Mouse source", "Both", "Both", "Player", "Camera"));
    private final BoolSetting info = add(new BoolSetting("Show info", true));

    private OtherClientPlayerEntity cam;
    private float savedYaw, savedPitch, cyaw, cpitch;
    private double cx, cy, cz;
    private Vec3d vel = Vec3d.ZERO;
    private long last;

    public Freecam() {
        super("Freecam", "Fly the camera around while your body stays put", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override public boolean persistEnabled() { return false; }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.world == null) return;
        cam = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        cx = mc.player.getX(); cy = mc.player.getY(); cz = mc.player.getZ();
        cyaw = savedYaw = mc.player.getYaw();
        cpitch = savedPitch = mc.player.getPitch();
        vel = Vec3d.ZERO;
        last = 0;
        place();
        mc.setCameraEntity(cam);
    }

    @Override
    protected void onDisable() {
        if (cam != null && mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
            mc.player.prevYaw = savedYaw;
            mc.player.prevPitch = savedPitch;
        }
        cam = null;
        KeyBinding.updatePressedStates(); // give the movement keys back
    }

    private void place() {
        cam.setPosition(cx, cy, cz);
        cam.prevX = cx; cam.prevY = cy; cam.prevZ = cz;
        cam.setYaw(cyaw); cam.setPitch(cpitch);
        cam.prevYaw = cyaw; cam.prevPitch = cpitch;
    }

    /** stop the real player from reacting to keys while we fly the camera */
    @Override
    public void onPreTick(MinecraftClient mc) {
        if (cam == null) return;
        GameOptions o = mc.options;
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey,
                o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey, o.useKey}) k.setPressed(false);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (cam == null) return;
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam); // something reset it - take the camera back
        place();
    }

    private boolean down(KeyBinding k) {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), KeyBindingHelper.getBoundKeyOf(k).getCode());
    }

    @Override
    public void onFrame() {
        if (cam == null || mc.player == null || mc.world == null || cam.getWorld() != mc.world) {
            setEnabled(false);
            return;
        }
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);

        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;

        // Mouse look. Depending on the game version the mouse turns either the player or the camera entity,
        // so read both (see "Mouse source") and put the movement on the camera; the body keeps its rotation.
        float dyP = mc.player.getYaw() - savedYaw, dpP = mc.player.getPitch() - savedPitch;
        float dyC = cam.getYaw() - cyaw, dpC = cam.getPitch() - cpitch;
        float dy = (mouse.is("Camera") ? 0f : dyP) + (mouse.is("Player") ? 0f : dyC);
        float dp = (mouse.is("Camera") ? 0f : dpP) + (mouse.is("Player") ? 0f : dpC);
        cyaw += dy;
        cpitch = MathHelper.clamp(cpitch + dp, -90f, 90f);
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
        mc.player.prevYaw = savedYaw;
        mc.player.prevPitch = savedPitch;

        Vec3d wish = Vec3d.ZERO;
        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            Vec3d look = Vec3d.fromPolar(cpitch, cyaw);
            double yr = Math.toRadians(cyaw);
            Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
            Vec3d mv = Vec3d.ZERO;
            if (down(o.forwardKey)) mv = mv.add(look);
            if (down(o.backKey)) mv = mv.subtract(look);
            if (down(o.rightKey)) mv = mv.add(right);
            if (down(o.leftKey)) mv = mv.subtract(right);
            if (down(o.jumpKey)) mv = mv.add(0, 1, 0);
            if (down(o.sneakKey)) mv = mv.subtract(0, 1, 0);
            if (mv.lengthSquared() > 1.0E-6)
                wish = mv.normalize().multiply(speed.get() * (down(o.sprintKey) ? boost.get() : 1.0));
        }
        double s = smooth.get();
        double k = s <= 0.01 ? 1.0 : 1.0 - Math.exp(-dt * (34.0 - 28.0 * s)); // 0 = instant, 1 = floaty
        vel = vel.add(wish.subtract(vel).multiply(k));
        cx += vel.x * dt; cy += vel.y * dt; cz += vel.z * dt;
        place();
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (cam == null || !info.get()) return;
        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        String a = "FREECAM";
        String b = String.format(Locale.ROOT, "%.0f  %.0f  %.0f", cx, cy, cz);
        int w = Draw.boldWidth(tr, a) + Draw.width(tr, b) + 34;
        int x = (mc.getWindow().getScaledWidth() - w) / 2, y = 38;
        Draw.shadow(ctx, x, y, w, 18, 7);
        Draw.round(ctx, x, y, w, 18, 7, 0xD00B0B0F);
        Draw.round(ctx, x + 7, y + 6, 6, 6, 3, Theme.accent());
        Draw.bold(ctx, tr, a, x + 18, y + 5, Theme.accent(), false);
        Draw.text(ctx, tr, b, x + 24 + Draw.boldWidth(tr, a), y + 5, Theme.SOFT, false);
    }
}
