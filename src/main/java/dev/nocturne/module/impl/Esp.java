package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.TargetSettings;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class Esp extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Box", "Box", "Corners", "Filled", "Both"));
    private final NumSetting range = add(new NumSetting("Range", 128, 8, 512, 1));
    private final NumSetting width = add(new NumSetting("Line width", 2, 0.5, 6, 0.5));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.2, 0.05, 0.8, 0.05));
    private final TargetSettings targets = new TargetSettings(this, true, true, false, false);

    public Esp() {
        super("ESP", "Boxes around entities, visible through walls", Category.RENDER);
        fillAlpha.showIf(() -> mode.is("Filled") || mode.is("Both"));
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || mc.player == null || !RenderUtil.valid) return;
        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities())
            if (targets.test(e) && mc.player.distanceTo(e) <= range.get()) list.add(e);
        if (list.isEmpty()) return;

        boolean fill = mode.is("Filled") || mode.is("Both");
        boolean box = mode.is("Box") || mode.is("Both");
        boolean corners = mode.is("Corners");

        RenderUtil.setup(width.getFloat());
        if (fill) {
            BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
            for (Entity e : list) {
                int rgb = targets.color(e, mc.player.distanceTo(e), range.get());
                RenderUtil.boxFill(b, RenderUtil.lerpedBox(e), RenderUtil.argb(rgb, fillAlpha.getFloat()));
            }
            RenderUtil.finish(b);
        }
        if (box || corners) {
            BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
            for (Entity e : list) {
                int rgb = targets.color(e, mc.player.distanceTo(e), range.get());
                if (corners) RenderUtil.boxCorners(b, RenderUtil.lerpedBox(e), RenderUtil.argb(rgb, 1f));
                else RenderUtil.boxOutline(b, RenderUtil.lerpedBox(e), RenderUtil.argb(rgb, 1f));
            }
            RenderUtil.finish(b);
        }
        RenderUtil.restore();
    }
}
