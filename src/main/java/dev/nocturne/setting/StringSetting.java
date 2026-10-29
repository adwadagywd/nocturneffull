package dev.nocturne.setting;

public class StringSetting extends Setting<String> implements Editable {
    private final int max;

    public StringSetting(String name, String def, int max) { super(name, def); this.max = max; }

    @Override public String text() { return value; }
    @Override public void setText(String s) { value = s; }
    @Override public char filter(char c) { return (c < 128 && Character.isLetterOrDigit(c)) || c == '_' ? c : 0; }
    @Override public int maxLen() { return max; }
    @Override public String display() { return value; }
    @Override public Object save() { return value; }
    @Override public void load(Object o) { if (o instanceof String s && s.length() <= max) value = s; }
}
