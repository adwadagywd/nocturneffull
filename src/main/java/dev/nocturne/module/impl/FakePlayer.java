package dev.nocturne.module.impl;

import com.mojang.authlib.GameProfile;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.StringSetting;
import dev.nocturne.util.Combat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A real-looking player that stands still. Hitting it feels like hitting a person: hurt flash and sound, the
 * attacker's hit sounds (weak / strong / crit), crit and damage particles, invulnerability frames, a flinch of
 * the head - but no knockback, no movement. It takes damage and "pops a totem" (full health) when it would die.
 * Everything is client-side: the attack packet is never sent to the server, so servers never see the dummy.
 */
public class FakePlayer extends Module {
    public static FakePlayer INSTANCE;
    private static final int FAKE_ID = -696969;
    private static final float ARMOR = 20f, TOUGHNESS = 12f; // full netherite

    private final StringSetting name = add(new StringSetting("Name", "Dummy", 16));

    private OtherClientPlayerEntity fake;
    private double fx, fy, fz;
    private float fyaw;
    private String spawnedName = "";
    private int pops, iframes, flinch;
    private float lastDmg, flinchDir = 1f;
    private final Map<Integer, Vec3d> crystals = new HashMap<>();

    public FakePlayer() {
        super("FakePlayer", "Standing dummy that reacts like a real player: takes damage, pops infinite totems", Category.MISC);
        INSTANCE = this;
    }

    @Override public boolean persistEnabled() { return false; }

