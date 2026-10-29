package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.ItemDisplay;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;

/** Your own armor and its wear, next to the hotbar. */
public class ArmorHud extends Module {
    private final ModeSetting durability = add(new ModeSetting("Durability", "Percent", "Percent", "Bar", "Number", "Off"));
    private final NumSetting offX = add(new NumSetting("Offset X", 98, -300, 300, 1));
    private final NumSetting offY = add(new NumSetting("Offset Y", 48, 20, 300, 1));

    public ArmorHud() { super("ArmorHUD", "Shows your armor pieces and their durability", Category.CLIENT); }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (mc.player == null) return;
        Draw.alpha = 1f;
        int x = mc.getWindow().getScaledWidth() / 2 + offX.getInt();
        int y = mc.getWindow().getScaledHeight() - offY.getInt();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Draw.round(ctx, x - 4, y - 4, slots.length * 20 + 6, 36, 7, 0xA00B0B0F);
        for (int i = 0; i < slots.length; i++)
            ItemDisplay.draw(ctx, mc.textRenderer, mc.player.getEquippedStack(slots[i]), x + i * 20, y, durability.get(), true);
    }
}
