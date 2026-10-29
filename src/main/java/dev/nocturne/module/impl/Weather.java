package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.MinecraftClient;

/** Client-side weather: clear / rain / thunder (snow where the biome is cold). */
public class Weather extends Module {
    private final ModeSetting mode = add(new ModeSetting("Weather", "Clear", "Clear", "Rain", "Thunder"));
    private final NumSetting strength = add(new NumSetting("Strength", 1.0, 0.1, 1.0, 0.05));

    public Weather() {
        super("Weather", "Changes the weather you see", Category.WORLD);
        strength.showIf(() -> !mode.is("Clear"));
    }

    private void apply() {
        if (mc.world == null) return;
        float rain = mode.is("Clear") ? 0f : strength.getFloat();
        float thunder = mode.is("Thunder") ? strength.getFloat() : 0f;
        mc.world.setRainGradient(rain);
        mc.world.setThunderGradient(thunder);
    }

    @Override public void onFrame() { apply(); }
    @Override public void onTick(MinecraftClient mc) { apply(); }
}
