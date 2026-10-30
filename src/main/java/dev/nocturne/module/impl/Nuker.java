package dev.nocturne.module.impl;

import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import dev.nocturne.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Breaks blocks around you. Creative: several per tick. Survival: nearest first at normal mining speed. */
public class Nuker extends Module {
    private final ModeSetting breakMode = add(new ModeSetting("Break mode", "Normal", "Normal", "Fast AF"));
    private final NumSetting blocksPerTick = add(new NumSetting("Blocks per tick", 16, 1, 64, 1));
    private final BoolSetting predict = add(new BoolSetting("Instant remove", true));
    private final BoolSetting flashBlocks = add(new BoolSetting("Break flash", true));
    private final ModeSetting mode = add(new ModeSetting("Mode", "All", "All", "Selected", "Flatten", "Tunnel"));
    private final NumSetting range = add(new NumSetting("Range", 4.5, 1.0, 6.0, 0.1));
    private final NumSetting speed = add(new NumSetting("Speed (creative)", 4, 1, 20, 1));
    private final NumSetting delay = add(new NumSetting("Delay (ticks)", 0, 0, 10, 1));
    private final ModeSetting sort = add(new ModeSetting("Sort", "Distance", "Distance", "Hardness"));
    private final BoolSetting exposed = add(new BoolSetting("Exposed only", true));
    private final BoolSetting autoTool = add(new BoolSetting("Auto tool", true));
    private final ModeSetting swing = add(new ModeSetting("Swing", "Normal", "Normal", "None"));
    private final BoolSetting highlight = add(new BoolSetting("Highlight target", true));
    private final BoolSetting learn = add(new BoolSetting("Select by mining", true));
    private final BlockListSetting blocks = add(new BlockListSetting("Selected blocks"));

    private final Set<Block> set = new HashSet<>();
    private int builtVersion = -1, timer;
    private BlockPos current;
    private BlockPos watchPos;
    private Block watchBlock;
    private int attackTicks;

    // ---- "Fast AF" mode ----
    private record Flash(BlockPos pos, long t) {}
    private final Map<BlockPos, Integer> tried = new HashMap<>();
    private final ArrayDeque<Flash> flashes = new ArrayDeque<>();
    private int tick, laneTicks;
    private BlockPos lane;

    public Nuker() {
        super("Nuker", "Mass-breaks blocks: All / Selected / Flatten / Tunnel", Category.WORLD);
        speed.showIf(() -> !fast());
        delay.showIf(() -> !fast());
        blocksPerTick.showIf(this::fast);
        predict.showIf(this::fast);
        blocks.showIf(() -> mode.is("Selected"));
        learn.showIf(() -> mode.is("Selected"));
        blocks.searchable = false;
        blocks.hint = "Mine a block - its type gets selected";
    }

    private boolean fast() { return breakMode.is("Fast AF"); }

    @Override protected void onEnable() { builtVersion = -1; current = null; timer = 0; lane = null; laneTicks = 0; tried.clear(); flashes.clear(); }
    @Override protected void onDisable() { current = null; lane = null; tried.clear(); flashes.clear(); }

