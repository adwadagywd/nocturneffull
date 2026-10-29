package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Floating damage / heal numbers above entities. */
public class DamageText extends Module {
    private static DamageText INSTANCE;

    private record Pop(Vec3d pos, String text, int color, float scale, long born) {}

    private final BoolSetting onlyMine = add(new BoolSetting("Only my hits", false));
    private final BoolSetting self = add(new BoolSetting("Show on me", false));
    private final BoolSetting heals = add(new BoolSetting("Show healing", true));
    private final NumSetting minDamage = add(new NumSetting("Min damage", 0.5, 0.1, 5.0, 0.1));
    private final NumSetting duration = add(new NumSetting("Duration", 1.2, 0.4, 3.0, 0.1));
    private final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.6, 2.0, 0.1));

    private final Map<Integer, Float> hp = new HashMap<>();
    private final List<Pop> pops = new ArrayList<>();
    private int lastId = -1;
    private long lastAt;

    public DamageText() {
        super("DamageText", "Floating damage numbers", Category.VISUALS);
        INSTANCE = this;
    }

    public static void noteAttack(Entity e) {
        if (INSTANCE != null) { INSTANCE.lastId = e.getId(); INSTANCE.lastAt = System.currentTimeMillis(); }
    }

    private void spawn(LivingEntity e, String text, int color, float sc) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3d pos = e.getPos().add((r.nextDouble() - 0.5) * 0.6, e.getHeight() + 0.2, (r.nextDouble() - 0.5) * 0.6);
        pops.add(new Pop(pos, text, color, sc, System.currentTimeMillis()));
        if (pops.size() > 40) pops.remove(0);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        Set<Integer> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || (e == mc.player && !self.get())) continue;
            if (mc.player.distanceTo(e) > 48) continue;
            int id = e.getId();
            seen.add(id);
            float h = le.getHealth() + le.getAbsorptionAmount();
            Float prev = hp.put(id, h);
            if (prev == null) continue;
            float d = prev - h;
            if (d >= minDamage.getFloat()) {
                if (onlyMine.get() && !(id == lastId && now - lastAt < 1500)) continue;
                int col = Draw.lerp(0xFFFFD166, 0xFFFF4D6D, Math.min(1f, d / 10f));
                spawn(le, "-" + String.format(Locale.ROOT, "%.1f", d), col, 0.85f + Math.min(d / 12f, 0.7f));
            } else if (heals.get() && -d >= 1.0f) {
                spawn(le, "+" + String.format(Locale.ROOT, "%.1f", -d), 0xFF3DDC97, 0.85f);
            }
        }
        hp.keySet().retainAll(seen);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (pops.isEmpty() || !RenderUtil.valid) return;
        long now = System.currentTimeMillis();
        float dur = duration.getFloat();
        pops.removeIf(q -> (now - q.born()) / 1000f > dur);
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        for (Pop p : pops) {
            float t = (now - p.born()) / 1000f / dur;
            Vec3d s = RenderUtil.project(p.pos().add(0, t * 1.3, 0), sw, sh);
            if (s == null) continue;
            float pop = 1f + (1f - Math.min(1f, t * 7f)) * 0.9f;
            float sc = p.scale() * scale.getFloat() * pop;
            Draw.alpha = 1f - Math.max(0f, (t - 0.55f) / 0.45f);
            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            m.scale(sc, sc, 1f);
            Draw.bold(ctx, tr, p.text(), -Draw.boldWidth(tr, p.text()) / 2, -4, p.color(), true);
            m.pop();
        }
        Draw.alpha = 1f;
    }
}
