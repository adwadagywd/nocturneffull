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
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Easiest item ESP: open ANY inventory, ender chest or market GUI and MIDDLE-CLICK (scroll wheel button) an item -
 * it is added to the list (middle-click it again to remove it). Items of that type with (at least) the same
 * enchantments lying on the ground are then marked with the item's name, a box, beam, tracer...
 */
public class CustomItemEsp extends Module {
    public static CustomItemEsp INSTANCE;

    private static final int[] PALETTE = {0, 0xFFD34E, 0x4DEBFF, 0xFF6DE1, 0x6DFF9A, 0xFF9A4D};

    private record Hit(ItemEntity ie, int rule, String text, String ench, int rgb) {}

    // --- what to look for
    private final ItemEntryListSetting entries = add(new ItemEntryListSetting("Tracked items"));
    private final BoolSetting matchEnch = add(new BoolSetting("Match enchants", true));
    private final NumSetting range = add(new NumSetting("Range", 64, 8, 128, 1));
    // --- look
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Per item", "Per item", "Theme", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom color", 0.55f));
    private final ModeSetting espStyle = add(new ModeSetting("ESP style", "Box", "None", "Box", "Corners", "Filled", "Both", "Gradient", "Nodes"));
    private final NumSetting espSize = add(new NumSetting("ESP size", 1.0, 0.5, 3.0, 0.05));
    private final NumSetting lineWidth = add(new NumSetting("Line width", 2.0, 0.5, 6.0, 0.5));
    private final NumSetting glow = add(new NumSetting("Glow", 1.0, 0.0, 3.0, 0.1));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
    private final BoolSetting beam = add(new BoolSetting("Beam", true));
    private final BoolSetting ring = add(new BoolSetting("Ground ring", true));
    private final BoolSetting sparkles = add(new BoolSetting("Sparkles", true));
    // --- label
    private final ModeSetting labelStyle = add(new ModeSetting("Label", "Card", "Card", "Pill", "Minimal", "None"));
    private final NumSetting textSize = add(new NumSetting("Text size", 1.0, 0.6, 2.5, 0.05));
    private final BoolSetting icon = add(new BoolSetting("Item icon", true));
    private final BoolSetting showEnch = add(new BoolSetting("Show enchants", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", false));

    private List<ItemEntryListSetting.Entry> parsed = new ArrayList<>();
    private int builtVersion = -1, tick = 100;
    private List<Hit> hits = new ArrayList<>();

    public CustomItemEsp() {
        super("CustomItemESP", "Middle-click items in any GUI to track them on the ground", Category.RENDER);
        INSTANCE = this;
        custom.showIf(() -> colorMode.is("Custom"));
        espSize.showIf(() -> !espStyle.is("None") || beam.get() || ring.get());
        glow.showIf(() -> !espStyle.is("None"));
        textSize.showIf(() -> !labelStyle.is("None"));
        icon.showIf(() -> !labelStyle.is("None"));
        showEnch.showIf(() -> !labelStyle.is("None"));
        distance.showIf(() -> !labelStyle.is("None"));
    }

    @Override protected void onEnable() { builtVersion = -1; tick = 100; }
    @Override protected void onDisable() { hits = new ArrayList<>(); }

    // ---------------------------------------------------------------- adding with the scroll-wheel click

    /** called by HandledScreenMixin on every middle click inside a container screen; true = handled */
    public static boolean onMiddleClick(Slot slot) {
        CustomItemEsp m = INSTANCE;
        if (m == null || !m.isEnabled() || slot == null || !slot.hasStack()) return false;
        m.toggleEntry(slot.getStack());
        return true;
    }

    private void toggleEntry(ItemStack st) {
        String item = Registries.ITEM.getId(st.getItem()).toString();
        String name = st.getName().getString().trim();
        if (name.length() > 32) name = name.substring(0, 32);
        String entry = ItemEntryListSetting.encode(item, enchants(st), name);
        boolean had = entries.has(entry);
        entries.toggle(entry);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("CustomItemESP: " + (had ? "removed " : "added ") + entries.label(entry)), true);
    }

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

    /** first tracked entry that fits: same item and (when "Match enchants" is on) at least the same enchants */
    private int ruleFor(ItemStack st, Map<String, Integer> have) {
        String id = Registries.ITEM.getId(st.getItem()).toString();
        for (int i = 0; i < parsed.size(); i++) {
            ItemEntryListSetting.Entry e = parsed.get(i);
            if (!e.item().equals(id)) continue;
            boolean ok = true;
            if (matchEnch.get())
                for (Map.Entry<String, Integer> en : e.ench().entrySet())
                    if (have.getOrDefault(en.getKey(), 0) < en.getValue()) { ok = false; break; }
            if (ok) return i;
        }
        return -1;
    }

    private static String enchText(Map<String, Integer> have) {
        if (have.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : have.entrySet()) {
            if (shown == 4) { sb.append(" +").append(have.size() - 4); break; }
            if (shown++ > 0) sb.append(", ");
            sb.append(ItemEntryListSetting.pretty(e.getKey())).append(' ').append(ItemEntryListSetting.roman(e.getValue()));
        }
        return sb.toString();
    }

    private int rgbOf(int rule) {
        switch (colorMode.get()) {
            case "Theme": return Theme.accent() & 0xFFFFFF;
            case "Custom": return custom.rgb() & 0xFFFFFF;
            default: {
                int c = PALETTE[rule % PALETTE.length];
                return c == 0 ? Theme.accent() & 0xFFFFFF : c;
            }
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        if (builtVersion != entries.version) {
            parsed = new ArrayList<>();
            for (String s : entries.get()) parsed.add(ItemEntryListSetting.parse(s));
            builtVersion = entries.version;
            tick = 100;
        }
        if (++tick < 4) return;
        tick = 0;
        List<Hit> out = new ArrayList<>();
        if (!parsed.isEmpty()) {
            double r = range.get();
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ItemEntity ie) || ie.isRemoved()) continue;
                if (mc.player.distanceTo(ie) > r) continue;
                ItemStack st = ie.getStack();
                Map<String, Integer> have = enchants(st);
                int rule = ruleFor(st, have);
                if (rule < 0) continue;
                ItemEntryListSetting.Entry en = parsed.get(rule);
                out.add(new Hit(ie, rule, en.name().isEmpty() ? st.getName().getString() : en.name(),
                        showEnch.get() ? enchText(have) : "", rgbOf(rule)));
            }
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

    private Box boxOf(ItemEntity ie) {
        return RenderUtil.lerpedBox(ie).expand(Math.max(-0.1, 0.05 + (espSize.get() - 1.0) * 0.25));
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        List<Hit> list = hits;
        if (list.isEmpty() || !RenderUtil.valid || mc.player == null || labelStyle.is("None")) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        String ls = labelStyle.get();
        boolean pill = ls.equals("Pill"), minimal = ls.equals("Minimal");
        float ts = textSize.getFloat();
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
            if (st.getCount() > 1) sb.append(" x").append(st.getCount());
            if (distance.get()) sb.append("  ").append((int) d).append("m");
            String text = sb.toString(), sub = h.ench();
            int textW = Math.max(Draw.width(tr, text), sub.isEmpty() ? 0 : Draw.width(tr, sub));
            int hh = (pill ? 18 : 20) + (sub.isEmpty() ? 0 : 9);
            int w = Math.max(40, (icon.get() ? 20 : 4) + textW + 8 + (pill ? 8 : 0));

            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            m.scale(ts, ts, 1f);
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
            if (icon.get() && a > 0.5f) ctx.drawItem(st, ix, -hh + (hh - 16) / 2);
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
        boolean boxes = !espStyle.is("None");
        double time = System.currentTimeMillis() / 1000.0, size = espSize.get();
        float g = glow.getFloat();

        RenderUtil.setup(lineWidth.getFloat());
        BufferBuilder q = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            Vec3d pos = ie.getLerpedPos(RenderUtil.tickDelta);
            int rgb = h.rgb();
            float a = fadeFor(mc.player.distanceTo(ie));
            if (boxes) EspDraw.quads(q, espStyle.get(), boxOf(ie), rgb, a, 0.25f, g, 0.08 * size);
            if (ring.get()) {
                double rr = (0.38 + 0.07 * Math.sin(time * 3 + ie.getId())) * size;
                RenderUtil.disc(q, pos.add(0, 0.03, 0), 0, rr * 1.7, 28, RenderUtil.argb(rgb, 0.3f * a), RenderUtil.argb(rgb, 0f));
                RenderUtil.disc(q, pos.add(0, 0.03, 0), rr * 0.85, rr, 28, RenderUtil.argb(rgb, 0.6f * a), RenderUtil.argb(rgb, 0.6f * a));
            }
            if (beam.get()) {
                EspDraw.beam(q, pos, 3.0, 0.13 * size, rgb, 0.32f * a);
                EspDraw.beam(q, pos, 3.0, 0.025, rgb, 0.9f * a);
            }
            if (sparkles.get()) {
                for (int j = 0; j < 5; j++) {
                    double ph = (time * 0.5 + hash(ie.getId(), j)) % 1.0;
                    Vec3d p = pos.add((hash(ie.getId(), j + 10) - 0.5) * 0.7 * size, 0.15 + ph * 1.4, (hash(ie.getId(), j + 20) - 0.5) * 0.7 * size);
                    float al = (float) Math.sin(Math.PI * ph) * a;
                    RenderUtil.poly(q, p, 0.035, 4, 0, 0.35, RenderUtil.argb(0xFFFFFF, 0.9f * al), RenderUtil.argb(rgb, 0.5f * al));
                }
            }
        }
        RenderUtil.finish(q);

        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        Vec3d from = tracers.get() ? RenderUtil.unproject(0f, 0f, 0.5f) : null;
        for (Hit h : list) {
            ItemEntity ie = h.ie();
            if (ie.isRemoved()) continue;
            float a = fadeFor(mc.player.distanceTo(ie));
            int rgb = h.rgb();
            Vec3d pos = ie.getLerpedPos(RenderUtil.tickDelta);
            if (boxes) EspDraw.lines(l, espStyle.get(), boxOf(ie), rgb, a, 0.3);
            if (ring.get()) {
                double rr = (0.38 + 0.07 * Math.sin(time * 3 + ie.getId())) * size;
                RenderUtil.ring(l, pos.add(0, 0.035, 0), rr, 28, RenderUtil.argb(rgb, a));
            }
            if (from != null) RenderUtil.line(l, from, pos.add(0, ie.getHeight() / 2, 0), RenderUtil.argb(rgb, 0.85f * a));
        }
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
