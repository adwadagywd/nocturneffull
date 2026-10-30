package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.ItemDisplay;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Animated card about whatever you are looking at: health, armor, armor wear, held items. */
public class TargetHud extends Module {
    private final BoolSetting healthText = add(new BoolSetting("Health text", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", true));
    private final BoolSetting armorValue = add(new BoolSetting("Armor points", true));
    private final BoolSetting showArmor = add(new BoolSetting("Armor pieces", true));
    private final BoolSetting held = add(new BoolSetting("Held items", false));
    private final ModeSetting durability = add(new ModeSetting("Durability", "Percent", "Percent", "Bar", "Number", "Off"));
    private final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.7, 1.6, 0.05));
    private final NumSetting offX = add(new NumSetting("Offset X", 22, -400, 400, 1));
    private final NumSetting offY = add(new NumSetting("Offset Y", 12, -400, 400, 1));

    private LivingEntity last;
    private float alpha, healthAnim;
    private long lastNs;

    public TargetHud() {
        super("TargetHUD", "Health, armor and armor wear of the entity you look at", Category.CLIENT);
        durability.showIf(() -> showArmor.get() || held.get());
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;

        Entity t = mc.targetedEntity;
        LivingEntity cur = (t instanceof LivingEntity le && le.isAlive()) ? le : null;
        if (cur != null && cur != last) { last = cur; healthAnim = cur.getHealth(); }
        alpha += ((cur != null ? 1f : 0f) - alpha) * (1f - (float) Math.exp(-12f * dt));
        if (last == null || alpha < 0.03f) { Draw.alpha = 1f; return; }

        float hp = last.getHealth(), max = Math.max(1f, last.getMaxHealth());
        healthAnim += (hp - healthAnim) * (1f - (float) Math.exp(-8f * dt));
        float ratio = MathHelper.clamp(healthAnim / max, 0f, 1f);

        List<ItemStack> items = new ArrayList<>();
        if (held.get()) items.add(last.getMainHandStack());
        if (showArmor.get()) {
            items.add(last.getEquippedStack(EquipmentSlot.HEAD));
            items.add(last.getEquippedStack(EquipmentSlot.CHEST));
            items.add(last.getEquippedStack(EquipmentSlot.LEGS));
            items.add(last.getEquippedStack(EquipmentSlot.FEET));
        }
        if (held.get()) items.add(last.getOffHandStack());
        boolean row = !items.isEmpty();

        TextRenderer tr = mc.textRenderer;
        float sc = scale.getFloat();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int w = 158, h = 44 + (row ? 34 : 0);
        float x = sw / 2f + offX.getInt() + (1f - alpha) * 10f, y = sh / 2f + offY.getInt();

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(sc, sc, 1f);
        Draw.alpha = alpha;
        Draw.shadow(ctx, 0, 0, w, h, 9);
        Draw.surface(ctx, 0, 0, w, h, 9);
        Draw.round(ctx, 7, 9, 3, 18, 1.5f, Theme.accent());
        Draw.bold(ctx, tr, Draw.trim(tr, last.getName().getString(), w - 62), 15, 8, Theme.text(), false);
        if (healthText.get()) {
            String s = String.format(Locale.ROOT, "%.1f", hp);
            Draw.text(ctx, tr, s, w - 10 - Draw.width(tr, s), 8, 0xFF000000 | MathHelper.hsvToRgb(ratio * 0.33f, 0.7f, 1f), false);
        }
        Draw.round(ctx, 15, 21, w - 25, 6, 3, Theme.track());
        Draw.round(ctx, 15, 21, Math.max(5, Math.round((w - 25) * ratio)), 6, 3, 0xFF000000 | MathHelper.hsvToRgb(ratio * 0.33f, 0.75f, 1f));
        if (armorValue.get()) Draw.text(ctx, tr, "Armor " + last.getArmor(), 15, 31, Theme.dim(), false);
        if (distance.get()) {
            String d = String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(last));
            Draw.text(ctx, tr, d, w - 10 - Draw.width(tr, d), 31, Theme.dim(), false);
        }
        if (row) {
            int cell = 22;
            int start = (w - items.size() * cell) / 2 + 3;
            for (int i = 0; i < items.size(); i++)
                ItemDisplay.draw(ctx, tr, items.get(i), start + i * cell, 45, durability.get(), alpha > 0.6f);
        }
        m.pop();
        Draw.alpha = 1f;
    }
}
