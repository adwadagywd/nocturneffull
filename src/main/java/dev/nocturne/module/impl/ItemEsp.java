package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dropped items get a nametag-style label: icon, name and count. */
public class ItemEsp extends Module {
    private final NumSetting range = add(new NumSetting("Range", 48, 8, 128, 1));
    private final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.6, 2.0, 0.05));
    private final ModeSetting minRarity = add(new ModeSetting("Min rarity", "All", "All", "Uncommon", "Rare", "Epic"));
    private final BoolSetting icon = add(new BoolSetting("Icon", true));
    private final BoolSetting name = add(new BoolSetting("Name", true));
    private final BoolSetting count = add(new BoolSetting("Count", true));
    private final BoolSetting background = add(new BoolSetting("Background", true));
    private final BoolSetting fade = add(new BoolSetting("Fade with distance", true));
    private final BoolSetting box = add(new BoolSetting("Box", false));

    public ItemEsp() { super("ItemESP", "Labels dropped items with icon and name", Category.RENDER); }

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

    private int minRank() {
        return switch (minRarity.get()) { case "Uncommon" -> 1; case "Rare" -> 2; case "Epic" -> 3; default -> 0; };
    }

    private List<ItemEntity> items() {
        List<ItemEntity> list = new ArrayList<>();
        if (mc.world == null || mc.player == null) return list;
        for (Entity e : mc.world.getEntities())
            if (e instanceof ItemEntity ie && !ie.isRemoved() && mc.player.distanceTo(e) <= range.get() && rank(ie.getStack()) >= minRank())
                list.add(ie);
        list.sort(Comparator.comparingDouble((ItemEntity e) -> -mc.player.squaredDistanceTo(e)));
        if (list.size() > 80) list = new ArrayList<>(list.subList(list.size() - 80, list.size()));
        return list;
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        if (!RenderUtil.valid) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        MatrixStack m = ctx.getMatrices();
        for (ItemEntity ie : items()) {
            Vec3d s = RenderUtil.project(ie.getLerpedPos(RenderUtil.tickDelta).add(0, ie.getHeight() + 0.45, 0), sw, sh);
            if (s == null) continue;
            ItemStack st = ie.getStack();
            double d = mc.player.distanceTo(ie);
            float a = fade.get() ? (float) MathHelper.clamp(1.0 - (d - range.get() * 0.55) / (range.get() * 0.45), 0.25, 1.0) : 1f;

            StringBuilder sb = new StringBuilder();
            if (name.get()) sb.append(st.getName().getString());
            if (count.get() && st.getCount() > 1) sb.append(sb.length() > 0 ? " x" : "x").append(st.getCount());
            String text = sb.toString();
            int w = (icon.get() ? 20 : 4) + (text.isEmpty() ? 0 : Draw.width(tr, text) + 8), h = 20;
            if (w < 22) w = 22;

            m.push();
            m.translate((float) s.x, (float) s.y, 0);
            m.scale(scale.getFloat(), scale.getFloat(), 1f);
            Draw.alpha = a;
            if (background.get()) {
                Draw.panel(ctx, -w / 2, -h, w, h, 6, 0xC00B0B0F, 0x30FFFFFF);
                Draw.round(ctx, -w / 2 + 6, -3, w - 12, 1, 0.5f, rarityColor(rank(st)));
            }
            if (icon.get() && a > 0.5f) ctx.drawItem(st, -w / 2 + 3, -h + 2);
            if (!text.isEmpty()) Draw.text(ctx, tr, text, -w / 2 + (icon.get() ? 22 : 6), -h + 6, rarityColor(rank(st)), false);
            m.pop();
        }
        Draw.alpha = 1f;
    }

    @Override
    public void onRender3d() {
        if (!box.get() || !RenderUtil.valid) return;
        List<ItemEntity> list = items();
        if (list.isEmpty()) return;
        RenderUtil.setup(1.5f);
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        for (ItemEntity ie : list)
            RenderUtil.boxOutline(b, RenderUtil.lerpedBox(ie).expand(0.05), rarityColor(rank(ie.getStack())));
        RenderUtil.finish(b);
        RenderUtil.restore();
    }
}
