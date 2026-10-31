package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.EspDraw;
import dev.nocturne.util.RenderUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Item ESP with your own rules: pick the item types (search), pick enchantments (search) and give the item
 * a name that is shown above it while it lies on the ground. Up to 5 rules, the first matching rule wins.
 */
public class CustomItemEsp extends Module {
    private static final int RULES = 5;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private record Hit(ItemEntity ie, int rule, String text, String ench, int rgb) {}

    private final NumSetting rules = add(new NumSetting("Rules", 2, 1, RULES, 1));
    private final NumSetting range = add(new NumSetting("Range", 64, 8, 128, 1));

    private final ItemListSetting[] items = new ItemListSetting[RULES];
    private final EnchantListSetting[] ench = new EnchantListSetting[RULES];
    private final ModeSetting[] match = new ModeSetting[RULES];
    private final NumSetting[] minLvl = new NumSetting[RULES];
    private final TextSetting[] label = new TextSetting[RULES];
    private final ColorSetting[] color = new ColorSetting[RULES];

    private final ModeSetting labelStyle, boxStyle;
    private final NumSetting scale, beamHeight, glow;
    private final BoolSetting icon, count, distance, showEnch, beam, ring, sparkles, fade;

    private List<Hit> hits = new ArrayList<>();
    private int tick = 100;