    private void rebuild() {
        set.clear();
        for (String id : blocks.get()) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null) set.add(Registries.BLOCK.get(ident));
        }
        builtVersion = blocks.version;
    }

    private boolean isExposed(MinecraftClient mc, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (!mc.world.getBlockState(n).isSolidBlock(mc.world, n)) return true;
        }
        return false;
    }

    private List<BlockPos> find(MinecraftClient mc, PlayerEntity p) {
        List<BlockPos> out = new ArrayList<>();
        Vec3d eye = p.getEyePos();
        double r = range.get();
        int ri = (int) Math.ceil(r);
        BlockPos base = p.getBlockPos();
        Vec3d look = Vec3d.fromPolar(0f, p.getYaw());
        for (int dx = -ri; dx <= ri; dx++) for (int dy = -ri; dy <= ri; dy++) for (int dz = -ri; dz <= ri; dz++) {
            BlockPos pos = base.add(dx, dy, dz);
            Vec3d ctr = Vec3d.ofCenter(pos);
            if (ctr.distanceTo(eye) > r) continue;
            BlockState st = mc.world.getBlockState(pos);
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            if (st.getHardness(mc.world, pos) < 0f) continue;
            switch (mode.get()) {
                case "Selected" -> { if (!set.contains(st.getBlock())) continue; }
                case "Flatten" -> { if (pos.getY() < p.getBlockY()) continue; }
                case "Tunnel" -> {
                    Vec3d d = ctr.subtract(eye);
                    double fwd = d.x * look.x + d.z * look.z;
                    double side = Math.abs(d.x * look.z - d.z * look.x);
                    if (fwd < 0.3 || side > 1.2 || Math.abs(d.y) > 2.2) continue;
                }
                default -> {}
            }
            if (exposed.get() && !isExposed(mc, pos)) continue;
            out.add(pos);
        }
        if (sort.is("Hardness")) out.sort(Comparator.comparingDouble((BlockPos q) -> mc.world.getBlockState(q).getHardness(mc.world, q)));
        else out.sort(Comparator.comparingDouble((BlockPos q) -> Vec3d.ofCenter(q).squaredDistanceTo(eye)));
        return out;
    }

    private static Direction face(Vec3d eye, BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        return Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
    }

    private void equip(PlayerEntity p, BlockState st) {
        int best = p.getInventory().selectedSlot;
        float bestSpeed = p.getMainHandStack().getMiningSpeedMultiplier(st);
        for (int i = 0; i < 9; i++) {
            float s = p.getInventory().getStack(i).getMiningSpeedMultiplier(st);
            if (s > bestSpeed + 0.01f) { bestSpeed = s; best = i; }
        }
        if (best != p.getInventory().selectedSlot) p.getInventory().selectedSlot = best;
    }

    /** before the game handles your mouse this tick: remember which block your crosshair is on */
    @Override
    public void onPreTick(MinecraftClient mc) {
        watchPos = null;
        watchBlock = null;
        if (!mode.is("Selected") || !learn.get() || mc.world == null) return;
        if (mc.options.attackKey.isPressed()) attackTicks = 4;
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState st = mc.world.getBlockState(hit.getBlockPos());
            if (!st.isAir()) { watchPos = hit.getBlockPos(); watchBlock = st.getBlock(); }
        }
    }

    /** after the tick: if the block you were digging is gone, its type becomes selected */
    private void learnFromMining(MinecraftClient mc) {
        if (mc.options.attackKey.isPressed()) attackTicks = 4;
        boolean recent = attackTicks > 0;
        if (attackTicks > 0) attackTicks--;
        if (!recent || watchPos == null || watchBlock == null || !mode.is("Selected") || !learn.get()) return;
        if (mc.world.getBlockState(watchPos).getBlock() == watchBlock) return; // still there
        if (!watchPos.equals(current)) { // not broken by the nuker itself
            String id = Registries.BLOCK.getId(watchBlock).toString();
            if (!blocks.has(id)) {
                blocks.toggle(id);
                Notifications.push("Nuker", "Selected " + watchBlock.getName().getString(), true);
            }
        }
        watchPos = null;
        watchBlock = null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        learnFromMining(mc);
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null || Combat.blocked()) return;
        if (builtVersion != blocks.version) rebuild();
        if (fast()) { fastTick(mc, p); return; }
        if (timer > 0) { timer--; return; }

        List<BlockPos> targets = find(mc, p);
        if (targets.isEmpty()) { current = null; return; }
        Vec3d eye = p.getEyePos();
        boolean creative = mc.interactionManager.getCurrentGameMode().isCreative();
        if (creative) {
            int n = Math.min(speed.getInt(), targets.size());
            for (int i = 0; i < n; i++) mc.interactionManager.attackBlock(targets.get(i), face(eye, targets.get(i)));
            current = targets.get(0);
        } else {
            BlockPos t = (current != null && targets.contains(current)) ? current : targets.get(0);
            current = t;
            if (autoTool.get()) equip(p, mc.world.getBlockState(t));
            mc.interactionManager.updateBlockBreakingProgress(t, face(eye, t));
        }
        if (!swing.is("None")) p.swingHand(Hand.MAIN_HAND);
        timer = delay.getInt();
    }

    // =====================================================================================
    //  FAST AF: raw break packets, many blocks per tick
    //  - blocks that break in a single tick (progress >= 0.7 after one tick) are broken in bulk
    //  - everything slower is mined in a single lane, finished at 78% instead of 100%
    //  Servers with strict anti-cheat can reject this; on vanilla / lax servers it is instant.
    // =====================================================================================
    private void fastTick(MinecraftClient mc, PlayerEntity p) {
        tick++;
        tried.values().removeIf(t -> tick - t > 8);
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) return;
        List<BlockPos> targets = find(mc, p);
        targets.removeIf(tried::containsKey);
        if (targets.isEmpty()) { current = null; lane = null; laneTicks = 0; return; }

        Vec3d eye = p.getEyePos();
        boolean creative = mc.interactionManager.getCurrentGameMode().isCreative();
        int limit = blocksPerTick.getInt(), done = 0;
        BlockPos firstSlow = null;
        for (BlockPos pos : targets) {
            BlockState st = mc.world.getBlockState(pos);
            if (st.isAir()) continue;
            float delta = creative ? 1f : st.calcBlockBreakingDelta(p, mc.world, pos);
            if (delta >= 0.7f) {
                if (done >= limit) break;
                if (!creative && autoTool.get()) equipPacket(net, p, st);
                Direction f = face(eye, pos);
                net.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, f));
                net.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, f));
                finishBlock(mc, pos);
                done++;
            } else if (firstSlow == null) firstSlow = pos;
        }
        if (done > 0) { lane = null; laneTicks = 0; }
        else if (firstSlow != null) laneStep(mc, p, net, firstSlow, eye);
        else lane = null;
        if ((done > 0 || lane != null) && !swing.is("None")) p.swingHand(Hand.MAIN_HAND);
    }

    private void laneStep(MinecraftClient mc, PlayerEntity p, ClientPlayNetworkHandler net, BlockPos pos, Vec3d eye) {
        BlockState st = mc.world.getBlockState(pos);
        Direction f = face(eye, pos);
        if (autoTool.get()) equipPacket(net, p, st);
        float delta = st.calcBlockBreakingDelta(p, mc.world, pos);
        if (delta <= 0f) { tried.put(pos, tick); return; }
        current = pos;
        if (!pos.equals(lane)) {
            lane = pos;
            laneTicks = 0;
            net.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, f));
            return;
        }
        laneTicks++;
        if (delta * (laneTicks + 1) >= 0.78f) {
            net.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, f));
            finishBlock(mc, pos);
            lane = null;
            laneTicks = 0;
        }
    }

    private void finishBlock(MinecraftClient mc, BlockPos pos) {
        tried.put(pos, tick);
        current = pos;
        if (flashBlocks.get()) {
            flashes.addLast(new Flash(pos, System.currentTimeMillis()));
            while (flashes.size() > 300) flashes.removeFirst();
        }
        if (predict.get()) mc.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 11);
    }

    /** switch to the best tool for this block - and tell the server about it */
    private void equipPacket(ClientPlayNetworkHandler net, PlayerEntity p, BlockState st) {
        int cur = p.getInventory().selectedSlot, best = cur;
        float bestSpeed = p.getMainHandStack().getMiningSpeedMultiplier(st);
        for (int i = 0; i < 9; i++) {
            float sp = p.getInventory().getStack(i).getMiningSpeedMultiplier(st);
            if (sp > bestSpeed + 0.01f) { bestSpeed = sp; best = i; }
        }
        if (best != cur) {
            p.getInventory().selectedSlot = best;
            net.sendPacket(new UpdateSelectedSlotC2SPacket(best));
        }
    }


    @Override
    public void onRender3d() {
        if (!RenderUtil.valid) return;
        long now = System.currentTimeMillis();
        while (!flashes.isEmpty() && now - flashes.peekFirst().t() > 380) flashes.removeFirst();
        BlockPos c = current;
        boolean hl = highlight.get() && c != null && mc.world != null && !mc.world.getBlockState(c).isAir();
        if (!hl && flashes.isEmpty()) return;
        int rgb = Theme.accent() & 0xFFFFFF;

        RenderUtil.setup(2f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        if (hl) RenderUtil.boxFill(q, new Box(c).expand(0.003), RenderUtil.argb(rgb, 0.22f));
        for (Flash f : flashes) {
            float a = 1f - (now - f.t()) / 380f;
            RenderUtil.boxFill(q, new Box(f.pos()).expand(0.02 + 0.14 * (1f - a)), RenderUtil.argb(rgb, 0.4f * a));
        }
        RenderUtil.finish(q);
        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        if (hl) RenderUtil.boxOutline(l, new Box(c).expand(0.003), RenderUtil.argb(rgb, 1f));
        for (Flash f : flashes) {
            float a = 1f - (now - f.t()) / 380f;
            RenderUtil.boxOutline(l, new Box(f.pos()).expand(0.02 + 0.14 * (1f - a)), RenderUtil.argb(rgb, a));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
