package dev.nocturne.module.impl;

import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/** Coloured, filled highlight on the block you are looking at. */
public class BlockHighlight extends Module {
    private final BoolSetting fill = add(new BoolSetting("Fill", true));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.22, 0.05, 0.8, 0.01));
    private final NumSetting width = add(new NumSetting("Line width", 2.0, 0.5, 5, 0.5));

    public BlockHighlight() {
        super("BlockHighlight", "Highlights the block under your crosshair in the theme colour", Category.VISUALS);
        fillAlpha.showIf(fill::get);
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || !RenderUtil.valid) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
        box = box.expand(0.002);
        int rgb = Theme.accent() & 0xFFFFFF;

        RenderUtil.setup(width.getFloat());
        if (fill.get()) {
            BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
            RenderUtil.boxFill(b, box, RenderUtil.argb(rgb, fillAlpha.getFloat()));
            RenderUtil.finish(b);
        }
        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        RenderUtil.boxOutline(l, box, RenderUtil.argb(rgb, 1f));
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
