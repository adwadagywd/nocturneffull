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

/** Glowing orbs with comet tails circling you, plus a soft halo above your head. */
public class Orbitals extends Module {
    private final NumSetting count = add(new NumSetting("Orbs", 4, 1, 10, 1));
    private final NumSetting radius = add(new NumSetting("Radius", 0.9, 0.4, 2.0, 0.05));
    private final NumSetting speed = add(new NumSetting("Speed", 1.4, 0.2, 4.0, 0.1));
    private final NumSetting size = add(new NumSetting("Size", 0.09, 0.03, 0.3, 0.01));
    private final NumSetting tail = add(new NumSetting("Tail length", 14, 0, 30, 1));
    private final ModeSetting pattern = add(new ModeSetting("Pattern", "Helix", "Helix", "Ring", "Gyro", "Atom", "Tornado", "Infinity", "Crown", "Pulse"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Gradient", "Theme", "Gradient", "Rainbow"));
    private final BoolSetting halo = add(new BoolSetting("Halo", true));
    private final BoolSetting others = add(new BoolSetting("Other players", false));
    private final BoolSetting hideFirst = add(new BoolSetting("Hide in first person", true));

    public Orbitals() { super("Orbitals", "Glowing orbs circling the player", Category.VISUALS); }

    private int rgb(float t, double time) {
        switch (colorMode.get()) {
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.1 + t * 0.6) % 1.0), 0.65f, 1f);
            case "Gradient": return Draw.lerp(Theme.accent(), Theme.accent2(), t) & 0xFFFFFF;
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    private Vec3d orbPos(Vec3d base, double h, int i, int n, double ang, double time) {
        double r = radius.get();
        double a = ang + i * (Math.PI * 2 / n);
        switch (pattern.get()) {
            case "Ring": return base.add(Math.cos(a) * r, h * 0.55, Math.sin(a) * r);
            case "Gyro": {
                double tilt = (i % 2 == 0 ? 1 : -1) * 0.9;
                double x = Math.cos(a) * r, y = Math.sin(a) * r * Math.sin(tilt), z = Math.sin(a) * r * Math.cos(tilt);
                return base.add(x, h * 0.55 + y, z);
            }
            case "Atom": { // three tilted electron orbits
                int plane = i % 3;
                double phi = plane * Math.PI / 3, tilt = 1.0;
                double u = Math.cos(a) * r, w = Math.sin(a) * r;
                double y = w * Math.sin(tilt), v = w * Math.cos(tilt);
                return base.add(u * Math.cos(phi) - v * Math.sin(phi), h * 0.55 + y, u * Math.sin(phi) + v * Math.cos(phi));
            }
            case "Tornado": { // funnel that widens towards the top, orbs drift up and down it
                double yf = 0.5 + 0.5 * Math.sin(time * 0.9 + i * 2.1);
                double rr = r * (0.35 + 0.9 * yf);
                return base.add(Math.cos(a * 1.4) * rr, h * (0.05 + 0.95 * yf), Math.sin(a * 1.4) * rr);
            }
            case "Infinity": { // figure-eight (lemniscate) around the body, slowly turning
                double d = 1.0 + Math.sin(a) * Math.sin(a);
                double x = r * 1.35 * Math.cos(a) / d, z = r * 1.35 * Math.sin(a) * Math.cos(a) / d;
                double rot = time * 0.4, c = Math.cos(rot), sn = Math.sin(rot);
                return base.add(x * c - z * sn, h * 0.55 + Math.sin(a * 2) * 0.15, x * sn + z * c);
            }
            case "Crown": // tight ring floating above the head
                return base.add(Math.cos(a) * r * 0.55, h + 0.3 + Math.sin(a * 2 + i) * 0.06, Math.sin(a) * r * 0.55);
            case "Pulse": { // breathing ring with a wavy edge
                double rr = r * (0.75 + 0.45 * Math.sin(time * 2.0));
                return base.add(Math.cos(a) * rr, h * 0.55 + Math.sin(a * 3 + time * 2) * 0.1, Math.sin(a) * rr);
            }
            default: return base.add(Math.cos(a) * r, h * (0.12 + 0.76 * (0.5 + 0.5 * Math.sin(time * 0.8 + i * 1.3))), Math.sin(a) * r);
        }
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || mc.player == null || !RenderUtil.valid) return;
        boolean first = mc.options.getPerspective().isFirstPerson();
        double time = System.currentTimeMillis() / 1000.0;
        int n = count.getInt(), tl = tail.getInt();
        float sp = speed.getFloat();

        RenderUtil.setupBlend3d(2f);
        for (int pass = 0; pass < 2; pass++) {
            boolean glowPass = pass == 0;
            BufferBuilder buf = RenderUtil.begin(glowPass ? VertexFormat.DrawMode.QUADS : VertexFormat.DrawMode.DEBUG_LINES);
            for (PlayerEntity p : mc.world.getPlayers()) {
                boolean self = p == mc.player;
                if (!self && !others.get()) continue;
                if (self && hideFirst.get() && first) continue;
                if (p.isInvisible() || p.isSpectator()) continue;
                Vec3d base = p.getLerpedPos(RenderUtil.tickDelta);
                double h = p.getHeight();
                double ang = time * sp;
                for (int i = 0; i < n; i++) {
                    float t = n == 1 ? 0f : i / (float) (n - 1);
                    int col = rgb(t, time);
                    Vec3d c = orbPos(base, h, i, n, ang, time);
                    double s = size.get();
                    if (glowPass) {
                        RenderUtil.poly(buf, c, s * 3.4, 14, 0, 1.0, RenderUtil.argb(col, 0.35f), RenderUtil.argb(col, 0f));
                        RenderUtil.poly(buf, c, s, 12, 0, 1.0, RenderUtil.argb(0xFFFFFF, 0.95f), RenderUtil.argb(col, 0.9f));
                    } else {
                        Vec3d prev = c;
                        for (int k = 1; k <= tl; k++) {
                            Vec3d pt = orbPos(base, h, i, n, ang - k * 0.075 * Math.max(0.5, sp), time - k * 0.0375);
                            RenderUtil.line(buf, prev, pt, RenderUtil.argb(col, (1f - k / (float) (tl + 1)) * 0.8f));
                            prev = pt;
                        }
                    }
                }
                if (halo.get()) {
                    Vec3d top = base.add(0, h + 0.28 + Math.sin(time * 1.6) * 0.03, 0);
                    int col = rgb(0.5f, time);
                    if (glowPass) RenderUtil.disc(buf, top, 0.24, 0.36, 40, RenderUtil.argb(col, 0.55f), RenderUtil.argb(col, 0.05f));
                    else RenderUtil.ring(buf, top, 0.3, 40, RenderUtil.argb(0xFFFFFF, 0.9f));
                }
            }
            RenderUtil.finish(buf);
        }
        RenderUtil.restore();
    }
}
