package dev.nocturne.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** Draws an item with its durability (percent / bar / number) underneath. */
public final class ItemDisplay {
    private ItemDisplay() {}

    /** @param mode Percent, Bar, Number or Off */
    public static void draw(DrawContext ctx, TextRenderer tr, ItemStack st, int x, int y, String mode, boolean icon) {
        if (st.isEmpty()) {
            Draw.round(ctx, x, y, 16, 16, 4, 0x22FFFFFF);
            return;
        }
        if (icon) ctx.drawItem(st, x, y);
        if (!st.isDamageable() || mode.equals("Off")) return;

        int max = Math.max(1, st.getMaxDamage());
        int left = max - st.getDamage();
        float ratio = MathHelper.clamp(left / (float) max, 0f, 1f);
        int color = 0xFF000000 | MathHelper.hsvToRgb(ratio * 0.33f, 0.8f, 1f);

        if (mode.equals("Bar")) {
            Draw.round(ctx, x, y + 18, 16, 3, 1.5f, Theme.track());
            Draw.round(ctx, x, y + 18, Math.max(2, Math.round(16 * ratio)), 3, 1.5f, color);
        } else {
            String s = mode.equals("Number") ? Integer.toString(left) : Math.round(ratio * 100) + "%";
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x + 8, y + 18, 0);
            m.scale(0.6f, 0.6f, 1f);
            Draw.text(ctx, tr, s, -Draw.width(tr, s) / 2, 0, color, false);
            m.pop();
        }
    }
}
