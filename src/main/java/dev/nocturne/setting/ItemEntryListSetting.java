package dev.nocturne.setting;

import java.util.*;

/**
 * A list of tracked items that is not searched but filled in-game: middle-click an item in any inventory,
 * ender chest or market GUI. One entry = item id + its enchantments + the name it is shown with:
 *   minecraft:mace|minecraft:density:5,minecraft:breach:4|Wind Mace
 */
public class ItemEntryListSetting extends BlockListSetting {
    public record Entry(String item, Map<String, Integer> ench, String name) {}

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public ItemEntryListSetting(String name) {
        super(name);
        searchable = false;
        hint = "Middle-click an item in any inventory / market";
    }

    @Override protected List<String> ids() { return List.of(); }
    @Override public String presetLabel() { return null; }
    @Override protected String unit() { return "items"; }

    public static String encode(String item, Map<String, Integer> ench, String name) {
        StringBuilder e = new StringBuilder();
        for (Map.Entry<String, Integer> en : ench.entrySet()) {
            if (e.length() > 0) e.append(',');
            e.append(en.getKey()).append(':').append(en.getValue());
        }
        return item + "|" + e + "|" + name.replace("|", "").trim();
    }

    public static Entry parse(String s) {
        String[] p = s.split("\\|", -1);
        Map<String, Integer> ench = new LinkedHashMap<>();
        if (p.length > 1 && !p[1].isEmpty()) {
            for (String part : p[1].split(",")) {
                int c = part.lastIndexOf(':');
                if (c < 0) continue;
                try { ench.put(part.substring(0, c), Integer.parseInt(part.substring(c + 1))); } catch (NumberFormatException ignored) { }
            }
        }
        return new Entry(p[0], ench, p.length > 2 ? p[2] : "");
    }

    public static String pretty(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String roman(int lv) { return lv > 0 && lv < ROMAN.length ? ROMAN[lv] : String.valueOf(lv); }

    @Override
    public String label(String entry) {
        Entry e = parse(entry);
        StringBuilder sb = new StringBuilder(e.name().isEmpty() ? pretty(e.item()) : e.name());
        if (!e.ench().isEmpty()) {
            sb.append("  ");
            int n = 0;
            for (Map.Entry<String, Integer> en : e.ench().entrySet()) {
                if (n++ > 0) sb.append(", ");
                sb.append(pretty(en.getKey())).append(' ').append(roman(en.getValue()));
            }
        }
        return sb.toString();
    }
}
