package dev.nocturne.module;

import dev.nocturne.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    public final String name, description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    public boolean expanded;
    /** GLFW key code, -1 = unbound */
    public int key = -1;
    public boolean keyDown;
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    public <S extends Setting<?>> S add(S s) { settings.add(s); return s; }

    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
        dev.nocturne.module.impl.Notifications.moduleToggled(this, e);
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(MinecraftClient mc) {}
    /** runs at the START of every client tick (before the player ticks) */
    public void onPreTick(MinecraftClient mc) {}
    /** runs every rendered frame (use for smooth rotation / camera movement) */
    public void onFrame() {}
    /** called when the game closes (restore options you changed) */
    public void onShutdown() {}
    /** false = module is never auto-enabled from the saved config */
    public boolean persistEnabled() { return true; }
    public void onRender3d() {}
    public void onRenderHud(DrawContext ctx) {}
}
