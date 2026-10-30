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

/** Glowing wings on your back that idle gently and flap when you are in the air. */
public class Wings extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Angel", "Angel", "Demon", "Blade"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Gradient", "Theme", "Gradient", "Rainbow"));
    private final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.5, 2.0, 0.05));
    private final NumSetting flap = add(new NumSetting("Flap speed", 1.0, 0.2, 3.0, 0.1));
    private final NumSetting opacity = add(new NumSetting("Opacity", 0.8, 0.2, 1.0, 0.05));
    private final BoolSetting others = add(new BoolSetting("Other players", false));
    private final BoolSetting hideFirst = add(new BoolSetting("Hide in first person", true));

    private static final double[] DEMON_LEN = {1.2, 1.1, 0.95, 0.78, 0.6};

    private double phase, colTime;
    private float amp = 0.12f;
    private long last;

    public Wings() { super("Wings", "Glowing wings on your back", Category.VISUALS); }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        if (mc.player == null) return;
        boolean air = !mc.player.isOnGround();
        amp += ((air ? 0.5f : 0.12f) - amp) * (1f - (float) Math.exp(-5f * dt));
        phase += dt * (air ? 7.0 : 1.6) * flap.get();
    }

    private int rgb(float t) {
        switch (colorMode.get()) {
            case "Rainbow": return MathHelper.hsvToRgb((float) ((colTime * 0.1 + t * 0.5) % 1.0), 0.65f, 1f);
            case "Gradient": return Draw.lerp(Theme.accent(), Theme.accent2(), t) & 0xFFFFFF;
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || !RenderUtil.valid) return;
        boolean first = mc.options.getPerspective().isFirstPerson();
        colTime = System.currentTimeMillis() / 1000.0;

        RenderUtil.setupBlend3d(2.5f);
        for (int pass = 0; pass < 2; pass++) {
            boolean lines = pass == 1;
            BufferBuilder b = RenderUtil.begin(lines ? VertexFormat.DrawMode.DEBUG_LINES : VertexFormat.DrawMode.QUADS);
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && !others.get()) continue;
                if (hideFirst.get() && first && p == mc.getCameraEntity()) continue;
                if (p.isInvisible() || p.isSpectator() || p.isSleeping() || p.isSwimming() || p.isGliding()) continue;
                drawPlayer(b, lines, p);
            }
            RenderUtil.finish(b);
        }
        RenderUtil.restore();
    }

    private void drawPlayer(BufferBuilder b, boolean lines, PlayerEntity p) {
        float td = RenderUtil.tickDelta;
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        Vec3d back = new Vec3d(-fx, 0, -fz), side = new Vec3d(-fz, 0, fx);
        Vec3d o = p.getLerpedPos(td).add(0, p.getHeight() * 0.7, 0).add(back.multiply(0.16));
        for (int sgn = -1; sgn <= 1; sgn += 2)
            wing(b, lines, o.add(side.multiply(sgn * 0.08)), side, back, scale.get(), sgn);
    }

    private static Vec3d at(Vec3d pivot, Vec3d side, Vec3d back, int sgn, double x, double y, double z) {
        return pivot.add(side.multiply(sgn * x)).add(0, y, 0).add(back.multiply(z));
    }

    private static void vv(BufferBuilder b, Vec3d p, int c) { RenderUtil.v(b, p.x, p.y, p.z, c); }

    private void wing(BufferBuilder b, boolean lines, Vec3d pivot, Vec3d side, Vec3d back, double s, int sgn) {
        String st = style.get();
        boolean demon = st.equals("Demon"), blade = st.equals("Blade");
        int n = demon ? 5 : blade ? 6 : 8;
        float op = opacity.getFloat();
        Vec3d[] tip = new Vec3d[n];

        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1), beta, len, w;
            if (demon) { beta = 1.45 - i * 0.32; len = DEMON_LEN[i]; w = 0; }
            else if (blade) { beta = 0.1 + i * 0.26; len = 1.25 - i * 0.1; w = 0.03; }
            else { beta = -0.5 + t * 1.85; len = 0.45 + 0.7 * t; w = 0.075; }
            double ang = beta + 0.1 + amp * Math.sin(phase - t * 0.9);
            double dx = Math.cos(ang), dy = Math.sin(ang), dz = 0.38, nn = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= nn; dy /= nn; dz /= nn;
            len *= s; w *= s;
            int col = RenderUtil.argb(rgb((float) t), 1f) & 0xFFFFFF;
            Vec3d p2 = at(pivot, side, back, sgn, dx * len, dy * len, dz * len);
            tip[i] = p2;

            if (demon) {
                if (lines) RenderUtil.line(b, pivot, p2, RenderUtil.argb(col, 0.9f * op));
                continue;
            }
            double m = blade ? 0.25 : 0.45, px = -dy, py = dx, pl = Math.sqrt(px * px + py * py);
            px /= pl; py /= pl;
            Vec3d v1 = at(pivot, side, back, sgn, dx * len * m + px * w, dy * len * m + py * w, dz * len * m);
            Vec3d v3 = at(pivot, side, back, sgn, dx * len * m - px * w, dy * len * m - py * w, dz * len * m);
            if (!lines) {
                vv(b, pivot, RenderUtil.argb(col, 0.85f * op));
                vv(b, v1, RenderUtil.argb(col, 0.55f * op));
                vv(b, p2, RenderUtil.argb(col, 0.12f * op));
                vv(b, v3, RenderUtil.argb(col, 0.55f * op));
            } else {
                int e = RenderUtil.argb(col, 0.6f * op);
                RenderUtil.line(b, pivot, v1, e);
                RenderUtil.line(b, v1, p2, e);
                RenderUtil.line(b, p2, v3, e);
                RenderUtil.line(b, v3, pivot, e);
            }
        }

        if (demon) { // scalloped membrane between the spokes
            for (int i = 0; i < n - 1; i++) {
                Vec3d mid = pivot.add(tip[i].add(tip[i + 1]).multiply(0.5).subtract(pivot).multiply(0.86));
                int c = RenderUtil.argb(rgb(0.6f) , 1f) & 0xFFFFFF;
                if (!lines) {
                    vv(b, pivot, RenderUtil.argb(c, 0.5f * op)); vv(b, tip[i], RenderUtil.argb(c, 0.25f * op));
                    vv(b, mid, RenderUtil.argb(c, 0.18f * op)); vv(b, mid, RenderUtil.argb(c, 0.18f * op));
                    vv(b, pivot, RenderUtil.argb(c, 0.5f * op)); vv(b, mid, RenderUtil.argb(c, 0.18f * op));
                    vv(b, tip[i + 1], RenderUtil.argb(c, 0.25f * op)); vv(b, tip[i + 1], RenderUtil.argb(c, 0.25f * op));
                } else {
                    RenderUtil.line(b, tip[i], mid, RenderUtil.argb(c, 0.8f * op));
                    RenderUtil.line(b, mid, tip[i + 1], RenderUtil.argb(c, 0.8f * op));
                }
            }
        }
    }
}
