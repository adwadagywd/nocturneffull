package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Custom animated crosshair. The vanilla one is hidden through an optional mixin. */
public class Crosshair extends Module {
    public static Crosshair INSTANCE;

    private final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Dot", "Circle", "Corners"));
    private final NumSetting size = add(new NumSetting("Size", 5, 2, 16, 1));
    private final NumSetting gap = add(new NumSetting("Gap", 3, 0, 12, 1));
    private final NumSetting thickness = add(new NumSetting("Thickness", 1, 1, 3, 1));
    private final BoolSetting dynamic = add(new BoolSetting("Dynamic", true));
    private final BoolSetting outline = add(new BoolSetting("Outline", true));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "White", "Custom", "Rainbow"));
    private final ColorSetting hue = add(new ColorSetting("Custom hue", 0.3f));
    private final BoolSetting targetColor = add(new BoolSetting("Red on target", true));
    private final BoolSetting cooldown = add(new BoolSetting("Cooldown arc", false));
    private final BoolSetting hideVanilla = add(new BoolSetting("Hide vanilla", true));

    private float spread, flash;
    private long lastNs;

    public Crosshair() {
        super("Crosshair", "Custom crosshair with dynamic spread", Category.VISUALS);
        INSTANCE = this;
        hue.showIf(() -> colorMode.is("Custom"));
    }

    public static boolean hidesVanilla() {
        Crosshair c = INSTANCE;
        return c != null && c.isEnabled() && c.hideVanilla.get();
    }

    private void bar(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (outline.get()) Draw.rect(ctx, x - 1, y - 1, w + 2, h + 2, 0x99000000);
        Draw.rect(ctx, x, y, w, h, color);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        PlayerEntity p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        Draw.alpha = 1f;

        float cd = p.getAttackCooldownProgress(0f);
        double sp = Math.hypot(p.getVelocity().x, p.getVelocity().z);
        float target = dynamic.get() ? (float) Math.min(1.0, sp * 4.0) * 4f + (cd < 1f ? (1f - cd) * 3f : 0f) : 0f;
        spread += (target - spread) * (1f - (float) Math.exp(-14f * dt));
        flash += (((mc.targetedEntity != null) ? 1f : 0f) - flash) * (1f - (float) Math.exp(-16f * dt));

        int base = switch (colorMode.get()) {
            case "White" -> 0xFFFFFFFF;
            case "Custom" -> hue.rgb();
            case "Rainbow" -> 0xFF000000 | MathHelper.hsvToRgb((System.currentTimeMillis() % 4000L) / 4000f, 0.7f, 1f);
            default -> Theme.accent();
        };
        int color = targetColor.get() ? Draw.lerp(base, 0xFFFF4D6D, flash) : base;

        int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
        int t = thickness.getInt(), s = size.getInt();
        int g = Math.round(gap.getFloat() + spread);

        switch (style.get()) {
            case "Dot" -> {
                int d = t + 2;
                Draw.round(ctx, cx - d / 2, cy - d / 2, d, d, d / 2f, color);
            }
            case "Circle" -> {
                Draw.ring(ctx, cx, cy, s + g, t, color, 1f);
            }
            case "Corners" -> {
                int o = s + g;
                for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
                    int ex = cx + sx * o, ey = cy + sy * o;
                    bar(ctx, sx < 0 ? ex : ex - s, sy < 0 ? ey : ey - t, s, t, color);   // horizontal arm
                    bar(ctx, sx < 0 ? ex : ex - t, sy < 0 ? ey : ey - s, t, s, color);   // vertical arm
                }
            }
            default -> {
                bar(ctx, cx - g - s, cy - t / 2, s, t, color);
                bar(ctx, cx + g + 1, cy - t / 2, s, t, color);
                bar(ctx, cx - t / 2, cy - g - s, t, s, color);
                bar(ctx, cx - t / 2, cy + g + 1, t, s, color);
            }
        }
        if (cooldown.get() && cd < 1f) Draw.ring(ctx, cx, cy, s + g + 5, 1, Theme.accent(), cd);
    }
}
