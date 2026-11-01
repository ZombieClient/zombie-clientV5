package dev.zombie.util;

import net.minecraft.entity.player.PlayerEntity;

/** Temporarily selects a hotbar slot (for one swing / throw) and puts the old slot back afterwards. */
public final class SlotSwap {
    private static int previous = -1;
    private static int ticks;

    private SlotSwap() {}

    public static void select(PlayerEntity p, int slot) {
        if (previous == -1) previous = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        ticks = 2; // held through this tick and the next, then restored
    }

    public static void tick(PlayerEntity p) {
        if (previous == -1 || p == null) return;
        if (--ticks > 0) return;
        p.getInventory().setSelectedSlot(previous);
        previous = -1;
        ticks = 0;
    }
}
