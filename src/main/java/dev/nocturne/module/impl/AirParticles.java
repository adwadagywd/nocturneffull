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

import java.util.concurrent.ThreadLocalRandom;

/** Ambient glowing particles floating around you: shape, glow, colour and motion are all configurable. */
public class AirParticles extends Module {
    private final NumSetting count = add(new NumSetting("Count", 150, 10, 600, 10));
    private final NumSetting radius = add(new NumSetting("Radius", 14, 4, 40, 1));
    private final NumSetting size = add(new NumSetting("Size", 0.12, 0.02, 0.5, 0.01));
    private final ModeSetting shape = add(new ModeSetting("Shape", "Circle", "Circle", "Square", "Diamond", "Triangle", "Star"));
    private final NumSetting glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "Gradient", "Custom", "Rainbow"));
    private final ColorSetting hue = add(new ColorSetting("Custom hue", 0.55f));
    private final ModeSetting motion = add(new ModeSetting("Motion", "Float", "Float", "Rise", "Fall", "Swirl"));
    private final NumSetting speed = add(new NumSetting("Speed", 0.6, 0.0, 4.0, 0.1));
    private final NumSetting opacity = add(new NumSetting("Opacity", 0.85, 0.1, 1.0, 0.05));
    private final BoolSetting twinkle = add(new BoolSetting("Twinkle", true));

    private double[] x = new double[0], y = new double[0], z = new double[0], vx = new double[0], vy = new double[0], vz = new double[0], ph = new double[0];
    private long last;
    private double time;

    public AirParticles() {
        super("AirParticles", "Glowing particles in the air - choose shape, glow and colour", Category.VISUALS);
        hue.showIf(() -> colorMode.is("Custom"));
    }

    private void respawn(int i) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3d c = RenderUtil.cam;
        double d = radius.get() * Math.cbrt(r.nextDouble());
        double a = r.nextDouble() * Math.PI * 2, b = Math.acos(2 * r.nextDouble() - 1);
        x[i] = c.x + d * Math.sin(b) * Math.cos(a);
        y[i] = c.y + d * Math.cos(b) * 0.7;
        z[i] = c.z + d * Math.sin(b) * Math.sin(a);
        ph[i] = r.nextDouble() * 100.0;
    }

    private void ensure(int n) {
        if (x.length == n) return;
        x = new double[n]; y = new double[n]; z = new double[n];
        vx = new double[n]; vy = new double[n]; vz = new double[n]; ph = new double[n];
        for (int i = 0; i < n; i++) respawn(i);
    }

    private int rgb(int i) {
        switch (colorMode.get()) {
            case "Custom": return hue.rgb() & 0xFFFFFF;
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.08 + i * 0.011) % 1.0), 0.65f, 1f);
            case "Gradient": return Draw.lerp(Theme.accent(), Theme.accent2(), (i % 24) / 23f) & 0xFFFFFF;
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    @Override
    public void onFrame() {
        if (!RenderUtil.valid) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        time += dt;
        int n = count.getInt();
        ensure(n);
        double R = radius.get(), sp = speed.get();
        Vec3d c = RenderUtil.cam;
        String mo = motion.get();
        for (int i = 0; i < n; i++) {
            switch (mo) {
                case "Rise" -> {
                    vy[i] = sp * 0.5 + Math.sin(time + ph[i]) * 0.1;
                    vx[i] = Math.sin(time * 0.7 + ph[i]) * 0.15 * sp;
                    vz[i] = Math.cos(time * 0.6 + ph[i]) * 0.15 * sp;
                }
                case "Fall" -> {
                    vy[i] = -sp * 0.6;
                    vx[i] = Math.sin(time * 0.9 + ph[i]) * 0.3 * sp;
                    vz[i] = Math.cos(time * 0.8 + ph[i]) * 0.3 * sp;
                }
                case "Swirl" -> {
                    double dx = x[i] - c.x, dz = z[i] - c.z, ang = sp * 0.5 * dt;
                    x[i] = c.x + dx * Math.cos(ang) - dz * Math.sin(ang);
                    z[i] = c.z + dx * Math.sin(ang) + dz * Math.cos(ang);
                    vx[i] = 0; vz[i] = 0;
                    vy[i] = Math.sin(time + ph[i]) * 0.2 * sp;
                }
                default -> {
                    vx[i] = Math.sin(time * 0.4 + ph[i]) * 0.35 * sp;
                    vy[i] = Math.cos(time * 0.5 + ph[i] * 1.3) * 0.25 * sp;
                    vz[i] = Math.sin(time * 0.3 + ph[i] * 0.7) * 0.35 * sp;
                }
            }
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            double dx = x[i] - c.x, dy = y[i] - c.y, dz = z[i] - c.z;
            if (dx * dx + dy * dy + dz * dz > R * R) respawn(i);
        }
    }

    @Override
    public void onRender3d() {
        int n = Math.min(count.getInt(), x.length);
        if (n == 0 || !RenderUtil.valid) return;
        Vec3d cam = RenderUtil.cam;
        double R = radius.get();
        float g = glow.getFloat();
        String sh = shape.get();

        RenderUtil.setupBlend3d(1f);
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (int i = 0; i < n; i++) {
            double dx = x[i] - cam.x, dy = y[i] - cam.y, dz = z[i] - cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float edge = (float) MathHelper.clamp((R - dist) / (R * 0.3), 0.0, 1.0);
            float tw = twinkle.get() ? (float) (0.65 + 0.35 * Math.sin(time * 2.2 + ph[i])) : 1f;
            float a = opacity.getFloat() * edge * tw;
            if (a < 0.02f) continue;
            double s = size.get() * (0.6 + 0.8 * (ph[i] - Math.floor(ph[i])));
            int col = rgb(i);
            Vec3d c = new Vec3d(x[i], y[i], z[i]);

            if (g > 0.01f) RenderUtil.poly(b, c, s * (1.5 + g * 1.6), 12, 0, 1.0,
                    RenderUtil.argb(col, Math.min(1f, a * 0.30f * g)), RenderUtil.argb(col, 0f));
            int core = RenderUtil.argb(col, a);
            switch (sh) {
                case "Square" -> RenderUtil.poly(b, c, s * 1.3, 4, Math.PI / 4, 1.0, core, core);
                case "Diamond" -> RenderUtil.poly(b, c, s * 1.3, 4, 0, 1.0, core, core);
                case "Triangle" -> RenderUtil.poly(b, c, s * 1.4, 3, Math.PI / 2, 1.0, core, core);
                case "Star" -> RenderUtil.poly(b, c, s * 1.9, 4, 0, 0.28, core, core);
                default -> RenderUtil.poly(b, c, s, 12, 0, 1.0, core, core);
            }
        }
        RenderUtil.finish(b);
        RenderUtil.restore();
    }
}
