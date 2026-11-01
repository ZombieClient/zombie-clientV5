package dev.zombie.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

public final class Util {
    private Util() {}

    /** First hotbar slot (0-8) holding a matching stack, or -1. */
    public static int findHotbar(PlayerEntity p, Predicate<ItemStack> match) {
        for (int i = 0; i < 9; i++) if (match.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    /** Turns the camera toward a yaw/pitch by at most `step` degrees. Returns the angle still left to turn. */
    public static float turnTo(ClientPlayerEntity p, float yaw, float pitch, float step) {
        float dYaw = MathHelper.wrapDegrees(yaw - p.getYaw());
        float dPitch = pitch - p.getPitch();
        float mYaw = MathHelper.clamp(dYaw, -step, step);
        float mPitch = MathHelper.clamp(dPitch, -step, step);
        p.setYaw(p.getYaw() + mYaw);
        p.setPitch(MathHelper.clamp(p.getPitch() + mPitch, -90f, 90f));
        return Math.max(Math.abs(dYaw - mYaw), Math.abs(dPitch - mPitch));
    }

    public static float turnToward(ClientPlayerEntity p, Vec3d point, float step) {
        float[] r = rotationsTo(p.getEyePos(), point);
        return turnTo(p, r[0], r[1], step);
    }

    public static double lerp(double from, double to, double t) { return from + (to - from) * t; }

    /**
     * Picks a target. sort: 0 = closest, 1 = lowest health, 2 = nearest to where you are looking.
     */
    public static LivingEntity findTarget(MinecraftClient mc, double range, boolean playersOnly, boolean needSight, int sort) {
        ClientPlayerEntity p = mc.player;
        LivingEntity best = null;
        double bestKey = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive()
                    || le.isSpectator() || le instanceof ArmorStandEntity) continue;
            if (playersOnly && !(le instanceof PlayerEntity)) continue;
            double d2 = p.squaredDistanceTo(le);
            if (d2 > range * range) continue;
            if (needSight && !p.canSee(le)) continue;
            double key = switch (sort) {
                case 1 -> le.getHealth();
                case 2 -> {
                    float[] r = rotationsTo(p.getEyePos(), le.getBoundingBox().getCenter());
                    yield Math.abs(MathHelper.wrapDegrees(r[0] - p.getYaw())) + Math.abs(r[1] - p.getPitch());
                }
                default -> d2;
            };
            if (key < bestKey) { bestKey = key; best = le; }
        }
        return best;
    }

    /** mode: 0 = head, 1 = body, 2 = feet */
    public static Vec3d aimPoint(LivingEntity e, int mode) {
        return switch (mode) {
            case 0 -> new Vec3d(e.getX(), e.getEyeY(), e.getZ());
            case 2 -> new Vec3d(e.getX(), e.getY() + 0.3, e.getZ());
            default -> e.getBoundingBox().getCenter();
        };
    }

    public static LivingEntity nearestTarget(MinecraftClient mc, double range, boolean playersOnly) {
        LivingEntity best = null;
        double bestDist = range * range;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()
                    || le.isSpectator() || le instanceof ArmorStandEntity) continue;
            if (playersOnly && !(le instanceof PlayerEntity)) continue;
            double d = mc.player.squaredDistanceTo(le);
            if (d < bestDist) { bestDist = d; best = le; }
        }
        return best;
    }

    /** Returns {yaw, pitch} needed to look from one point to another. */
    public static float[] rotationsTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        return new float[]{yaw, pitch};
    }

    /** Horizontal movement vector from WASD in the player's facing direction, or ZERO if no input. */
    public static Vec3d moveInput(MinecraftClient mc, double speed) {
        GameOptions o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(mc.player.getYaw());
        double x = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double z = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.sqrt(x * x + z * z);
        return new Vec3d(x / len * speed, 0, z / len * speed);
    }
}
