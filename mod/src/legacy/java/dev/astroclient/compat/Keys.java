package dev.astroclient.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Version shim (1.21.1): key bindings use a translation-key string as their category. */
public final class Keys {
    private Keys() {}

    public static KeyBinding register(String translationKey, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, "category.astroclient"));
    }

    public static boolean matches(KeyBinding binding, int key, int scancode, int modifiers) {
        return binding.matchesKey(key, scancode);
    }
}
