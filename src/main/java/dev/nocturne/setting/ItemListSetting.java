package dev.nocturne.setting;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/** A searchable set of item ids. */
public class ItemListSetting extends BlockListSetting {
    private static List<String> ALL;

    public ItemListSetting(String name, String... defaults) { super(name, defaults); }

    @Override
    protected List<String> ids() {
        if (ALL == null) {
            List<String> l = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) if (!id.getPath().equals("air")) l.add(id.toString());
            Collections.sort(l);
            ALL = l;
        }
        return ALL;
    }

    @Override public String searchHint() { return "Search items..."; }
    @Override public String presetLabel() { return null; }
    @Override protected String unit() { return "items"; }
}
