package dev.astroclient.module;

import dev.astroclient.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private boolean pinned;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Called every client tick while enabled. */
    public void onTick() {}

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    /** Favourite in the mod menu: listed first and under the Favourites tab. */
    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    /** Name of the key this module is used with (e.g. "C" for Zoom), or null when it has none. */
    public String keyHint() {
        return null;
    }

    /** True when every setting still has the value the module shipped with. */
    public boolean hasDefaultSettings() {
        for (Setting<?> s : settings) if (!s.isDefault()) return false;
        return true;
    }

    public void resetSettings() {
        for (Setting<?> s : settings) s.reset();
    }
}
