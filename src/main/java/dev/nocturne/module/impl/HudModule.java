package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HudModule extends Module {
    private final BoolSetting watermark = add(new BoolSetting("Watermark", true));
    private final BoolSetting list = add(new BoolSetting("Module list", true));
    private final BoolSetting coords = add(new BoolSetting("Coordinates", true));
    private final BoolSetting speed = add(new BoolSetting("Speed", false));
    private final BoolSetting ping = add(new BoolSetting("Ping", false));
    /** Auto = detect; switch to CPU/GPU if ESP or tracers look offset */
    public final ModeSetting matrixMode = add(new ModeSetting("Matrix", "Auto", "Auto", "CPU", "GPU"));

    public HudModule() { super("HUD", "Watermark, module list and info lines", Category.CLIENT); }

    public int color(int index) { return Theme.accent(index); }

    @Override
    public void onRenderHud(DrawContext ctx) {
        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        int y0 = 6;
        if (watermark.get()) {
            String a = "NOCTURNE", b = mc.getCurrentFps() + " fps";
            int aw = Draw.boldWidth(tr, a);
            int w = aw + Draw.width(tr, b) + 32;
            Draw.shadow(ctx, 6, 6, w, 18, 7);
            Draw.round(ctx, 6, 6, w, 18, 7, 0xD00B0B0F);
            Draw.round(ctx, 13, 12, 6, 6, 3, color(0));
            Draw.bold(ctx, tr, a, 24, 11, color(0), false);
            Draw.rect(ctx, 24 + aw + 6, 11, 1, 8, 0xFF2A2A34);
            Draw.text(ctx, tr, b, 24 + aw + 12, 11, 0xFF9A9AA6, false);
            y0 = 28;
        }

        List<String> info = new ArrayList<>();
        if (mc.player != null) {
            if (coords.get()) info.add(String.format(Locale.ROOT, "XYZ  %.1f  %.1f  %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            if (speed.get()) {
                Vec3d v = mc.player.getVelocity();
                info.add(String.format(Locale.ROOT, "Speed  %.1f b/s", Math.hypot(v.x, v.z) * 20.0));
            }
            if (ping.get() && mc.getNetworkHandler() != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) info.add("Ping  " + e.getLatency() + " ms");
            }
        }
        if (!info.isEmpty()) {
            int w = 0;
            for (String s : info) w = Math.max(w, Draw.width(tr, s));
            int h = info.size() * 11 + 6;
            Draw.round(ctx, 6, y0, w + 14, h, 6, 0xB00B0B0F);
            Draw.round(ctx, 6, y0 + 4, 2, h - 8, 1, color(0));
            for (int i = 0; i < info.size(); i++) Draw.text(ctx, tr, info.get(i), 13, y0 + 4 + i * 11, 0xFFD2D2DC, false);
        }

        if (list.get()) {
            List<Module> on = new ArrayList<>();
            for (Module m : ModuleManager.ALL) if (m.isEnabled() && m != this) on.add(m);
            on.sort(Comparator.comparingInt((Module m) -> -Draw.width(tr, m.name)));
            int sw = mc.getWindow().getScaledWidth();
            int y = 6, i = 0;
            for (Module m : on) {
                int w = Draw.width(tr, m.name) + 14;
                int x = sw - w - 6;
                Draw.round(ctx, x, y, w, 14, 5, 0xC00B0B0F);
                Draw.round(ctx, sw - 10, y + 3, 2, 8, 1, color(i));
                Draw.text(ctx, tr, m.name, x + 5, y + 3, color(i), false);
                y += 16;
                i++;
            }
        }
    }
}
