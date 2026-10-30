package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.TargetSettings;
import dev.nocturne.setting.*;
import dev.nocturne.util.EspDraw;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** Boxes, glow, gradients, light beams and health bars around entities. */
public class Esp extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Box", EspDraw.STYLES));
    private final NumSetting range = add(new NumSetting("Range", 128, 8, 512, 1));
    private final NumSetting width = add(new NumSetting("Line width", 2, 0.5, 6, 0.5));
    private final NumSetting fillAlpha = add(new NumSetting("Fill alpha", 0.2, 0.05, 0.8, 0.05));
    private final NumSetting glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));
    private final NumSetting glowSize = add(new NumSetting("Glow size", 0.12, 0.04, 0.4, 0.01));
    private final NumSetting expand = add(new NumSetting("Expand", 0.0, -0.2, 0.5, 0.02));
    private final NumSetting cornerLen = add(new NumSetting("Corner length", 0.3, 0.1, 0.5, 0.05));
    private final BoolSetting healthBar = add(new BoolSetting("Health bar", false));
    private final BoolSetting hitFlash = add(new BoolSetting("Hit flash", true));
    private final BoolSetting pulse = add(new BoolSetting("Pulse", false));
    private final BoolSetting fade = add(new BoolSetting("Fade by distance", true));
    private final BoolSetting walls = add(new BoolSetting("Through walls", true));
    private final TargetSettings targets = new TargetSettings(this, true, true, false, false);

    private record Item(Entity e, Box box, int rgb, float a) {}

    public Esp() {
        super("ESP", "Boxes, glow and beams around entities", Category.RENDER);
        fillAlpha.showIf(() -> !style.is("Box") && !style.is("Corners") && !style.is("Beam"));
        cornerLen.showIf(() -> style.is("Corners"));
        glowSize.showIf(() -> glow.get() > 0.01 && !style.is("Beam"));
    }

    @Override
    public void onRender3d() {
        if (mc.world == null || mc.player == null || !RenderUtil.valid) return;
        double rng = range.get(), time = System.currentTimeMillis() / 1000.0;
        List<Item> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!targets.test(e)) continue;
            double d = mc.player.distanceTo(e);
            if (d > rng) continue;
            int rgb = targets.color(e, d, rng);
            float a = fade.get() ? (float) MathHelper.clamp(1.0 - (d - rng * 0.6) / (rng * 0.4), 0.15, 1.0) : 1f;
            if (pulse.get()) a *= 0.8f + 0.2f * (float) Math.sin(time * 3 + e.getId() * 0.7);
            if (hitFlash.get() && e instanceof LivingEntity le && le.hurtTime > 0)
                rgb = Draw.lerp(0xFF000000 | rgb, 0xFFFFFFFF, MathHelper.clamp(le.hurtTime / 10f, 0f, 1f) * 0.85f) & 0xFFFFFF;
            list.add(new Item(e, RenderUtil.lerpedBox(e).expand(expand.get()), rgb, a));
        }
        if (list.isEmpty()) return;

        String st = style.get();
        if (walls.get()) RenderUtil.setup(width.getFloat()); else RenderUtil.setupBlend3d(width.getFloat());

        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Item it : list) {
            EspDraw.quads(q, st, it.box(), it.rgb(), it.a(), fillAlpha.getFloat(), glow.getFloat(), glowSize.get());
            if (healthBar.get() && it.e() instanceof LivingEntity le)
                EspDraw.healthBar(q, it.box(), le.getHealth() / Math.max(1f, le.getMaxHealth()), it.a());
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Item it : list) EspDraw.lines(l, st, it.box(), it.rgb(), it.a(), cornerLen.get());
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
