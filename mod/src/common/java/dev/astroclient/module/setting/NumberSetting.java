package dev.astroclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.math.MathHelper;

public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void set(Double value) {
        double snapped = Math.round(value / step) * step;
        this.value = MathHelper.clamp(snapped, min, max);
    }

    /** Sets the value from a 0..1 slider position. */
    public void setFraction(double fraction) {
        set(min + MathHelper.clamp(fraction, 0, 1) * (max - min));
    }

    public double getFraction() {
        return (value - min) / (max - min);
    }

    @Override
    public boolean isDefault() {
        return Math.abs(value - getDefault()) < 1e-9;
    }

    public String format() {
        return step >= 1 ? String.valueOf(Math.round(value)) : String.format("%.2f", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        set(json.getAsDouble());
    }
}
