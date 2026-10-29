package dev.nocturne.gui;

import dev.nocturne.module.impl.ThemeModule;

/** All colours/metrics come from the Theme module (Client panel or the Theme tab). */
public final class Theme {
    public static final int TEXT = 0xFFEDEDF2, DIM = 0xFF7C7C8A, SOFT = 0xFFBDBDC9, KNOB = 0xFFFFFFFF;

    private Theme() {}

    private static ThemeModule t() { return ThemeModule.INSTANCE; }

    public static int accent() { return t() == null ? 0xFFB06CFF : t().accent(0); }
    public static int accent(int index) { return t() == null ? 0xFFB06CFF : t().accent(index); }
    public static int accent2() { return t() == null ? 0xFFE3C8FF : t().accent2(); }
    public static int accentA(int alpha) { return (accent() & 0xFFFFFF) | (alpha << 24); }

    private static int tint() { return t() == null ? 0x0B0B0F : t().bgTint(); }
    private static int mix(float f) { return Draw.lerp(0xFF000000 | tint(), 0xFFFFFFFF, f) & 0xFFFFFF; }

    public static int bg() {
        int a = t() == null ? 0xEB : (int) (t().opacity.get() * 255);
        return (a << 24) | tint();
    }
    public static int header() { return 0xF8000000 | mix(0.045f); }
    public static int rowHover() { return 0xFF000000 | mix(0.09f); }
    public static int track() { return 0xFF000000 | mix(0.13f); }
    public static int field() { return 0xFF000000 | mix(0.06f); }
    public static int fieldFocus() { return 0xFF000000 | mix(0.11f); }
    public static int border() { return 0x30FFFFFF; }
    public static int inset() { return 0x66000000; }

    public static float roundness() { return t() == null ? 1f : t().roundness.getFloat(); }
    public static float anim() { return t() == null ? 1f : t().animSpeed.getFloat(); }
    public static boolean blur() { return t() == null || t().blur.get(); }
    public static boolean particles() { return t() == null || t().particles.get(); }
    public static boolean glow() { return t() == null || t().glow.get(); }
    public static boolean shadows() { return t() == null || t().shadows.get(); }
    public static boolean uiSounds() { return t() == null || t().uiSounds.get(); }
    public static String closeEffect() { return t() == null ? "Burst" : t().closeEffect.get(); }
    public static boolean customFont() { return t() == null || t().font.is("Custom"); }
    public static int fontOffset() { return t() == null ? 0 : t().fontOffset.getInt(); }
}
