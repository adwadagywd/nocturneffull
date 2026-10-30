package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
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

/** A rotating rune circle on the ground beneath you, with sparks rising from it. */
public class MagicCircle extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Hexagram", "Hexagram", "Pentagram", "Mandala"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Gradient", "Theme", "Gradient", "Rainbow"));
    private final NumSetting radius = add(new NumSetting("Radius", 1.8, 0.8, 4.0, 0.1));
    private final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.2, 3.0, 0.1));
    private final NumSetting opacity = add(new NumSetting("Opacity", 0.85, 0.2, 1.0, 0.05));
    private final BoolSetting glow = add(new BoolSetting("Glow", true));
    private final BoolSetting sparks = add(new BoolSetting("Sparks", true));
    private final BoolSetting others = add(new BoolSetting("Other players", false));

    private static final double TAU = Math.PI * 2;
    private double lastGroundY = Double.NaN;

    public MagicCircle() { super("MagicCircle", "Rotating rune circle under your feet", Category.VISUALS); }

    @Override
    public void onFrame() {
        if (mc.player != null && mc.player.isOnGround()) lastGroundY = mc.player.getY();
    }

    private int rgb(float f, double time) {
        switch (colorMode.get()) {
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.05 + f * 0.5) % 1.0), 0.65f, 1f);
            case "Gradient": return Draw.lerp(Theme.accent(), Theme.accent2(), f) & 0xFFFFFF;
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    private static double hash(double seed, double salt) {
        double v = Math.sin(seed * 12.9898 + salt * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    private static Vec3d pt(Vec3d c, double r, double a) { return new Vec3d(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r); }

    private static void star(BufferBuilder l, Vec3d c, double r, int n, int k, double rot, int argb) {
        for (int i = 0; i < n; i++)
            RenderUtil.line(l, pt(c, r, rot + i * TAU / n), pt(c, r, rot + ((i + k) % n) * TAU / n), argb);
    }

    private Vec3d center(PlayerEntity p) {
        Vec3d pos = p.getLerpedPos(RenderUtil.tickDelta);
        double y = pos.y;
        if (p == mc.player && !p.isOnGround() && !Double.isNaN(lastGroundY) && Math.abs(pos.y - lastGroundY) < 8) y = lastGroundY;
        return new Vec3d(pos.x, y + 0.03, pos.z);
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || !RenderUtil.valid) return;
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        float op = opacity.getFloat();
        double R = radius.get();
        String st = style.get();

        RenderUtil.setupBlend3d(2f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (PlayerEntity p : mc.world.getPlayers()) {
            if ((p != mc.player && !others.get()) || p.isSpectator() || p.isInvisible()) continue;
            Vec3d c = center(p);
            int c0 = rgb(0f, time), c1 = rgb(1f, time);
            if (glow.get()) RenderUtil.disc(q, c, 0.0, R, 48, RenderUtil.argb(c0, 0.2f * op), RenderUtil.argb(c1, 0f));

            // glowing nodes on the star's points
            int pts = st.equals("Pentagram") ? 5 : st.equals("Mandala") ? 12 : 6;
            double rot = time * 0.5;
            for (int i = 0; i < pts; i++) {
                Vec3d v = pt(c, R * 0.88, rot + i * TAU / pts);
                float pulse = 0.6f + 0.4f * (float) Math.sin(time * 3 + i);
                RenderUtil.poly(q, v, 0.1, 10, 0, 1.0, RenderUtil.argb(0xFFFFFF, 0.9f * pulse * op), RenderUtil.argb(rgb(i / (float) pts, time), 0f));
            }

            if (sparks.get()) {
                for (int j = 0; j < 28; j++) {
                    double ph = (time * 0.35 + hash(j, 1)) % 1.0;
                    double a = hash(j, 2) * TAU, rr = R * (0.15 + 0.85 * hash(j, 3));
                    float al = (float) Math.sin(Math.PI * ph) * op;
                    Vec3d v = new Vec3d(c.x + Math.cos(a) * rr, c.y + ph * 1.8, c.z + Math.sin(a) * rr);
                    RenderUtil.poly(q, v, 0.03 + 0.025 * hash(j, 4), 4, 0, 0.35, RenderUtil.argb(0xFFFFFF, 0.9f * al), RenderUtil.argb(rgb((float) hash(j, 5), time), 0.5f * al));
                }
            }
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (PlayerEntity p : mc.world.getPlayers()) {
            if ((p != mc.player && !others.get()) || p.isSpectator() || p.isInvisible()) continue;
            Vec3d c = center(p);
            int main = RenderUtil.argb(rgb(0.2f, time), 0.95f * op), soft = RenderUtil.argb(rgb(0.7f, time), 0.55f * op);

            RenderUtil.ring(l, c, R, 72, main);
            RenderUtil.ring(l, c, R * 0.93, 72, soft);
            // outer ticks, turning slowly the other way
            for (int i = 0; i < 48; i++) {
                double a = -time * 0.25 + i * TAU / 48;
                RenderUtil.line(l, pt(c, R * 0.93, a), pt(c, R * (i % 4 == 0 ? 0.8 : 0.87), a), soft);
            }
            // dashed ring
            for (int i = 0; i < 20; i++) {
                double a = time * 0.35 + i * TAU / 20;
                for (int s = 0; s < 4; s++) {
                    RenderUtil.line(l, pt(c, R * 0.74, a + s * 0.04), pt(c, R * 0.74, a + (s + 1) * 0.04), soft);
                }
            }
            switch (st) {
                case "Pentagram" -> {
                    star(l, c, R * 0.88, 5, 2, time * 0.5, main);
                    RenderUtil.ring(l, c, R * 0.34, 40, soft);
                }
                case "Mandala" -> {
                    star(l, c, R * 0.88, 12, 5, time * 0.5, main);
                    star(l, c, R * 0.55, 8, 3, -time * 0.7, RenderUtil.argb(rgb(0.9f, time), 0.9f * op));
                    RenderUtil.ring(l, c, R * 0.55, 48, soft);
                    RenderUtil.ring(l, c, R * 0.22, 32, main);
                }
                default -> {
                    star(l, c, R * 0.88, 6, 2, time * 0.5, main);
                    RenderUtil.ring(l, c, R * 0.46, 48, soft);
                }
            }
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
