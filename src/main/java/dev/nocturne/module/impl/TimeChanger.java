package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Method;

/** Client-side time of day (only you see it). */
public class TimeChanger extends Module {
    private static Method SETTER;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Fixed", "Fixed", "Cycle"));
    private final NumSetting time = add(new NumSetting("Time", 18000, 0, 24000, 250));
    private final NumSetting speed = add(new NumSetting("Cycle speed", 10, 1, 100, 1));

    private double cycle;
    private long current, last;

    public TimeChanger() {
        super("TimeChanger", "Set the time of day (0 = sunrise, 6000 = noon, 18000 = midnight)", Category.WORLD);
        time.showIf(() -> mode.is("Fixed"));
        speed.showIf(() -> mode.is("Cycle"));
    }

    @Override
    protected void onEnable() {
        last = 0;
        cycle = mc.world != null ? mc.world.getTimeOfDay() : time.get();
    }

    /** Finds the "set time of day" method without knowing its (obfuscated) name by trying every void(long) method. */
    private Method probe(Object props) {
        long oldTod = mc.world.getTimeOfDay();
        long oldTime = mc.world.getTime();
        for (Method m : props.getClass().getMethods()) {
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != long.class || m.getReturnType() != void.class) continue;
            try {
                long probe = oldTod + 4321L;
                m.invoke(props, probe);
                boolean isTod = mc.world.getTimeOfDay() == probe;
                boolean isTime = mc.world.getTime() == probe;
                m.invoke(props, isTime && !isTod ? oldTime : oldTod);
                if (isTod) return m;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void apply() {
        if (mc.world == null) return;
        try {
            Object props = mc.world.getLevelProperties();
            if (SETTER == null) SETTER = probe(props);
            if (SETTER != null) SETTER.invoke(props, current);
        } catch (Exception ignored) {}
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        if (mode.is("Cycle")) { cycle += dt * 20.0 * speed.get(); current = (long) cycle; }
        else current = time.getInt();
        apply();
    }

    @Override
    public void onTick(MinecraftClient mc) { apply(); }
}
