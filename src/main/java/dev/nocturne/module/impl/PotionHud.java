package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Your active potion effects with timers. */
public class PotionHud extends Module {
    private final NumSetting offX = add(new NumSetting("Offset X", 6, 0, 800, 1));
    private final NumSetting offY = add(new NumSetting("Offset Y", 90, 0, 600, 1));

    public PotionHud() { super("PotionHUD", "Active effects with timers", Category.CLIENT); }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (mc.player == null) return;
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        if (list.isEmpty()) return;
        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        int x = offX.getInt(), y = offY.getInt();
        for (StatusEffectInstance in : list) {
            String name = in.getEffectType().value().getName().getString();
            if (in.getAmplifier() > 0) name += " " + (in.getAmplifier() + 1);
            String time;
            if (in.isInfinite()) time = "inf";
            else {
                int s = in.getDuration() / 20;
                time = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
            }
            int w = Draw.width(tr, name) + Draw.width(tr, time) + 38;
            int col = 0xFF000000 | in.getEffectType().value().getColor();
            Draw.round(ctx, x, y, w, 17, 6, 0xC00B0B0F);
            Draw.round(ctx, x + 6, y + 5, 7, 7, 3.5f, col);
            Draw.text(ctx, tr, name, x + 18, y + 5, 0xFFEDEDF2, false);
            Draw.text(ctx, tr, time, x + w - 8 - Draw.width(tr, time), y + 5, 0xFF9A9AA6, false);
            y += 20;
        }
    }
}
