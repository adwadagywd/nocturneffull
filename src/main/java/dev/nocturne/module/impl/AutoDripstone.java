package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * While a trapdoor is in your hand:
 *  - Drop:  every dripstone in your inventory is thrown out in one go (whole stacks per click, the fastest possible)
 *  - Place: dripstone from your other hand is placed on the block you look at, every tick
 */
public class AutoDripstone extends Module {
    private final ModeSetting action = add(new ModeSetting("Action", "Drop", "Drop", "Place"));
    private final ModeSetting items = add(new ModeSetting("Items", "Both", "Both", "Pointed dripstone", "Dripstone block"));
    private final BoolSetting needTrapdoor = add(new BoolSetting("Require trapdoor in hand", true));
    private final ModeSetting amount = add(new ModeSetting("Drop amount", "Whole stacks", "Whole stacks", "Single items"));
    private final NumSetting clicks = add(new NumSetting("Clicks per run", 64, 1, 128, 1));

    public AutoDripstone() {
        super("AutoDripstone", "Holding a trapdoor: drops / places dripstone at maximum speed", Category.WORLD);
        amount.showIf(() -> action.is("Drop"));
        clicks.showIf(() -> action.is("Drop"));
    }

    private boolean wanted(ItemStack st) {
        if (st.isEmpty()) return false;
        return switch (items.get()) {
            case "Pointed dripstone" -> st.isOf(Items.POINTED_DRIPSTONE);
            case "Dripstone block" -> st.isOf(Items.DRIPSTONE_BLOCK);
            default -> st.isOf(Items.POINTED_DRIPSTONE) || st.isOf(Items.DRIPSTONE_BLOCK);
        };
    }

    private static boolean trapdoor(ItemStack st) {
        return st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TrapdoorBlock;
    }

    private boolean armed(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return false;
        return !needTrapdoor.get() || trapdoor(p.getMainHandStack()) || trapdoor(p.getOffHandStack());
    }

    // dropping is done every tick AND every frame: it finishes instantly, repeating only catches new pickups
    @Override public void onPreTick(MinecraftClient mc) { if (action.is("Drop") && armed(mc)) drop(mc); }
    @Override public void onFrame() { if (action.is("Drop") && armed(mc)) drop(mc); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (action.is("Place") && armed(mc)) place(mc);
    }

    private void drop(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        ScreenHandler h = p.playerScreenHandler;
        int budget = clicks.getInt();
        boolean whole = amount.is("Whole stacks");
        for (Slot s : h.slots) {
            if (budget <= 0) break;
            if (s.id < 9 || s.id > 45) continue; // inventory, hotbar and offhand only - never armor / crafting
            ItemStack st = s.getStack();
            if (!wanted(st)) continue;
            if (whole) {
                mc.interactionManager.clickSlot(h.syncId, s.id, 1, SlotActionType.THROW, p); // button 1 = the whole stack
                budget--;
            } else {
                for (int n = st.getCount(); n > 0 && budget > 0; n--) {
                    mc.interactionManager.clickSlot(h.syncId, s.id, 0, SlotActionType.THROW, p);
                    budget--;
                }
            }
        }
    }

    private void place(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        Hand hand = wanted(p.getOffHandStack()) ? Hand.OFF_HAND : wanted(p.getMainHandStack()) ? Hand.MAIN_HAND : null;
        if (hand == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (r.isAccepted()) p.swingHand(hand);
    }
}
