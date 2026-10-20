package com.autotrading.mod.mixin

import com.autotrading.mod.client.Settings
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
abstract class MinecraftMixin {
    /** Focus loss opens the pause menu after 500 ms. Esc still calls pauseGame directly. */
    @Inject(method = ["pauseIfInactive"], at = [At("HEAD")], cancellable = true)
    private fun autotradingNoPause(callback: CallbackInfo) {
        if (Settings.enabled && !Minecraft.getInstance().isWindowActive) callback.cancel()
    }
}
