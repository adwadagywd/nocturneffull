package dev.nocturne;

import dev.nocturne.config.ConfigManager;
import dev.nocturne.gui.ClickGui;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.module.impl.DamageText;
import dev.nocturne.module.impl.FakePlayer;
import dev.nocturne.module.impl.HitBurst;
import dev.nocturne.module.impl.HitMarker;
import dev.nocturne.module.impl.Notifications;
import dev.nocturne.module.impl.NoFriendsDamage;
import dev.nocturne.util.Friends;
import dev.nocturne.util.RenderUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class Nocturne implements ClientModInitializer {
    public static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Friends.load();
        ConfigManager.load();

        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.nocturne.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.nocturne"));

        // every attack the local player makes goes through here (manual hits and TriggerBot alike)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient()) return ActionResult.PASS;
            if (NoFriendsDamage.blocks(entity)) return ActionResult.FAIL;
            HitMarker.onHit();
            HitBurst.onHit(entity);
            DamageText.noteAttack(entity);
            if (FakePlayer.onAttack(player, entity)) return ActionResult.FAIL; // client-only dummy: no attack packet
            return ActionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;
            for (Module m : ModuleManager.ALL) if (m.isEnabled()) m.onPreTick(mc);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.wasPressed()) mc.setScreen(new ClickGui());
            if (mc.player == null || mc.world == null) return;
            for (Module m : ModuleManager.ALL) if (m.isEnabled()) m.onTick(mc);
        });

        WorldRenderEvents.LAST.register(ctx -> {
            RenderUtil.capture(ctx);
            for (Module m : ModuleManager.ALL) {
                if (!m.isEnabled()) continue;
                m.onFrame();
                m.onRender3d();
            }
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            ModuleManager.pollBinds(); // end of the frame: safe place to toggle modules
            if (mc.options.hudHidden) return;
            for (Module m : ModuleManager.ALL) if (m.isEnabled()) m.onRenderHud(ctx);
        });

        Notifications.suppress = false; // startup toggles stay silent

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            for (Module m : ModuleManager.ALL) if (m.isEnabled()) m.onShutdown();
            ConfigManager.save();
            Friends.save();
        });
    }
}
