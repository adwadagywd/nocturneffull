package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Coloured screen-edge glow that pulses red when your health is low. */
public class Vignette extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "Custom", "Rainbow"));
    private final ColorSetting hue = add(new ColorSetting("Custom hue", 0.6f));
    private final NumSetting strength = add(new NumSetting("Strength", 0.55, 0.1, 1.0, 0.05));
    private final NumSetting size = add(new NumSetting("Size", 0.22, 0.05, 0.5, 0.01));
    private final BoolSetting pulse = add(new BoolSetting("Low health pulse", true));

    public Vignette() {
        super("Vignette", "Glowing screen edges", Category.VISUALS);
        hue.showIf(() -> colorMode.is("Custom"));
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        Draw.alpha = 1f;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        int rgb = switch (colorMode.get()) {
            case "Custom" -> hue.rgb() & 0xFFFFFF;
            case "Rainbow" -> MathHelper.hsvToRgb((System.currentTimeMillis() % 6000L) / 6000f, 0.7f, 1f);
            default -> Theme.accent() & 0xFFFFFF;
        };
        float str = strength.getFloat();
        if (pulse.get() && mc.player != null && mc.player.getHealth() <= 6f) {
            rgb = 0xFF2D55;
            str = Math.min(1f, str + 0.25f * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0)));
        }
        int n = (int) (Math.min(w, h) * size.get());
        for (int i = 0; i < n; i++) {
            float f = 1f - i / (float) n;
            int a = (int) (255 * str * 0.7f * f * f);
            if (a < 2) continue;
            int c = (a << 24) | rgb;
            ctx.fill(i, i, w - i, i + 1, c);                 // top
            ctx.fill(i, h - 1 - i, w - i, h - i, c);         // bottom
            ctx.fill(i, i + 1, i + 1, h - 1 - i, c);         // left
            ctx.fill(w - 1 - i, i + 1, w - i, h - 1 - i, c); // right
        }
    }
}
