package dev.nocturne.setting;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/** A searchable set of entity type ids. */
public class EntityListSetting extends BlockListSetting {
    private static List<String> ALL;

    public EntityListSetting(String name, String... defaults) { super(name, defaults); }

    @Override
    protected List<String> ids() {
        if (ALL == null) {
            List<String> l = new ArrayList<>();
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) if (!id.getPath().equals("player")) l.add(id.toString());
            Collections.sort(l);
            ALL = l;
        }
        return ALL;
    }

    @Override public String searchHint() { return "Search entities..."; }
    @Override public String presetLabel() { return null; }
    @Override protected String unit() { return "entities"; }
}
