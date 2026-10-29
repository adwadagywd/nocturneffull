package dev.nocturne.module;

import dev.nocturne.module.impl.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static HudModule HUD;
    public static ThemeModule THEME;

    public static void init() {
        HUD = new HudModule();
        THEME = new ThemeModule();
        Notifications notif = new Notifications();
        // combat
        ALL.add(new Hitboxes());
        ALL.add(new AimAssist());
        ALL.add(new TriggerBot());
        ALL.add(new AutoTotem());
        ALL.add(new NoFriendsDamage());
        // render
        ALL.add(new Tracers());
        ALL.add(new Esp());
        ALL.add(new StorageEsp());
        ALL.add(new ItemEsp());
        ALL.add(new Nametags());
        ALL.add(new XRay());
        ALL.add(new Glow());
        ALL.add(new Fullbright());
        // visuals
        ALL.add(new Vignette());
        ALL.add(new AirParticles());
        ALL.add(new Trails());
        ALL.add(new JumpCircles());
        ALL.add(new TargetRing());
        ALL.add(new HitMarker());
        ALL.add(new DamageText());
        ALL.add(new Crosshair());
        ALL.add(new BlockHighlight());
        ALL.add(new Zoom());
        ALL.add(new Aurora());
        ALL.add(new Orbitals());
        ALL.add(new HitBurst());
        ALL.add(new SpeedLines());
        // world
        ALL.add(new WorldFog());
        ALL.add(new TimeChanger());
        ALL.add(new Weather());
        ALL.add(new Nuker());
        // movement
        ALL.add(new AutoSprint());
        ALL.add(new Freecam());
        // misc
        ALL.add(new FakePlayer());
        // client
        ALL.add(new TargetHud());
        ALL.add(new Keystrokes());
        ALL.add(new ArmorHud());
        ALL.add(new PotionHud());
        ALL.add(new Radar());
        ALL.add(notif);
        ALL.add(HUD);
        ALL.add(THEME);
        HUD.setEnabled(true);
        THEME.setEnabled(true);
        notif.setEnabled(true);
        if (NoFriendsDamage.INSTANCE != null) NoFriendsDamage.INSTANCE.setEnabled(true); // friends are safe by default
    }

    /** called every frame: toggles modules whose bound key was just pressed */
    public static void pollBinds() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long h = mc.getWindow().getHandle();
        for (Module m : ALL) {
            if (m.key < 0) { m.keyDown = false; continue; }
            boolean down = InputUtil.isKeyPressed(h, m.key);
            if (down && !m.keyDown && mc.currentScreen == null) m.toggle();
            m.keyDown = down;
        }
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }
}
