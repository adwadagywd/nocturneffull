package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Slide-in toasts: module toggles, saved configs, added friends... */
public class Notifications extends Module {
    public static Notifications INSTANCE;
    /** true while the game starts or a config is applied, so those toggles stay silent */
    public static boolean suppress = true;

    private record Toast(String title, String msg, boolean good, long born) {}
    private static final List<Toast> TOASTS = new ArrayList<>();

    private final BoolSetting onToggle = add(new BoolSetting("On module toggle", true));
    private final NumSetting duration = add(new NumSetting("Duration", 3.0, 1.0, 8.0, 0.5));

    public Notifications() {
        super("Notifications", "Slide-in toasts when modules change", Category.CLIENT);
        INSTANCE = this;
    }

    public static void moduleToggled(Module m, boolean on) {
        Notifications n = INSTANCE;
        if (suppress || n == null || !n.isEnabled() || !n.onToggle.get() || m == n || m instanceof ThemeModule) return;
        push(m.name, on ? "Enabled" : "Disabled", on);
    }

    public static void push(String title, String msg, boolean good) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        TOASTS.add(new Toast(title, msg, good, System.currentTimeMillis()));
        while (TOASTS.size() > 6) TOASTS.remove(0);
    }

    private static float ease(float t) { float u = 1f - Math.max(0f, Math.min(1f, t)); return 1f - u * u * u; }

    @Override
    public void onRenderHud(DrawContext ctx) {
        long now = System.currentTimeMillis();
        float dur = duration.getFloat();
        TOASTS.removeIf(t -> (now - t.born()) / 1000f > dur + 0.6f);
        if (TOASTS.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int row = 0;
        for (int i = TOASTS.size() - 1; i >= 0; i--, row++) {
            Toast t = TOASTS.get(i);
            float age = (now - t.born()) / 1000f;
            float in = ease(age / 0.3f);
            float out = age > dur ? ease((age - dur) / 0.5f) : 0f;
            int w = Math.max(Draw.boldWidth(tr, t.title()), Draw.width(tr, t.msg())) + 40;
            int h = 30;
            int x = sw - w - 10 + Math.round((1f - in + out) * (w + 20));
            int y = sh - 44 - row * (h + 6);
            int col = t.good() ? Theme.accent() : 0xFFFF5C6C;

            Draw.alpha = 1f - out * 0.6f;
            Draw.shadow(ctx, x, y, w, h, 9);
            Draw.panel(ctx, x, y, w, h, 9, Theme.bg(), Theme.border());
            Draw.round(ctx, x + 6, y + 7, 3, h - 14, 1.5f, col);
            Draw.round(ctx, x + 16, y + 8, 6, 6, 3, col);
            Draw.bold(ctx, tr, t.title(), x + 28, y + 6, Theme.TEXT, false);
            Draw.text(ctx, tr, t.msg(), x + 28, y + 17, Theme.DIM, false);
        }
        Draw.alpha = 1f;
    }
}
