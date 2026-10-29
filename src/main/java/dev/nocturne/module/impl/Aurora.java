package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

/** Flowing aurora curtains in the night sky, plus optional shooting stars. */
public class Aurora extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Classic", "Classic", "Theme", "Rainbow"));
    private final NumSetting height = add(new NumSetting("Height", 55, 25, 110, 1));
    private final NumSetting curtains = add(new NumSetting("Curtains", 3, 1, 5, 1));
    private final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.2, 3.0, 0.1));
    private final NumSetting intensity = add(new NumSetting("Intensity", 0.75, 0.2, 1.0, 0.05));
    private final BoolSetting stars = add(new BoolSetting("Shooting stars", true));
    private final BoolSetting always = add(new BoolSetting("Visible in daytime", false));

    private static final int SEGS = 72;
    private static final double RADIUS = 85;

    private double time;
    private long last;
    private Vec3d starFrom = Vec3d.ZERO, starDir = Vec3d.ZERO;
    private double starAge = 99;

    public Aurora() { super("Aurora", "Northern lights and shooting stars in the sky", Category.VISUALS); }

    private float night() {
        if (mc.world == null || mc.world.getRegistryKey() != World.OVERWORLD) return always.get() ? 1f : 0f;
        if (always.get()) return 1f;
        long t = mc.world.getTimeOfDay() % 24000L;
        float dusk = MathHelper.clamp((t - 12000f) / 1500f, 0f, 1f);
        float dawn = MathHelper.clamp((24000f - t - 400f) / 1500f, 0f, 1f);
        return Math.min(dusk, dawn);
    }

    private int rgb(float v, double ph) {
        switch (colorMode.get()) {
            case "Theme": return Draw.lerp(Theme.accent(), Theme.accent2(), v) & 0xFFFFFF;
            case "Rainbow": return MathHelper.hsvToRgb((float) ((ph * 0.05 + v * 0.35) % 1.0), 0.65f, 1f);
            default: return Draw.lerp(0xFF2BFFA0, 0xFFB06CFF, v) & 0xFFFFFF; // green -> violet
        }
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        time += dt * speed.get();
        starAge += dt;
        if (stars.get() && starAge > 3.0 && RenderUtil.valid && night() > 0.5f
                && ThreadLocalRandom.current().nextDouble() < dt * 0.25) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double a = r.nextDouble() * Math.PI * 2, el = 0.35 + r.nextDouble() * 0.5;
            Vec3d c = RenderUtil.cam;
            starFrom = new Vec3d(c.x + Math.cos(a) * 90, c.y + 40 + el * 50, c.z + Math.sin(a) * 90);
            double d = a + Math.PI / 2 + (r.nextDouble() - 0.5);
            starDir = new Vec3d(Math.cos(d) * 55, -14 - r.nextDouble() * 12, Math.sin(d) * 55);
            starAge = 0;
        }
    }

    @Override
    public void onRender3d() {
        float n = night();
        if (n < 0.02f || !RenderUtil.valid) return;
        Vec3d cam = RenderUtil.cam;
        int layers = curtains.getInt();
        float inten = intensity.getFloat() * n;

        RenderUtil.setupBlend3d(1f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (int L = 0; L < layers; L++) {
            double baseY = cam.y + height.get() + L * 9;
            double rad = RADIUS - L * 6;
            double arc = Math.PI * (1.15 - L * 0.12), start = L * 1.7 + time * 0.03;
            for (int i = 0; i < SEGS; i++) {
                double u0 = i / (double) SEGS, u1 = (i + 1) / (double) SEGS;
                double a0 = start + u0 * arc, a1 = start + u1 * arc;
                double w0 = wave(u0, L), w1 = wave(u1, L);
                double h0 = 14 + 16 * w0, h1 = 14 + 16 * w1;
                double y0 = baseY + Math.sin(u0 * 5 + time * 0.5 + L) * 3, y1 = baseY + Math.sin(u1 * 5 + time * 0.5 + L) * 3;
                float e0 = (float) Math.sin(Math.PI * u0), e1 = (float) Math.sin(Math.PI * u1); // fade at the ends
                float f0 = inten * e0 * (0.35f + 0.65f * (float) w0), f1 = inten * e1 * (0.35f + 0.65f * (float) w1);
                int c0 = rgb((float) u0, time + L), c1 = rgb((float) u1, time + L);
                double x0 = cam.x + Math.cos(a0) * rad, z0 = cam.z + Math.sin(a0) * rad;
                double x1 = cam.x + Math.cos(a1) * rad, z1 = cam.z + Math.sin(a1) * rad;
                // bright lower edge fading up into nothing
                RenderUtil.v(q, x0, y0, z0, RenderUtil.argb(c0, f0 * 0.85f));
                RenderUtil.v(q, x1, y1, z1, RenderUtil.argb(c1, f1 * 0.85f));
                RenderUtil.v(q, x1, y1 + h1, z1, RenderUtil.argb(c1, 0f));
                RenderUtil.v(q, x0, y0 + h0, z0, RenderUtil.argb(c0, 0f));
            }
        }
        RenderUtil.finish(q);

        if (stars.get() && starAge < 1.2) {
            float p = (float) (starAge / 1.2);
            RenderUtil.setupBlend3d(2.5f);
            BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
            int tail = 14;
            for (int k = 0; k < tail; k++) {
                double t0 = p - k * 0.012, t1 = p - (k + 1) * 0.012;
                if (t1 < 0) break;
                Vec3d a = starFrom.add(starDir.multiply(t0)), b = starFrom.add(starDir.multiply(t1));
                float al = (1f - k / (float) tail) * (1f - p) * n;
                RenderUtil.line(l, a, b, RenderUtil.argb(0xFFFFFF, al));
            }
            RenderUtil.finish(l);
        }
        RenderUtil.restore();
    }

    /** smooth 0..1 shimmer travelling along the curtain */
    private double wave(double u, int layer) {
        double v = Math.sin(u * 9 + time * 0.9 + layer * 2.0) * 0.5 + Math.sin(u * 17 - time * 1.3) * 0.3 + 0.5;
        return MathHelper.clamp(v, 0.0, 1.0);
    }
}
