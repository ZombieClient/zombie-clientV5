package dev.zombie.hack;

import dev.zombie.util.SlotSwap;

import java.util.ArrayList;
import java.util.List;

public class HackManager {
    private final List<Hack> hacks = new ArrayList<>();

    public HackManager() {
        hacks.add(new Hacks.KillAura());
        hacks.add(new Hacks.TriggerBot());
        hacks.add(new Hacks.AimAssist());
        hacks.add(new Hacks.Reach());
        hacks.add(new Hacks.Hitboxes());
        hacks.add(new Hacks.Criticals());
        hacks.add(new Hacks.Velocity());
        hacks.add(new Hacks.AutoTotem());
        hacks.add(new Hacks.Fly());
        hacks.add(new Hacks.Speed());
        hacks.add(new Hacks.NoFall());
        hacks.add(new Hacks.Sprint());
        hacks.add(new Hacks.Fullbright());
        hacks.add(new Hacks.ESP());
        hacks.add(new Hacks.MaceSwap());
        hacks.add(new Hacks.AutoLunge());
        hacks.add(new Hacks.MaceBomb());
        hacks.add(new Hacks.StunSlam());
        hacks.add(new Hacks.PearlCatch());
        hacks.add(new Hacks.WindClutch());
        hacks.add(new Hacks.MaceHud());
    }

    public List<Hack> all() { return hacks; }

    @SuppressWarnings("unchecked")
    public <T extends Hack> T get(Class<T> type) {
        for (Hack h : hacks) if (type.isInstance(h)) return (T) h;
        return null;
    }

    public void tick() {
        for (Hack h : hacks) {
            while (h.binding != null && h.binding.wasPressed()) h.toggle();
            if (h.isEnabled()) h.onTick();
        }
        SlotSwap.tick(net.minecraft.client.MinecraftClient.getInstance().player);
    }
}
