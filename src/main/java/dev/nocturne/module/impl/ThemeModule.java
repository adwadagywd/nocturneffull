package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.util.math.MathHelper;

public class ThemeModule extends Module {
    public static ThemeModule INSTANCE;

    public record Preset(String name, int accent, int accent2, int bg) {}

    public static final Preset[] PRESETS = {
            new Preset("Nocturne", 0xB06CFF, 0xE3C8FF, 0x0B0B0F),
            new Preset("Ocean", 0x3FA9F5, 0x8FE3FF, 0x090E14),
            new Preset("Sunset", 0xFF7A59, 0xFFC371, 0x140B0B),
            new Preset("Mint", 0x2EE6A6, 0xA6FFE0, 0x09130F),
            new Preset("Rose", 0xFF5C8A, 0xFFB0C8, 0x130A0E),
            new Preset("Gold", 0xF5C542, 0xFFEBA3, 0x13100A),
            new Preset("Mono", 0xE6E6EA, 0xFFFFFF, 0x0D0D0D),
            new Preset("Glass", 0x66B8FF, 0xE4F2FF, 0x0E1624),
    };

    private static String[] names() {
        String[] n = new String[PRESETS.length + 1];
        for (int i = 0; i < PRESETS.length; i++) n[i] = PRESETS[i].name();
        n[PRESETS.length] = "Custom";
        return n;
    }

    public final ModeSetting preset = add(new ModeSetting("Preset", "Nocturne", names()));
    public final ColorSetting hue1 = add(new ColorSetting("Accent hue", 0.76f));
    public final ColorSetting hue2 = add(new ColorSetting("Accent 2 hue", 0.88f));
    public final BoolSetting glassStyle = add(new BoolSetting("Glass material", false));
    public final NumSetting glassFrost = add(new NumSetting("Glass frost", 0.55, 0.1, 1.0, 0.05));
    public final NumSetting glassShine = add(new NumSetting("Glass shine", 0.8, 0.0, 1.0, 0.05));
    public final BoolSetting glassBlobs = add(new BoolSetting("Glass refraction", true));
    public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
    public final NumSetting rainbowSpeed = add(new NumSetting("Rainbow speed", 1.0, 0.2, 4.0, 0.1));
    public final NumSetting roundness = add(new NumSetting("Roundness", 1.0, 0.0, 2.0, 0.1));
    public final NumSetting opacity = add(new NumSetting("Panel opacity", 0.92, 0.4, 1.0, 0.02));
    public final NumSetting animSpeed = add(new NumSetting("Animation speed", 1.0, 0.5, 2.5, 0.1));
    public final BoolSetting blur = add(new BoolSetting("Background blur", true));
    public final BoolSetting glow = add(new BoolSetting("Background glow", true));
    public final BoolSetting particles = add(new BoolSetting("Particles", true));
    public final BoolSetting shadows = add(new BoolSetting("Shadows", true));
    public final BoolSetting uiSounds = add(new BoolSetting("UI sounds", true));
    public final ModeSetting closeEffect = add(new ModeSetting("Close effect", "Burst", "Burst", "Slide", "Fade"));
    public final ModeSetting font = add(new ModeSetting("Font", "Custom", "Custom", "Minecraft"));
    public final NumSetting fontOffset = add(new NumSetting("Font Y offset", 0, -3, 3, 1));

    public ThemeModule() {
        super("Theme", "Colours, corners and animations of the whole client", Category.CLIENT);
        INSTANCE = this;
        hue1.showIf(() -> preset.is("Custom") && !rainbow.get());
        hue2.showIf(() -> preset.is("Custom") && !rainbow.get());
        rainbowSpeed.showIf(rainbow::get);
        glassStyle.showIf(() -> !preset.is("Glass"));
        glassFrost.showIf(this::glassOn);
        glassShine.showIf(this::glassOn);
        glassBlobs.showIf(this::glassOn);
        fontOffset.showIf(() -> font.is("Custom"));
    }

    @Override public boolean persistEnabled() { return false; }

    /** true for the Glass preset, or when "Glass material" is switched on with any other colours */
    public boolean glassOn() { return preset.is("Glass") || glassStyle.get(); }

    public Preset current() {
        for (Preset p : PRESETS) if (preset.is(p.name())) return p;
        return null;
    }

    public int accent(int index) {
        if (rainbow.get()) {
            double h = (System.currentTimeMillis() * rainbowSpeed.get() / 6000.0 + index * 0.04) % 1.0;
            return 0xFF000000 | MathHelper.hsvToRgb((float) h, 0.7f, 1f);
        }
        Preset p = current();
        return p != null ? (0xFF000000 | p.accent()) : hue1.rgb();
    }

    public int accent2() {
        if (rainbow.get()) return accent(10);
        Preset p = current();
        return p != null ? (0xFF000000 | p.accent2()) : hue2.rgb();
    }

    public int bgTint() {
        Preset p = current();
        return p != null ? p.bg() : 0x0B0B0F;
    }
}
