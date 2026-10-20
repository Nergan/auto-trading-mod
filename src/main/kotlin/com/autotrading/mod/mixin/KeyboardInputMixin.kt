package com.autotrading.mod.mixin

import com.autotrading.mod.client.Session
import net.minecraft.client.player.KeyboardInput
import net.minecraft.world.phys.Vec2
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(KeyboardInput::class)
abstract class KeyboardInputMixin {
    @Inject(method = ["tick"], at = [At("TAIL")])
    private fun autotradingMove(callback: CallbackInfo) {
        val access = this as ClientInputAccessor
        val forced = Session.overrideInput(access.getKeyPresses()) ?: return
        access.setKeyPresses(forced)
        val forward = if (forced.forward()) 1f else 0f
        access.setMoveVector(if (forward == 0f) Vec2.ZERO else Vec2(0f, forward))
    }
}