    private String cleanName() {
        String n = name.get().replaceAll("[^A-Za-z0-9_]", "");
        if (n.isEmpty()) n = "Dummy";
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.world == null) return;
        fx = mc.player.getX();
        fy = mc.player.getY();
        fz = mc.player.getZ();
        fyaw = mc.player.getYaw() + 180f; // faces you
        pops = 0;
        iframes = 0;
        flinch = 0;
        crystals.clear();
        spawn();
    }

    private void spawn() {
        spawnedName = cleanName();
        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), spawnedName));
        fake.setId(FAKE_ID);
        fake.refreshPositionAndAngles(fx, fy, fz, fyaw, 0f);
        fake.setHeadYaw(fyaw);
        fake.setBodyYaw(fyaw);
        fake.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        fake.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        fake.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        fake.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        fake.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        fake.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        fake.setHealth(fake.getMaxHealth());
        mc.world.addEntity(fake);
    }

    private void despawn() {
        if (fake != null && mc.world != null) mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        fake = null;
    }

    @Override protected void onDisable() { despawn(); crystals.clear(); }

    // ---------------- damage model ----------------
    private static float armorReduce(float dmg) {
        float red = Math.min(20f, Math.max(ARMOR / 5f, ARMOR - dmg / (2f + TOUGHNESS / 4f)));
        return dmg * (1f - red / 25f);
    }

    private static float weaponDamage(ItemStack s) {
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        boolean sword = id.endsWith("_sword"), axe = id.endsWith("_axe");
        if (!sword && !axe) return id.equals("mace") ? 6f : id.equals("trident") ? 9f : 1f;
        if (sword) {
            if (id.startsWith("netherite")) return 8f;
            if (id.startsWith("diamond")) return 7f;
            if (id.startsWith("iron")) return 6f;
            if (id.startsWith("stone")) return 5f;
            return 4f;
        }
        if (id.startsWith("netherite")) return 10f;
        if (id.startsWith("wooden") || id.startsWith("golden")) return 7f;
        return 9f;
    }

    // ---------------- feedback ----------------
    private void sound(String id, float pitch) {
        if (mc.world == null || fake == null) return;
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
        if (ev != null) mc.world.playSound(fake.getX(), fake.getY(), fake.getZ(), ev, SoundCategory.PLAYERS, 1.0f, pitch, false);
    }

    private void sparks(boolean crit, float applied) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double y = fake.getY() + fake.getHeight() * 0.6;
        if (crit) {
            for (int i = 0; i < 14; i++)
                mc.world.addParticle(ParticleTypes.CRIT,
                        fake.getX() + (r.nextDouble() - 0.5) * 0.7, y + (r.nextDouble() - 0.5) * 0.7, fake.getZ() + (r.nextDouble() - 0.5) * 0.7,
                        (r.nextDouble() - 0.5) * 0.9, r.nextDouble() * 0.7, (r.nextDouble() - 0.5) * 0.9);
        }
        int n = Math.min(10, (int) (applied * 0.5f));
        for (int i = 0; i < n; i++)
            mc.world.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                    fake.getX() + (r.nextDouble() - 0.5) * 0.5, y + (r.nextDouble() - 0.5) * 0.5, fake.getZ() + (r.nextDouble() - 0.5) * 0.5,
                    0.0, 0.25, 0.0);
    }

    private void hurt(float raw) {
        if (fake == null) return;
        float dmg = armorReduce(raw);
        fake.handleStatus((byte) 2); // red hurt flash + hurt sound + limb jolt
        float h = fake.getHealth() - dmg;
        if (h <= 0f) {
            fake.setHealth(fake.getMaxHealth());
            fake.handleStatus((byte) 35); // totem pop
            pops++;
            if (mc.player != null) mc.player.sendMessage(Text.literal(spawnedName + " popped a totem  (" + pops + ")"), true);
        } else {
            fake.setHealth(h);
        }
    }

    private void hit(PlayerEntity attacker) {
        float cd = attacker.getAttackCooldownProgress(0.5f);
        boolean strong = cd > 0.9f;
        boolean crit = strong && Combat.canCrit(attacker);
        float dmg = weaponDamage(attacker.getMainHandStack()) * (0.2f + cd * cd * 0.8f);
        if (crit) dmg *= 1.5f;
        attacker.resetLastAttackedTicks(); // the vanilla attack would do this - we cancel that attack

        float applied = dmg;
        if (iframes > 0) { // invulnerability frames: only the part above the last hit gets through
            if (dmg <= lastDmg) { sound("entity.player.attack.nodamage", 1.0f); return; }
            applied = dmg - lastDmg;
        }
        lastDmg = dmg;
        iframes = 10;

        hurt(applied);
        sound(crit ? "entity.player.attack.crit" : strong ? "entity.player.attack.strong" : "entity.player.attack.weak", 1.0f);
        if (attacker.isSprinting() && strong) sound("entity.player.attack.knockback", 1.0f);
        sparks(crit, applied);
        flinch = 6;
        flinchDir = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
    }

    /**
     * Called for every attack the local player makes.
     * @return true when the dummy was hit - the real attack is then cancelled (no packet to the server)
     */
    public static boolean onAttack(PlayerEntity attacker, Entity target) {
        FakePlayer m = INSTANCE;
        if (m == null || m.fake == null || target != m.fake) return false;
        m.hit(attacker);
        return true;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (fake == null || fake.getWorld() != mc.world) {
            fake = null;
            setEnabled(false);
            return;
        }
        if (!spawnedName.equals(cleanName())) { despawn(); spawn(); } // name changed in the GUI
        if (iframes > 0) iframes--;

        // a little flinch of the head, then back to normal - the body never moves
        if (flinch > 0) {
            float f = flinch / 6f;
            fake.setHeadYaw(fyaw + 24f * f * flinchDir);
            fake.setPitch(12f * f);
            flinch--;
        } else {
            fake.setHeadYaw(fyaw);
            fake.setPitch(0f);
        }
        fake.setBodyYaw(fyaw);

        // end crystals that vanish near the dummy = an explosion: apply vanilla explosion damage (power 6)
        Set<Integer> now = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity) { now.add(e.getId()); crystals.put(e.getId(), e.getPos()); }
        }
        Iterator<Map.Entry<Integer, Vec3d>> it = crystals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Vec3d> en = it.next();
            if (now.contains(en.getKey())) continue;
            double d = fake.getPos().distanceTo(en.getValue()) / 12.0;
            if (d <= 1.0) {
                double impact = 1.0 - d;
                hurt((float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0));
            }
            it.remove();
        }
        fake.setVelocity(Vec3d.ZERO); // never moves, never gets knockback
    }
}
