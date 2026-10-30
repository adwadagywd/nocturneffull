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

/** Glowing trail behind you while you walk or run: a ribbon, a thin line, or glowing footsteps. */
public class Trails extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Ribbon", "Ribbon", "Line", "Footsteps"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Gradient", "Theme", "Gradient", "Rainbow"));
    private final NumSetting duration = add(new NumSetting("Duration", 2.5, 0.5, 8.0, 0.5));
    private final NumSetting height = add(new NumSetting("Height", 0.6, 0.1, 2.0, 0.1));
    private final NumSetting width = add(new NumSetting("Line width", 2.5, 1.0, 6.0, 0.5));
    private final BoolSetting sparkles = add(new BoolSetting("Sparkles", true));
    private final BoolSetting others = add(new BoolSetting("Other players", false));
    private final BoolSetting hideFirst = add(new BoolSetting("Hide in first person", false));

    private record Pt(Vec3d pos, long t) {}
    private record Step(Vec3d pos, double fx, double fz, long t) {}
    private static final class Data {
        final ArrayDeque<Pt> pts = new ArrayDeque<>();
        final ArrayDeque<Step> steps = new ArrayDeque<>();
        Vec3d lastStep;
        boolean left;
    }

    private final Map<Integer, Data> trails = new HashMap<>();

    public Trails() { super("Trails", "Glowing trail behind you when you walk or run", Category.VISUALS); }

    @Override protected void onDisable() { trails.clear(); }

    @Override
    public void onTick(MinecraftClient mc) {
        long now = System.currentTimeMillis(), life = (long) (duration.get() * 1000);
        Set<Integer> seen = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !others.get()) continue;
            seen.add(p.getId());
            Data d = trails.computeIfAbsent(p.getId(), k -> new Data());
            Vec3d pos = p.getPos();

            Pt last = d.pts.peekLast();
            if (last != null && last.pos().squaredDistanceTo(pos) > 64) { // teleported: do not stretch a ribbon across the map
                d.pts.clear(); d.steps.clear(); d.lastStep = null;
                last = null;
            }
            if (last == null || last.pos().squaredDistanceTo(pos) > 0.04) d.pts.addLast(new Pt(pos, now));
            while (!d.pts.isEmpty() && (now - d.pts.peekFirst().t() > life || d.pts.size() > 400)) d.pts.removeFirst();

            if (p.isOnGround()) {
                if (d.lastStep == null) d.lastStep = pos;
                else {
                    double dx = pos.x - d.lastStep.x, dz = pos.z - d.lastStep.z, dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 0.85) {
                        double fx = dx / dist, fz = dz / dist, side = d.left ? 0.13 : -0.13;
                        d.left = !d.left;
                        d.steps.addLast(new Step(new Vec3d(pos.x - fz * side, pos.y + 0.03, pos.z + fx * side), fx, fz, now));
                        d.lastStep = pos;
                    }
                }
            }
            while (!d.steps.isEmpty() && now - d.steps.peekFirst().t() > life) d.steps.removeFirst();
        }
        trails.keySet().retainAll(seen);
    }

    private int rgb(float t, double time) {
        switch (colorMode.get()) {
            case "Rainbow": return MathHelper.hsvToRgb((float) ((t * 0.5 + time) % 1.0), 0.7f, 1f);
            case "Gradient": return Draw.lerp(Theme.accent2(), Theme.accent(), t) & 0xFFFFFF;
            default: return Theme.accent() & 0xFFFFFF;
        }
    }

    private static double hash(double seed, double salt) {
        double v = Math.sin(seed * 12.9898 + salt * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    @Override
    public void onRender3d() {
        if (trails.isEmpty() || mc.world == null || !RenderUtil.valid) return;
        boolean first = mc.options.getPerspective().isFirstPerson();
        long now = System.currentTimeMillis(), life = Math.max(1L, (long) (duration.get() * 1000));
        double time = now / 6000.0, anim = now / 1000.0;
        String st = style.get();
        boolean steps = st.equals("Footsteps");
        float h = height.getFloat();

        RenderUtil.setupBlend3d(width.getFloat());
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Map.Entry<Integer, Data> en : trails.entrySet()) {
            if (hideFirst.get() && first && mc.player != null && en.getKey() == mc.player.getId()) continue;
            Data d = en.getValue();

            if (steps) {
                int total = d.steps.size(), i = 0;
                for (Step s : d.steps) {
                    float a = 1f - (now - s.t()) / (float) life;
                    int col = rgb(total <= 1 ? 1f : i / (float) (total - 1), time);
                    i++;
                    if (a <= 0.01f) continue;
                    Vec3d c = s.pos();
                    blob(q, c.add(s.fx() * 0.07, 0, s.fz() * 0.07), s.fx(), s.fz(), 0.085, 0.075, col, a);
                    blob(q, c.subtract(s.fx() * 0.075, 0, s.fz() * 0.075), s.fx(), s.fz(), 0.06, 0.055, col, a);
                }
                continue;
            }

            List<Pt> pts = new ArrayList<>(d.pts);
            Entity ent = mc.world.getEntityById(en.getKey());
            if (ent != null && !pts.isEmpty()) pts.add(new Pt(ent.getLerpedPos(RenderUtil.tickDelta), pts.get(pts.size() - 1).t()));
            int n = pts.size();
            if (n < 2) continue;
            for (int i = 0; i + 1 < n; i++) {
                Pt a = pts.get(i), b = pts.get(i + 1);
                float a0 = 1f - (now - a.t()) / (float) life, a1 = 1f - (now - b.t()) / (float) life;
                if (a0 <= 0.01f && a1 <= 0.01f) continue;
                a0 = Math.max(0f, a0); a1 = Math.max(0f, a1);
                int c0 = rgb(i / (float) (n - 1), time), c1 = rgb((i + 1) / (float) (n - 1), time);
                if (st.equals("Ribbon")) {
                    Vec3d p0 = a.pos(), p1 = b.pos();
                    RenderUtil.v(q, p0.x, p0.y + 0.03, p0.z, RenderUtil.argb(c0, a0 * 0.7f));
                    RenderUtil.v(q, p1.x, p1.y + 0.03, p1.z, RenderUtil.argb(c1, a1 * 0.7f));
                    RenderUtil.v(q, p1.x, p1.y + h, p1.z, RenderUtil.argb(c1, 0f));
                    RenderUtil.v(q, p0.x, p0.y + h, p0.z, RenderUtil.argb(c0, 0f));
                }
            }
            if (sparkles.get()) {
                for (int i = 0; i < n; i += 2) {
                    Pt p = pts.get(i);
                    float a = 1f - (now - p.t()) / (float) life;
                    if (a <= 0.05f) continue;
                    double seed = p.t() % 100000L, age = (now - p.t()) / 1000.0;
                    Vec3d c = p.pos().add((hash(seed, 1) - 0.5) * 0.5, 0.1 + hash(seed, 2) * Math.max(0.3, h) * 0.8 + age * 0.3, (hash(seed, 3) - 0.5) * 0.5);
                    int col = rgb(i / (float) (n - 1), time);
                    RenderUtil.poly(q, c, 0.03 + 0.025 * hash(seed, 4), 4, anim, 0.35, RenderUtil.argb(0xFFFFFF, a * 0.9f), RenderUtil.argb(col, a * 0.5f));
                }
            }
        }
        RenderUtil.finish(q);

        if (!steps) {
            BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
            for (Map.Entry<Integer, Data> en : trails.entrySet()) {
                if (hideFirst.get() && first && mc.player != null && en.getKey() == mc.player.getId()) continue;
                List<Pt> pts = new ArrayList<>(en.getValue().pts);
                Entity ent = mc.world.getEntityById(en.getKey());
                if (ent != null && !pts.isEmpty()) pts.add(new Pt(ent.getLerpedPos(RenderUtil.tickDelta), pts.get(pts.size() - 1).t()));
                int n = pts.size();
                for (int i = 0; i + 1 < n; i++) {
                    float a1 = 1f - (now - pts.get(i + 1).t()) / (float) life;
                    if (a1 <= 0.01f) continue;
                    int c1 = rgb((i + 1) / (float) (n - 1), time);
                    RenderUtil.line(l, pts.get(i).pos().add(0, 0.03, 0), pts.get(i + 1).pos().add(0, 0.03, 0), RenderUtil.argb(c1, a1));
                }
            }
            RenderUtil.finish(l);
        }
        RenderUtil.restore();
    }

    /** soft glowing ellipse lying on the ground, long axis along (fx, fz) */
    private static void blob(BufferBuilder b, Vec3d c, double fx, double fz, double rf, double rs, int rgb, float a) {
        int segs = 14;
        double sx = -fz, sz = fx;
        int cc = RenderUtil.argb(rgb, a * 0.85f), cr = RenderUtil.argb(rgb, a * 0.2f);
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            RenderUtil.v(b, c.x, c.y, c.z, cc);
            RenderUtil.v(b, c.x + fx * Math.cos(a0) * rf + sx * Math.sin(a0) * rs, c.y, c.z + fz * Math.cos(a0) * rf + sz * Math.sin(a0) * rs, cr);
            RenderUtil.v(b, c.x + fx * Math.cos(a1) * rf + sx * Math.sin(a1) * rs, c.y, c.z + fz * Math.cos(a1) * rf + sz * Math.sin(a1) * rs, cr);
            RenderUtil.v(b, c.x + fx * Math.cos(a1) * rf + sx * Math.sin(a1) * rs, c.y, c.z + fz * Math.cos(a1) * rf + sz * Math.sin(a1) * rs, cr);
        }
    }
}
