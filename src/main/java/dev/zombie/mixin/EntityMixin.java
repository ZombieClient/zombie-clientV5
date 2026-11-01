package dev.zombie.mixin;

import dev.zombie.ZombieClient;
import dev.zombie.hack.Hacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true)
    private void zombie$hitboxes(CallbackInfoReturnable<Float> cir) {
        if (ZombieClient.hacks() == null) return;
        Hacks.Hitboxes h = ZombieClient.hacks().get(Hacks.Hitboxes.class);
        Entity self = (Entity) (Object) this;
        if (h == null || !h.isEnabled() || self == MinecraftClient.getInstance().player) return;
        if (self instanceof PlayerEntity || (!h.playersOnly() && self instanceof LivingEntity)) {
            cir.setReturnValue(h.margin());
        }
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void zombie$esp(CallbackInfoReturnable<Boolean> cir) {
        if (ZombieClient.hacks() == null) return;
        Hacks.ESP esp = ZombieClient.hacks().get(Hacks.ESP.class);
        Entity self = (Entity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (esp == null || !esp.isEnabled() || mc.player == null || self == mc.player) return;
        if (esp.range() > 0 && self.distanceTo(mc.player) > esp.range()) return;
        if (self instanceof PlayerEntity
                || (esp.includeMobs() && self instanceof LivingEntity && !(self instanceof ArmorStandEntity))) {
            cir.setReturnValue(true);
        }
    }
}
