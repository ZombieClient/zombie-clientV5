package dev.zombie.setting;

/** A setting that cycles through a fixed list of named options. */
public class ModeSetting extends Setting {
    public final String[] options;
    private final int def;
    private int index;

    public ModeSetting(String name, int def, String... options) {
        super(name);
        this.options = options;
        this.def = def;
        this.index = def;
    }

    public String get() { return options[index]; }
    public int index() { return index; }
    public void next() { index = (index + 1) % options.length; }

    public void set(String option) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(option)) { index = i; return; }
    }

    @Override public void reset() { index = def; }
}
