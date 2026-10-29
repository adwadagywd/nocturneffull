package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Tracers extends Module {
    private final ModeSetting start = add(new ModeSetting("Start", "Crosshair", "Crosshair", "Bottom"));
    private final ModeSetting target = add(new ModeSetting("Target", "Center", "Feet", "Center", "Eyes"));
    private final NumSetting range = add(new NumSetting("Range", 128, 8, 512, 1));
    private final NumSetting width = add(new NumSetting("Width", 1.5, 0.5, 6, 0.5));
    private final NumSetting opacity = add(new NumSetting("Opacity", 0.9, 0.1, 1, 0.05));
    private final BoolSetting fade = add(new BoolSetting("Fade by dist", true));
    private final TargetSettings targets = new TargetSettings(this, true, false, false, false);

    public Tracers() { super("Tracers", "Lines from your crosshair to entities", Category.RENDER); }

    @Override
    public void onRender3d() {
        if (mc.world == null || mc.player == null || !RenderUtil.valid) return;
        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities())
            if (targets.test(e) && mc.player.distanceTo(e) <= range.get()) list.add(e);
        if (list.isEmpty()) return;

        // exact centre of the screen (= crosshair), taken through the real camera matrices
        Vec3d from = RenderUtil.unproject(0f, start.is("Bottom") ? -0.85f : 0f, 0.5f);

        RenderUtil.setup(width.getFloat());
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Entity e : list) {
            double dist = mc.player.distanceTo(e);
            Vec3d p = e.getLerpedPos(RenderUtil.tickDelta);
            switch (target.get()) {
                case "Center" -> p = p.add(0, e.getHeight() / 2.0, 0);
                case "Eyes" -> p = p.add(0, e.getStandingEyeHeight(), 0);
                default -> {}
            }
            float a = opacity.getFloat() * (fade.get() ? (float) (1.0 - 0.6 * dist / range.get()) : 1f);
            RenderUtil.line(b, from, p, RenderUtil.argb(targets.color(e, dist, range.get()), a));
        }
        RenderUtil.finish(b);
        RenderUtil.restore();
    }
}
