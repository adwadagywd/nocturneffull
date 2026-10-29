package dev.nocturne.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class Sounds {
    private Sounds() {}

    /** soft UI click; higher pitch when something turns on */
    public static void click(boolean up) {
        if (!Theme.uiSounds()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, up ? 1.35f : 0.9f));
    }
}
