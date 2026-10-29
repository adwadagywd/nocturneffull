package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Expanding glowing rings under your feet when you jump (and land). */
public class JumpCircles extends Module {
    private record Ring(Vec3d pos, long born) {}

    private final NumSetting radius = add(new NumSetting("Radius", 2.2, 0.8, 5.0, 0.1));
    private final NumSetting duration = add(new NumSetting("Duration", 1.0, 0.4, 2.5, 0.1));
    private final NumSetting width = add(new NumSetting("Line width", 2.0, 0.5, 5.0, 0.5));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "Gradient", "Rainbow"));
    private final BoolSetting landing = add(new BoolSetting("Also on landing", true));

    private final List<Ring> rings = new ArrayList<>();
    private boolean wasGround = true;

    public JumpCircles() { super("JumpCircles", "Glowing ring effect when you jump", Category.VISUALS); }

    @Override protected void onDisable() { rings.clear(); }

    @Override
    public void onTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        boolean g = p.isOnGround();
        if ((wasGround && !g && p.getVelocity().y > 0.05) || (landing.get() && !wasGround && g))
            rings.add(new Ring(p.getPos(), System.currentTimeMillis()));
        wasGround = g;
        if (rings.size() > 12) rings.remove(0);
    }

    private int rgb(long born, float t) {
        switch (colorMode.get()) {
            case "Rainbow": return MathHelper.hsvToRgb((float) (((born / 1000.0) * 0.15 + t * 0.2) % 1.0), 0.7f, 1f);
            case "Gradient": return Draw.lerp(Theme.accent(), Theme.accent2(), t) & 0xFFFFFF;
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    @Override
    public void onRender3d() {
        long now = System.currentTimeMillis();
        float dur = duration.getFloat();
        rings.removeIf(r -> (now - r.born()) / 1000f > dur);
        if (rings.isEmpty() || !RenderUtil.valid) return;

        RenderUtil.setupBlend3d(width.getFloat());
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Ring r : rings) {
            float t = (now - r.born()) / 1000f / dur;
            double rad = radius.get() * (1f - (1f - t) * (1f - t) * (1f - t)) + 0.15;
            int col = rgb(r.born(), t);
            RenderUtil.disc(q, r.pos().add(0, 0.02, 0), rad * 0.6, rad, 44,
                    RenderUtil.argb(col, 0f), RenderUtil.argb(col, 0.5f * (1f - t)));
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Ring r : rings) {
            float t = (now - r.born()) / 1000f / dur;
            double rad = radius.get() * (1f - (1f - t) * (1f - t) * (1f - t)) + 0.15;
            int col = rgb(r.born(), t);
            Vec3d c = r.pos().add(0, 0.025, 0);
            RenderUtil.ring(l, c, rad, 56, RenderUtil.argb(col, 1f - t));
            RenderUtil.ring(l, c, rad * 0.82, 48, RenderUtil.argb(col, 0.5f * (1f - t)));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
