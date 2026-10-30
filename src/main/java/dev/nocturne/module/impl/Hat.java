package dev.nocturne.module.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** A 3D hat that sits on top of the head (wizard, top hat, crown, party hat). */
public class Hat extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Wizard", "Wizard", "Top hat", "Crown", "Party"));
    private final ModeSetting color = add(new ModeSetting("Color", "Theme", "Theme", "Midnight", "Crimson", "Emerald", "Rainbow"));
    private final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.6, 1.8, 0.05));
    private final BoolSetting sparkles = add(new BoolSetting("Sparkles", true));
    private final BoolSetting others = add(new BoolSetting("Other players", false));
    private final BoolSetting hideFirst = add(new BoolSetting("Hide in first person", true));

    private static final double TAU = Math.PI * 2;
    private static final int GOLD = 0xFFD34E;

    public Hat() { super("Hat", "A 3D hat on your head", Category.VISUALS); }

    private int mainColor(double time) {
        switch (color.get()) {
            case "Midnight": return 0x3A2D7C;
            case "Crimson": return 0xB3202F;
            case "Emerald": return 0x1E9E62;
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.1) % 1.0), 0.7f, 0.9f);
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    private int trimColor(double time) {
        switch (color.get()) {
            case "Theme": return Theme.accent2() & 0xFFFFFF;
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.1 + 0.5) % 1.0), 0.6f, 1f);
            default: return GOLD;
        }
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || !RenderUtil.valid) return;
        boolean first = mc.options.getPerspective().isFirstPerson();
        double time = System.currentTimeMillis() / 1000.0;

        RenderUtil.setupBlend3d(1f);
        RenderSystem.depthMask(true); // the hat is solid, so let it sort itself
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (PlayerEntity p : mc.world.getPlayers()) {
            boolean self = p == mc.player;
            if (!self && !others.get()) continue;
            if (hideFirst.get() && first && p == mc.getCameraEntity()) continue;
            if (p.isInvisible() || p.isSpectator() || p.isSleeping() || p.isSwimming() || p.isGliding()) continue;
            draw(b, p, time);
        }
        RenderUtil.finish(b);
        RenderUtil.restore();
    }

    private void draw(BufferBuilder b, PlayerEntity p, double time) {
        double s = scale.get();
        Vec3d base = p.getLerpedPos(RenderUtil.tickDelta).add(0, p.getHeight() - 0.03, 0);
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(RenderUtil.tickDelta, p.prevHeadYaw, p.headYaw));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        int main = mainColor(time), trim = trimColor(time);

        switch (style.get()) {
            case "Top hat" -> {
                RenderUtil.disc(b, base, 0.18 * s, 0.44 * s, 32, shade(main, 0.9f), shade(main, 0.75f));
                lathe(b, base, fx, fz, s, new double[][]{{0, 0.26, 0}, {0.44, 0.26, 0}}, 20, main);
                cap(b, base, s, 0.44, 0.26, 20, shade(main, 1.0f));
                lathe(b, base, fx, fz, s, new double[][]{{0.0, 0.268, 0}, {0.1, 0.268, 0}}, 20, trim);
            }
            case "Crown" -> {
                int gold = color.is("Theme") ? GOLD : main;
                lathe(b, base, fx, fz, s, new double[][]{{0, 0.3, 0}, {0.13, 0.3, 0}}, 20, gold);
                for (int j = 0; j < 5; j++) {
                    double a = j * TAU / 5, d = 0.3;
                    Vec3d l = pt(base, fx, fz, s, 0.13, 0.3, 0, a - d), r = pt(base, fx, fz, s, 0.13, 0.3, 0, a + d);
                    Vec3d tip = pt(base, fx, fz, s, 0.31, 0.3, 0, a);
                    int c = shade(gold, 0.75f + 0.25f * (float) Math.max(0, Math.cos(a - 0.9)));
                    RenderUtil.v(b, l.x, l.y, l.z, c);
                    RenderUtil.v(b, r.x, r.y, r.z, c);
                    RenderUtil.v(b, tip.x, tip.y, tip.z, c);
                    RenderUtil.v(b, tip.x, tip.y, tip.z, c);
                    RenderUtil.poly(b, tip, 0.032 * s, 8, 0, 1.0, RenderUtil.argb(0xFFFFFF, 1f), RenderUtil.argb(trim, 1f));
                }
            }
            case "Party" -> {
                lathe(b, base, fx, fz, s, new double[][]{{0, 0.22, 0}, {0.5, 0.0, 0}}, 12, main, 0xFFFFFF);
                Vec3d tip = base.add(0, 0.5 * s, 0);
                RenderUtil.poly(b, tip, 0.1 * s, 12, 0, 1.0, RenderUtil.argb(trim, 0.4f), RenderUtil.argb(trim, 0f));
                RenderUtil.poly(b, tip, 0.06 * s, 10, 0, 1.0, RenderUtil.argb(0xFFFFFF, 1f), RenderUtil.argb(trim, 1f));
            }
            default -> { // Wizard
                RenderUtil.disc(b, base, 0.24 * s, 0.5 * s, 36, shade(main, 0.95f), shade(main, 0.7f));
                lathe(b, base, fx, fz, s, new double[][]{
                        {0, 0.30, 0}, {0.18, 0.26, 0}, {0.36, 0.19, 0.03}, {0.52, 0.13, 0.10},
                        {0.66, 0.08, 0.21}, {0.78, 0.04, 0.35}, {0.86, 0.0, 0.5}}, 18, main);
                lathe(b, base, fx, fz, s, new double[][]{{0.0, 0.305, 0}, {0.09, 0.288, 0}}, 18, trim);
                Vec3d star = pt(base, fx, fz, s, 0.3, 0.27, 0, Math.atan2(fz, fx)); // front of the hat
                RenderUtil.poly(b, star, 0.055 * s, 5, time * 0.5, 0.45, RenderUtil.argb(0xFFF1A8, 1f), RenderUtil.argb(trim, 1f));
            }
        }

        if (sparkles.get()) {
            for (int j = 0; j < 5; j++) {
                double a = time * 1.2 + j * TAU / 5;
                double y = (0.15 + 0.5 * (0.5 + 0.5 * Math.sin(time * 1.7 + j * 1.9))) * s;
                Vec3d c = base.add(Math.cos(a) * 0.52 * s, y, Math.sin(a) * 0.52 * s);
                double r = 0.05 * s * (0.8 + 0.4 * Math.sin(time * 6 + j));
                RenderUtil.poly(b, c, r, 4, 0, 0.35, RenderUtil.argb(0xFFFFFF, 0.95f), RenderUtil.argb(trim, 0.6f));
            }
        }
    }

    /** point on a lathe profile: height y, radius r, bend = offset towards the back of the head */
    private static Vec3d pt(Vec3d base, double fx, double fz, double s, double y, double r, double bend, double ang) {
        return new Vec3d(base.x - fx * bend * s + Math.cos(ang) * r * s, base.y + y * s, base.z - fz * bend * s + Math.sin(ang) * r * s);
    }

    /** solid of revolution; every profile row is {height, radius, backwards bend}. Segments cycle through cols. */
    private void lathe(BufferBuilder b, Vec3d base, double fx, double fz, double s, double[][] prof, int segs, int... cols) {
        int rows = prof.length;
        for (int k = 0; k < rows - 1; k++) {
            double[] p0 = prof[k], p1 = prof[k + 1];
            float vert = 0.86f + 0.14f * (k / (float) Math.max(1, rows - 2));
            for (int i = 0; i < segs; i++) {
                double a0 = i * TAU / segs, a1 = (i + 1) * TAU / segs, am = (a0 + a1) * 0.5;
                float sh = (0.55f + 0.45f * (float) Math.max(0, Math.cos(am - 0.9))) * vert;
                int c = shade(cols[i % cols.length], sh);
                Vec3d q0 = pt(base, fx, fz, s, p0[0], p0[1], p0[2], a0), q1 = pt(base, fx, fz, s, p0[0], p0[1], p0[2], a1);
                Vec3d q2 = pt(base, fx, fz, s, p1[0], p1[1], p1[2], a1), q3 = pt(base, fx, fz, s, p1[0], p1[1], p1[2], a0);
                RenderUtil.v(b, q0.x, q0.y, q0.z, c);
                RenderUtil.v(b, q1.x, q1.y, q1.z, c);
                RenderUtil.v(b, q2.x, q2.y, q2.z, c);
                RenderUtil.v(b, q3.x, q3.y, q3.z, c);
            }
        }
    }

    /** flat lid */
    private void cap(BufferBuilder b, Vec3d base, double s, double y, double r, int segs, int c) {
        Vec3d ctr = new Vec3d(base.x, base.y + y * s, base.z);
        for (int i = 0; i < segs; i++) {
            double a0 = i * TAU / segs, a1 = (i + 1) * TAU / segs;
            RenderUtil.v(b, ctr.x, ctr.y, ctr.z, c);
            RenderUtil.v(b, ctr.x + Math.cos(a0) * r * s, ctr.y, ctr.z + Math.sin(a0) * r * s, c);
            RenderUtil.v(b, ctr.x + Math.cos(a1) * r * s, ctr.y, ctr.z + Math.sin(a1) * r * s, c);
            RenderUtil.v(b, ctr.x + Math.cos(a1) * r * s, ctr.y, ctr.z + Math.sin(a1) * r * s, c);
        }
    }
}
