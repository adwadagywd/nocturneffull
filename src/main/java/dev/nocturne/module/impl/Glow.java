package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.TargetSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Vanilla glowing outline on the chosen entities (client-side flag). */
public class Glow extends Module {
    private final TargetSettings targets = new TargetSettings(this, true, true, false, false);
    private final Set<Entity> flagged = Collections.newSetFromMap(new IdentityHashMap<>());

    public Glow() { super("Glow", "Outline glow around entities, visible through walls", Category.RENDER); }

    @Override
    public void onTick(MinecraftClient mc) {
        Set<Entity> now = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity e : mc.world.getEntities()) {
            if (!targets.test(e)) continue;
            now.add(e);
            if (!e.isGlowing()) { e.setGlowing(true); flagged.add(e); }
        }
        flagged.removeIf(e -> {
            if (now.contains(e)) return false;
            e.setGlowing(false);
            return true;
        });
    }

    @Override
    protected void onDisable() {
        for (Entity e : flagged) e.setGlowing(false);
        flagged.clear();
    }
}
