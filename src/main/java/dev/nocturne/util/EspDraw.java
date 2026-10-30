package dev.nocturne.util;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Shared 3D box styles for ESP modules: glow shells, gradients, glowing corner nodes, light beams, health bars. */
public final class EspDraw {
    private EspDraw() {}

    /** every box style an ESP can use */
    public static final String[] STYLES = {"Box", "Corners", "Filled", "Both", "Gradient", "Nodes", "Beam"};

    /** glowing / filled parts (call between begin(QUADS) and finish) */
    public static void quads(BufferBuilder q, String st, Box bx, int rgb, float a, float fill, float glow, double glowSize) {
        if (glow > 0.01f && !st.equals("Beam")) shell(q, bx, rgb, 0.11f * glow * a, glowSize, 3);
        switch (st) {
            case "Filled", "Both" -> RenderUtil.boxFill(q, bx, RenderUtil.argb(rgb, fill * a));
            case "Gradient" -> fillGradient(q, bx, RenderUtil.argb(rgb, Math.min(1f, fill * 2.2f) * a), RenderUtil.argb(rgb, 0.02f * a));
            case "Nodes" -> {
                RenderUtil.boxFill(q, bx, RenderUtil.argb(rgb, fill * 0.4f * a));
                nodes(q, bx, rgb, a, 0.07);
            }
            case "Beam" -> {
                Vec3d base = new Vec3d((bx.minX + bx.maxX) / 2, bx.minY, (bx.minZ + bx.maxZ) / 2);
                double rad = Math.max(0.45, Math.max(bx.maxX - bx.minX, bx.maxZ - bx.minZ) * 0.7), h = (bx.maxY - bx.minY) + 1.6;
                RenderUtil.disc(q, base.add(0, 0.03, 0), 0, rad * 1.2, 32, RenderUtil.argb(rgb, 0.4f * a), RenderUtil.argb(rgb, 0f));
                beam(q, base, h, 0.32, rgb, 0.3f * (0.6f + 0.4f * glow) * a);
                beam(q, base, h, 0.05, rgb, 0.9f * a);
            }
            default -> {}
        }
    }

    /** outlines (call between begin(DEBUG_LINES) and finish) */
    public static void lines(BufferBuilder l, String st, Box bx, int rgb, float a, double cornerLen) {
        switch (st) {
            case "Box", "Both" -> RenderUtil.boxOutline(l, bx, RenderUtil.argb(rgb, a));
            case "Gradient" -> RenderUtil.boxOutline(l, bx, RenderUtil.argb(rgb, 0.7f * a));
            case "Nodes" -> RenderUtil.boxOutline(l, bx, RenderUtil.argb(rgb, 0.45f * a));
            case "Corners" -> corners(l, bx, cornerLen, RenderUtil.argb(rgb, a));
            case "Beam" -> {
                Vec3d base = new Vec3d((bx.minX + bx.maxX) / 2, bx.minY + 0.03, (bx.minZ + bx.maxZ) / 2);
                double rad = Math.max(0.45, Math.max(bx.maxX - bx.minX, bx.maxZ - bx.minZ) * 0.7);
                RenderUtil.ring(l, base, rad, 48, RenderUtil.argb(rgb, a));
                RenderUtil.ring(l, base, rad * 1.3, 48, RenderUtil.argb(rgb, 0.35f * a));
            }
            default -> {}
        }
    }

    /** soft halo: slightly larger boxes with fading alpha */
    public static void shell(BufferBuilder b, Box bx, int rgb, float a, double size, int layers) {
        for (int k = 1; k <= layers; k++) {
            double e = size * k / layers;
            float al = a * (1f - (k - 1f) / layers);
            RenderUtil.boxFill(b, bx.expand(e), RenderUtil.argb(rgb, al));
        }
    }

    /** fill that is strong at the feet and fades out towards the top */
    public static void fillGradient(BufferBuilder b, Box bx, int cBottom, int cTop) {
        double x1 = bx.minX, y1 = bx.minY, z1 = bx.minZ, x2 = bx.maxX, y2 = bx.maxY, z2 = bx.maxZ;
        side(b, x1, z1, x2, z1, y1, y2, cBottom, cTop);
        side(b, x2, z1, x2, z2, y1, y2, cBottom, cTop);
        side(b, x2, z2, x1, z2, y1, y2, cBottom, cTop);
        side(b, x1, z2, x1, z1, y1, y2, cBottom, cTop);
        cap(b, x1, z1, x2, z2, y1, cBottom);
        cap(b, x1, z1, x2, z2, y2, cTop);
    }

    private static void side(BufferBuilder b, double ax, double az, double bx, double bz, double y1, double y2, int cb, int ct) {
        RenderUtil.v(b, ax, y1, az, cb);
        RenderUtil.v(b, bx, y1, bz, cb);
        RenderUtil.v(b, bx, y2, bz, ct);
        RenderUtil.v(b, ax, y2, az, ct);
    }

