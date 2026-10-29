package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Anime-style radial streaks that rush in from the screen edges when you move fast. */
public class SpeedLines extends Module {
    private final NumSetting threshold = add(new NumSetting("Min speed", 0.30, 0.1, 1.5, 0.05));
    private final NumSetting lines = add(new NumSetting("Lines", 36, 10, 80, 2));
    private final NumSetting length = add(new NumSetting("Length", 0.28, 0.1, 0.5, 0.01));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "White", "White", "Theme", "Rainbow"));
    private final NumSetting opacity = add(new NumSetting("Opacity", 0.55, 0.1, 1.0, 0.05));

    private float amt;
    private long last;

    public SpeedLines() { super("SpeedLines", "Radial speed streaks at high speed", Category.VISUALS); }

    private static float rnd(int i, int salt) {
        double s = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (mc.player == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        Vec3d v = mc.player.getVelocity();
        double sp = Math.sqrt(v.x * v.x + v.z * v.z + (mc.player.isFallFlying() ? v.y * v.y : 0));
        float target = (float) MathHelper.clamp((sp - threshold.get()) / 0.6, 0.0, 1.0);
        amt += (target - amt) * (1f - (float) Math.exp(-6f * dt));
        if (amt < 0.02f) return;

        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        double cx = w / 2.0, cy = h / 2.0, diag = Math.sqrt(cx * cx + cy * cy);
        double time = System.currentTimeMillis() / 1000.0;
        int n = lines.getInt(), segs = 10;
        MatrixStack m = ctx.getMatrices();
        for (int i = 0; i < n; i++) {
            float phase = (float) ((time * (1.3 + rnd(i, 3)) + rnd(i, 1)) % 1.0);
            double ang = rnd(i, 2) * 360.0;
            double len = diag * length.get() * (0.5 + rnd(i, 4)) * (0.5 + 0.5 * amt);
            double outer = diag * (1.02 - phase * 0.35);        // streaks slide towards the centre
            double inner = outer - len;
            float fade = (float) Math.sin(Math.PI * phase);
            int rgb = switch (colorMode.get()) {
                case "Theme" -> Theme.accent() & 0xFFFFFF;
                case "Rainbow" -> MathHelper.hsvToRgb((float) ((rnd(i, 5) + time * 0.1) % 1.0), 0.55f, 1f);
                default -> 0xFFFFFF;
            };
            m.push();
            m.translate(cx, cy, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) ang));
            for (int s = 0; s < segs; s++) {
                float f = (s + 1) / (float) segs; // 0 = inner (transparent) .. 1 = outer (bright)
                int a = (int) (255 * opacity.get() * amt * fade * f * f);
                if (a < 3) continue;
                int x = (int) (inner + len * s / segs);
                Draw.alpha = 1f;
                Draw.rect(ctx, x, 0, (int) Math.ceil(len / segs) + 1, 1, (a << 24) | rgb);
            }
            m.pop();
        }
        Draw.alpha = 1f;
    }
}
