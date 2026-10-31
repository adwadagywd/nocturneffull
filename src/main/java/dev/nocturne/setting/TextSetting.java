package dev.nocturne.setting;

/** Free text (spaces and symbols allowed) - used for custom names. */
public class TextSetting extends StringSetting {
    public TextSetting(String name, String def, int max) { super(name, def, max); }

    @Override public char filter(char c) { return c >= 32 && c < 127 ? c : 0; }
}
