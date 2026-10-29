package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;

public class Keystrokes extends Module {
    private final NumSetting offX = add(new NumSetting("Offset X", 10, 0, 600, 1));
    private final NumSetting offY = add(new NumSetting("Offset Y", 90, 0, 600, 1));
    private final BoolSetting mouse = add(new BoolSetting("Mouse buttons", true));

    private final float[] st = new float[7];
    private long last;

    public Keystrokes() { super("Keystrokes", "Animated WASD / mouse display", Category.CLIENT); }

    private void key(DrawContext ctx, TextRenderer tr, String label, int x, int y, int w, int h, float a) {
        Draw.round(ctx, x, y, w, h, 6, Draw.lerp(0xB00B0B0F, Theme.accentA(0xD0), a));
        Draw.text(ctx, tr, label, x + (w - Draw.width(tr, label)) / 2, y + (h - 8) / 2, Draw.lerp(0xFFBDBDC9, 0xFF0B0B0F, a), false);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        GameOptions o = mc.options;
        boolean[] down = {o.forwardKey.isPressed(), o.leftKey.isPressed(), o.backKey.isPressed(), o.rightKey.isPressed(),
                o.jumpKey.isPressed(), o.attackKey.isPressed(), o.useKey.isPressed()};
        for (int i = 0; i < st.length; i++) st[i] += ((down[i] ? 1f : 0f) - st[i]) * (1f - (float) Math.exp(-18f * dt));

        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        int x = offX.getInt(), y = offY.getInt(), s = 22, g = 3;
        int full = 3 * s + 2 * g;
        key(ctx, tr, "W", x + s + g, y, s, s, st[0]);
        key(ctx, tr, "A", x, y + s + g, s, s, st[1]);
        key(ctx, tr, "S", x + s + g, y + s + g, s, s, st[2]);
        key(ctx, tr, "D", x + 2 * (s + g), y + s + g, s, s, st[3]);
        key(ctx, tr, "SPACE", x, y + 2 * (s + g), full, 14, st[4]);
        if (mouse.get()) {
            int my = y + 2 * (s + g) + 17, half = full / 2 - 1;
            key(ctx, tr, "LMB", x, my, half, 16, st[5]);
            key(ctx, tr, "RMB", x + full - half, my, half, 16, st[6]);
        }
    }
}
