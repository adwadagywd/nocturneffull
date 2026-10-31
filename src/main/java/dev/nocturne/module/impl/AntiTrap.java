package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pick what you do not want to deal with: banners, minecarts, boats, armor stands... Disabled things are not drawn,
 * your crosshair passes straight through them and you do not collide with them - as if they were not there.
 * (client side only: the server still knows about them)
 */
public class AntiTrap extends Module {
    public static AntiTrap INSTANCE;

    private final BoolSetting minecarts = add(new BoolSetting("Minecarts", true));
    private final BoolSetting boats = add(new BoolSetting("Boats / rafts", true));
    private final BoolSetting armorStands = add(new BoolSetting("Armor stands", false));
    private final BoolSetting itemFrames = add(new BoolSetting("Item frames", false));
    private final BoolSetting paintings = add(new BoolSetting("Paintings", false));
    private final BoolSetting displays = add(new BoolSetting("Display entities", true));
    private final BoolSetting interactions = add(new BoolSetting("Interaction entities", false));
    private final BoolSetting crystals = add(new BoolSetting("End crystals", false));
    private final BoolSetting banners = add(new BoolSetting("Banners", true));
    private final BoolSetting signs = add(new BoolSetting("Signs", false));
    private final BoolSetting heads = add(new BoolSetting("Heads / skulls", false));
    private final EntityListSetting customEntities = add(new EntityListSetting("Custom entities"));
    private final BlockListSetting customBlocks = add(new BlockListSetting("Custom blocks (no hitbox)") {
        @Override public String presetLabel() { return null; }
    });

    private static final Map<EntityType<?>, String> IDS = new IdentityHashMap<>();
    private final Set<Block> blockSet = new HashSet<>();
    private int builtVersion = -1;

    public AntiTrap() {
        super("AntiTrap", "Ignore chosen entities / blocks: invisible, no hitbox", Category.RENDER);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }

    private static String idOf(EntityType<?> t) {
        return IDS.computeIfAbsent(t, k -> Registries.ENTITY_TYPE.getId(k).toString());
    }

    /** true = pretend this entity does not exist (client side only) */
    public static boolean hides(Entity e) {
        AntiTrap a = INSTANCE;
        if (a == null || !a.isEnabled() || e == null || !e.getWorld().isClient()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == mc.player || (mc.player != null && e == mc.player.getVehicle())) return false;
        String id = idOf(e.getType()), p = id.substring(id.indexOf(':') + 1);
        if (a.minecarts.get() && p.endsWith("minecart")) return true;
        if (a.boats.get() && (p.endsWith("boat") || p.endsWith("raft"))) return true;
        if (a.armorStands.get() && p.equals("armor_stand")) return true;
        if (a.itemFrames.get() && p.endsWith("item_frame")) return true;
        if (a.paintings.get() && p.equals("painting")) return true;
        if (a.displays.get() && p.endsWith("_display")) return true;
        if (a.interactions.get() && p.equals("interaction")) return true;
        if (a.crystals.get() && p.equals("end_crystal")) return true;
        return a.customEntities.has(id);
    }

    private void rebuild() {
        blockSet.clear();
        for (String id : customBlocks.get()) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null) blockSet.add(Registries.BLOCK.get(ident));
        }
        builtVersion = customBlocks.version;
    }

    /** true = the crosshair ray goes straight through this block (client thread only) */
    public static boolean ignoresBlock(AbstractBlock.AbstractBlockState st) {
        AntiTrap a = INSTANCE;
        if (a == null || !a.isEnabled() || !MinecraftClient.getInstance().isOnThread()) return false;
        Block b = st.getBlock();
        if (a.banners.get() && b instanceof AbstractBannerBlock) return true;
        if (a.signs.get() && b instanceof AbstractSignBlock) return true;
        if (a.heads.get() && b instanceof AbstractSkullBlock) return true;
        if (a.builtVersion != a.customBlocks.version) a.rebuild();
        return !a.blockSet.isEmpty() && a.blockSet.contains(b);
    }

    /** true = do not draw this block entity (banner, sign, skull...) */
    public static boolean hidesBlockEntity(BlockEntity be) {
        return be != null && ignoresBlock(be.getCachedState());
    }
}
