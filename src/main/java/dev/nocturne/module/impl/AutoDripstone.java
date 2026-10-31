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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Automatic dripstone dropping. The trick: a pointed dripstone hangs under a CLOSED trapdoor; when the trapdoor
 * is OPENED the dripstone loses its support and falls. Hold a trapdoor and dripstone, aim at the placed trapdoor
 * (or at the dripstone hanging under it) and keep the mouse still. The module repeats:
 *
 *   PLACE dripstone against the underside of the trapdoor (needs sneaking: the sneak key is held for that tick)
 *   OPEN  the trapdoor  -> the dripstone falls
 *   CLOSE the trapdoor  -> ready for the next one
 *
 * It waits for the old dripstone to be gone before placing the next one, so it runs exactly as fast as the server allows.
 */
public class AutoDripstone extends Module {
    private final ModeSetting items = add(new ModeSetting("Items", "Both", "Both", "Pointed dripstone", "Dripstone block"));
    private final NumSetting stepTicks = add(new NumSetting("Ticks per step", 1, 1, 5, 1));
    private final BoolSetting needBoth = add(new BoolSetting("Require trapdoor + dripstone in hands", true));
    private final BoolSetting sneakPlace = add(new BoolSetting("Sneak to place", true));

    private enum Phase { PLACE, OPEN, CLOSE }

    private Phase phase = Phase.PLACE;
    private BlockHitResult anchor;
    private int wait, gate;
    private boolean forcedSneak;

    public AutoDripstone() {
        super("AutoDripstone", "Trapdoor + dripstone: place, open (it falls), close, repeat", Category.WORLD);
    }

    @Override protected void onEnable() { reset(); }

    @Override
    protected void onDisable() {
        reset();
        releaseSneak();
    }

    private void reset() {
        phase = Phase.PLACE;
        anchor = null;
        wait = 0;
        gate = 0;
    }

    private void releaseSneak() {
        if (forcedSneak) {
            KeyBinding.updatePressedStates(); // hand the key back to the real keyboard state
            forcedSneak = false;
        }
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

    /** where the dripstone goes: the block next to the face we click (the cell under a ceiling trapdoor) */
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

    /** the sneak key is held during the tick before the placing tick, so the server knows we are sneaking */
    @Override
    public void onPreTick(MinecraftClient mc) {
        boolean want = sneakPlace.get() && anchor != null && phase == Phase.PLACE && armed(mc);
        if (want) {
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
        } else {
            releaseSneak();
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (!armed(mc)) { reset(); return; }
        PlayerEntity p = mc.player;

        BlockHitResult seen = mc.crosshairTarget instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK ? trapdoorAt(mc, h) : null;
        if (anchor == null || (seen != null && !seen.getBlockPos().equals(anchor.getBlockPos()))) {
            if (seen == null) return; // nothing to work on - aim at a trapdoor
            anchor = seen;
            wait = 0;
            gate = 0;
            phase = isOpen(mc) ? Phase.CLOSE : slotHasDrip(mc) ? Phase.OPEN : Phase.PLACE; // continue from what is there
        }
        if (!isTrapdoor(mc)) { reset(); return; }

        if (wait > 0) { wait--; return; }
        if (phase == Phase.PLACE && sneakPlace.get() && !forcedSneak) return; // sneak has not reached the server yet

        int pause = stepTicks.getInt() - 1;
        switch (phase) {
            case PLACE -> {
                if (!slotFree(mc) && ++gate < 20) return; // the previous dripstone is still falling
                gate = 0;
                Hand hand = dripHand(p);
                if (hand != null) place(mc, p, hand);
                phase = Phase.OPEN;
                wait = pause;
            }
            case OPEN -> {
                if (isOpen(mc)) {
                    phase = Phase.CLOSE;
                } else if (!slotHasDrip(mc)) {
                    phase = Phase.PLACE; // nothing hangs there yet (placing failed) - try again
                    wait = pause;
                } else {
                    use(mc, Hand.MAIN_HAND, anchor); // opens: the dripstone loses its support and falls
                    phase = Phase.CLOSE;
                    wait = pause;
                }
            }
            case CLOSE -> {
                if (isOpen(mc)) use(mc, Hand.MAIN_HAND, anchor);
                phase = Phase.PLACE;
                wait = pause;
            }
        }
    }

    private void place(MinecraftClient mc, PlayerEntity p, Hand hand) {
        boolean was = p.isSneaking();
        if (sneakPlace.get()) p.setSneaking(true); // client side part of the check; the server gets it from the sneak key
        use(mc, hand, anchor);
        p.setSneaking(was);
    }

    private void use(MinecraftClient mc, Hand hand, BlockHitResult hit) {
        ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (r.isAccepted()) mc.player.swingHand(hand);
    }
}
