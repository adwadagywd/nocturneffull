package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.EspDraw;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dropped items: label with icon and name, rarity beams, ground rings, sparkles and optional boxes. */
public class ItemEsp extends Module {
    private final NumSetting range = add(new NumSetting("Range", 48, 8, 128, 1));
    private final ModeSetting minRarity = add(new ModeSetting("Min rarity", "All", "All", "Uncommon", "Rare", "Epic"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Rarity", "Rarity", "Theme", "Rainbow"));
    private final BoolSetting fade = add(new BoolSetting("Fade with distance", true));
    private final NumSetting glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));

    private final ModeSetting labelStyle = add(new ModeSetting("Label style", "Card", "Card", "Pill", "Minimal", "None"));
    private final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.6, 2.0, 0.05));
    private final BoolSetting icon = add(new BoolSetting("Icon", true));
    private final BoolSetting name = add(new BoolSetting("Name", true));
    private final BoolSetting count = add(new BoolSetting("Count", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", false));
    private final BoolSetting background = add(new BoolSetting("Background", true));

    private final BoolSetting beam = add(new BoolSetting("Beam", true));
    private final ModeSetting beamRarity = add(new ModeSetting("Beam min rarity", "Uncommon", "All", "Uncommon", "Rare", "Epic"));
    private final NumSetting beamHeight = add(new NumSetting("Beam height", 3.0, 1.0, 8.0, 0.5));
    private final BoolSetting ring = add(new BoolSetting("Ground ring", true));
    private final BoolSetting sparkles = add(new BoolSetting("Sparkles", true));
    private final ModeSetting boxStyle = add(new ModeSetting("Box style", "None", "None", "Box", "Corners", "Filled", "Both", "Gradient", "Nodes"));

    public ItemEsp() {
        super("ItemESP", "Labels, beams and glow for dropped items", Category.RENDER);
        scale.showIf(() -> !labelStyle.is("None"));
        icon.showIf(() -> !labelStyle.is("None"));
        name.showIf(() -> !labelStyle.is("None"));
        count.showIf(() -> !labelStyle.is("None"));
        distance.showIf(() -> !labelStyle.is("None"));
        background.showIf(() -> labelStyle.is("Card"));
        beamRarity.showIf(beam::get);
        beamHeight.showIf(beam::get);
    }

    private static int rank(ItemStack s) {
        return switch (s.getRarity().name()) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            default -> 0;
        };
    }

    private static int rarityColor(int rank) {
        return switch (rank) {
            case 1 -> 0xFFFFFF55;
            case 2 -> 0xFF55FFFF;
            case 3 -> 0xFFFF55FF;
            default -> 0xFFFFFFFF;
        };
    }

    private static int toRank(String s) {
        return switch (s) { case "Uncommon" -> 1; case "Rare" -> 2; case "Epic" -> 3; default -> 0; };
    }

    private int rgbFor(ItemEntity ie, double time) {
        switch (colorMode.get()) {
            case "Theme": return Theme.accent() & 0xFFFFFF;
            case "Rainbow": return MathHelper.hsvToRgb((float) ((time * 0.1 + ie.getId() * 0.07) % 1.0), 0.7f, 1f);
            default: return rarityColor(rank(ie.getStack())) & 0xFFFFFF;
        }
    }

    private float fadeFor(double d) {
        return fade.get() ? (float) MathHelper.clamp(1.0 - (d - range.get() * 0.55) / (range.get() * 0.45), 0.25, 1.0) : 1f;
    }

    private static double hash(double seed, double salt) {
        double v = Math.sin(seed * 12.9898 + salt * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    private List<ItemEntity> items() {
        List<ItemEntity> list = new ArrayList<>();
        if (mc.world == null || mc.player == null) return list;
        int min = toRank(minRarity.get());
        for (Entity e : mc.world.getEntities())
            if (e instanceof ItemEntity ie && !ie.isRemoved() && mc.player.distanceTo(e) <= range.get() && rank(ie.getStack()) >= min)
                list.add(ie);
        list.sort(Comparator.comparingDouble((ItemEntity e) -> -mc.player.squaredDistanceTo(e)));
        if (list.size() > 80) list = new ArrayList<>(list.subList(list.size() - 80, list.size()));
        return list;
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (!RenderUtil.valid || labelStyle.is("None")) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        double time = System.currentTimeMillis() / 1000.0;
        String ls = labelStyle.get();
        for (ItemEntity ie : items()) {
            Vec3d s = RenderUtil.project(ie.getLerpedPos(RenderUtil.tickDelta).add(0, ie.getHeight() + 0.45, 0), sw, sh);
            if (s == null) continue;
            ItemStack st = ie.getStack();
            double d = mc.player.distanceTo(ie);
            float a = fadeFor(d);
            int rgb = rgbFor(ie, time), argb = 0xFF000000 | rgb;

            StringBuilder sb = new StringBuilder();
            if (name.get()) sb.append(st.getName().getString());
            if (count.get() && st.getCount() > 1) sb.append(sb.length() > 0 ? " x" : "x").append(st.getCount());
            if (distance.get()) sb.append(sb.length() > 0 ? "  " : "").append((int) d).append("m");
            String text = sb.toString();
            boolean pill = ls.equals("Pill"), minimal = ls.equals("Minimal");
            int h = pill ? 18 : 20;
            int w = (icon.get() ? 20 : 4) + (text.isEmpty() ? 0 : Draw.width(tr, text) + 8) + (pill ? 8 : 0);
            if (w < 22) w = 22;

            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            m.scale(scale.getFloat(), scale.getFloat(), 1f);
            Draw.alpha = a;
            if (!minimal && (background.get() || pill)) {
                if (pill) {
                    Draw.surface(ctx, -w / 2, -h, w, h, h / 2f, 0xD00B0B0F, Draw.scaleA(argb, 0.55f));
                    Draw.round(ctx, -w / 2 + 6, -h / 2 - 3, 6, 6, 3f, argb);
                } else {
                    Draw.surface(ctx, -w / 2, -h, w, h, 6, 0xC00B0B0F, 0x30FFFFFF);
                    Draw.round(ctx, -w / 2 + 6, -3, w - 12, 1, 0.5f, argb);
                }
            }
            int ix = -w / 2 + (pill ? 14 : 3);
            if (icon.get() && a > 0.5f) ctx.drawItem(st, ix, -h + (pill ? 1 : 2));
            if (!text.isEmpty()) Draw.text(ctx, tr, text, ix + (icon.get() ? 19 : 3), -h + (pill ? 5 : 6), argb, minimal);
            m.pop();
        }
        Draw.alpha = 1f;
    }

    @Override
    public void onRender3d() {
        if (!RenderUtil.valid) return;
        boolean boxes = !boxStyle.is("None");
        if (!boxes && !beam.get() && !ring.get() && !sparkles.get()) return;
        List<ItemEntity> list = items();
        if (list.isEmpty()) return;
        double time = System.currentTimeMillis() / 1000.0;
        float g = glow.getFloat();
        int bMin = toRank(beamRarity.get());

        RenderUtil.setup(1.5f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (ItemEntity ie : list) {
            Vec3d pos = ie.getLerpedPos(RenderUtil.tickDelta);
            int rank = rank(ie.getStack()), rgb = rgbFor(ie, time);
            float a = fadeFor(mc.player.distanceTo(ie));
            if (boxes) EspDraw.quads(q, boxStyle.get(), RenderUtil.lerpedBox(ie).expand(0.05), rgb, a, 0.25f, g, 0.08);
            if (ring.get()) {
                double rr = 0.38 + 0.07 * Math.sin(time * 3 + ie.getId());
                RenderUtil.disc(q, pos.add(0, 0.03, 0), 0, rr * 1.7, 28, RenderUtil.argb(rgb, 0.3f * a * (0.5f + 0.5f * g)), RenderUtil.argb(rgb, 0f));
                RenderUtil.disc(q, pos.add(0, 0.03, 0), rr * 0.85, rr, 28, RenderUtil.argb(rgb, 0.6f * a), RenderUtil.argb(rgb, 0.6f * a));
            }
            if (beam.get() && rank >= bMin) {
                EspDraw.beam(q, pos, beamHeight.get(), 0.13, rgb, 0.32f * (0.6f + 0.4f * g) * a);
                EspDraw.beam(q, pos, beamHeight.get(), 0.025, rgb, 0.9f * a);
            }
            if (sparkles.get() && rank >= 1) {
                for (int j = 0; j < 6; j++) {
                    double ph = (time * 0.5 + hash(ie.getId(), j)) % 1.0;
                    Vec3d p = pos.add((hash(ie.getId(), j + 10) - 0.5) * 0.7, 0.15 + ph * 1.4, (hash(ie.getId(), j + 20) - 0.5) * 0.7);
                    float al = (float) Math.sin(Math.PI * ph) * a;
                    RenderUtil.poly(q, p, 0.035, 4, 0, 0.35, RenderUtil.argb(0xFFFFFF, 0.9f * al), RenderUtil.argb(rgb, 0.5f * al));
                }
            }
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (ItemEntity ie : list) {
            int rgb = rgbFor(ie, time);
            float a = fadeFor(mc.player.distanceTo(ie));
            if (boxes) EspDraw.lines(l, boxStyle.get(), RenderUtil.lerpedBox(ie).expand(0.05), rgb, a, 0.3);
            if (ring.get()) {
                double rr = 0.38 + 0.07 * Math.sin(time * 3 + ie.getId());
                RenderUtil.ring(l, ie.getLerpedPos(RenderUtil.tickDelta).add(0, 0.035, 0), rr, 28, RenderUtil.argb(rgb, a));
            }
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
