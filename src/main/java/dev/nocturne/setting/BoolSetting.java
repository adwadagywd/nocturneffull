package dev.nocturne.setting;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, boolean def) { super(name, def); }
    public void toggle() { value = !value; }
    @Override public String display() { return value ? "On" : "Off"; }
    @Override public Object save() { return value; }
    @Override public void load(Object o) { if (o instanceof Boolean b) value = b; }
}
