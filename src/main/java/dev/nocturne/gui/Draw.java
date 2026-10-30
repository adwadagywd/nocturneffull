package dev.nocturne.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
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
        if (Theme.glass()) { // wide, soft, slightly offset downwards - like a floating pane of glass
            for (int i = 9; i >= 1; i--)
                round(ctx, x - i * 2, y - i * 2 + 6, w + i * 4, h + i * 4, r * 1.5f + i * 2, 0x06000000, false);
            return;
        }
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

    // ================= GLASS (iOS "liquid glass" look) =================

    /** panel that is a normal dark panel in the classic theme and a frosted glass pane in the glass theme */
    public static void surface(DrawContext ctx, int x, int y, int w, int h, float r) {
        if (Theme.glass()) glassPanel(ctx, x, y, w, h, r);
        else panel(ctx, x, y, w, h, r, Theme.bg(), Theme.border());
    }

    public static void surface(DrawContext ctx, int x, int y, int w, int h, float r, int fill, int border) {
        if (Theme.glass()) glassPanel(ctx, x, y, w, h, r);
        else panel(ctx, x, y, w, h, r, fill, border);
    }

    private static int withA(int c, double f) {
        int a = (int) ((c >>> 24) * f);
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    private static int tri(int cA, int cB, int cC, double t) {
        return t < 0.5 ? lerp(cA, cB, (float) (t * 2)) : lerp(cB, cC, (float) ((t - 0.5) * 2));
    }

    /** one row of the rounded shape (anti-aliased corner pixels + solid middle) */
    private static void roundRow(DrawContext ctx, int X, int Wp, int ty, double rp, int i, int c) {
        int a = c >>> 24, rgb = c & 0xFFFFFF;
        double a0 = edge(rp, i), a1 = edge(rp, i + 1);
        double lo = Math.min(a0, a1), hi = Math.max(a0, a1);
        int c0 = (int) Math.floor(lo), c1 = Math.max((int) Math.ceil(hi), c0 + 1);
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
        }
        if (X + c1 < X + Wp - c1) ctx.fill(X + c1, ty, X + Wp - c1, ty + 1, c);
    }

    /** rounded rectangle with a vertical colour gradient; either pair of corners can be left square */
    public static void glassFill(DrawContext ctx, int x, int y, int w, int h, float r, int cTop, int cBot, boolean roundTop, boolean roundBottom) {
        if (w <= 0 || h <= 0) return;
        int t0 = col(cTop), b0 = col(cBot);
        if (((t0 | b0) >>> 24) == 0) return;
        double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int X = (int) Math.round(x * s), Y = (int) Math.round(y * s);
        int Wp = (int) Math.round(w * s), Hp = (int) Math.round(h * s);
        double rp = Math.min(r * Theme.roundness() * s, Math.min(Wp, Hp) / 2.0);
        int R = rp < 0.75 ? 0 : Math.min((int) Math.ceil(rp), Hp / 2);
        int rt = roundTop ? R : 0, rb = roundBottom ? R : 0;

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale((float) (1.0 / s), (float) (1.0 / s), 1f);
        int midTop = Y + rt, midBot = Y + Hp - rb;
        if (midBot > midTop)
            ctx.fillGradient(X, midTop, X + Wp, midBot,
                    lerp(t0, b0, (midTop - Y) / (float) Hp), lerp(t0, b0, (midBot - Y) / (float) Hp));
        for (int i = 0; i < R; i++) {
            if (roundTop) roundRow(ctx, X, Wp, Y + i, rp, i, lerp(t0, b0, (i + 0.5f) / Hp));
            if (roundBottom) roundRow(ctx, X, Wp, Y + Hp - 1 - i, rp, i, lerp(t0, b0, (Hp - 0.5f - i) / Hp));
        }
        m.pop();
    }

    /**
     * 1px light-catching rim. The colour travels diagonally: cA at the top-left, cB in the middle, cC at the
     * bottom-right - bright on the lit corner, faint in between, a second glint at the far corner.
     */
    public static void glassRim(DrawContext ctx, int x, int y, int w, int h, float r, int cA0, int cB0, int cC0) {
        if (w <= 2 || h <= 2) return;
        int cA = col(cA0), cB = col(cB0), cC = col(cC0);
        if (((cA | cB | cC) >>> 24) == 0) return;
        double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int X = (int) Math.round(x * s), Y = (int) Math.round(y * s);
        int Wp = (int) Math.round(w * s), Hp = (int) Math.round(h * s);
        double rp = Math.min(r * Theme.roundness() * s, Math.min(Wp, Hp) / 2.0);
        int R = rp < 0.75 ? 0 : Math.min((int) Math.ceil(rp), Hp / 2);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale((float) (1.0 / s), (float) (1.0 / s), 1f);

        int edgeStart = 0;
        for (int i = 0; i < R; i++) {
            double a0 = edge(rp, i), a1 = edge(rp, i + 1);
            double lo = Math.min(a0, a1), hi = Math.max(a0, a1);
            int c0 = (int) Math.floor(lo), c1 = Math.max((int) Math.ceil(hi), c0 + 1);
            if (i == 0) edgeStart = c1;
            double v = (i + 0.5) / Hp;
            for (int k = c0; k < c1; k++) {
                double cov = 0;
                for (int q = 0; q < 4; q++) {
                    double xe = a0 + (a1 - a0) * ((q + 0.5) / 4.0);
                    cov += Math.max(0, Math.min(1, k + 1 - xe));
                }
                cov /= 4.0;
                if (cov < 0.02) continue;
                double u = (k + 0.5) / Wp;
                ctx.fill(X + k, Y + i, X + k + 1, Y + i + 1, withA(tri(cA, cB, cC, (u + v) / 2), cov));
                ctx.fill(X + Wp - 1 - k, Y + i, X + Wp - k, Y + i + 1, withA(tri(cA, cB, cC, ((1 - u) + v) / 2), cov));
                ctx.fill(X + k, Y + Hp - 1 - i, X + k + 1, Y + Hp - i, withA(tri(cA, cB, cC, (u + (1 - v)) / 2), cov));
                ctx.fill(X + Wp - 1 - k, Y + Hp - 1 - i, X + Wp - k, Y + Hp - i, withA(tri(cA, cB, cC, ((1 - u) + (1 - v)) / 2), cov));
            }
        }
        // straight top / bottom edges, drawn in chunks so the colour can travel along them
        int n = 12, span = Wp - 2 * edgeStart;
        if (span > 0) {
            for (int j = 0; j < n; j++) {
                int xs = X + edgeStart + span * j / n, xe = X + edgeStart + span * (j + 1) / n;
                double u = ((xs + xe) / 2.0 - X) / Wp;
                ctx.fill(xs, Y, xe, Y + 1, tri(cA, cB, cC, u / 2));
                ctx.fill(xs, Y + Hp - 1, xe, Y + Hp, tri(cA, cB, cC, (u + 1) / 2));
            }
        }
        // straight left / right edges as vertical gradients
        int sy = Y + R, ey = Y + Hp - R;
        if (ey > sy) {
            double v0 = (sy - Y) / (double) Hp, v1 = (ey - Y) / (double) Hp;
            ctx.fillGradient(X, sy, X + 1, ey, tri(cA, cB, cC, v0 / 2), tri(cA, cB, cC, v1 / 2));
            ctx.fillGradient(X + Wp - 1, sy, X + Wp, ey, tri(cA, cB, cC, (1 + v0) / 2), tri(cA, cB, cC, (1 + v1) / 2));
        }
        m.pop();
    }

    /** the whole frosted-glass pane: dark tint, white frost, top sheen, accent glow at the bottom and a bright rim */
    public static void glassPanel(DrawContext ctx, int x, int y, int w, int h, float r0) {
        float rr = r0 * 1.5f;
        boolean hud = MinecraftClient.getInstance().currentScreen == null; // no backdrop blur in the HUD -> darker base
        float frost = Theme.glassFrost(), shine = Theme.glassShine();
        int tint = Theme.glassTint() & 0xFFFFFF;

        int sTop = hud ? 0x84 : (int) (0x30 + 0x30 * frost), sBot = hud ? 0x98 : sTop + 0x12;
        glassFill(ctx, x, y, w, h, rr, (sTop << 24) | tint, (sBot << 24) | tint, true, true);

        int fTop = Math.min(255, (int) (0x38 * (0.5f + frost))), fBot = Math.min(255, (int) (0x14 * (0.5f + frost)));
        glassFill(ctx, x, y, w, h, rr, (fTop << 24) | 0xFFFFFF, (fBot << 24) | 0xFFFFFF, true, true);

        int sheen = Math.min(h / 2, 54);
        if (sheen > 6) glassFill(ctx, x + 1, y + 1, w - 2, sheen, rr - 1, ((int) (0x30 * shine) << 24) | 0xFFFFFF, 0x00FFFFFF, true, false);

        int ac = Theme.accent() & 0xFFFFFF;
        if (h > 24) glassFill(ctx, x + 1, y + h - 1 - h / 3, w - 2, h / 3, rr - 1, ac, ((int) (0x1C * shine) << 24) | ac, false, true);

        glassRim(ctx, x, y, w, h, rr, ((int) (0xC8 * shine) << 24) | 0xFFFFFF, 0x1CFFFFFF, ((int) (0x70 * shine) << 24) | 0xFFFFFF);
        glassRim(ctx, x + 1, y + 1, w - 2, h - 2, rr - 1, ((int) (0x28 * shine) << 24) | 0xFFFFFF, 0x00FFFFFF, ((int) (0x14 * shine) << 24) | 0xFFFFFF);
    }

    /** soft round glow (triangle fan with a colour at the centre and another at the rim) */
    public static void radial(DrawContext ctx, double cx, double cy, double rx, double ry, int cCenter, int cEdge) {
        int c0 = col(cCenter), c1 = col(cEdge);
        if (((c0 | c1) >>> 24) == 0) return;
        ctx.draw(); // flush what was queued so the drawing order stays correct
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        b.vertex(mat, (float) cx, (float) cy, 0f).color((c0 >> 16) & 255, (c0 >> 8) & 255, c0 & 255, c0 >>> 24);
        int n = 44;
        for (int i = 0; i <= n; i++) {
            double a = i * (Math.PI * 2 / n);
            b.vertex(mat, (float) (cx + Math.cos(a) * rx), (float) (cy + Math.sin(a) * ry), 0f)
                    .color((c1 >> 16) & 255, (c1 >> 8) & 255, c1 & 255, c1 >>> 24);
        }
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** rotates the hue of a colour (dh = 0..1 of the colour wheel) */
    public static int hueShift(int argb, float dh) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d < 1.0E-5f) h = 0f;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0f) h += 1f;
        float sat = max < 1.0E-5f ? 0f : d / max;
        return 0xFF000000 | MathHelper.hsvToRgb((h + dh) % 1f, Math.max(0.45f, sat), 1f);
    }
}