    public CustomItemEsp() {
        super("CustomItemESP", "Your own item rules: item + enchants + custom name on the ground", Category.RENDER);
        float[] hues = {0.0f, 0.55f, 0.13f, 0.78f, 0.36f};
        for (int i = 0; i < RULES; i++) {
            final int idx = i, n = i + 1;
            items[i] = add(new ItemListSetting("Rule " + n + " items"));
            ench[i] = add(new EnchantListSetting("Rule " + n + " enchants"));
            match[i] = add(new ModeSetting("Rule " + n + " match", "Any", "Any", "All"));
            minLvl[i] = add(new NumSetting("Rule " + n + " min level", 1, 1, 10, 1));
            label[i] = add(new TextSetting("Rule " + n + " name", "", 32));
            color[i] = add(new ColorSetting("Rule " + n + " color", hues[i]));
            items[i].showIf(() -> idx < rules.getInt());
            ench[i].showIf(() -> idx < rules.getInt());
            match[i].showIf(() -> idx < rules.getInt() && !ench[idx].get().isEmpty());
            minLvl[i].showIf(() -> idx < rules.getInt() && !ench[idx].get().isEmpty());
            label[i].showIf(() -> idx < rules.getInt());
            color[i].showIf(() -> idx < rules.getInt());
        }
        labelStyle = add(new ModeSetting("Label style", "Card", "Card", "Pill", "Minimal", "None"));
        scale = add(new NumSetting("Scale", 1.0, 0.6, 2.0, 0.05));
        icon = add(new BoolSetting("Icon", true));
        count = add(new BoolSetting("Count", true));
        distance = add(new BoolSetting("Distance", false));
        showEnch = add(new BoolSetting("Show enchants", true));
        beam = add(new BoolSetting("Beam", true));
        beamHeight = add(new NumSetting("Beam height", 3.0, 1.0, 8.0, 0.5));
        ring = add(new BoolSetting("Ground ring", true));
        sparkles = add(new BoolSetting("Sparkles", true));
        boxStyle = add(new ModeSetting("Box style", "None", "None", "Box", "Corners", "Filled", "Both", "Gradient", "Nodes"));
        glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));
        fade = add(new BoolSetting("Fade with distance", true));
        scale.showIf(() -> !labelStyle.is("None"));
        icon.showIf(() -> !labelStyle.is("None"));
        count.showIf(() -> !labelStyle.is("None"));
        distance.showIf(() -> !labelStyle.is("None"));
        showEnch.showIf(() -> !labelStyle.is("None"));
        beamHeight.showIf(beam::get);
    }

    @Override protected void onEnable() { tick = 100; }
    @Override protected void onDisable() { hits = new ArrayList<>(); }

    // ---------------------------------------------------------------- matching

    private static void collect(Map<String, Integer> m, ItemEnchantmentsComponent c) {
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : c.getEnchantmentEntries())
            e.getKey().getKey().ifPresent(k -> m.merge(k.getValue().toString(), e.getIntValue(), Math::max));
    }

    /** enchantments on the stack, including the ones stored in an enchanted book */
    private static Map<String, Integer> enchants(ItemStack st) {
        Map<String, Integer> m = new LinkedHashMap<>();
        collect(m, st.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
        collect(m, st.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
        return m;
    }

    private int ruleFor(ItemStack st, Map<String, Integer> have) {
        String id = Registries.ITEM.getId(st.getItem()).toString();
        int n = rules.getInt();
        for (int i = 0; i < n; i++) {
            Set<String> its = items[i].get(), en = ench[i].get();
            if (its.isEmpty() && en.isEmpty()) continue;          // an empty rule matches nothing
            if (!its.isEmpty() && !its.contains(id)) continue;
            if (!en.isEmpty()) {
                int min = minLvl[i].getInt();
                boolean all = match[i].is("All"), ok = all;
                for (String e : en) {
                    boolean has = have.getOrDefault(e, 0) >= min;
                    if (all && !has) { ok = false; break; }
                    if (!all && has) { ok = true; break; }
                }
                if (!ok) continue;
            }
            return i;
        }
        return -1;
    }

    private static String pretty(String id) {
        String p = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : p.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String enchText(Map<String, Integer> have) {
        if (have.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : have.entrySet()) {
            if (shown == 4) { sb.append(" +").append(have.size() - 4); break; }
            if (shown > 0) sb.append(", ");
            int lv = e.getValue();
            sb.append(pretty(e.getKey())).append(lv > 0 && lv < ROMAN.length ? " " + ROMAN[lv] : " " + lv);
            shown++;
        }
        return sb.toString();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        if (++tick < 4) return;
        tick = 0;
        List<Hit> out = new ArrayList<>();
        double r = range.get();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity ie) || ie.isRemoved()) continue;
            if (mc.player.distanceTo(ie) > r) continue;
            ItemStack st = ie.getStack();
            Map<String, Integer> have = enchants(st);
            int rule = ruleFor(st, have);
            if (rule < 0) continue;
            String custom = label[rule].get().trim();
            out.add(new Hit(ie, rule, custom.isEmpty() ? st.getName().getString() : custom,
                    showEnch.get() ? enchText(have) : "", color[rule].rgb() & 0xFFFFFF));
        }
        hits = out;
    }

    // ---------------------------------------------------------------- drawing

    private float fadeFor(double d) {
        return fade.get() ? (float) MathHelper.clamp(1.0 - (d - range.get() * 0.55) / (range.get() * 0.45), 0.25, 1.0) : 1f;
    }

    private static double hash(double seed, double salt) {
        double v = Math.sin(seed * 12.9898 + salt * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        List<Hit> list = hits;
        if (list.isEmpty() || !RenderUtil.valid || labelStyle.is("None") || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        String ls = labelStyle.get();
        boolean pill = ls.equals("Pill"), minimal = ls.equals("Minimal");
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            Vec3d s = RenderUtil.project(ie.getLerpedPos(RenderUtil.tickDelta).add(0, ie.getHeight() + 0.45, 0), sw, sh);
            if (s == null) continue;
            ItemStack st = ie.getStack();
            double d = mc.player.distanceTo(ie);
            float a = fadeFor(d);
            int argb = 0xFF000000 | h.rgb();

            StringBuilder sb = new StringBuilder(h.text());
            if (count.get() && st.getCount() > 1) sb.append(" x").append(st.getCount());
            if (distance.get()) sb.append("  ").append((int) d).append("m");
            String text = sb.toString(), sub = h.ench();
            int textW = Math.max(Draw.width(tr, text), sub.isEmpty() ? 0 : Draw.width(tr, sub));
            int hh = (pill ? 18 : 20) + (sub.isEmpty() ? 0 : 9);
            int w = Math.max(22, (icon.get() ? 20 : 4) + textW + 8 + (pill ? 8 : 0));

            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            m.scale(scale.getFloat(), scale.getFloat(), 1f);
            Draw.alpha = a;
            if (!minimal) {
                if (pill) {
                    Draw.surface(ctx, -w / 2, -hh, w, hh, hh / 2f, 0xD00B0B0F, Draw.scaleA(argb, 0.55f));
                    Draw.round(ctx, -w / 2 + 6, -hh / 2 - 3, 6, 6, 3f, argb);
                } else {
                    Draw.surface(ctx, -w / 2, -hh, w, hh, 6, 0xC00B0B0F, 0x30FFFFFF);
                    Draw.round(ctx, -w / 2 + 6, -3, w - 12, 1, 0.5f, argb);
                }
            }
            int ix = -w / 2 + (pill ? 14 : 3), tx = ix + (icon.get() ? 19 : 3), ty = -hh + (pill ? 5 : 6);
            if (icon.get() && a > 0.5f) ctx.drawItem(st, ix, -hh + (hh - 16) / 2 - (sub.isEmpty() ? 0 : 0));
            Draw.text(ctx, tr, text, tx, ty, argb, minimal);
            if (!sub.isEmpty()) Draw.text(ctx, tr, sub, tx, ty + 10, Theme.dim(), minimal);
            m.pop();
        }
        Draw.alpha = 1f;
    }

    @Override
    public void onRender3d() {
        List<Hit> list = hits;
        if (list.isEmpty() || !RenderUtil.valid || mc.player == null) return;
        boolean boxes = !boxStyle.is("None");
        if (!boxes && !beam.get() && !ring.get() && !sparkles.get()) return;
        double time = System.currentTimeMillis() / 1000.0;
        float g = glow.getFloat();

        RenderUtil.setup(1.5f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            Vec3d pos = ie.getLerpedPos(RenderUtil.tickDelta);
            int rgb = h.rgb();
            float a = fadeFor(mc.player.distanceTo(ie));
            if (boxes) EspDraw.quads(q, boxStyle.get(), RenderUtil.lerpedBox(ie).expand(0.05), rgb, a, 0.25f, g, 0.08);
            if (ring.get()) {
                double rr = 0.38 + 0.07 * Math.sin(time * 3 + ie.getId());
                RenderUtil.disc(q, pos.add(0, 0.03, 0), 0, rr * 1.7, 28, RenderUtil.argb(rgb, 0.3f * a * (0.5f + 0.5f * g)), RenderUtil.argb(rgb, 0f));
                RenderUtil.disc(q, pos.add(0, 0.03, 0), rr * 0.85, rr, 28, RenderUtil.argb(rgb, 0.6f * a), RenderUtil.argb(rgb, 0.6f * a));
            }
            if (beam.get()) {
                EspDraw.beam(q, pos, beamHeight.get(), 0.13, rgb, 0.32f * (0.6f + 0.4f * g) * a);
                EspDraw.beam(q, pos, beamHeight.get(), 0.025, rgb, 0.9f * a);
            }
            if (sparkles.get()) {
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
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            float a = fadeFor(mc.player.distanceTo(ie));
            if (boxes) EspDraw.lines(l, boxStyle.get(), RenderUtil.lerpedBox(ie).expand(0.05), h.rgb(), a, 0.3);
            if (ring.get()) {
                double rr = 0.38 + 0.07 * Math.sin(time * 3 + ie.getId());
                RenderUtil.ring(l, ie.getLerpedPos(RenderUtil.tickDelta).add(0, 0.035, 0), rr, 28, RenderUtil.argb(h.rgb(), a));
            }
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
