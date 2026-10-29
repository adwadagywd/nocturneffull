package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;

public class Zoom extends Module {
    private final NumSetting fov = add(new NumSetting("FOV", 30, 30, 90, 1));
    private final NumSetting smooth = add(new NumSetting("Smoothing", 0.5, 0.1, 1.0, 0.05));

    private int original = -1;
    private float cur;
    private long last;

    public Zoom() { super("Zoom", "Zooms in (bind it to a key for hold-style zoom)", Category.VISUALS); }

    @Override
    protected void onEnable() {
        original = mc.options.getFov().getValue();
        cur = original;
        last = 0;
    }

    private void restore() {
        if (original > 0) mc.options.getFov().setValue(original);
        original = -1;
    }

    @Override protected void onDisable() { restore(); }
    @Override public void onShutdown() { restore(); }

    @Override
    public void onFrame() {
        if (original < 0) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        float s = smooth.getFloat();
        cur += (fov.getFloat() - cur) * (s >= 0.995f ? 1f : 1f - (float) Math.exp(-s * 20.0 * dt));
        mc.options.getFov().setValue(Math.round(cur));
    }
}
