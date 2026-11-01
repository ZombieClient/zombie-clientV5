package dev.zombie.setting;

public class BoolSetting extends Setting {
    private final boolean def;
    private boolean value;

    public BoolSetting(String name, boolean def) {
        super(name);
        this.def = def;
        this.value = def;
    }

    public boolean get() { return value; }
    public void set(boolean v) { value = v; }
    public void toggle() { value = !value; }

    @Override public void reset() { value = def; }
}
