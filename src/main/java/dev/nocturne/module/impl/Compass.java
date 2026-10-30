package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Bearing strip at the top of the screen: N / NE / E ... with degree ticks and a fade towards the edges. */
public class Compass extends Module {
    private final NumSetting offY = add(new NumSetting("Offset Y", 8, 0, 200, 1));
    private final NumSetting width = add(new NumSetting("Width", 240, 120, 500, 10));
    private final BoolSetting degrees = add(new BoolSetting("Show degrees", true));

    private static final String[] CARD = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public Compass() { super("Compass", "Bearing strip with cardinal directions", Category.CLIENT); }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (mc.player == null) return;
        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int w = width.getInt(), h = 22, x = sw / 2 - w / 2, y = offY.getInt();
        double bearing = ((MathHelper.wrapDegrees(mc.player.getYaw()) + 180.0) % 360.0 + 360.0) % 360.0; // 0 = north
        double ppd = w / 120.0; // 120 degrees visible
        int cx = sw / 2;

        Draw.shadow(ctx, x, y, w, h, 9);
        Draw.surface(ctx, x, y, w, h, 9, 0xC00B0B0F, 0x30FFFFFF);
        ctx.enableScissor(x + 6, y, x + w - 6, y + h);
        int start = (int) Math.floor(bearing - 62), end = (int) Math.ceil(bearing + 62);
        for (int d = start; d <= end; d++) {
            if (d % 5 != 0) continue;
            double dx = (d - bearing) * ppd;
            float fade = (float) MathHelper.clamp(1.0 - Math.pow(Math.abs(dx) / (w / 2.0), 2.2), 0.0, 1.0);
            if (fade < 0.03f) continue;
            int norm = ((d % 360) + 360) % 360;
            int px = (int) Math.round(cx + dx);
            if (norm % 45 == 0) {
                String label = CARD[norm / 45];
                int col = label.equals("N") ? 0xFFFF5566 : (label.length() == 1 ? Theme.text() : Theme.soft());
                int lw = Draw.width(tr, label);
                Draw.bold(ctx, tr, label, px - lw / 2, y + 4, Draw.scaleA(col, fade), false);
            } else if (norm % 15 == 0) {
                Draw.rect(ctx, px, y + 5, 1, 7, Draw.scaleA(Theme.dim(), fade));
            } else {
                Draw.rect(ctx, px, y + 8, 1, 3, Draw.scaleA(Theme.dim(), fade * 0.7f));
            }
        }
        ctx.disableScissor();
        // centre marker
        Draw.round(ctx, cx - 1, y + h - 7, 2, 5, 1, Theme.accent());
        if (degrees.get()) {
            String s = ((int) Math.round(bearing) % 360) + "\u00B0";
            Draw.text(ctx, tr, s, cx - Draw.width(tr, s) / 2, y + h + 3, Theme.dim(), true);
        }
    }
}
