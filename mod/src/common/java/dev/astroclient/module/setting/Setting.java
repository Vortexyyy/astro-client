package dev.astroclient.module.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    private final String name;
    private final T defaultValue;
    protected T value;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T getDefault() {
        return defaultValue;
    }

    public boolean isDefault() {
        return java.util.Objects.equals(value, defaultValue);
    }

    /** Back to the value the module shipped with. */
    public void reset() {
        value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);
}
