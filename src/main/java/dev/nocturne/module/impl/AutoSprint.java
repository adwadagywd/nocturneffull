package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import net.minecraft.client.MinecraftClient;

public class AutoSprint extends Module {
    public AutoSprint() { super("AutoSprint", "Sprints whenever you walk forward", Category.MOVEMENT); }

    @Override
    public void onTick(MinecraftClient mc) {
        var p = mc.player;
        if (mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision
                && !p.isUsingItem() && p.getHungerManager().getFoodLevel() > 6) {
            mc.options.sprintKey.setPressed(true);
        }
    }

    @Override
    protected void onDisable() { mc.options.sprintKey.setPressed(false); }
}
