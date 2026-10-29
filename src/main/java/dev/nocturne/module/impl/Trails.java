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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** A glowing ribbon that follows you (or everyone) and fades out behind. */
public class Trails extends Module {
    private final NumSetting length = add(new NumSetting("Length", 40, 5, 160, 1));
    private final NumSetting width = add(new NumSetting("Width", 2.5, 1.0, 6.0, 0.5));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Gradient", "Theme", "Gradient", "Rainbow"));
    private final BoolSetting others = add(new BoolSetting("Other players", false));
    private final BoolSetting hideFirst = add(new BoolSetting("Hide in first person", false));

    private final Map<Integer, ArrayDeque<Vec3d>> trails = new HashMap<>();

    public Trails() { super("Trails", "Fading ribbon behind players", Category.VISUALS); }

    @Override protected void onDisable() { trails.clear(); }

    @Override
    public void onTick(MinecraftClient mc) {
        Set<Integer> seen = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !others.get()) continue;
            seen.add(p.getId());
            ArrayDeque<Vec3d> d = trails.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            Vec3d pos = p.getPos().add(0, 0.08, 0);
            if (d.isEmpty() || d.peekLast().squaredDistanceTo(pos) > 0.0009) d.addLast(pos);
            while (d.size() > length.getInt()) d.removeFirst();
        }
        trails.keySet().retainAll(seen);
    }

    @Override
    public void onRender3d() {
        if (trails.isEmpty() || mc.world == null || !RenderUtil.valid) return;
        boolean first = mc.options.getPerspective().isFirstPerson();
        double time = System.currentTimeMillis() / 6000.0;

        RenderUtil.setupBlend3d(width.getFloat());
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Map.Entry<Integer, ArrayDeque<Vec3d>> en : trails.entrySet()) {
            if (hideFirst.get() && first && mc.player != null && en.getKey() == mc.player.getId()) continue;
            List<Vec3d> pts = new ArrayList<>(en.getValue());
            Entity ent = mc.world.getEntityById(en.getKey());
            if (ent != null) pts.add(ent.getLerpedPos(RenderUtil.tickDelta).add(0, 0.08, 0));
            int n = pts.size();
            for (int i = 0; i + 1 < n; i++) {
                float t = (i + 1) / (float) n; // 0 = old end, 1 = newest
                int rgb;
                switch (colorMode.get()) {
                    case "Rainbow" -> rgb = MathHelper.hsvToRgb((float) ((t * 0.5 + time) % 1.0), 0.7f, 1f);
                    case "Gradient" -> rgb = Draw.lerp(Theme.accent2(), Theme.accent(), t) & 0xFFFFFF;
                    default -> rgb = Theme.accent() & 0xFFFFFF;
                }
                RenderUtil.line(b, pts.get(i), pts.get(i + 1), RenderUtil.argb(rgb, t * t));
            }
        }
        RenderUtil.finish(b);
        RenderUtil.restore();
    }
}
