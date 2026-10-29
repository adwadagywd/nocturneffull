package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Friends;
import dev.nocturne.util.RenderUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class Nametags extends Module {
    public static Nametags INSTANCE;

    private final NumSetting range = add(new NumSetting("Range", 128, 8, 256, 1));
    private final NumSetting textSize = add(new NumSetting("Text size", 0.7, 0.3, 2.0, 0.05));
    private final ModeSetting scaling = add(new ModeSetting("Scaling", "Fixed", "Fixed", "Distance"));
    private final BoolSetting health = add(new BoolSetting("Health", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", true));
    private final BoolSetting hands = add(new BoolSetting("Held items", true));
    private final BoolSetting durability = add(new BoolSetting("Durability", true));
    private final BoolSetting enchants = add(new BoolSetting("Enchants", true));
    private final ModeSetting enchantNames = add(new ModeSetting("Enchant style", "Short", "Short", "Tiny", "Full"));
    private final NumSetting maxEnchants = add(new NumSetting("Max enchants", 5, 1, 8, 1));
    private final NumSetting enchantScale = add(new NumSetting("Enchant size", 0.55, 0.3, 1.0, 0.05));
    private final BoolSetting background = add(new BoolSetting("Background", true));
    private final BoolSetting shadow = add(new BoolSetting("Text shadow", false));
    private final BoolSetting hideVanilla = add(new BoolSetting("Hide vanilla", true));
    private final TargetSettings targets = new TargetSettings(this, true, false, false, false);

    private static final Map<String, String> SHORT = new HashMap<>();
    static {
        String[] p = {
            "protection", "Prot", "fire_protection", "FProt", "blast_protection", "BProt", "projectile_protection", "PProt",
            "feather_falling", "Fall", "respiration", "Resp", "aqua_affinity", "Aqua", "thorns", "Thorns",
            "depth_strider", "Depth", "frost_walker", "Frost", "soul_speed", "Soul", "swift_sneak", "Sneak",
            "unbreaking", "Unb", "mending", "Mend", "sharpness", "Sharp", "smite", "Smite", "bane_of_arthropods", "Bane",
            "knockback", "KB", "fire_aspect", "Fire", "looting", "Loot", "sweeping_edge", "Sweep", "efficiency", "Eff",
            "fortune", "Fort", "silk_touch", "Silk", "power", "Power", "punch", "Punch", "flame", "Flame", "infinity", "Inf",
            "luck_of_the_sea", "Luck", "lure", "Lure", "loyalty", "Loyal", "riptide", "Rip", "channeling", "Chan",
            "impaling", "Impale", "multishot", "Multi", "quick_charge", "Quick", "piercing", "Pierce", "density", "Dens",
            "breach", "Breach", "wind_burst", "Wind", "lunge", "Lunge", "binding_curse", "Bind", "vanishing_curse", "Van"
        };
        for (int i = 0; i < p.length; i += 2) SHORT.put(p[i], p[i + 1]);
    }

    private record Line(String text, int color) {}

    public Nametags() {
        super("Nametags", "Custom nametags with health, armor and enchants", Category.RENDER);
        INSTANCE = this;
        enchantNames.showIf(enchants::get);
        maxEnchants.showIf(enchants::get);
        enchantScale.showIf(enchants::get);
    }

    public static boolean hidesVanilla(Entity e) {
        Nametags n = INSTANCE;
        return n != null && n.isEnabled() && n.hideVanilla.get() && n.targets.test(e);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (!RenderUtil.valid) return;
        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities())
            if (targets.test(e) && mc.player.distanceTo(e) <= range.get()) list.add(e);
        list.sort(Comparator.comparingDouble(e -> -mc.player.squaredDistanceTo(e)));

        for (Entity e : list) {
            Vec3d pos = e.getLerpedPos(RenderUtil.tickDelta).add(0, e.getHeight() + 0.3, 0);
            Vec3d s = RenderUtil.project(pos, sw, sh);
            if (s == null) continue;
            draw(ctx, tr, e, (float) s.x, (float) s.y, mc.player.distanceTo(e));
        }
    }

    private void draw(DrawContext ctx, TextRenderer tr, Entity e, float sx, float sy, float dist) {
        float sc = textSize.getFloat();
        if (scaling.is("Distance")) sc *= MathHelper.clamp(10f / Math.max(dist, 1f), 0.6f, 1.6f);

        String name = e instanceof ItemEntity ie
                ? ie.getStack().getName().getString() + " x" + ie.getStack().getCount()
                : e.getName().getString();
        String hp = null;
        int hpColor = 0xFFFFFFFF;
        if (health.get() && e instanceof LivingEntity le) {
            float h = le.getHealth() + le.getAbsorptionAmount();
            hp = String.format(Locale.ROOT, "%.1f", h);
            hpColor = 0xFF000000 | MathHelper.hsvToRgb(MathHelper.clamp(le.getHealth() / le.getMaxHealth(), 0f, 1f) * 0.33f, 0.8f, 1f);
        }
        String ds = distance.get() ? String.format(Locale.ROOT, "%.0fm", dist) : null;

        int w = Draw.width(tr, name) + (hp != null ? 4 + Draw.width(tr, hp) : 0) + (ds != null ? 4 + Draw.width(tr, ds) : 0);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(sx, sy, 0);
        m.scale(sc, sc, 1f);

        int x = -w / 2, y = -10;
        if (background.get()) Draw.round(ctx, x - 3, y - 2, w + 6, 11, 3, 0xA00B0B0F);
        Draw.text(ctx, tr, name, x, y, Friends.isFriend(e) ? (0xFF000000 | targets.friendRgb()) : 0xFFFFFFFF, shadow.get());
        x += Draw.width(tr, name) + 4;
        if (hp != null) { Draw.text(ctx, tr, hp, x, y, hpColor, shadow.get()); x += Draw.width(tr, hp) + 4; }
        if (ds != null) Draw.text(ctx, tr, ds, x, y, 0xFF9A9AA6, shadow.get());

        if (e instanceof LivingEntity le) drawGear(ctx, tr, le, y);
        m.pop();
    }

    private void drawGear(DrawContext ctx, TextRenderer tr, LivingEntity le, int textY) {
        List<ItemStack> stacks = new ArrayList<>();
        if (hands.get()) stacks.add(le.getMainHandStack());
        if (armor.get()) {
            stacks.add(le.getEquippedStack(EquipmentSlot.HEAD));
            stacks.add(le.getEquippedStack(EquipmentSlot.CHEST));
            stacks.add(le.getEquippedStack(EquipmentSlot.LEGS));
            stacks.add(le.getEquippedStack(EquipmentSlot.FEET));
        }
        if (hands.get()) stacks.add(le.getOffHandStack());
        stacks.removeIf(ItemStack::isEmpty);
        if (stacks.isEmpty()) return;

        MatrixStack m = ctx.getMatrices();
        int startX = -(stacks.size() * 18) / 2;
        int itemY = textY - 19;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack st = stacks.get(i);
            int ix = startX + i * 18;
            ctx.drawItem(st, ix, itemY);
            if (durability.get()) ctx.drawStackOverlay(tr, st, ix, itemY);

            if (enchants.get()) {
                List<Line> lines = enchantLines(st);
                if (lines.isEmpty()) continue;
                float es = enchantScale.getFloat();
                m.push();
                m.translate(ix + 8, itemY - 1, 0);
                m.scale(es, es, 1f);
                for (int k = 0; k < lines.size(); k++) {
                    Line ln = lines.get(k);
                    Draw.text(ctx, tr, ln.text(), -Draw.width(tr, ln.text()) / 2, -(k + 1) * 9, ln.color(), shadow.get());
                }
                m.pop();
            }
        }
    }

    private static final List<String> PRIORITY = List.of("protection", "sharpness", "power", "unbreaking", "mending",
            "efficiency", "fortune", "looting", "feather_falling", "thorns");

    private static String abbreviate(String id) {
        String s = SHORT.get(id);
        if (s != null) return s;
        String w = id.contains("_") ? id.substring(0, id.indexOf('_')) : id;
        return w.isEmpty() ? "?" : Character.toUpperCase(w.charAt(0)) + w.substring(1, Math.min(4, w.length()));
    }

    private List<Line> enchantLines(ItemStack st) {
        record E(String id, int lvl, int max, String full) {}
        List<E> all = new ArrayList<>();
        ItemEnchantmentsComponent comp = EnchantmentHelper.getEnchantments(st);
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> en : comp.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> re = en.getKey();
            String id = re.getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            all.add(new E(id, en.getIntValue(), re.value().getMaxLevel(), Enchantment.getName(re, en.getIntValue()).getString()));
        }
        // most important enchants first, then alphabetical
        all.sort(Comparator.comparingInt((E e) -> { int i = PRIORITY.indexOf(e.id()); return i < 0 ? 100 : i; }).thenComparing(E::id));

        List<Line> out = new ArrayList<>();
        int limit = maxEnchants.getInt();
        for (E e : all) {
            if (out.size() >= limit) break;
            String text;
            if (enchantNames.is("Full")) {
                text = e.full();
            } else {
                String base = abbreviate(e.id());
                boolean tiny = enchantNames.is("Tiny");
                if (tiny) base = base.length() > 1 && Character.isUpperCase(base.charAt(1)) ? base.substring(0, 2) : base.substring(0, 1);
                text = e.max() > 1 ? base + (tiny ? "" : " ") + e.lvl() : base;
            }
            int color = e.id().endsWith("_curse") ? 0xFFFF5555 : (e.lvl() >= e.max() ? 0xFFFFAA00 : 0xFFFFFFFF);
            out.add(new Line(text, color));
        }
        if (all.size() > out.size()) out.add(new Line("+" + (all.size() - out.size()), 0xFF9A9AA6));
        return out;
    }
}
