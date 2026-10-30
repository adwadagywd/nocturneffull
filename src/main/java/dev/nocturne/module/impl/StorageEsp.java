package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.EspDraw;
import dev.nocturne.util.RenderUtil;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Highlights chests, furnaces, hoppers... of every loaded chunk through walls - with styles, glow and labels. */
public class StorageEsp extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Both", EspDraw.STYLES));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Type", "Type", "Theme", "Rainbow", "Distance"));
    private final NumSetting range = add(new NumSetting("Range", 96, 16, 256, 8));
    private final NumSetting width = add(new NumSetting("Line width", 1.5, 0.5, 5, 0.5));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.2, 0.05, 0.8, 0.05));
    private final NumSetting glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));
    private final NumSetting glowSize = add(new NumSetting("Glow size", 0.1, 0.04, 0.4, 0.01));
    private final NumSetting expand = add(new NumSetting("Expand", 0.0, -0.1, 0.4, 0.02));
    private final NumSetting cornerLen = add(new NumSetting("Corner length", 0.3, 0.1, 0.5, 0.05));
    private final BoolSetting fade = add(new BoolSetting("Fade by distance", true));
    private final BoolSetting pulse = add(new BoolSetting("Pulse", false));
    private final BoolSetting labels = add(new BoolSetting("Labels", false));
    private final NumSetting labelRange = add(new NumSetting("Label range", 40, 8, 128, 4));
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
    private List<String> names = new ArrayList<>();
    private int tick = 100;

    public StorageEsp() {
        super("StorageESP", "Marks chests, furnaces, shulkers and more", Category.RENDER);
        fillAlpha.showIf(() -> !style.is("Box") && !style.is("Corners") && !style.is("Beam"));
        cornerLen.showIf(() -> style.is("Corners"));
        glowSize.showIf(() -> glow.get() > 0.01 && !style.is("Beam"));
        labelRange.showIf(labels::get);
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
        List<String> nn = new ArrayList<>();
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
                    nn.add(be.getCachedState().getBlock().getName().getString());
                }
            }
        }
        boxes = nb;
        colors = nc;
        names = nn;
    }

    private int rgbAt(int i, double d, double time) {
        switch (colorMode.get()) {
            case "Theme": return Theme.accent() & 0xFFFFFF;
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.1 + i * 0.05) % 1.0), 0.7f, 1f);
            case "Distance": return MathHelper.hsvToRgb((float) MathHelper.clamp(d / range.get(), 0, 1) * 0.33f, 0.85f, 1f);
            default: return colors.get(i);
        }
    }

    private float alphaAt(int i, double d, double time) {
        float a = fade.get() ? (float) MathHelper.clamp(1.0 - (d - range.get() * 0.6) / (range.get() * 0.4), 0.15, 1.0) : 1f;
        if (pulse.get()) a *= 0.8f + 0.2f * (float) Math.sin(time * 3 + i * 0.9);
        return a;
    }

    @Override
    public void onRender3d() {
        List<Box> bs = boxes;
        List<Integer> cs = colors;
        if (bs.isEmpty() || cs.size() != bs.size() || !RenderUtil.valid) return;
        Vec3d cam = RenderUtil.cam;
        double time = System.currentTimeMillis() / 1000.0;
        String st = style.get();
        int n = bs.size();
        Box[] bx = new Box[n];
        int[] rgb = new int[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            bx[i] = bs.get(i).expand(expand.get());
            double d = bx[i].getCenter().distanceTo(cam);
            rgb[i] = rgbAt(i, d, time);
            al[i] = alphaAt(i, d, time);
        }

        RenderUtil.setup(width.getFloat());
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (int i = 0; i < n; i++) EspDraw.quads(q, st, bx[i], rgb[i], al[i], fillAlpha.getFloat(), glow.getFloat(), glowSize.get());
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        Vec3d from = tracers.get() ? RenderUtil.unproject(0f, 0f, 0.5f) : null;
        for (int i = 0; i < n; i++) {
            EspDraw.lines(l, st, bx[i], rgb[i], al[i], cornerLen.get());
            if (from != null) RenderUtil.line(l, from, bx[i].getCenter(), RenderUtil.argb(rgb[i], 0.8f * al[i]));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        List<Box> bs = boxes;
        List<String> ns = names;
        if (!labels.get() || bs.isEmpty() || ns.size() != bs.size() || colors.size() != bs.size() || !RenderUtil.valid) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        Vec3d cam = RenderUtil.cam;
        double time = System.currentTimeMillis() / 1000.0, lr = labelRange.get();

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < bs.size(); i++) if (bs.get(i).getCenter().distanceTo(cam) <= lr) idx.add(i);
        idx.sort(Comparator.comparingDouble(i -> bs.get(i).getCenter().squaredDistanceTo(cam)));
        if (idx.size() > 40) idx = new ArrayList<>(idx.subList(0, 40));

        for (int i : idx) {
            Box b = bs.get(i);
            double d = b.getCenter().distanceTo(cam);
            Vec3d s = RenderUtil.project(new Vec3d((b.minX + b.maxX) / 2, b.maxY + 0.45, (b.minZ + b.maxZ) / 2), sw, sh);
            if (s == null) continue;
            int argb = 0xFF000000 | rgbAt(i, d, time);
            String text = ns.get(i) + "  " + (int) d + "m";
            int w = Draw.width(tr, text) + 16, h = 16;
            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            Draw.alpha = (float) MathHelper.clamp(1.0 - (d - lr * 0.6) / (lr * 0.4), 0.3, 1.0);
            Draw.surface(ctx, -w / 2, -h, w, h, 5, 0xC00B0B0F, 0x30FFFFFF);
            Draw.round(ctx, -w / 2 + 5, -3, w - 10, 1, 0.5f, argb);
            Draw.text(ctx, tr, text, -w / 2 + 8, -h + 4, argb, false);
            m.pop();
        }
        Draw.alpha = 1f;
    }
}
