package dev.astroclient.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an option's value directly, bypassing its validator (Fullbright needs brightness above 100%). */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("value")
    void astroclient$setRawValue(Object value);
}
