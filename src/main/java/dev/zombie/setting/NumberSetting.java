package dev.zombie.setting;

import java.util.Locale;

public class NumberSetting extends Setting {
    public final double min, max, step;
    public final String unit;
    private final double def;
    private double value;

    public NumberSetting(String name, double min, double max, double step, double def, String unit) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.def = def;
        this.unit = unit;
        set(def);
    }

    public double get() { return value; }
    public float f() { return (float) value; }
    public int i() { return (int) Math.round(value); }

    public final void set(double v) {
        v = Math.max(min, Math.min(max, v));
        v = min + Math.round((v - min) / step) * step;
        value = Math.max(min, Math.min(max, Math.round(v * 1000.0) / 1000.0));
    }

    @Override public void reset() { set(def); }

    public String display() {
        int decimals = step >= 1 ? 0 : (step >= 0.1 ? 1 : 2);
        return String.format(Locale.ROOT, "%." + decimals + "f", value) + unit;
    }
}
