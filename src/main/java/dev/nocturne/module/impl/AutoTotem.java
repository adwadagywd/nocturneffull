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
    private final ModeSetting mode = add(new ModeSetting("Mode", "Silent", "Silent", "Legit"));
    private final NumSetting legitSpeed = add(new NumSetting("Legit step (ticks)", 1, 0, 4, 1));
    private final NumSetting health = add(new NumSetting("Health", 20, 1, 20, 1));
    private final NumSetting delay = add(new NumSetting("Delay (ticks)", 1, 0, 10, 1));
    private final BoolSetting hotbarFirst = add(new BoolSetting("Prefer hotbar", true));
    private final BoolSetting counter = add(new BoolSetting("Show count", true));
    private int timer;
    private int legitPhase, legitWait; // legit mode: 0 idle, 1 inventory open (swap next), 2 swapped (close next)

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in your offhand (Health 20 = always)", Category.COMBAT);
        legitSpeed.showIf(() -> mode.is("Legit"));
    }

    @Override protected void onDisable() { legitPhase = 0; }

    private void swap(MinecraftClient mc, PlayerEntity p, int slot) {
        int id = slot < 9 ? slot + 36 : slot; // inventory index -> player screen-handler slot
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, id, 40, SlotActionType.SWAP, p); // 40 = offhand
    }

    /**
     * Legit mode: the inventory really opens, the totem goes to the offhand, the inventory closes.
     * One step every "Legit step" ticks (0 = all in the same tick, 1 = open / swap / close on three following ticks).
     */
    private void legitStep(MinecraftClient mc, PlayerEntity p) {
        if (!(mc.currentScreen instanceof InventoryScreen)) { legitPhase = 0; return; } // you closed it yourself
        if (legitWait > 0) { legitWait--; return; }
        if (legitPhase == 1) {
            int slot = find(p);
            if (slot >= 0 && !p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) swap(mc, p, slot);
            legitPhase = 2;
            legitWait = legitSpeed.getInt() - 1;
        } else if (legitPhase == 2) {
            mc.currentScreen.close(); // sends the close packet like a real player
            legitPhase = 0;
        }
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
        if (p == null || mc.interactionManager == null) { legitPhase = 0; return; }
        if (legitPhase != 0) { legitStep(mc, p); return; }
        if (mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { timer = 0; return; }
        if (p.getHealth() + p.getAbsorptionAmount() > health.get()) return;
        if (timer++ < delay.getInt()) return;
        timer = 0;

        int slot = find(p);
        if (slot < 0) return;
        if (mode.is("Legit") && mc.currentScreen == null && !p.isCreative()) {
            mc.setScreen(new InventoryScreen(p));
            legitPhase = 1;
            legitWait = legitSpeed.getInt() - 1;
            if (legitSpeed.getInt() == 0) { legitStep(mc, p); legitStep(mc, p); } // everything in this tick
            return;
        }
        swap(mc, p, slot);
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
