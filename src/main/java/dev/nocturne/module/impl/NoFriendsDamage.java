package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.BoolSetting;
import dev.nocturne.util.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/**
 * Stops you from hitting players in your friends list. Two independent layers:
 *  1) the attack event itself is cancelled (covers manual hits and TriggerBot),
 *  2) while your crosshair is on a friend the attack key is swallowed before the game can use it.
 */
public class NoFriendsDamage extends Module {
    public static NoFriendsDamage INSTANCE;
    private final BoolSetting notify = add(new BoolSetting("Notify", true));
    private long lastNotify;

    public NoFriendsDamage() {
        super("NoFriendsDamage", "Blocks your attacks on players in the Friends tab", Category.COMBAT);
        INSTANCE = this;
    }

    private void tell(Entity e) {
        long now = System.currentTimeMillis();
        if (!notify.get() || now - lastNotify < 1000) return;
        lastNotify = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("Friend: " + e.getName().getString() + " - attack blocked"), true);
    }

    /** layer 1: called from the attack event */
    public static boolean blocks(Entity e) {
        NoFriendsDamage m = INSTANCE;
        if (m == null || !m.isEnabled() || !Friends.isFriend(e)) return false;
        m.tell(e);
        return true;
    }

    /** layer 2: runs before the game reads your mouse buttons this tick */
    @Override
    public void onPreTick(MinecraftClient mc) {
        Entity t = mc.targetedEntity;
        if (t == null || !Friends.isFriend(t)) return;
        boolean pressed = false;
        while (mc.options.attackKey.wasPressed()) pressed = true; // consume queued clicks
        if (pressed) tell(t);
    }
}
