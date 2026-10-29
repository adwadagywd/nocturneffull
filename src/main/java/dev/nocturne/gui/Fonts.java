package dev.nocturne.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Picks the UI font (bundled Instrument Sans) or falls back to the vanilla one. */
public final class Fonts {
    public static final Identifier REGULAR = Identifier.of("nocturne", "ui");
    public static final Identifier BOLD = Identifier.of("nocturne", "ui_bold");
    private static final Style REGULAR_STYLE = Style.EMPTY.withFont(REGULAR);
    private static final Style BOLD_STYLE = Style.EMPTY.withFont(BOLD);

    private static boolean ok;
    private static long checkedAt;

    private Fonts() {}

    /** true when the custom font is selected AND actually loaded (a missing font draws equal-width boxes) */
    public static boolean custom(TextRenderer tr) {
        if (!Theme.customFont()) return false;
        if (ok) return true;
        long now = System.currentTimeMillis();
        if (now - checkedAt < 2000) return false;
        checkedAt = now;
        int narrow = tr.getWidth(Text.literal("iiiiiiiiii").setStyle(REGULAR_STYLE));
        int wide = tr.getWidth(Text.literal("WWWWWWWWWW").setStyle(REGULAR_STYLE));
        ok = narrow > 0 && narrow < wide;
        return ok;
    }

    public static Text text(TextRenderer tr, String s) {
        return custom(tr) ? Text.literal(s).setStyle(REGULAR_STYLE) : Text.literal(s);
    }

    public static Text bold(TextRenderer tr, String s) {
        return custom(tr) ? Text.literal(s).setStyle(BOLD_STYLE) : Text.literal(s).formatted(Formatting.BOLD);
    }

    /** vertical tuning for the custom font (Theme -> Font Y offset) */
    public static int dy(TextRenderer tr) {
        return custom(tr) ? Theme.fontOffset() : 0;
    }
}
