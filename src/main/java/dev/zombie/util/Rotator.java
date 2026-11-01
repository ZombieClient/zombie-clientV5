package dev.zombie.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Smooth camera turning. Modules call aim() every tick with a target; the camera then eases toward it
 * on every rendered frame (not just every 20th of a second), so movement looks fluid.
 */
public final class Rotator {
    private static float targetYaw, targetPitch, maxStep, ease;
    private static int ttl;
    private static long last;

    private Rotator() {}

    /** @param maxDegPerTick fastest allowed turn, @param smoothing 0 = snap-fast, 95 = very gentle */
    public static void aim(float yaw, float pitch, float maxDegPerTick, float smoothing) {
        targetYaw = yaw;
        targetPitch = MathHelper.clamp(pitch, -90f, 90f);
        maxStep = maxDegPerTick;
        ease = (float) Util.lerp(1.0, 0.08, smoothing / 100.0);
        ttl = 3; // expires by itself if the module stops asking
    }

    /** Jumps straight to a rotation (used when something must happen this very tick). */
    public static void snap(ClientPlayerEntity p, float yaw, float pitch) {
        p.setYaw(yaw);
        p.setPitch(MathHelper.clamp(pitch, -90f, 90f));
        targetYaw = yaw;
        targetPitch = p.getPitch();
        ttl = 3;
    }

    /** Degrees still to turn to reach the current target (0 when nothing is being aimed). */
    public static float error(ClientPlayerEntity p) {
        if (ttl <= 0) return 0f;
        return Math.max(Math.abs(MathHelper.wrapDegrees(targetYaw - p.getYaw())), Math.abs(targetPitch - p.getPitch()));
    }

    /** Call once per client tick. */
    public static void tick() { if (ttl > 0) ttl--; }

    /** Call as often as possible (every frame and every tick). */
    public static void frame(MinecraftClient mc) {
        long now = System.nanoTime();
        double ticks = last == 0 ? 0 : Math.min((now - last) / 50_000_000.0, 2.0);
        last = now;
        ClientPlayerEntity p = mc.player;
        if (p == null || ttl <= 0 || ticks <= 0) return;

        double frac = 1.0 - Math.pow(1.0 - ease, ticks);
        float cap = (float) (maxStep * ticks);
        float creep = (float) (0.6 * ticks); // keep moving a little so it always arrives
        float dYaw = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dPitch = targetPitch - p.getPitch();
        float moveYaw = step(dYaw, frac, cap, creep);
        float movePitch = step(dPitch, frac, cap, creep);
        // changeLookDirection keeps the camera's previous-frame rotation in sync, like moving the mouse does
        p.changeLookDirection(moveYaw / 0.15, movePitch / 0.15);
    }

    private static float step(float diff, double frac, float cap, float creep) {
        float mag = (float) Math.abs(diff * frac);
        mag = Math.max(mag, Math.min(creep, Math.abs(diff)));
        mag = Math.min(mag, cap);
        mag = Math.min(mag, Math.abs(diff));
        return Math.copySign(mag, diff);
    }
}
