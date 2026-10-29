package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

/** Custom fog distance and colour. Works through an optional mixin; if that cannot apply, this does nothing. */
public class WorldFog extends Module {
    public static WorldFog INSTANCE;

    private final NumSetting start = add(new NumSetting("Start", 8, 0, 512, 1));
    private final NumSetting end = add(new NumSetting("End", 64, 8, 1024, 1));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Original", "Original", "Custom", "Rainbow"));
    private final ColorSetting hue = add(new ColorSetting("Fog hue", 0.6f));

    public WorldFog() {
        super("WorldFog", "Change how thick and what colour the fog is", Category.WORLD);
        INSTANCE = this;
        hue.showIf(() -> colorMode.is("Custom"));
    }

    /** receives the game's Fog record and returns a modified copy (or the original when anything is unexpected) */
    public static Object apply(Object fog) {
        WorldFog f = INSTANCE;
        if (f == null || !f.isEnabled() || fog == null) return fog;
        try {
            RecordComponent[] rc = fog.getClass().getRecordComponents();
            if (rc == null || rc.length != 7) return fog;
            Object[] v = new Object[7];
            for (int i = 0; i < 7; i++) {
                Method m = rc[i].getAccessor();
                m.setAccessible(true);
                v[i] = m.invoke(fog);
            }
            if (!(v[0] instanceof Float) || !(v[1] instanceof Float e0) || v[2] instanceof Float || !(v[3] instanceof Float)) return fog;
            if (e0 < 30f) return fog; // underwater, lava, blindness... keep vanilla

            float endV = Math.max(f.end.getFloat(), 4f);
            float startV = Math.min(f.start.getFloat(), endV - 1f);
            v[0] = startV;
            v[1] = endV;
            if (!f.colorMode.is("Original")) {
                int rgb = f.colorMode.is("Rainbow")
                        ? MathHelper.hsvToRgb((System.currentTimeMillis() % 8000L) / 8000f, 0.6f, 1f)
                        : f.hue.rgb() & 0xFFFFFF;
                v[3] = ((rgb >> 16) & 255) / 255f;
                v[4] = ((rgb >> 8) & 255) / 255f;
                v[5] = (rgb & 255) / 255f;
            }
            Constructor<?> ctor = fog.getClass().getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(v);
        } catch (Throwable t) {
            return fog;
        }
    }
}
