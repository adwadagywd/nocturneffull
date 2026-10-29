package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Four ticks that burst out of the crosshair every time you hit something. */
public class HitMarker extends Module {
    public static HitMarker INSTANCE;
    private long lastHit;

    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "White", "Red", "Custom"));
    private final ColorSetting hue = add(new ColorSetting("Custom hue", 0.1f));
    private final NumSetting size = add(new NumSetting("Size", 6, 3, 16, 1));
    private final NumSetting gap = add(new NumSetting("Gap", 4, 1, 14, 1));
    private final NumSetting thickness = add(new NumSetting("Thickness", 1, 1, 3, 1));
    private final NumSetting duration = add(new NumSetting("Duration", 0.35, 0.1, 1.0, 0.05));

    public HitMarker() {
        super("HitMarker", "Animated hit confirmation around the crosshair", Category.VISUALS);
        INSTANCE = this;
        hue.showIf(() -> colorMode.is("Custom"));
    }

    public static void onHit() {
        if (INSTANCE != null && INSTANCE.isEnabled()) INSTANCE.lastHit = System.currentTimeMillis();
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (lastHit == 0) return;
        float age = (System.currentTimeMillis() - lastHit) / 1000f / duration.getFloat();
        if (age >= 1f) return;
        float e = 1f - (1f - age) * (1f - age);
        int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
        int color = switch (colorMode.get()) {
            case "White" -> 0xFFFFFFFF;
            case "Red" -> 0xFFFF4D6D;
            case "Custom" -> hue.rgb();
            default -> Theme.accent();
        };
        int t = thickness.getInt();
        float g = gap.getFloat() + e * 5f;
        Draw.alpha = 1f - age;
        MatrixStack m = ctx.getMatrices();
        for (int k = 0; k < 4; k++) {
            m.push();
            m.translate(cx, cy, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45f + 90f * k));
            Draw.rect(ctx, Math.round(g), -(t / 2), size.getInt(), t, color);
            m.pop();
        }
        Draw.alpha = 1f;
    }
}
