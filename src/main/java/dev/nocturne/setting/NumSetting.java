package dev.nocturne.setting;

import java.util.Locale;

public class NumSetting extends Setting<Double> implements Slidable {
    public final double min, max, step;

    public NumSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        this.min = min; this.max = max; this.step = step;
    }

    public float getFloat() { return value.floatValue(); }
    public int getInt() { return (int) Math.round(value); }

    @Override public double fraction() { return (value - min) / (max - min); }

    @Override public void setFraction(double f) {
        f = Math.max(0, Math.min(1, f));
        double v = min + f * (max - min);
        v = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, v));
    }

    @Override public String display() {
        if (step >= 1) return Integer.toString((int) Math.round(value));
        String s = String.format(Locale.ROOT, "%.2f", value);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override public Object save() { return value; }
    @Override public void load(Object o) { if (o instanceof Number n) value = Math.max(min, Math.min(max, n.doubleValue())); }
}
