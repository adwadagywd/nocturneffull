package dev.nocturne.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 2D toolkit: global alpha, anti-aliased rounded rects (drawn in real screen pixels), gradients, shadows. */
public final class Draw {
    public static float alpha = 1f;

    private Draw() {}

    public static int col(int argb) {
        int a = (int) (((argb >>> 24) & 255) * alpha);
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    /** multiply the alpha channel of an ARGB colour */
    public static int scaleA(int argb, float f) {
        int a = (int) (((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            out |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return out;
    }

    public static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int c = col(color);
        if ((c >>> 24) == 0) return;
        ctx.fill(x, y, x + w, y + h, c);
    }

    /** x position (in physical px, measured from the left edge) of a corner arc at height y */
    private static double edge(double rp, double y) {
        if (y >= rp) return 0;
        double dy = rp - y;
        return rp - Math.sqrt(Math.max(0, rp * rp - dy * dy));
    }

    public static void round(DrawContext ctx, int x, int y, int w, int h, float r, int color) {
        round(ctx, x, y, w, h, r, color, true);
    }

    /**
     * Rounded rectangle. The GUI scale makes one GUI pixel 2-4 real pixels, which is what makes corners look
     * blocky - so we switch to real-pixel coordinates and give every edge pixel partial alpha (anti-aliasing).
     */
    public static void round(DrawContext ctx, int x, int y, int w, int h, float r, int color, boolean aa) {
        if (w <= 0 || h <= 0) return;
        int c = col(color);
        int a = c >>> 24;
        if (a == 0) return;
        double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int X = (int) Math.round(x * s), Y = (int) Math.round(y * s);
        int Wp = (int) Math.round(w * s), Hp = (int) Math.round(h * s);
        double rp = Math.min(r * Theme.roundness() * s, Math.min(Wp, Hp) / 2.0);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale((float) (1.0 / s), (float) (1.0 / s), 1f);
        if (rp < 0.75) {
            ctx.fill(X, Y, X + Wp, Y + Hp, c);
        } else {
            int R = Math.min((int) Math.ceil(rp), Hp / 2);
            int rgb = c & 0xFFFFFF;
            if (Hp - 2 * R > 0) ctx.fill(X, Y + R, X + Wp, Y + Hp - R, c);
            for (int i = 0; i < R; i++) {
                double a0 = edge(rp, i), a1 = edge(rp, i + 1);
                double lo = Math.min(a0, a1), hi = Math.max(a0, a1);
                int c0 = (int) Math.floor(lo);
                int c1 = Math.max((int) Math.ceil(hi), c0 + 1);
                int ty = Y + i, by = Y + Hp - 1 - i;
                int solid = c0 + 1;
                if (aa) {
                    solid = c1;
                    for (int k = c0; k < c1; k++) {
                        double cov = 0;
                        for (int q = 0; q < 4; q++) {
                            double xe = a0 + (a1 - a0) * ((q + 0.5) / 4.0);
                            cov += Math.max(0, Math.min(1, k + 1 - xe));
                        }
                        cov /= 4.0;
                        if (cov < 0.02) continue;
                        int cc = (((int) (a * cov)) << 24) | rgb;
                        ctx.fill(X + k, ty, X + k + 1, ty + 1, cc);
                        ctx.fill(X + Wp - 1 - k, ty, X + Wp - k, ty + 1, cc);
                        ctx.fill(X + k, by, X + k + 1, by + 1, cc);
                        ctx.fill(X + Wp - 1 - k, by, X + Wp - k, by + 1, cc);
                    }
                } else {
                    solid = (int) Math.round((lo + hi) / 2.0);
                }
                if (X + solid < X + Wp - solid) {
                    ctx.fill(X + solid, ty, X + Wp - solid, ty + 1, c);
                    ctx.fill(X + solid, by, X + Wp - solid, by + 1, c);
                }
            }
        }
        m.pop();
    }

    /** circle outline (or an arc: frac = 0..1 of the full turn, starting at the top) drawn in real screen pixels */
    public static void ring(DrawContext ctx, double cx, double cy, double radius, double thick, int color, float frac) {
        int c = col(color);
        if ((c >>> 24) == 0 || frac <= 0f || radius <= 0) return;
        double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
        double rp = radius * s;
        int t = Math.max(1, (int) Math.round(thick * s));
        int n = (int) Math.max(16, Math.min(720, Math.ceil(2 * Math.PI * rp / Math.max(1.0, t * 0.85))));
        int steps = (int) Math.ceil(n * Math.min(1f, frac));
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale((float) (1.0 / s), (float) (1.0 / s), 1f);
        for (int i = 0; i < steps; i++) {
            double a = -Math.PI / 2 + i * (Math.PI * 2 / n);
            int px = (int) Math.round(cx * s + Math.cos(a) * rp - t / 2.0);
            int py = (int) Math.round(cy * s + Math.sin(a) * rp - t / 2.0);
            ctx.fill(px, py, px + t, py + t, c);
        }
        m.pop();
    }

    /** 1px (GUI px) rounded outline made of an outer + inner rounded rect */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, float r, int fill, int border) {
        round(ctx, x, y, w, h, r, border);
        round(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0f, r - 1f), fill);
    }

    public static void hgrad(DrawContext ctx, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i++) rect(ctx, x + i, y, 1, h, lerp(c1, c2, i / (float) Math.max(1, w - 1)));
    }

    public static void shadow(DrawContext ctx, int x, int y, int w, int h, float r) {
        if (!Theme.shadows()) return;
        for (int i = 6; i >= 1; i--)
            round(ctx, x - i * 2, y - i * 2, w + i * 4, h + i * 4, r + i * 2, 0x07000000, false);
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
        int c = col(color);
        if ((c >>> 24) < 8) return; // vanilla draws near-transparent text fully opaque, so skip it
        ctx.drawText(tr, Fonts.text(tr, s), x, y + Fonts.dy(tr), c, shadow);
    }

    public static void bold(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
        int c = col(color);
        if ((c >>> 24) < 8) return;
        ctx.drawText(tr, Fonts.bold(tr, s), x, y + Fonts.dy(tr), c, shadow);
    }

    public static int width(TextRenderer tr, String s) {
        return tr.getWidth(Fonts.text(tr, s));
    }

    public static int boldWidth(TextRenderer tr, String s) {
        return tr.getWidth(Fonts.bold(tr, s));
    }

    /** cuts the end of the text so it fits, adding ".." */
    public static String trim(TextRenderer tr, String s, int w) {
        if (width(tr, s) <= w) return s;
        String t = s;
        while (!t.isEmpty() && width(tr, t + "..") > w) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    /** keeps the END of the text (used while typing in a text box) */
    public static String trimTail(TextRenderer tr, String s, int w) {
        if (width(tr, s) <= w) return s;
        String t = s;
        while (!t.isEmpty() && width(tr, t) > w) t = t.substring(1);
        return t;
    }
}
