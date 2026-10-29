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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** A burst of glowing sparks and a shockwave ring where you hit something. */
public class HitBurst extends Module {
    public static HitBurst INSTANCE;

    private static final class Spark {
        Vec3d pos, vel; final long born; final float hue;
        Spark(Vec3d p, Vec3d v, float h) { pos = p; vel = v; born = System.currentTimeMillis(); hue = h; }
    }
    private record Wave(Vec3d pos, long born) {}

    private final NumSetting amount = add(new NumSetting("Sparks", 24, 6, 80, 2));
    private final NumSetting power = add(new NumSetting("Power", 1.0, 0.4, 2.5, 0.1));
    private final NumSetting life = add(new NumSetting("Lifetime", 0.8, 0.3, 2.0, 0.1));
    private final NumSetting gravity = add(new NumSetting("Gravity", 1.0, 0.0, 3.0, 0.1));
    private final BoolSetting shock = add(new BoolSetting("Shockwave ring", true));
    private final BoolSetting glow = add(new BoolSetting("Glow", true));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Gradient", "Theme", "Gradient", "Rainbow", "Fire"));

    private final List<Spark> sparks = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private long last;

    public HitBurst() {
        super("HitBurst", "Spark burst and shockwave when you hit something", Category.VISUALS);
        INSTANCE = this;
    }

    @Override protected void onDisable() { sparks.clear(); waves.clear(); }

    public static void onHit(Entity e) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || e == null) return;
        INSTANCE.burst(e);
    }

    private void burst(Entity e) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3d c = e.getPos().add(0, e.getHeight() * 0.6, 0);
        double p = power.get();
        for (int i = 0; i < amount.getInt(); i++) {
            double a = r.nextDouble() * Math.PI * 2, el = (r.nextDouble() - 0.35) * 1.4;
            double sp = (2.2 + r.nextDouble() * 3.8) * p;
            Vec3d v = new Vec3d(Math.cos(a) * Math.cos(el) * sp, Math.sin(el) * sp + 1.2 * p, Math.sin(a) * Math.cos(el) * sp);
            sparks.add(new Spark(c, v, r.nextFloat()));
        }
        while (sparks.size() > 400) sparks.remove(0);
        if (shock.get()) waves.add(new Wave(e.getPos().add(0, 0.05, 0), System.currentTimeMillis()));
        if (waves.size() > 8) waves.remove(0);
    }

    private int rgb(float t, float hue) {
        switch (colorMode.get()) {
            case "Rainbow": return MathHelper.hsvToRgb(hue, 0.7f, 1f);
            case "Fire": return Draw.lerp(0xFFFFE066, 0xFFFF3B1F, t) & 0xFFFFFF;
            case "Theme": return Theme.accent() & 0xFFFFFF;
            default: return Draw.lerp(Theme.accent2(), Theme.accent(), t) & 0xFFFFFF;
        }
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        double g = 9.0 * gravity.get(), drag = Math.exp(-2.2 * dt);
        for (Spark s : sparks) {
            s.vel = new Vec3d(s.vel.x * drag, s.vel.y * drag - g * dt, s.vel.z * drag);
            s.pos = s.pos.add(s.vel.multiply(dt));
        }
    }

    @Override
    public void onRender3d() {
        long now = System.currentTimeMillis();
        float life = this.life.getFloat();
        sparks.removeIf(s -> (now - s.born) / 1000f > life);
        waves.removeIf(w -> (now - w.born()) / 1000f > 0.6f);
        if ((sparks.isEmpty() && waves.isEmpty()) || !RenderUtil.valid) return;

        RenderUtil.setupBlend3d(2f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Spark s : sparks) {
            float t = (now - s.born) / 1000f / life, a = 1f - t;
            int col = rgb(t, s.hue);
            if (glow.get()) {
                RenderUtil.poly(q, s.pos, 0.16 * (1f - t * 0.5f), 10, 0, 1.0, RenderUtil.argb(col, 0.5f * a), RenderUtil.argb(col, 0f));
                RenderUtil.poly(q, s.pos, 0.035, 8, 0, 1.0, RenderUtil.argb(0xFFFFFF, a), RenderUtil.argb(0xFFFFFF, a));
            }
        }
        for (Wave w : waves) {
            float t = (now - w.born()) / 1000f / 0.6f;
            double rad = 0.3 + 1.6 * (1f - (1f - t) * (1f - t));
            int col = rgb(t, 0.6f);
            RenderUtil.disc(q, w.pos(), rad * 0.8, rad, 40, RenderUtil.argb(col, 0f), RenderUtil.argb(col, 0.5f * (1f - t)));
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Spark s : sparks) {
            float t = (now - s.born) / 1000f / life;
            RenderUtil.line(l, s.pos, s.pos.subtract(s.vel.multiply(0.045)), RenderUtil.argb(rgb(t, s.hue), 1f - t));
        }
        for (Wave w : waves) {
            float t = (now - w.born()) / 1000f / 0.6f;
            double rad = 0.3 + 1.6 * (1f - (1f - t) * (1f - t));
            RenderUtil.ring(l, w.pos(), rad, 48, RenderUtil.argb(rgb(t, 0.6f), 1f - t));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
