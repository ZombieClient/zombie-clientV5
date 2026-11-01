package dev.zombie.hack;

import dev.zombie.ZombieClient;
import dev.zombie.setting.BoolSetting;
import dev.zombie.setting.ModeSetting;
import dev.zombie.setting.NumberSetting;
import dev.zombie.util.Rotator;
import dev.zombie.util.SlotSwap;
import dev.zombie.util.Util;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** All modules live here as small nested classes. Each one declares its own sliders / toggles / modes. */
public final class Hacks {
    private Hacks() {}

    private static final String DEG = "\u00B0/tick";

    private static void attack(MinecraftClient mc, ClientPlayerEntity p, LivingEntity target) {
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
    }

    // ======================= COMBAT =======================

    public static class KillAura extends Hack {
        private final NumberSetting range = num("Range", 2.0, 6.0, 0.1, 4.0, " blocks");
        private final NumberSetting turn = num("Turn speed", 5, 180, 5, 60, DEG);
        private final NumberSetting smoothing = num("Smoothing", 0, 95, 5, 40, "%");
        private final ModeSetting priority = mode("Priority", 0, "Closest", "Lowest health", "Nearest crosshair");
        private final ModeSetting aimAt = mode("Aim at", 1, "Head", "Body", "Feet");
        private final NumberSetting tolerance = num("Aim tolerance", 2, 45, 1, 12, "\u00B0");
        private final NumberSetting charge = num("Attack charge", 50, 100, 5, 95, "%");
        private final BoolSetting sight = bool("Line of sight", true);
        private final BoolSetting playersOnly = bool("Players only", true);

        public KillAura() { super("KillAura", Category.COMBAT, GLFW.GLFW_KEY_R, "Auto-attacks nearby targets."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;
            LivingEntity t = Util.findTarget(mc, range.get(), playersOnly.get(), sight.get(), priority.index());
            if (t == null) return;

            float[] rot = Util.rotationsTo(p.getEyePos(), Util.aimPoint(t, aimAt.index()));
            Rotator.aim(rot[0], rot[1], turn.f(), smoothing.f());

            // swing only once the camera has turned close enough and the weapon is charged
            if (Rotator.error(p) <= tolerance.get() && p.getAttackCooldownProgress(0.5f) >= charge.f() / 100f) {
                attack(mc, p, t);
            }
        }
    }

    public static class TriggerBot extends Hack {
        private final NumberSetting charge = num("Attack charge", 50, 100, 5, 95, "%");
        private final NumberSetting delay = num("Reaction delay", 0, 10, 1, 0, " ticks");
        private final BoolSetting critOnly = bool("Crits only", false);
        private final BoolSetting playersOnly = bool("Players only", true);
        private int onTarget;

        public TriggerBot() { super("TriggerBot", Category.COMBAT, -1, "Attacks what you aim at."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;
            LivingEntity target = null;
            if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le
                    && (!playersOnly.get() || le instanceof PlayerEntity)) {
                target = le;
            }
            if (target == null) { onTarget = 0; return; }
            if (++onTarget <= delay.i()) return;
            if (p.getAttackCooldownProgress(0.5f) < charge.f() / 100f) return;
            if (critOnly.get() && (p.isOnGround() || p.fallDistance <= 0)) return;
            attack(mc, p, target);
        }
    }

    public static class AimAssist extends Hack {
        private final NumberSetting range = num("Range", 3, 8, 0.5, 6, " blocks");
        private final NumberSetting speed = num("Speed", 1, 30, 1, 6, DEG);
        private final NumberSetting smoothing = num("Smoothing", 0, 95, 5, 50, "%");
        private final NumberSetting fov = num("Field of view", 10, 180, 5, 70, "\u00B0");
        private final NumberSetting vertical = num("Vertical pull", 0, 100, 5, 75, "%");
        private final ModeSetting aimAt = mode("Aim at", 1, "Head", "Body", "Feet");
        private final BoolSetting needClick = bool("Only while attacking", false);
        private final BoolSetting sight = bool("Line of sight", true);
        private final BoolSetting playersOnly = bool("Players only", true);

