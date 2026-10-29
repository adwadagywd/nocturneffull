package dev.nocturne.setting;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/** A set of block ids with a built-in search box (drawn by the ClickGUI). */
public class BlockListSetting extends Setting<Set<String>> implements Editable {
    public static final int MAX_RESULTS = 6, MAX_SHOWN = 8;
    private static List<String> ALL;

    public String query = "";
    /** false = no search box: the list is filled some other way (e.g. by mining) */
    public boolean searchable = true;
    public String hint = "";
    /** bumped on every change so modules know when to rebuild */
    public int version;
    private String cachedQuery;
    private List<String> cached = List.of();

    public BlockListSetting(String name, String... defaults) {
        super(name, new LinkedHashSet<>(Arrays.asList(defaults)));
    }

    private static List<String> all() {
        if (ALL == null) {
            List<String> l = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) l.add(id.toString());
            Collections.sort(l);
            ALL = l;
        }
        return ALL;
    }

    private static String path(String id) { return id.substring(id.indexOf(':') + 1); }

    public void setQuery(String q) { query = q; }

    @Override public String text() { return query; }
    @Override public void setText(String s) { query = s; }
    @Override public char filter(char c) {
        return (c < 128 && Character.isLetterOrDigit(c)) || c == '_' || c == ':' ? Character.toLowerCase(c) : 0;
    }
    @Override public int maxLen() { return 40; }

    public List<String> results() {
        if (query.isEmpty()) return List.of();
        if (!query.equals(cachedQuery)) {
            String q = query.toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : all()) if (id.contains(q)) out.add(id);
            out.sort(Comparator.comparingInt((String id) -> path(id).startsWith(q) ? 0 : 1));
            cached = new ArrayList<>(out.size() > 40 ? out.subList(0, 40) : out);
            cachedQuery = query;
        }
        return cached;
    }

    public boolean has(String id) { return value.contains(id); }

    public void toggle(String id) {
        if (!value.remove(id)) value.add(id);
        version++;
    }

    public void addAllOres() {
        for (String id : all()) {
            String p = path(id);
            if (id.startsWith("minecraft:") && (p.endsWith("_ore") || p.equals("ancient_debris"))) value.add(id);
        }
        version++;
    }

    public void clear() { value.clear(); version++; }

    /** pixel height of the widget, must match ClickGui's layout */
    public int height() {
        int m0 = Math.min(value.size(), MAX_SHOWN);
        if (!searchable) return 37 + m0 * 11 + (value.size() > m0 ? 11 : 0);
        int n = Math.min(results().size(), MAX_RESULTS);
        int m = Math.min(value.size(), MAX_SHOWN);
        return 42 + n * 12 + m * 11 + (value.size() > m ? 11 : 0);
    }

    @Override public String display() { return value.size() + " blocks"; }
    @Override public Object save() { return new ArrayList<>(value); }

    @Override
    public void load(Object o) {
        if (o instanceof List<?> l) {
            value.clear();
            for (Object x : l) if (x instanceof String s) value.add(s);
            version++;
        }
    }
}
