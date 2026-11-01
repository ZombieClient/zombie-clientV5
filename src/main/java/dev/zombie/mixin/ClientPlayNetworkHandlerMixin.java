package dev.zombie.mixin;

import dev.zombie.ZombieClient;
import dev.zombie.hack.Hacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    /** 0% / 0%: ignore the knockback packet completely. */
    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void zombie$noKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        Hacks.Velocity v = active(packet);
        if (v != null && v.hScale() == 0.0 && v.vScale() == 0.0) ci.cancel();
    }

    /** Otherwise let it through, then scale it by the chosen percentages. */
    @Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
    private void zombie$scaleKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        Hacks.Velocity v = active(packet);
        if (v == null) return;
        var player = MinecraftClient.getInstance().player;
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x * v.hScale(), vel.y * v.vScale(), vel.z * v.hScale());
    }

    private static Hacks.Velocity active(EntityVelocityUpdateS2CPacket packet) {
        if (ZombieClient.hacks() == null) return null;
        Hacks.Velocity v = ZombieClient.hacks().get(Hacks.Velocity.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (v != null && v.isEnabled() && mc.player != null && packet.getEntityId() == mc.player.getId()) return v;
        return null;
    }
}
