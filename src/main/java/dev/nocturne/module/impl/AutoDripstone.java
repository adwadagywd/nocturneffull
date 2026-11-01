package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * HOLD the right mouse button with the crosshair on a placed trapdoor (or on the dripstone hanging under it),
 * holding a trapdoor and dripstone. Nothing else - no crouching, no clicking. The module repeats:
 *
 *   PLACE dripstone under the trapdoor   (the module sneaks by itself for that one tick)
 *   OPEN + CLOSE the trapdoor in the same tick -> the dripstone loses its support and falls
 *
 * One cycle is "Cycle ticks" long (3 = the fastest that works on most servers; the dripstone needs 2 ticks to fall).
 * Vanilla right-click is switched off while the module works, so nothing toggles the trapdoor by accident.
 */
public class AutoDripstone extends Module {
    private final ModeSetting items = add(new ModeSetting("Items", "Both", "Both", "Pointed dripstone", "Dripstone block"));
    private final NumSetting cycleTicks = add(new NumSetting("Cycle ticks", 3, 2, 8, 1));
    private final BoolSetting waitFree = add(new BoolSetting("Wait until slot is free", false));
    private final BoolSetting needBoth = add(new BoolSetting("Require trapdoor + dripstone in hands", true));
    private final BoolSetting debug = add(new BoolSetting("Debug (action bar)", false));

    private enum Phase { PLACE, OPENCLOSE }

    private Phase phase = Phase.PLACE;
    private BlockHitResult anchor;
    private int delay, gate, cycles;
    private boolean forcedSneak, takenOver;
    private String lastMsg = "";

    public AutoDripstone() {
        super("AutoDripstone", "Hold right mouse on a trapdoor: place dripstone, open + close, repeat", Category.WORLD);
    }

    @Override protected void onEnable() { reset(); }

    @Override
    protected void onDisable() {
        reset();
        giveBackKeys();
    }

    private void reset() {
        phase = Phase.PLACE;
        anchor = null;
        delay = 0;
        gate = 0;
    }

    private void giveBackKeys() {
        if (forcedSneak || takenOver) {
            KeyBinding.updatePressedStates(); // hand the keys back to the real keyboard / mouse state
            forcedSneak = false;
            takenOver = false;
        }
    }

    private void say(MinecraftClient mc, String msg, boolean force) {
        if (!debug.get() || mc.player == null || (!force && msg.equals(lastMsg))) return;
        lastMsg = msg;
        mc.player.sendMessage(Text.literal("[AutoDripstone] " + msg), true);
    }

    // ------------------------------------------------------------------ helpers

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

    private Hand dripHand(PlayerEntity p) {
        if (wanted(p.getOffHandStack())) return Hand.OFF_HAND;
        if (wanted(p.getMainHandStack())) return Hand.MAIN_HAND;
        return null;
    }

    private boolean armed(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return false;
        if (dripHand(p) == null) return false;
        return !needBoth.get() || trapdoor(p.getMainHandStack()) || trapdoor(p.getOffHandStack());
    }

    /** the real state of the right mouse button (vanilla's own use key is switched off while we work) */
    private static boolean rightHeld(MinecraftClient mc) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private BlockPos slot() { return anchor.getBlockPos().offset(anchor.getSide()); }

    private boolean isTrapdoor(MinecraftClient mc) {
        return mc.world.getBlockState(anchor.getBlockPos()).getBlock() instanceof TrapdoorBlock;
    }

    private boolean isOpen(MinecraftClient mc) {
        BlockState st = mc.world.getBlockState(anchor.getBlockPos());
        return st.getBlock() instanceof TrapdoorBlock && st.get(TrapdoorBlock.OPEN);
    }

    private boolean slotFree(MinecraftClient mc) {
        BlockState st = mc.world.getBlockState(slot());
        return st.isAir() || st.isReplaceable();
    }

    private boolean slotHasDrip(MinecraftClient mc) {
        return mc.world.getBlockState(slot()).isOf(Blocks.POINTED_DRIPSTONE);
    }

