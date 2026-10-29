package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/** Finds the blocks you pick and highlights them through walls. */
public class XRay extends Module {
    private final BlockListSetting list = add(new BlockListSetting("Blocks",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris"));
    private final NumSetting radius = add(new NumSetting("Radius", 28, 8, 64, 1));
    private final NumSetting maxBlocks = add(new NumSetting("Max blocks", 1500, 100, 6000, 100));
    private final NumSetting interval = add(new NumSetting("Scan every (ticks)", 15, 5, 80, 5));
    private final NumSetting width = add(new NumSetting("Line width", 1.5, 0.5, 5, 0.5));
    private final BoolSetting fill = add(new BoolSetting("Fill", true));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.25, 0.05, 0.8, 0.05));

    private final Set<Block> blocks = new HashSet<>();
    private final Map<Block, Integer> colors = new HashMap<>();
    private int builtVersion = -1, tick;
    private List<BlockPos> found = new ArrayList<>();
    private List<Integer> foundColors = new ArrayList<>();

    public XRay() {
        super("XRay", "Highlights chosen blocks (search them in the list)", Category.RENDER);
        fillAlpha.showIf(fill::get);
    }

    @Override protected void onEnable() { builtVersion = -1; tick = 0; }

    @Override
    protected void onDisable() {
        found = new ArrayList<>();
        foundColors = new ArrayList<>();
    }

    private static int colorFor(String id) {
        String p = id.substring(id.indexOf(':') + 1);
        if (p.contains("diamond")) return 0x4CC9F0;
        if (p.contains("emerald")) return 0x57E389;
        if (p.contains("gold")) return 0xFFD166;
        if (p.contains("iron")) return 0xE0C3A0;
        if (p.contains("coal")) return 0x8D99AE;
        if (p.contains("redstone")) return 0xFF4D6D;
        if (p.contains("lapis")) return 0x4361EE;
        if (p.contains("copper")) return 0xFF9F1C;
        if (p.contains("debris")) return 0xC1782E;
        if (p.contains("quartz")) return 0xF8F9FA;
        return MathHelper.hsvToRgb(((id.hashCode() & 0x7FFFFFFF) % 360) / 360f, 0.7f, 1f);
    }

    private void rebuild() {
        blocks.clear();
        colors.clear();
        for (String id : list.get()) {
            Identifier ident = Identifier.tryParse(id);
            if (ident == null) continue;
            Block b = Registries.BLOCK.get(ident);
            if (b == Blocks.AIR) continue;
            blocks.add(b);
            colors.put(b, colorFor(id));
        }
        builtVersion = list.version;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (builtVersion == list.version && ++tick < interval.getInt()) return;
        tick = 0;
        if (builtVersion != list.version) rebuild();
        scan(mc);
    }

    private void scan(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        List<BlockPos> outPos = new ArrayList<>();
        List<Integer> outCol = new ArrayList<>();
        if (!blocks.isEmpty()) {
            double r = radius.get(), r2 = r * r;
            int cap = maxBlocks.getInt();
            int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
            int rc = ((int) r >> 4) + 1;
            outer:
            for (int cx = pcx - rc; cx <= pcx + rc; cx++) {
                for (int cz = pcz - rc; cz <= pcz + rc; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    ChunkSection[] secs = chunk.getSectionArray();
                    for (int i = 0; i < secs.length; i++) {
                        ChunkSection sec = secs[i];
                        if (sec == null || !sec.hasAny(st -> blocks.contains(st.getBlock()))) continue;
                        int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                            Block b = sec.getBlockState(x, y, z).getBlock();
                            if (!blocks.contains(b)) continue;
                            int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
                            double dx = wx + 0.5 - p.getX(), dy = wy + 0.5 - p.getY(), dz = wz + 0.5 - p.getZ();
                            if (dx * dx + dy * dy + dz * dz > r2) continue;
                            outPos.add(new BlockPos(wx, wy, wz));
                            outCol.add(colors.getOrDefault(b, 0xFFFFFF));
                            if (outPos.size() >= cap) break outer;
                        }
                    }
                }
            }
        }
        found = outPos;
        foundColors = outCol;
    }

    @Override
    public void onRender3d() {
        List<BlockPos> ps = found;
        List<Integer> cs = foundColors;
        if (ps.isEmpty() || !RenderUtil.valid) return;

        RenderUtil.setup(width.getFloat());
        if (fill.get()) {
            BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
            for (int i = 0; i < ps.size(); i++)
                RenderUtil.boxFill(b, new Box(ps.get(i)), RenderUtil.argb(cs.get(i), fillAlpha.getFloat()));
            RenderUtil.finish(b);
        }
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (int i = 0; i < ps.size(); i++)
            RenderUtil.boxOutline(b, new Box(ps.get(i)), RenderUtil.argb(cs.get(i), 1f));
        RenderUtil.finish(b);
        RenderUtil.restore();
    }
}
