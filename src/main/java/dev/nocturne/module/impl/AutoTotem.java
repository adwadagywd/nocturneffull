package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    private final NumSetting health = add(new NumSetting("Health", 20, 1, 20, 1));
    private final NumSetting delay = add(new NumSetting("Delay (ticks)", 1, 0, 10, 1));
    private final BoolSetting hotbarFirst = add(new BoolSetting("Prefer hotbar", true));
    private final BoolSetting counter = add(new BoolSetting("Show count", true));
    private int timer;

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in your offhand (Health 20 = always)", Category.COMBAT);
    }

    private int find(PlayerEntity p) {
        int[][] order = hotbarFirst.get() ? new int[][]{{0, 9}, {9, 36}} : new int[][]{{9, 36}, {0, 9}};
        for (int[] r : order)
            for (int i = r[0]; i < r[1]; i++)
                if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        return -1;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { timer = 0; return; }
        if (p.getHealth() + p.getAbsorptionAmount() > health.get()) return;
        if (timer++ < delay.getInt()) return;
        timer = 0;

        int slot = find(p);
        if (slot < 0) return;
        int id = slot < 9 ? slot + 36 : slot; // inventory index -> player screen-handler slot
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, id, 40, SlotActionType.SWAP, p); // 40 = offhand
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (!counter.get() || mc.player == null) return;
        Draw.alpha = 1f;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) n += mc.player.getOffHandStack().getCount();
        TextRenderer tr = mc.textRenderer;
        int x = mc.getWindow().getScaledWidth() / 2 + 98, y = mc.getWindow().getScaledHeight() - 21;
        ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x, y);
        Draw.text(ctx, tr, "x" + n, x + 18, y + 4, n > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);
    }
}
