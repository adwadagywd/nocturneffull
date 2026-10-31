package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Pick hotbar slots in the settings - items in those slots can no longer be dropped:
 * Q / Ctrl+Q do nothing on a locked slot, and in inventory screens a locked slot cannot be thrown out
 * (Q over the slot, or picking the item up and clicking outside the window).
 */
public class DropLock extends Module {
    public static DropLock INSTANCE;

    private final BoolSetting[] slots = new BoolSetting[9];
    private final BoolSetting marks;

    /** true while the item on the cursor was picked up from a locked slot */
    private static boolean fromLocked;

    public DropLock() {
        super("DropLock", "Hotbar slots you can not drop items from", Category.MISC);
        INSTANCE = this;
        for (int i = 0; i < 9; i++) slots[i] = add(new BoolSetting("Lock slot " + (i + 1), false));
        marks = add(new BoolSetting("Show lock marks", true));
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }

    public static boolean locked(int hotbarIndex) {
        return active() && hotbarIndex >= 0 && hotbarIndex < 9 && INSTANCE.slots[hotbarIndex].get();
    }

    @Override protected void onDisable() { fromLocked = false; }

    /** the Q key: the queued presses are eaten before the game can turn them into a drop */
    @Override
    public void onPreTick(MinecraftClient mc) {
        if (mc.player == null) return;
        if (locked(mc.player.getInventory().selectedSlot)) {
            while (mc.options.dropKey.wasPressed()) { /* discard */ }
        }
    }

    private static boolean lockedSlot(PlayerEntity p, int slotId) {
        ScreenHandler h = p.currentScreenHandler;
        if (h == null || slotId < 0 || slotId >= h.slots.size()) return false;
        Slot s = h.slots.get(slotId);
        return s.inventory == p.getInventory() && locked(s.getIndex());
    }

    /** called by ClientPlayerInteractionManagerMixin for every inventory click - true = swallow the click */
    public static boolean blocksClick(int slotId, SlotActionType type, PlayerEntity p) {
        if (!active() || p == null) return false;
        if (type == SlotActionType.THROW) return lockedSlot(p, slotId);
        if (type == SlotActionType.PICKUP) {
            ItemStack cursor = p.currentScreenHandler == null ? ItemStack.EMPTY : p.currentScreenHandler.getCursorStack();
            if (slotId < 0) return fromLocked && !cursor.isEmpty(); // clicking outside the window = dropping the cursor stack
            fromLocked = cursor.isEmpty() && lockedSlot(p, slotId);
        }
        return false;
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (!marks.get() || mc.player == null) return;
        Draw.alpha = 1f;
        int left = mc.getWindow().getScaledWidth() / 2 - 91, bottom = mc.getWindow().getScaledHeight();
        for (int i = 0; i < 9; i++) {
            if (!slots[i].get()) continue;
            int x = left + 3 + i * 20;
            Draw.round(ctx, x, bottom - 5, 16, 2, 1f, 0xFFFF5C6C);   // red bar under the slot
            Draw.round(ctx, x + 11, bottom - 17, 5, 5, 1.5f, 0xE0FF5C6C); // small lock body in the corner
            Draw.round(ctx, x + 12, bottom - 19, 3, 3, 1.5f, 0xE0FFFFFF); // shackle
        }
    }
}
