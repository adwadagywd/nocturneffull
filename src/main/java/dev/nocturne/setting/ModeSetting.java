package dev.nocturne.setting;

import java.util.Arrays;

public class ModeSetting extends Setting<String> {
    public final String[] modes;

    public ModeSetting(String name, String def, String... modes) { super(name, def); this.modes = modes; }

    public boolean is(String m) { return value.equals(m); }

    public void cycle(int dir) {
        int i = Arrays.asList(modes).indexOf(value);
        value = modes[Math.floorMod(i + dir, modes.length)];
    }

    @Override public String display() { return value; }
    @Override public Object save() { return value; }
    @Override public void load(Object o) { if (o instanceof String s && Arrays.asList(modes).contains(s)) value = s; }
}
