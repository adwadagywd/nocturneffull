package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Highlights chests, furnaces, hoppers... of every loaded chunk through walls. */
public class StorageEsp extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Both", "Box", "Filled", "Both"));
    private final NumSetting range = add(new NumSetting("Range", 96, 16, 256, 8));
    private final NumSetting width = add(new NumSetting("Line width", 1.5, 0.5, 5, 0.5));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.2, 0.05, 0.8, 0.05));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
    private final BoolSetting chests = add(new BoolSetting("Chests", true));
    private final BoolSetting trapped = add(new BoolSetting("Trapped chests", true));
    private final BoolSetting ender = add(new BoolSetting("Ender chests", true));
    private final BoolSetting barrels = add(new BoolSetting("Barrels", true));
    private final BoolSetting shulkers = add(new BoolSetting("Shulker boxes", true));
    private final BoolSetting furnaces = add(new BoolSetting("Furnaces", true));
    private final BoolSetting hoppers = add(new BoolSetting("Hoppers", false));
    private final BoolSetting droppers = add(new BoolSetting("Dispensers/droppers", false));
    private final BoolSetting brewing = add(new BoolSetting("Brewing stands", false));
    private final BoolSetting spawners = add(new BoolSetting("Spawners", true));

    private List<Box> boxes = new ArrayList<>();
    private List<Integer> colors = new ArrayList<>();
    private int tick = 100;

    public StorageEsp() {
        super("StorageESP", "Marks chests, furnaces, shulkers and more", Category.RENDER);
        fillAlpha.showIf(() -> !mode.is("Box"));
    }

    @Override protected void onEnable() { tick = 100; }

    private Integer colorFor(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return trapped.get() ? 0xFF4D4D : null;
        if (be instanceof ChestBlockEntity) return chests.get() ? 0xFFB347 : null;
        if (be instanceof EnderChestBlockEntity) return ender.get() ? 0xB66DFF : null;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? 0xC98B4B : null;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? 0xFF6DE1 : null;
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get() ? 0xB8B8C0 : null;
        if (be instanceof HopperBlockEntity) return hoppers.get() ? 0x6D8CFF : null;
        if (be instanceof DispenserBlockEntity) return droppers.get() ? 0xE0E04D : null;
        if (be instanceof BrewingStandBlockEntity) return brewing.get() ? 0x5DE0A0 : null;
        if (be instanceof MobSpawnerBlockEntity) return spawners.get() ? 0xFF2D55 : null;
        return null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (++tick < 10) return;
        tick = 0;
        PlayerEntity p = mc.player;
        List<Box> nb = new ArrayList<>();
        List<Integer> nc = new ArrayList<>();
        double r = range.get(), r2 = r * r;
        int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
        int rc = ((int) r >> 4) + 1;
        for (int cx = pcx - rc; cx <= pcx + rc; cx++) {
            for (int cz = pcz - rc; cz <= pcz + rc; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    Integer col = colorFor(be);
                    if (col == null) continue;
                    BlockPos pos = be.getPos();
                    double dx = pos.getX() + 0.5 - p.getX(), dy = pos.getY() + 0.5 - p.getY(), dz = pos.getZ() + 0.5 - p.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    VoxelShape shape = be.getCachedState().getOutlineShape(mc.world, pos);
                    nb.add(shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos));
                    nc.add(col);
                }
            }
        }
        boxes = nb;
        colors = nc;
    }

    @Override
    public void onRender3d() {
        List<Box> bs = boxes;
        List<Integer> cs = colors;
        if (bs.isEmpty() || !RenderUtil.valid) return;

        RenderUtil.setup(width.getFloat());
        if (!mode.is("Box")) {
            BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
            for (int i = 0; i < bs.size(); i++) RenderUtil.boxFill(b, bs.get(i), RenderUtil.argb(cs.get(i), fillAlpha.getFloat()));
            RenderUtil.finish(b);
        }
        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        Vec3d from = tracers.get() ? RenderUtil.unproject(0f, 0f, 0.5f) : null;
        for (int i = 0; i < bs.size(); i++) {
            int col = RenderUtil.argb(cs.get(i), 1f);
            if (!mode.is("Filled")) RenderUtil.boxOutline(l, bs.get(i), col);
            if (from != null) RenderUtil.line(l, from, bs.get(i).getCenter(), RenderUtil.argb(cs.get(i), 0.8f));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
