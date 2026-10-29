package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;

public class Hitboxes extends Module {
    public static Hitboxes INSTANCE;

    private final NumSetting expand = add(new NumSetting("Expand", 0.2, 0.0, 1.0, 0.05));
    private final BoolSetting draw = add(new BoolSetting("Draw box", true));
    private final NumSetting alpha = add(new NumSetting("Box alpha", 0.8, 0.1, 1.0, 0.05));
    private final TargetSettings targets = new TargetSettings(this, true, false, false, false);

    private final BoolSetting throughWebs = add(new BoolSetting("Through cobwebs", true));

    /** used by an optional mixin: cobwebs stop blocking your crosshair, so players standing in them can be hit */
    public static boolean ignoreWebs(net.minecraft.block.AbstractBlock.AbstractBlockState state) {
        Hitboxes h = INSTANCE;
        return h != null && h.isEnabled() && h.throughWebs.get() && state.isOf(net.minecraft.block.Blocks.COBWEB)
                && net.minecraft.client.MinecraftClient.getInstance().isOnThread();
    }

    public Hitboxes() {
        super("Hitboxes", "Expands the area you can hit entities in", Category.COMBAT);
        INSTANCE = this;
        alpha.showIf(draw::get);
    }

    public float margin(Entity e) {
        return isEnabled() && targets.test(e) ? expand.getFloat() : 0f;
    }

    @Override
    public void onRender3d() {
        if (!draw.get() || mc.world == null || mc.player == null || !RenderUtil.valid) return;
        float m = expand.getFloat();
        RenderUtil.setup(1.5f);
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Entity e : mc.world.getEntities()) {
            if (!targets.test(e)) continue;
            double d = mc.player.distanceTo(e);
            if (d > 64) continue;
            int rgb = targets.color(e, d, 64);
            RenderUtil.boxOutline(b, RenderUtil.lerpedBox(e).expand(m), RenderUtil.argb(rgb, alpha.getFloat()));
        }
        RenderUtil.finish(b);
        RenderUtil.restore();
    }
}
