package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Hold a trapdoor and dripstone, aim at a placed trapdoor and do not touch the mouse:
 * OPEN the trapdoor -> PLACE dripstone -> CLOSE the trapdoor -> repeat.
 *
 * Where the dripstone goes ("Place at"):
 *  - Behind trapdoor: once the trapdoor is open the crosshair ray goes through it - the dripstone is placed on
 *    whatever block you now look at (the ray is recalculated right after the trapdoor opened, so no frame is needed)
 *  - Trapdoor face: placed against the trapdoor itself (needs sneaking; the sneak key is held for that tick)
 */
public class AutoDripstone extends Module {
    private final ModeSetting items = add(new ModeSetting("Items", "Both", "Both", "Pointed dripstone", "Dripstone block"));
    private final ModeSetting placeAt = add(new ModeSetting("Place at", "Behind trapdoor", "Behind trapdoor", "Trapdoor face"));
    private final NumSetting stepTicks = add(new NumSetting("Ticks per step", 1, 1, 5, 1));
    private final BoolSetting needBoth = add(new BoolSetting("Require trapdoor + dripstone in hands", true));
    private final BoolSetting sneakPlace = add(new BoolSetting("Sneak to place", true));

    private enum Phase { OPEN, PLACE, CLOSE }

    private Phase phase = Phase.OPEN;
    private BlockHitResult anchor;
    private int wait;
    private boolean forcedSneak;

    public AutoDripstone() {
        super("AutoDripstone", "Trapdoor + dripstone in hand: open, place, close, repeat", Category.WORLD);
    }

    @Override protected void onEnable() { reset(); }

    @Override
    protected void onDisable() {
        reset();
        releaseSneak();
    }

    private void reset() {
        phase = Phase.OPEN;
        anchor = null;
        wait = 0;
    }

    private void releaseSneak() {
        if (forcedSneak) {
            KeyBinding.updatePressedStates(); // hand the key back to the real keyboard state
            forcedSneak = false;
        }
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

    /** block under the crosshair, computed right now from the current world (not from the last rendered frame) */
    private BlockHitResult ray(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        Vec3d from = p.getCameraPosVec(1f);
        Vec3d to = from.add(p.getRotationVec(1f).multiply(4.5));
        BlockHitResult r = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        return r != null && r.getType() == HitResult.Type.BLOCK ? r : null;
    }

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

        if (phase == Phase.OPEN) {
            // pick up the trapdoor under the crosshair (so you can re-aim between cycles)
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || !(mc.world.getBlockState(hit.getBlockPos()).getBlock() instanceof TrapdoorBlock)) {
                anchor = null;
                return;
            }
            anchor = hit;
        }
        if (anchor == null) { reset(); return; }
        if (!(mc.world.getBlockState(anchor.getBlockPos()).getBlock() instanceof TrapdoorBlock)) { reset(); return; }

        if (wait > 0) { wait--; return; }
        wait = stepTicks.getInt() - 1;

        PlayerEntity p = mc.player;
        switch (phase) {
            case OPEN -> {
                BlockState st = mc.world.getBlockState(anchor.getBlockPos());
                if (!st.get(TrapdoorBlock.OPEN)) use(mc, Hand.MAIN_HAND, anchor);
                phase = Phase.PLACE;
            }
            case PLACE -> {
                Hand h = dripHand(p);
                if (h != null) place(mc, p, h);
                phase = Phase.CLOSE;
            }
            case CLOSE -> {
                use(mc, Hand.MAIN_HAND, anchor);
                phase = Phase.OPEN;
            }
        }
    }

    private void place(MinecraftClient mc, PlayerEntity p, Hand hand) {
        BlockHitResult target = anchor;
        if (placeAt.is("Behind trapdoor")) {
            BlockHitResult fresh = ray(mc);
            if (fresh != null && !fresh.getBlockPos().equals(anchor.getBlockPos())) target = fresh;
        }
        // clicking a trapdoor without sneaking would only toggle it again - never do that by accident
        boolean sneaking = p.isSneaking();
        if (mc.world.getBlockState(target.getBlockPos()).getBlock() instanceof TrapdoorBlock && !sneaking && !sneakPlace.get()) return;
        boolean was = p.isSneaking();
        if (sneakPlace.get()) p.setSneaking(true); // client side part of the check; the server gets it from the sneak key
        use(mc, hand, target);
        p.setSneaking(was);
    }

    private void use(MinecraftClient mc, Hand hand, BlockHitResult hit) {
        ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (r.isAccepted()) mc.player.swingHand(hand);
    }
}