        public AimAssist() { super("AimAssist", Category.COMBAT, -1, "Gently steers aim to targets."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;
            if (needClick.get() && !mc.options.attackKey.isPressed()) return;
            LivingEntity t = Util.findTarget(mc, range.get(), playersOnly.get(), sight.get(), 2);
            if (t == null) return;
            float[] rot = Util.rotationsTo(p.getEyePos(), Util.aimPoint(t, aimAt.index()));
            float dYaw = net.minecraft.util.math.MathHelper.wrapDegrees(rot[0] - p.getYaw());
            if (Math.abs(dYaw) > fov.get() / 2.0) return;
            float pitch = p.getPitch() + (rot[1] - p.getPitch()) * vertical.f() / 100f;
            Rotator.aim(p.getYaw() + dYaw, pitch, speed.f(), smoothing.f());
        }
    }

    /**
     * Raises the client-side reach. A server only honours it if the server's own range allows it
     * (on your own server: /attribute <you> minecraft:entity_interaction_range base set 8).
     */
    public static class Reach extends Hack {
        private final NumberSetting entityReach = num("Attack reach", 3, 8, 0.1, 4.5, " blocks");
        private final NumberSetting blockReach = num("Block reach", 4.5, 8, 0.1, 5.0, " blocks");

        public Reach() { super("Reach", Category.COMBAT, -1, "Longer attack and block reach."); }

        @Override public void onTick() { apply(entityReach.get(), blockReach.get()); }
        @Override protected void onDisable() { apply(3.0, 4.5); }

        private void apply(double entity, double block) {
            if (mc.player == null) return;
            set(EntityAttributes.ENTITY_INTERACTION_RANGE, entity);
            set(EntityAttributes.BLOCK_INTERACTION_RANGE, block);
        }

        private void set(RegistryEntry<EntityAttribute> attribute, double value) {
            EntityAttributeInstance inst = mc.player.getAttributeInstance(attribute);
            if (inst != null) inst.setBaseValue(value);
        }
    }

    /** The work is done in EntityMixin. */
    public static class Hitboxes extends Hack {
        private final NumberSetting expand = num("Expand", 0.0, 1.0, 0.05, 0.3, " blocks");
        private final BoolSetting playersOnly = bool("Players only", true);

        public Hitboxes() { super("Hitboxes", Category.COMBAT, -1, "Bigger target hitboxes."); }

        public float margin() { return expand.f(); }
        public boolean playersOnly() { return playersOnly.get(); }
    }

    public static class Criticals extends Hack {
        public Criticals() {
            super("Criticals", Category.COMBAT, -1, "Landing hits count as crits.");
            AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
                if (isEnabled() && player == mc.player && player.isOnGround()
                        && !player.isTouchingWater() && entity instanceof LivingEntity) {
                    double x = player.getX(), y = player.getY(), z = player.getZ();
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
                }
                return ActionResult.PASS;
            });
        }
    }

    /** The work is done in ClientPlayNetworkHandlerMixin. 0% on both = no knockback at all. */
    public static class Velocity extends Hack {
        private final NumberSetting horizontal = num("Horizontal", 0, 100, 5, 0, "%");
        private final NumberSetting vertical = num("Vertical", 0, 100, 5, 0, "%");

        public Velocity() { super("Velocity", Category.COMBAT, -1, "Reduces knockback."); }

        public double hScale() { return horizontal.get() / 100.0; }
        public double vScale() { return vertical.get() / 100.0; }
    }

    public static class AutoTotem extends Hack {
        private final NumberSetting delay = num("Delay", 0, 10, 1, 0, " ticks");
        private final NumberSetting health = num("Only below health", 2, 20, 1, 20, " HP");
        private int waited;

