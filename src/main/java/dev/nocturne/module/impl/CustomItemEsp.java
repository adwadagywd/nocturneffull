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
 * Pick an item, an enchantment and a level - and name it. Matching items lying on the ground get your name above
 * them (plus a beam). Each rule: Items (optional) + Enchants + Min level + Name. The next rule appears once the
 * previous one is filled in.
 */
public class CustomItemEsp extends Module {
    private static final int RULES = 4;
    private static final int[] PALETTE = {0, 0xFFD34E, 0x4DEBFF, 0xFF6DE1}; // rule 1 uses the theme accent
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private record Hit(ItemEntity ie, int rule, String text, String ench, int rgb) {}

    private final NumSetting range = add(new NumSetting("Range", 64, 8, 128, 1));
    private final ItemListSetting[] items = new ItemListSetting[RULES];
    private final EnchantListSetting[] ench = new EnchantListSetting[RULES];
    private final NumSetting[] level = new NumSetting[RULES];
    private final TextSetting[] name = new TextSetting[RULES];
    private final BoolSetting beam, showEnch;

    private List<Hit> hits = new ArrayList<>();
    private int tick = 100;

    public CustomItemEsp() {
        super("CustomItemESP", "Name items on the ground by item type + enchant + level", Category.RENDER);
        for (int i = 0; i < RULES; i++) {
            final int idx = i, n = i + 1;
            items[i] = add(new ItemListSetting("Rule " + n + " item"));
            ench[i] = add(new EnchantListSetting("Rule " + n + " enchant"));
            level[i] = add(new NumSetting("Rule " + n + " level", 1, 1, 10, 1));
            name[i] = add(new TextSetting("Rule " + n + " name", "", 32));
            items[i].showIf(() -> visible(idx));
            ench[i].showIf(() -> visible(idx));
            level[i].showIf(() -> visible(idx) && !ench[idx].get().isEmpty());
            name[i].showIf(() -> visible(idx));
        }
        beam = add(new BoolSetting("Beam", true));
        showEnch = add(new BoolSetting("Show enchants", true));
    }

    private boolean filled(int i) { return !items[i].get().isEmpty() || !ench[i].get().isEmpty(); }

    /** rule 1 is always there, every next rule shows up when the one before it is filled in */
    private boolean visible(int i) { return i == 0 || filled(i - 1); }

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

    /** first rule that fits: right item (if any chosen) AND at least one chosen enchant at the chosen level (if any chosen) */
    private int ruleFor(ItemStack st, Map<String, Integer> have) {
        String id = Registries.ITEM.getId(st.getItem()).toString();
        for (int i = 0; i < RULES; i++) {
            if (!visible(i) || !filled(i)) continue;
            Set<String> its = items[i].get(), en = ench[i].get();
            if (!its.isEmpty() && !its.contains(id)) continue;
            if (!en.isEmpty()) {
                int min = level[i].getInt();
                boolean ok = false;
                for (String e : en) if (have.getOrDefault(e, 0) >= min) { ok = true; break; }
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
            sb.append(pretty(e.getKey())).append(' ').append(lv > 0 && lv < ROMAN.length ? ROMAN[lv] : String.valueOf(lv));
            shown++;
        }
        return sb.toString();
    }

    private int rgbOf(int rule) { return rule == 0 ? Theme.accent() & 0xFFFFFF : PALETTE[rule]; }

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
            String custom = name[rule].get().trim();
            out.add(new Hit(ie, rule, custom.isEmpty() ? st.getName().getString() : custom,
                    showEnch.get() ? enchText(have) : "", rgbOf(rule)));
        }
        hits = out;
    }

    // ---------------------------------------------------------------- drawing

    private float fadeFor(double d) {
        return (float) MathHelper.clamp(1.0 - (d - range.get() * 0.55) / (range.get() * 0.45), 0.25, 1.0);
    }

    private static double hash(double seed, double salt) {
        double v = Math.sin(seed * 12.9898 + salt * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        List<Hit> list = hits;
        if (list.isEmpty() || !RenderUtil.valid || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            Vec3d s = RenderUtil.project(ie.getLerpedPos(RenderUtil.tickDelta).add(0, ie.getHeight() + 0.45, 0), sw, sh);
            if (s == null) continue;
            ItemStack st = ie.getStack();
            float a = fadeFor(mc.player.distanceTo(ie));
            int argb = 0xFF000000 | h.rgb();

            String text = h.text() + (st.getCount() > 1 ? " x" + st.getCount() : ""), sub = h.ench();
            int textW = Math.max(Draw.width(tr, text), sub.isEmpty() ? 0 : Draw.width(tr, sub));
            int hh = 20 + (sub.isEmpty() ? 0 : 9), w = Math.max(40, 20 + textW + 8);

            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            Draw.alpha = a;
            Draw.surface(ctx, -w / 2, -hh, w, hh, 6, 0xC00B0B0F, 0x30FFFFFF);
            Draw.round(ctx, -w / 2 + 6, -3, w - 12, 1, 0.5f, argb);
            int ix = -w / 2 + 3;
            if (a > 0.5f) ctx.drawItem(st, ix, -hh + (hh - 16) / 2);
            Draw.text(ctx, tr, text, ix + 19, -hh + 6, argb, false);
            if (!sub.isEmpty()) Draw.text(ctx, tr, sub, ix + 19, -hh + 16, Theme.dim(), false);
            m.pop();
        }
        Draw.alpha = 1f;
    }

    @Override
    public void onRender3d() {
        List<Hit> list = hits;
        if (list.isEmpty() || !RenderUtil.valid || mc.player == null) return;
        double time = System.currentTimeMillis() / 1000.0;

        RenderUtil.setup(1.5f);
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            Vec3d pos = ie.getLerpedPos(RenderUtil.tickDelta);
            int rgb = h.rgb();
            float a = fadeFor(mc.player.distanceTo(ie));
            double rr = 0.38 + 0.07 * Math.sin(time * 3 + ie.getId());
            RenderUtil.disc(q, pos.add(0, 0.03, 0), 0, rr * 1.7, 28, RenderUtil.argb(rgb, 0.3f * a), RenderUtil.argb(rgb, 0f));
            RenderUtil.disc(q, pos.add(0, 0.03, 0), rr * 0.85, rr, 28, RenderUtil.argb(rgb, 0.6f * a), RenderUtil.argb(rgb, 0.6f * a));
            if (beam.get()) {
                EspDraw.beam(q, pos, 3.0, 0.13, rgb, 0.32f * a);
                EspDraw.beam(q, pos, 3.0, 0.025, rgb, 0.9f * a);
            }
            for (int j = 0; j < 5; j++) {
                double ph = (time * 0.5 + hash(ie.getId(), j)) % 1.0;
                Vec3d p = pos.add((hash(ie.getId(), j + 10) - 0.5) * 0.7, 0.15 + ph * 1.4, (hash(ie.getId(), j + 20) - 0.5) * 0.7);
                float al = (float) Math.sin(Math.PI * ph) * a;
                RenderUtil.poly(q, p, 0.035, 4, 0, 0.35, RenderUtil.argb(0xFFFFFF, 0.9f * al), RenderUtil.argb(rgb, 0.5f * al));
            }
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            float a = fadeFor(mc.player.distanceTo(ie));
            double rr = 0.38 + 0.07 * Math.sin(time * 3 + ie.getId());
            RenderUtil.ring(l, ie.getLerpedPos(RenderUtil.tickDelta).add(0, 0.035, 0), rr, 28, RenderUtil.argb(h.rgb(), a));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
