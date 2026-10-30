package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Animated marker around the entity you are looking at: ring, helix, orbiting orbs, lock-on brackets or a halo. */
public class TargetRing extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Ring", "Ring", "Helix", "Orbit", "Lock-on", "Halo"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "Gradient", "Health", "Rainbow"));
    private final NumSetting speed = add(new NumSetting("Speed", 1.6, 0.5, 4.0, 0.1));
    private final NumSetting trail = add(new NumSetting("Trail rings", 3, 0, 6, 1));
    private final NumSetting width = add(new NumSetting("Line width", 2.0, 0.5, 5.0, 0.5));
    private final BoolSetting glow = add(new BoolSetting("Ground glow", true));
    private final BoolSetting hitFlash = add(new BoolSetting("Hit flash", true));

    private static final double TAU = Math.PI * 2;

    private LivingEntity last;
    private float alpha;
    private long lastNs;

    public TargetRing() {
        super("TargetRing", "Animated marker around your target", Category.VISUALS);
        trail.showIf(() -> style.is("Ring"));
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        Entity t = mc.targetedEntity;
        LivingEntity cur = (t instanceof LivingEntity le && le.isAlive()) ? le : null;
        if (cur != null) last = cur;
        alpha += ((cur != null ? 1f : 0f) - alpha) * (1f - (float) Math.exp(-10f * dt));
        if (cur == null && alpha < 0.02f) last = null;
    }

    private int col(LivingEntity t, float f, double time, float flash) {
        int rgb;
        switch (colorMode.get()) {
            case "Health" -> rgb = MathHelper.hsvToRgb(MathHelper.clamp(t.getHealth() / t.getMaxHealth(), 0f, 1f) * 0.33f, 0.8f, 1f);
            case "Rainbow" -> rgb = MathHelper.hsvToRgb((float) ((time * 0.2 + f * 0.4) % 1.0), 0.7f, 1f);
            case "Gradient" -> rgb = Draw.lerp(Theme.accent2(), Theme.accent(), f) & 0xFFFFFF;
            default -> rgb = Theme.accent() & 0xFFFFFF;
        }
        if (flash > 0f) rgb = Draw.lerp(0xFF000000 | rgb, 0xFFFFFFFF, flash * 0.85f) & 0xFFFFFF;
        return rgb;
    }

    private static Vec3d at(Vec3d c, double y, double r, double a) {
        return new Vec3d(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r);
    }

    private static void arc(BufferBuilder l, Vec3d c, double y, double r, double a0, double a1, int segs, int argb) {
        Vec3d prev = at(c, y, r, a0);
        for (int i = 1; i <= segs; i++) {
            Vec3d p = at(c, y, r, a0 + (a1 - a0) * i / segs);
            RenderUtil.line(l, prev, p, argb);
            prev = p;
        }
    }

    @Override
    public void onRender3d() {
        LivingEntity t = last;
        if (t == null || alpha < 0.02f || !RenderUtil.valid) return;
        Vec3d base = t.getLerpedPos(RenderUtil.tickDelta);
        double r = Math.max(0.45, t.getWidth() * 0.85 + 0.1), h = t.getHeight();
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        float flash = hitFlash.get() ? MathHelper.clamp(t.hurtTime / 10f, 0f, 1f) : 0f;
        float A = alpha;
        String st = style.get();
        int c0 = col(t, 0f, time, flash), c1 = col(t, 1f, time, flash);

        RenderUtil.setupBlend3d(width.getFloat());

        // ---- glowing parts (quads) ----
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        switch (st) {
            case "Helix" -> {
                for (int k = 0; k < 2; k++) {
                    double f = 0.5 + 0.5 * Math.sin(time * 1.2 + k * Math.PI);
                    Vec3d p = at(base, base.y + f * h, r, time * 2.0 + k * Math.PI + f * TAU * 1.5);
                    int c = col(t, (float) f, time, flash);
                    RenderUtil.poly(q, p, 0.16, 12, 0, 1.0, RenderUtil.argb(c, 0.4f * A), RenderUtil.argb(c, 0f));
                    RenderUtil.poly(q, p, 0.05, 10, 0, 1.0, RenderUtil.argb(0xFFFFFF, A), RenderUtil.argb(c, A));
                }
            }
            case "Orbit" -> {
                for (int i = 0; i < 3; i++) {
                    Vec3d p = orbit(base, r, h, i, time);
                    int c = col(t, i / 2f, time, flash);
                    RenderUtil.poly(q, p, 0.22, 12, 0, 1.0, RenderUtil.argb(c, 0.4f * A), RenderUtil.argb(c, 0f));
                    RenderUtil.poly(q, p, 0.06, 10, 0, 1.0, RenderUtil.argb(0xFFFFFF, A), RenderUtil.argb(c, A));
                }
                if (glow.get()) RenderUtil.disc(q, base.add(0, 0.02, 0), r * 0.4, r * 1.1, 40, RenderUtil.argb(c0, 0f), RenderUtil.argb(c0, 0.2f * A));
            }
            case "Lock-on" -> {
                if (glow.get()) RenderUtil.disc(q, base.add(0, 0.02, 0), r * 0.3, r * 1.2, 40, RenderUtil.argb(c0, 0f), RenderUtil.argb(c0, 0.22f * A));
            }
            case "Halo" -> {
                double R = Math.max(0.3, r * 0.55), y = base.y + h + 0.25 + 0.04 * Math.sin(time * 1.5);
                RenderUtil.disc(q, new Vec3d(base.x, y, base.z), R * 0.85, R * 1.3, 40, RenderUtil.argb(c0, 0.4f * A), RenderUtil.argb(c0, 0f));
                for (int j = 0; j < 6; j++) {
                    Vec3d p = at(base, y, R, time * 1.6 + j * TAU / 6);
                    int c = col(t, j / 5f, time, flash);
                    RenderUtil.poly(q, p, 0.09, 10, 0, 1.0, RenderUtil.argb(c, 0.5f * A), RenderUtil.argb(c, 0f));
                    RenderUtil.poly(q, p, 0.03, 8, 0, 1.0, RenderUtil.argb(0xFFFFFF, A), RenderUtil.argb(0xFFFFFF, A));
                }
            }
            default -> {
                if (glow.get()) RenderUtil.disc(q, base.add(0, 0.02, 0), r * 0.5, r, 40, RenderUtil.argb(c0, 0f), RenderUtil.argb(c0, 0.25f * A));
            }
        }
        RenderUtil.finish(q);

        // ---- lines ----
        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        switch (st) {
            case "Helix" -> {
                int segs = 48;
                for (int k = 0; k < 2; k++) {
                    for (int m = 0; m < segs; m++) {
                        double f0 = m / (double) segs, f1 = (m + 1) / (double) segs;
                        Vec3d a = at(base, base.y + f0 * h, r, time * 2.0 + k * Math.PI + f0 * TAU * 1.5);
                        Vec3d b = at(base, base.y + f1 * h, r, time * 2.0 + k * Math.PI + f1 * TAU * 1.5);
                        float al = (float) (0.25 + 0.65 * Math.sin(Math.PI * f1)) * A;
                        RenderUtil.line(l, a, b, RenderUtil.argb(col(t, (float) f1, time, flash), al));
                    }
                }
                RenderUtil.ring(l, base.add(0, 0.025, 0), r, 56, RenderUtil.argb(c0, 0.35f * A));
            }
            case "Orbit" -> {
                for (int i = 0; i < 3; i++) {
                    int c = col(t, i / 2f, time, flash);
                    Vec3d prev = orbit(base, r, h, i, time);
                    int tl = 16;
                    for (int k = 1; k <= tl; k++) {
                        Vec3d p = orbit(base, r, h, i, time - k * 0.05);
                        RenderUtil.line(l, prev, p, RenderUtil.argb(c, (1f - k / (float) (tl + 1)) * 0.9f * A));
                        prev = p;
                    }
                }
                RenderUtil.ring(l, base.add(0, 0.025, 0), r * 1.1, 56, RenderUtil.argb(c0, 0.25f * A));
            }
            case "Lock-on" -> {
                double R = r * 1.2 * (1 + 0.05 * Math.sin(time * 3));
                double yG = base.y + 0.03, span = 0.55;
                RenderUtil.ring(l, new Vec3d(base.x, yG, base.z), R * 0.82, 56, RenderUtil.argb(c0, 0.22f * A));
                for (int j = 0; j < 4; j++) {
                    int c = col(t, j / 3f, time, flash);
                    double a0 = time * 0.9 + j * Math.PI / 2;
                    arc(l, base, yG, R, a0 - span / 2, a0 + span / 2, 12, RenderUtil.argb(c, A));
                    for (int e = -1; e <= 1; e += 2) { // ticks pointing inwards at both ends
                        double ae = a0 + e * span / 2;
                        RenderUtil.line(l, at(base, yG, R, ae), at(base, yG, R * 0.8, ae), RenderUtil.argb(c, A));
                    }
                    double a1 = -time * 0.6 + j * Math.PI / 2 + Math.PI / 4, yM = base.y + h * 0.5; // counter-rotating chest brackets
                    arc(l, base, yM, r * 0.95, a1 - 0.3, a1 + 0.3, 8, RenderUtil.argb(c, 0.5f * A));
                }
            }
            case "Halo" -> {
                double R = Math.max(0.3, r * 0.55), y = base.y + h + 0.25 + 0.04 * Math.sin(time * 1.5);
                RenderUtil.ring(l, new Vec3d(base.x, y, base.z), R, 48, RenderUtil.argb(c1, A));
                RenderUtil.ring(l, new Vec3d(base.x, y, base.z), R * 1.25, 48, RenderUtil.argb(c0, 0.35f * A));
            }
            default -> {
                RenderUtil.ring(l, base.add(0, 0.025, 0), r, 56, RenderUtil.argb(c0, 0.55f * A));
                int n = trail.getInt();
                for (int k = 0; k <= n; k++) {
                    double ph = (Math.sin(time - k * 0.22) + 1.0) / 2.0;
                    float a = (1f - k / (n + 1f)) * A;
                    RenderUtil.ring(l, new Vec3d(base.x, base.y + ph * h, base.z), r, 56, RenderUtil.argb(col(t, (float) ph, time, flash), a));
                }
            }
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }

    private static Vec3d orbit(Vec3d base, double r, double h, int i, double time) {
        double a = time * 1.3 + i * TAU / 3, yf = 0.5 + 0.5 * Math.sin(time * 0.8 + i * 2.0);
        return at(base, base.y + 0.1 + yf * Math.max(0.1, h - 0.2), r * 1.1, a);
    }
}