        public AutoTotem() { super("AutoTotem", Category.COMBAT, -1, "Keeps a totem in your offhand."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;
            if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getHealth() > health.get()) { waited = 0; return; }
            if (waited < delay.i()) { waited++; return; }
            for (int i = 0; i < 36; i++) {
                if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int slot = i < 9 ? 36 + i : i; // hotbar slots sit at 36-44 in the player screen handler
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                    waited = 0;
                    return;
                }
            }
        }
    }

    // ======================= MOVEMENT =======================

    public static class Fly extends Hack {
        private final NumberSetting speed = num("Speed", 5, 60, 1, 15, " b/s");
        private final NumberSetting vertical = num("Up/down speed", 2, 30, 1, 10, " b/s");
        private final NumberSetting smoothing = num("Smoothing", 0, 90, 5, 30, "%");
        private final NumberSetting sprintBoost = num("Sprint boost", 1.0, 3.0, 0.1, 1.0, "x");
        private final BoolSetting hover = bool("Hover", true);

        public Fly() { super("Fly", Category.MOVEMENT, GLFW.GLFW_KEY_G, "Free flight. Space/Shift = up/down."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            double sp = speed.get() / 20.0;
            if (mc.options.sprintKey.isPressed()) sp *= sprintBoost.get();
            Vec3d target = Util.moveInput(mc, sp);

            double vs = vertical.get() / 20.0;
            boolean up = mc.options.jumpKey.isPressed(), down = mc.options.sneakKey.isPressed();
            Vec3d v = p.getVelocity();
            double ty = (up || down) ? (up ? vs : 0) - (down ? vs : 0) : (hover.get() ? 0 : v.y);

            double a = 1.0 - smoothing.get() / 100.0;
            p.setVelocity(Util.lerp(v.x, target.x, a), Util.lerp(v.y, ty, a), Util.lerp(v.z, target.z, a));
        }
    }

    public static class Speed extends Hack {
        private final NumberSetting speed = num("Speed", 5, 40, 1, 10, " b/s");
        private final NumberSetting smoothing = num("Smoothing", 0, 90, 5, 20, "%");
        private final BoolSetting autoJump = bool("Auto jump", false);
        private final BoolSetting air = bool("Works in air", true);

        public Speed() { super("Speed", Category.MOVEMENT, GLFW.GLFW_KEY_X, "Move faster."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            Vec3d target = Util.moveInput(mc, speed.get() / 20.0);
            if (target == Vec3d.ZERO) return;
            Vec3d v = p.getVelocity();
            double vy = v.y;
            if (autoJump.get() && p.isOnGround() && !mc.options.sneakKey.isPressed()) vy = 0.42;
            if (!air.get() && !p.isOnGround()) return;
            double a = 1.0 - smoothing.get() / 100.0;
            p.setVelocity(Util.lerp(v.x, target.x, a), vy, Util.lerp(v.z, target.z, a));
        }
    }

    public static class NoFall extends Hack {
        private final NumberSetting minFall = num("Trigger height", 2, 10, 1, 3, " blocks");

        public NoFall() { super("NoFall", Category.MOVEMENT, -1, "Cancels fall damage."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (p.fallDistance > minFall.get() && !p.isGliding()) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
            }
        }
    }

    public static class Sprint extends Hack {
        private final BoolSetting omni = bool("All directions", false);

        public Sprint() { super("Sprint", Category.MOVEMENT, -1, "Always sprint while moving."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            boolean moving = omni.get() ? Util.moveInput(mc, 1.0) != Vec3d.ZERO : mc.options.forwardKey.isPressed();
            if (moving && !p.isSneaking() && !p.horizontalCollision && p.getHungerManager().getFoodLevel() > 6) {
                p.setSprinting(true);
            }
        }
    }

    // ======================= RENDER =======================

    public static class Fullbright extends Hack {
        public Fullbright() { super("Fullbright", Category.RENDER, GLFW.GLFW_KEY_B, "See in the dark."); }

        @Override public void onTick() {
            StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 240) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
            }
        }

        @Override protected void onDisable() {
            if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    /** Glowing outline is forced in EntityMixin. */
    public static class ESP extends Hack {
        private final NumberSetting range = num("Range (0=any)", 0, 128, 8, 0, "m");
        private final BoolSetting mobs = bool("Include mobs", false);

        public ESP() { super("ESP", Category.RENDER, GLFW.GLFW_KEY_H, "Glowing outlines through walls."); }

        public double range() { return range.get(); }
        public boolean includeMobs() { return mobs.get(); }
    }

    // ======================= MACE =======================

    /** True while one of our own scripted attacks runs, so MaceSwap doesn't interfere with it. */
    private static boolean scripted;

    private static void scriptedAttack(MinecraftClient mc, ClientPlayerEntity p, LivingEntity target) {
        scripted = true;
        try {
            attack(mc, p, target);
        } finally {
            scripted = false;
        }
    }

    /** Shared "look down, (jump,) wind charge at your feet" routine used by AutoLunge and MaceBomb. */
    static final class Launch {
        private int stage, wait, cooldown;

        boolean busy() { return stage != 0; }

        void tick(MinecraftClient mc, boolean want, float turn, float smoothing, int cooldownTicks, boolean jump) {
            ClientPlayerEntity p = mc.player;
            if (cooldown > 0) cooldown--;
            int slot = Util.findHotbar(p, s -> s.isOf(Items.WIND_CHARGE));
            switch (stage) {
                case 0 -> {
                    if (want && cooldown == 0 && p.isOnGround() && slot >= 0) stage = 1;
                }
                case 1 -> {
                    if (!want || !p.isOnGround() || slot < 0) { stage = 0; return; }
                    Rotator.aim(p.getYaw(), 90f, turn, smoothing);
                    if (p.getPitch() >= 88f) {
                        if (jump) {
                            Vec3d v = p.getVelocity();
                            p.setVelocity(v.x, 0.42, v.z); // a normal jump
                            stage = 2;
                            wait = 0;
                        } else {
                            throwCharge(mc, p, slot, cooldownTicks);
                        }
                    }
                }
                case 2 -> {
                    if (slot < 0) { stage = 0; return; }
                    if (!p.isOnGround()) throwCharge(mc, p, slot, cooldownTicks);
                    else if (++wait > 6) stage = 0;
                }
                default -> stage = 0;
            }
        }

        private void throwCharge(MinecraftClient mc, ClientPlayerEntity p, int slot, int cooldownTicks) {
            Rotator.snap(p, p.getYaw(), 90f);
            SlotSwap.select(p, slot);
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.swingHand(Hand.MAIN_HAND);
            cooldown = cooldownTicks;
            stage = 0;
        }
    }

    /** While falling, swaps to your mace for the hit, then swaps back. Works with KillAura and manual hits. */
    public static class MaceSwap extends Hack {
        private final NumberSetting minFall = num("Min fall", 0.5, 10, 0.5, 1.5, " blocks");
        private final BoolSetting swapBack = bool("Swap back", true);
        private final BoolSetting playersOnly = bool("Players only", true);

        public MaceSwap() {
            super("MaceSwap", Category.MACE, -1, "Swaps to the mace mid-fall.");
            AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
                if (!isEnabled() || scripted || player != mc.player || !(entity instanceof LivingEntity)) return ActionResult.PASS;
                if (playersOnly.get() && !(entity instanceof PlayerEntity)) return ActionResult.PASS;
                if (player.isOnGround() || player.fallDistance < minFall.get()) return ActionResult.PASS;
                if (player.getMainHandStack().isOf(Items.MACE)) return ActionResult.PASS;
                int slot = Util.findHotbar(player, s -> s.isOf(Items.MACE));
                if (slot < 0) return ActionResult.PASS;
                if (swapBack.get()) SlotSwap.select(player, slot);
                else player.getInventory().setSelectedSlot(slot);
                return ActionResult.PASS;
            });
        }
    }

    /** Looks down, jumps and throws a wind charge at your feet to launch toward a nearby target. */
    public static class AutoLunge extends Hack {
        private final NumberSetting range = num("Trigger range", 4, 30, 1, 14, " blocks");
        private final NumberSetting minRange = num("Min range", 2, 10, 1, 4, " blocks");
        private final NumberSetting turn = num("Turn speed", 5, 90, 5, 40, DEG);
        private final NumberSetting smoothing = num("Smoothing", 0, 95, 5, 30, "%");
        private final NumberSetting cooldown = num("Cooldown", 10, 100, 5, 40, " ticks");
        private final BoolSetting jump = bool("Jump first", true);
        private final BoolSetting playersOnly = bool("Players only", true);
        private final Launch launch = new Launch();

        public AutoLunge() { super("AutoLunge", Category.MACE, -1, "Wind-charges you toward foes."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;
            LivingEntity t = Util.findTarget(mc, range.get(), playersOnly.get(), false, 0);
            boolean want = t != null && p.distanceTo(t) >= minRange.get();
            launch.tick(mc, want, turn.f(), smoothing.f(), cooldown.i(), jump.get());
            if (!launch.busy() && want && !p.isOnGround()) {
                float[] rot = Util.rotationsTo(p.getEyePos(), t.getBoundingBox().getCenter());
                Rotator.aim(rot[0], rot[1], turn.f(), smoothing.f()); // look at them on the way up
            }
        }
    }

    /** The full routine: launch, home in on the target from above, dive and smash. */
    public static class MaceBomb extends Hack {
        private final NumberSetting range = num("Target range", 5, 30, 1, 16, " blocks");
        private final NumberSetting steer = num("Steering", 0, 100, 5, 60, "%");
        private final NumberSetting steerSpeed = num("Steer speed", 5, 30, 1, 12, " b/s");
        private final NumberSetting dive = num("Dive speed", 0, 60, 5, 0, " b/s");
        private final NumberSetting predict = num("Prediction", 0, 10, 1, 3, " ticks");
        private final NumberSetting turn = num("Turn speed", 5, 90, 5, 45, DEG);
        private final NumberSetting smoothing = num("Smoothing", 0, 95, 5, 30, "%");
        private final NumberSetting minFall = num("Min fall", 1.5, 30, 0.5, 3, " blocks");
        private final NumberSetting strike = num("Strike range", 2.5, 5, 0.1, 3.2, " blocks");
        private final BoolSetting autoLaunch = bool("Auto launch", true);
        private final BoolSetting playersOnly = bool("Players only", true);
        private final Launch launch = new Launch();
        private int strikeCooldown;
        private LivingEntity lastTarget;
        private Vec3d lastPos, vel = Vec3d.ZERO;

        public MaceBomb() { super("MaceBomb", Category.MACE, -1, "Launch, steer and smash a target."); }

        @Override protected void onDisable() { strikeCooldown = 0; lastTarget = null; }

        /** Estimates how fast the target is moving (blocks per tick) so we can aim where they're heading. */
        private void track(LivingEntity t) {
            if (t == null) { lastTarget = null; lastPos = null; vel = Vec3d.ZERO; return; }
            Vec3d pos = new Vec3d(t.getX(), t.getY(), t.getZ());
            vel = (t == lastTarget && lastPos != null) ? pos.subtract(lastPos) : Vec3d.ZERO;
            lastTarget = t;
            lastPos = pos;
        }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;
            if (strikeCooldown > 0) strikeCooldown--;

            LivingEntity t = Util.findTarget(mc, range.get(), playersOnly.get(), false, 0);
            track(t);
            launch.tick(mc, autoLaunch.get() && t != null, turn.f(), smoothing.f(), 40, true);
            if (launch.busy() || t == null || p.isOnGround() || p.isGliding()) return;

            Vec3d aim = t.getBoundingBox().getCenter().add(vel.multiply(predict.get()));
            float[] rot = Util.rotationsTo(p.getEyePos(), aim);
            Rotator.aim(rot[0], rot[1], turn.f(), smoothing.f());

            Vec3d v = p.getVelocity();
            if (v.y > -0.05) return; // steer and strike only on the way down

            double dx = aim.x - p.getX(), dz = aim.z - p.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > 0.3) {
                double sp = Math.min(steerSpeed.get() / 20.0, dist);
                double a = steer.get() / 100.0;
                p.setVelocity(Util.lerp(v.x, dx / dist * sp, a), v.y, Util.lerp(v.z, dz / dist * sp, a));
                v = p.getVelocity();
            }
            if (dive.get() > 0) {
                double want = -dive.get() / 20.0;
                if (v.y > want) p.setVelocity(v.x, Util.lerp(v.y, want, 0.4), v.z);
            }

            // smash (let StunSlam handle blocking targets when it's on)
            StunSlam stun = ZombieClient.hacks().get(StunSlam.class);
            boolean deferToStun = stun != null && stun.isEnabled() && t instanceof PlayerEntity pl && pl.isBlocking();
            if (!deferToStun && strikeCooldown == 0 && p.fallDistance >= minFall.get() && p.distanceTo(t) <= strike.get()) {
                int slot = Util.findHotbar(p, s -> s.isOf(Items.MACE));
                if (slot >= 0) {
                    SlotSwap.select(p, slot);
                    scriptedAttack(mc, p, t);
                    strikeCooldown = 10;
                }
            }
        }
    }

    /** Axe the shield off a blocking player, then follow up with the mace. */
    public static class StunSlam extends Hack {
        private final NumberSetting range = num("Range", 2.5, 5, 0.1, 3.5, " blocks");
        private final NumberSetting delay = num("Mace delay", 1, 10, 1, 2, " ticks");
        private final NumberSetting minFall = num("Min fall", 0, 10, 0.5, 1.5, " blocks");
        private final NumberSetting turn = num("Turn speed", 5, 90, 5, 50, DEG);
        private final NumberSetting smoothing = num("Smoothing", 0, 95, 5, 30, "%");
        private final NumberSetting tolerance = num("Aim tolerance", 2, 45, 1, 15, "\u00B0");
        private int stage, timer, lockout;
        private LivingEntity locked;

        public StunSlam() { super("StunSlam", Category.MACE, -1, "Axe the shield, then mace slam."); }

        @Override protected void onDisable() { stage = 0; lockout = 0; }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;

            if (stage == 1) {
                if (locked == null || !locked.isAlive() || p.distanceTo(locked) > range.get() + 1.5) { stage = 0; return; }
                float[] rot = Util.rotationsTo(p.getEyePos(), locked.getBoundingBox().getCenter());
                Rotator.aim(rot[0], rot[1], turn.f(), smoothing.f());
                if (--timer <= 0) {
                    int mace = Util.findHotbar(p, s -> s.isOf(Items.MACE));
                    if (mace >= 0) {
                        SlotSwap.select(p, mace);
                        scriptedAttack(mc, p, locked);
                    }
                    stage = 0;
                    lockout = 15;
                }
                return;
            }

            if (lockout > 0) { lockout--; return; }
            LivingEntity t = Util.findTarget(mc, range.get(), true, false, 0);
            if (!(t instanceof PlayerEntity pl) || !pl.isBlocking()) return;
            if (p.fallDistance < minFall.get()) return;
            int axe = Util.findHotbar(p, s -> s.isIn(ItemTags.AXES));
            int mace = Util.findHotbar(p, s -> s.isOf(Items.MACE));
            if (axe < 0 || mace < 0) return;

            float[] rot = Util.rotationsTo(p.getEyePos(), t.getBoundingBox().getCenter());
            Rotator.aim(rot[0], rot[1], turn.f(), smoothing.f());
            if (Rotator.error(p) > tolerance.get()) return; // wait until facing them

            SlotSwap.select(p, axe);
            scriptedAttack(mc, p, t);
            locked = t;
            stage = 1;
            timer = delay.i();
        }
    }

    /** Throws an ender pearl straight up, then a wind charge a moment later so it hits the pearl in the air. */
    public static class PearlCatch extends Hack {
        private final NumberSetting turn = num("Turn speed", 10, 90, 5, 45, DEG);
        private final NumberSetting smoothing = num("Smoothing", 0, 95, 5, 30, "%");
        private final NumberSetting delay = num("Charge delay", 1, 12, 1, 4, " ticks");
        private final NumberSetting cooldown = num("Cooldown", 20, 200, 5, 60, " ticks");
        private final BoolSetting lookBack = bool("Look back after", true);
        private final BoolSetting repeat = bool("Repeat", false);
        private int stage, timer, cd;
        private float startPitch;

        public PearlCatch() { super("PearlCatch", Category.MACE, -1, "Pearl up, then wind charge it."); }

        @Override protected void onEnable() { stage = 0; cd = 0; }
        @Override protected void onDisable() { stage = 0; }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (mc.currentScreen != null) return;

            int pearl = Util.findHotbar(p, s -> s.isOf(Items.ENDER_PEARL));
            int wind = Util.findHotbar(p, s -> s.isOf(Items.WIND_CHARGE));
            if (stage == 0 && (pearl < 0 || wind < 0)) {
                p.sendMessage(Text.literal("PearlCatch: put an ender pearl and a wind charge in your hotbar"), true);
                setEnabled(false);
                return;
            }

            switch (stage) {
                case 0 -> {
                    if (cd > 0) { cd--; return; }
                    startPitch = p.getPitch();
                    stage = 1;
                }
                case 1 -> { // turn to look straight up, then throw the pearl
                    Rotator.aim(p.getYaw(), -90f, turn.f(), smoothing.f());
                    if (p.getPitch() <= -88f) {
                        Rotator.snap(p, p.getYaw(), -90f);
                        throwItem(p, pearl);
                        timer = delay.i();
                        stage = 2;
                    }
                }
                case 2 -> { // wait, then wind charge it
                    Rotator.snap(p, p.getYaw(), -90f);
                    if (--timer <= 0) {
                        if (wind >= 0) throwItem(p, wind);
                        stage = 3;
                    }
                }
                case 3 -> { // look back where you were
                    if (lookBack.get()) Rotator.aim(p.getYaw(), startPitch, turn.f(), smoothing.f());
                    if (!lookBack.get() || Math.abs(p.getPitch() - startPitch) < 2f) {
                        stage = 0;
                        cd = cooldown.i();
                        if (!repeat.get()) setEnabled(false);
                    }
                }
                default -> stage = 0;
            }
        }

        private void throwItem(ClientPlayerEntity p, int slot) {
            SlotSwap.select(p, slot);
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Throws a wind charge straight down just before a big fall ends. */
    public static class WindClutch extends Hack {
        private final NumberSetting minFall = num("Min fall", 5, 50, 1, 12, " blocks");
        private final NumberSetting height = num("Trigger height", 1, 6, 0.5, 3, " blocks");
        private final NumberSetting cooldown = num("Cooldown", 10, 60, 5, 20, " ticks");
        private int cd;

        public WindClutch() { super("WindClutch", Category.MACE, -1, "Wind charge softens big falls."); }

        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            if (cd > 0) { cd--; return; }
            if (p.isOnGround() || p.fallDistance < minFall.get() || p.getVelocity().y > -0.3) return;
            int slot = Util.findHotbar(p, s -> s.isOf(Items.WIND_CHARGE));
            if (slot < 0 || !groundWithin(p, height.get())) return;
            Rotator.snap(p, p.getYaw(), 90f); // must point down right now
            SlotSwap.select(p, slot);
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.swingHand(Hand.MAIN_HAND);
            cd = cooldown.i();
        }

        private boolean groundWithin(ClientPlayerEntity p, double h) {
            BlockPos base = p.getBlockPos();
            for (int i = 0; i <= (int) Math.ceil(h) + 1; i++) {
                BlockPos b = base.down(i);
                if (!mc.world.getBlockState(b).isAir()) return p.getY() - (b.getY() + 1.0) <= h;
            }
            return false;
        }
    }

    /** Shows your fall height and the extra damage a mace hit would deal right now. */
    public static class MaceHud extends Hack {
        private final BoolSetting showTarget = bool("Show target health", true);

        public MaceHud() { super("MaceHud", Category.MACE, -1, "Shows fall height and smash damage."); }

        @Override public String hudInfo() {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null) return null;
            double fall = Math.max(0.0, p.fallDistance);
            double bonus = fall <= 1.5 ? 0.0
                    : Math.min(fall, 3) * 4 + Math.min(Math.max(fall - 3, 0), 5) * 2 + Math.max(fall - 8, 0);
            String line = String.format(Locale.ROOT, "Fall %.1f  |  Smash +%.1f", fall, bonus);
            if (showTarget.get()) {
                LivingEntity t = Util.findTarget(mc, 8, true, false, 0);
                if (t != null) line += String.format(Locale.ROOT, "  |  %s %.1f HP", t.getName().getString(), t.getHealth());
            }
            return line;
        }
    }
}
