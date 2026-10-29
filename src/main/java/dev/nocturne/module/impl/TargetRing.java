package dev.nocturne.module.impl;

import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** A glowing ring that slides up and down the entity you are looking at. */
public class TargetRing extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "Health", "Rainbow"));
    private final NumSetting speed = add(new NumSetting("Speed", 1.6, 0.5, 4.0, 0.1));
    private final NumSetting trail = add(new NumSetting("Trail rings", 3, 0, 6, 1));
    private final NumSetting width = add(new NumSetting("Line width", 2.0, 0.5, 5.0, 0.5));

    private LivingEntity last;
    private float alpha;
    private long lastNs;

    public TargetRing() { super("TargetRing", "Animated ring around your target", Category.VISUALS); }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        Entity t = mc.targetedEntity;
        LivingEntity cur = (t instanceof LivingEntity le && le.isAlive()) ? le : null;
        if (cur != null) last = cur;
        alpha += ((cur != null ? 1f : 0f) - alpha) * (1f - (float) Math.exp(-10f * dt));
        if (cur == null && alpha < 0.02f) last = null;
    }

    @Override
    public void onRender3d() {
        LivingEntity t = last;
        if (t == null || alpha < 0.02f || !RenderUtil.valid) return;
        Vec3d base = t.getLerpedPos(RenderUtil.tickDelta);
        double r = Math.max(0.45, t.getWidth() * 0.85 + 0.1), h = t.getHeight();
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        int rgb;
        switch (colorMode.get()) {
            case "Health" -> rgb = MathHelper.hsvToRgb(MathHelper.clamp(t.getHealth() / t.getMaxHealth(), 0f, 1f) * 0.33f, 0.8f, 1f);
            case "Rainbow" -> rgb = MathHelper.hsvToRgb((float) ((time * 0.2) % 1.0), 0.7f, 1f);
            default -> rgb = Theme.accent() & 0xFFFFFF;
        }

        RenderUtil.setupBlend3d(width.getFloat());
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        RenderUtil.disc(q, base.add(0, 0.02, 0), r * 0.5, r, 40, RenderUtil.argb(rgb, 0f), RenderUtil.argb(rgb, 0.25f * alpha));
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        RenderUtil.ring(l, base.add(0, 0.025, 0), r, 56, RenderUtil.argb(rgb, 0.55f * alpha));
        int n = trail.getInt();
        for (int k = 0; k <= n; k++) {
            double ph = (Math.sin(time - k * 0.22) + 1.0) / 2.0;
            float a = (1f - k / (n + 1f)) * alpha;
            RenderUtil.ring(l, new Vec3d(base.x, base.y + ph * h, base.z), r, 56, RenderUtil.argb(rgb, a));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