    /** the trapdoor the crosshair is on - or the one a dripstone is hanging under (the ray hits the dripstone first) */
    private BlockHitResult trapdoorAt(MinecraftClient mc, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState st = mc.world.getBlockState(pos);
        if (st.getBlock() instanceof TrapdoorBlock) return hit;
        if (st.isOf(Blocks.POINTED_DRIPSTONE)) {
            BlockPos up = pos.up();
            if (mc.world.getBlockState(up).getBlock() instanceof TrapdoorBlock)
                return new BlockHitResult(new Vec3d(up.getX() + 0.5, up.getY(), up.getZ() + 0.5), Direction.DOWN, up, false);
        }
        return null;
    }

    // ------------------------------------------------------------------ logic

    /**
     * Runs before the game reads the keys: takes the right mouse button away from vanilla and presses sneak for
     * the placing tick only (the sneak packet then reaches the server before our click in the same tick).
     */
    @Override
    public void onPreTick(MinecraftClient mc) {
        boolean active = rightHeld(mc) && armed(mc);

        boolean sneak = active && anchor != null && phase == Phase.PLACE && delay == 0;
        if (sneak) {
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
        } else if (forcedSneak) {
            KeyBinding.updatePressedStates();
            forcedSneak = false;
            takenOver = false; // updatePressedStates() gave the use key back too - it is taken again just below
        }

        if (active) {
            mc.options.useKey.setPressed(false);
            while (mc.options.useKey.wasPressed()) { /* eat queued clicks */ }
            takenOver = true;
        } else if (takenOver) {
            KeyBinding.updatePressedStates();
            takenOver = false;
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (!rightHeld(mc) || !armed(mc)) {
            if (anchor != null) say(mc, "stopped (hold right mouse, trapdoor + dripstone in hands)", false);
            reset();
            return;
        }
        PlayerEntity p = mc.player;

        BlockHitResult seen = mc.crosshairTarget instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK ? trapdoorAt(mc, h) : null;
        if (anchor == null || (seen != null && !seen.getBlockPos().equals(anchor.getBlockPos()))) {
            if (seen == null) { say(mc, "aim at a trapdoor (or the dripstone hanging under it)", false); return; }
            anchor = seen;
            delay = 0;
            gate = 0;
            phase = slotHasDrip(mc) || isOpen(mc) ? Phase.OPENCLOSE : Phase.PLACE; // continue from what is there
            say(mc, "trapdoor found, starting with " + phase + " (slot " + slot().toShortString() + ", side " + anchor.getSide() + ")", true);
        }
        if (!isTrapdoor(mc)) { reset(); return; }

        if (delay > 0) { delay--; return; }

        switch (phase) {
            case PLACE -> {
                if (!forcedSneak) return; // the sneak key was not down at the start of this tick yet - next tick
                if (waitFree.get() && !slotFree(mc) && ++gate < 40) { say(mc, "waiting: slot under the trapdoor is not free", false); return; }
                gate = 0;
                Hand hand = dripHand(p);
                if (hand != null) say(mc, "#" + (++cycles) + " PLACE -> " + place(mc, p, hand), true);
                phase = Phase.OPENCLOSE;
                delay = 0;
            }
            case OPENCLOSE -> {
                if (!slotHasDrip(mc) && !isOpen(mc)) { phase = Phase.PLACE; delay = 0; return; } // nothing hangs there: place again
                boolean wasOpen = isOpen(mc);
                use(mc, Hand.MAIN_HAND, anchor, true);               // open (or close, if it was left open)
                if (!wasOpen) use(mc, Hand.MAIN_HAND, anchor, false); // ...and close again in the same tick
                say(mc, "OPEN+CLOSE", false);
                phase = Phase.PLACE;
                delay = Math.max(0, cycleTicks.getInt() - 2);
            }
        }
    }

    private ActionResult place(MinecraftClient mc, PlayerEntity p, Hand hand) {
        boolean was = p.isSneaking();
        p.setSneaking(true); // client side half of the check; the server got the sneak from the key
        ActionResult r = use(mc, hand, anchor, true);
        p.setSneaking(was);
        return r;
    }

    private ActionResult use(MinecraftClient mc, Hand hand, BlockHitResult hit, boolean swing) {
        ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (swing && r.isAccepted()) mc.player.swingHand(hand);
        return r;
    }
}