    private static void cap(BufferBuilder b, double x1, double z1, double x2, double z2, double y, int c) {
        RenderUtil.v(b, x1, y, z1, c);
        RenderUtil.v(b, x2, y, z1, c);
        RenderUtil.v(b, x2, y, z2, c);
        RenderUtil.v(b, x1, y, z2, c);
    }

    private static void ln(BufferBuilder l, double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        RenderUtil.v(l, x1, y1, z1, c);
        RenderUtil.v(l, x2, y2, z2, c);
    }

    /** only the corners of the box; frac = length of each arm relative to the box size */
    public static void corners(BufferBuilder l, Box bx, double frac, int c) {
        double dx = (bx.maxX - bx.minX) * frac, dy = (bx.maxY - bx.minY) * frac, dz = (bx.maxZ - bx.minZ) * frac;
        double[] xs = {bx.minX, bx.maxX}, ys = {bx.minY, bx.maxY}, zs = {bx.minZ, bx.maxZ};
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++) {
            double px = xs[i], py = ys[j], pz = zs[k];
            double sx = i == 0 ? 1 : -1, sy = j == 0 ? 1 : -1, sz = k == 0 ? 1 : -1;
            ln(l, px, py, pz, px + sx * dx, py, pz, c);
            ln(l, px, py, pz, px, py + sy * dy, pz, c);
            ln(l, px, py, pz, px, py, pz + sz * dz, c);
        }
    }

    /** glowing dots on the 8 corners */
    public static void nodes(BufferBuilder q, Box bx, int rgb, float a, double r) {
        double[] xs = {bx.minX, bx.maxX}, ys = {bx.minY, bx.maxY}, zs = {bx.minZ, bx.maxZ};
        for (double x : xs) for (double y : ys) for (double z : zs) {
            Vec3d p = new Vec3d(x, y, z);
            RenderUtil.poly(q, p, r * 2.6, 12, 0, 1.0, RenderUtil.argb(rgb, 0.5f * a), RenderUtil.argb(rgb, 0f));
            RenderUtil.poly(q, p, r, 10, 0, 1.0, RenderUtil.argb(0xFFFFFF, a), RenderUtil.argb(rgb, a));
        }
    }

    /** vertical light beam made of two crossed planes that fade out upwards */
    public static void beam(BufferBuilder q, Vec3d p, double h, double w, int rgb, float a) {
        int cb = RenderUtil.argb(rgb, a), ct = RenderUtil.argb(rgb, 0f);
        RenderUtil.v(q, p.x - w, p.y, p.z, cb);
        RenderUtil.v(q, p.x + w, p.y, p.z, cb);
        RenderUtil.v(q, p.x + w, p.y + h, p.z, ct);
        RenderUtil.v(q, p.x - w, p.y + h, p.z, ct);
        RenderUtil.v(q, p.x, p.y, p.z - w, cb);
        RenderUtil.v(q, p.x, p.y, p.z + w, cb);
        RenderUtil.v(q, p.x, p.y + h, p.z + w, ct);
        RenderUtil.v(q, p.x, p.y + h, p.z - w, ct);
    }

    /** vertical health bar beside the box, always facing the camera */
    public static void healthBar(BufferBuilder q, Box bx, float frac, float a) {
        frac = MathHelper.clamp(frac, 0f, 1f);
        double cx = (bx.minX + bx.maxX) / 2, cz = (bx.minZ + bx.maxZ) / 2;
        double rx = RenderUtil.right.x, rz = RenderUtil.right.z, len = Math.sqrt(rx * rx + rz * rz);
        if (len < 1.0E-4) { rx = 1; rz = 0; len = 1; }
        rx /= len; rz /= len;
        double off = Math.max(bx.maxX - bx.minX, bx.maxZ - bx.minZ) / 2 + 0.14;
        double px = cx - rx * off, pz = cz - rz * off, y1 = bx.minY, y2 = bx.maxY, yf = y1 + (y2 - y1) * frac;
        int bg = RenderUtil.argb(0x101018, 0.6f * a);
        bar(q, px, pz, rx, rz, 0.055, y1 - 0.02, y2 + 0.02, bg, bg);
        int col = MathHelper.hsvToRgb(frac * 0.33f, 0.85f, 1f);
        bar(q, px, pz, rx, rz, 0.035, y1, yf, RenderUtil.argb(col, 0.95f * a), RenderUtil.argb(0xFFFFFF & (col | 0x404040), 0.95f * a));
    }

    private static void bar(BufferBuilder q, double px, double pz, double rx, double rz, double t, double y1, double y2, int cb, int ct) {
        RenderUtil.v(q, px - rx * t, y1, pz - rz * t, cb);
        RenderUtil.v(q, px + rx * t, y1, pz + rz * t, cb);
        RenderUtil.v(q, px + rx * t, y2, pz + rz * t, ct);
        RenderUtil.v(q, px - rx * t, y2, pz - rz * t, ct);
    }
}
