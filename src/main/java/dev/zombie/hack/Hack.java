package dev.zombie.hack;

import dev.zombie.setting.BoolSetting;
import dev.zombie.setting.ModeSetting;
import dev.zombie.setting.NumberSetting;
import dev.zombie.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.List;

public abstract class Hack {
    public enum Category {
        COMBAT("Combat"), MACE("Mace"), MOVEMENT("Movement"), RENDER("Render");

        public final String label;
        Category(String label) { this.label = label; }
    }

    protected final MinecraftClient mc = MinecraftClient.getInstance();
    public final String name;
    public final String description;
    public final Category category;
    public final int key; // default GLFW key, -1 = unbound
    public KeyBinding binding; // set at startup, rebindable in Controls

    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;

    protected Hack(String name, Category category, int key, String description) {
        this.name = name;
        this.category = category;
        this.key = key;
        this.description = description;
    }

    public List<Setting> settings() { return settings; }

    protected NumberSetting num(String name, double min, double max, double step, double def, String unit) {
        NumberSetting s = new NumberSetting(name, min, max, step, def, unit);
        settings.add(s);
        return s;
    }

    protected BoolSetting bool(String name, boolean def) {
        BoolSetting s = new BoolSetting(name, def);
        settings.add(s);
        return s;
    }

    protected ModeSetting mode(String name, int def, String... options) {
        ModeSetting s = new ModeSetting(name, def, options);
        settings.add(s);
        return s;
    }

    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}

    /** Optional line shown under the crosshair while the module is on. */
    public String hudInfo() { return null; }
}
