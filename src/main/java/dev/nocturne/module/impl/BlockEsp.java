package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.gui.Theme;
import dev.nocturne.util.EspDraw;
import dev.nocturne.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Highlights chosen blocks (ores by default) around you through walls, with the shared ESP styles and glow. */
public class BlockEsp extends Module {
    private final BlockListSetting blocks = add(new BlockListSetting("Blocks",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore"));
    private final NumSetting range = add(new NumSetting("Range", 24, 8, 48, 4));
    private final ModeSetting style = add(new ModeSetting("Style", "Both", EspDraw.STYLES));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Block", "Block", "Theme"));
    private final NumSetting width = add(new NumSetting("Line width", 1.5, 0.5, 5, 0.5));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.25, 0.05, 0.8, 0.05));
    private final NumSetting glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));
    private final NumSetting glowSize = add(new NumSetting("Glow size", 0.1, 0.04, 0.4, 0.01));
    private final BoolSetting pulse = add(new BoolSetting("Pulse", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));

    private final Set<Block> set = new HashSet<>();
    private int builtVersion = -1, tick = 100;
    private List<BlockPos> found = new ArrayList<>();
    private List<Integer> colors = new ArrayList<>();

    public BlockEsp() {
        super("BlockESP", "Highlights ores / chosen blocks through walls", Category.RENDER);
        fillAlpha.showIf(() -> !style.is("Box") && !style.is("Corners") && !style.is("Beam"));
        glowSize.showIf(() -> glow.get() > 0.01 && !style.is("Beam"));
    }

    @Override protected void onEnable() { builtVersion = -1; tick = 100; }

    private void rebuild() {
        set.clear();
        for (String id : blocks.get()) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null) set.add(Registries.BLOCK.get(ident));
        }
        builtVersion = blocks.version;
    }

    private int colorOf(Block b) {
        if (colorMode.is("Theme")) return Theme.accent() & 0xFFFFFF;
        String n = Registries.BLOCK.getId(b).getPath();
        if (n.contains("diamond")) return 0x4DEBFF;
        if (n.contains("emerald")) return 0x2FE06B;
        if (n.contains("gold")) return 0xFFD34E;
        if (n.contains("iron")) return 0xE8B89A;
        if (n.contains("copper")) return 0xE08A5A;
        if (n.contains("redstone")) return 0xFF3B3B;
        if (n.contains("lapis")) return 0x3B6BFF;
        if (n.contains("coal")) return 0x9A9AAA;
        if (n.contains("quartz")) return 0xF2F2F2;
        if (n.contains("debris")) return 0xC07A4A;
        return Theme.accent() & 0xFFFFFF;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (builtVersion != blocks.version) { rebuild(); tick = 100; }
        if (++tick < 15) return;
        tick = 0;
        int r = range.getInt();
        double r2 = r * (double) r;
        BlockPos base = mc.player.getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        List<BlockPos> nf = new ArrayList<>();
        List<Integer> nc = new ArrayList<>();
        scan:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    Block b = mc.world.getBlockState(m).getBlock();
                    if (!set.contains(b)) continue;
                    nf.add(m.toImmutable());
                    nc.add(colorOf(b));
                    if (nf.size() >= 800) break scan;
                }
            }
        }
        found = nf;
        colors = nc;
    }

    @Override
    public void onRender3d() {
        List<BlockPos> list = found;
        List<Integer> cs = colors;
        if (list.isEmpty() || cs.size() != list.size() || !RenderUtil.valid) return;
        Vec3d cam = RenderUtil.cam;
        double rng = range.get(), time = System.currentTimeMillis() / 1000.0;
        String st = style.get();
        int n = list.size();
        Box[] bx = new Box[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            bx[i] = new Box(list.get(i)).expand(0.004);
            double d = bx[i].getCenter().distanceTo(cam);
            float a = (float) MathHelper.clamp(1.0 - (d - rng * 0.6) / (rng * 0.4), 0.2, 1.0);
            if (pulse.get()) a *= 0.8f + 0.2f * (float) Math.sin(time * 3 + i * 0.7);
            al[i] = a;
        }

        RenderUtil.setup(width.getFloat());
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (int i = 0; i < n; i++) EspDraw.quads(q, st, bx[i], cs.get(i), al[i], fillAlpha.getFloat(), glow.getFloat(), glowSize.get());
        RenderUtil.finish(q);
        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        Vec3d from = tracers.get() ? RenderUtil.unproject(0f, 0f, 0.5f) : null;
        for (int i = 0; i < n; i++) {
            EspDraw.lines(l, st, bx[i], cs.get(i), al[i], 0.3);
            if (from != null) RenderUtil.line(l, from, bx[i].getCenter(), RenderUtil.argb(cs.get(i), 0.7f * al[i]));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
