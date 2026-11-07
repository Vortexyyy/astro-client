package dev.astroclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One of a fixed list of named options; clicking it in the mod menu cycles to the next one. */
public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = List.of(modes);
    }

    public List<String> modes() {
        return modes;
    }

    public void cycle() {
        value = modes.get((modes.indexOf(value) + 1) % modes.size());
    }

    public void cycleBack() {
        value = modes.get((modes.indexOf(value) - 1 + modes.size()) % modes.size());
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        String v = json.getAsString();
        if (modes.contains(v)) value = v;
    }
}
