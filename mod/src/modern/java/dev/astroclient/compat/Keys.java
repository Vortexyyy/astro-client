package dev.astroclient.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

/** Version shim (1.21.9+): key binding categories are registered objects. */
public final class Keys {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("astroclient", "main"));

    private Keys() {}

    public static KeyBinding register(String translationKey, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, CATEGORY));
    }

    public static boolean matches(KeyBinding binding, int key, int scancode, int modifiers) {
        return binding.matchesKey(new KeyInput(key, scancode, modifiers));
    }
}
