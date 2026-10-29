package dev.nocturne.setting;

import net.minecraft.util.math.MathHelper;

/** Hue slider based colour (saturation/brightness fixed so it always looks good). */
public class ColorSetting extends Setting<Float> implements Slidable {
    public ColorSetting(String name, float hue) { super(name, hue); }

    public int rgb() { return 0xFF000000 | MathHelper.hsvToRgb(value, 0.75f, 1f); }

    @Override public double fraction() { return value; }
    @Override public void setFraction(double f) { value = (float) Math.max(0, Math.min(1, f)); }
    @Override public String display() { return ""; }
    @Override public Object save() { return value; }
    @Override public void load(Object o) { if (o instanceof Number n) value = n.floatValue(); }
}
