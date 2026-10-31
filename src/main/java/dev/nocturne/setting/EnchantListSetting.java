package dev.nocturne.setting;

import java.util.*;

/** A searchable set of enchantment ids (all vanilla enchantments). */
public class EnchantListSetting extends BlockListSetting {
    private static final List<String> ALL = new ArrayList<>();
    static {
        for (String n : new String[]{
                "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection", "respiration",
                "aqua_affinity", "thorns", "depth_strider", "frost_walker", "binding_curse", "soul_speed", "swift_sneak",
                "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge",
                "efficiency", "silk_touch", "unbreaking", "fortune", "power", "punch", "flame", "infinity",
                "luck_of_the_sea", "lure", "loyalty", "impaling", "riptide", "channeling", "multishot", "quick_charge",
                "piercing", "density", "breach", "wind_burst", "mending", "vanishing_curse"})
            ALL.add("minecraft:" + n);
        Collections.sort(ALL);
    }

    public EnchantListSetting(String name, String... defaults) { super(name, defaults); }

    @Override protected List<String> ids() { return ALL; }
    @Override public String searchHint() { return "Search enchants..."; }
    @Override public String presetLabel() { return null; }
    @Override protected String unit() { return "enchants"; }
}
