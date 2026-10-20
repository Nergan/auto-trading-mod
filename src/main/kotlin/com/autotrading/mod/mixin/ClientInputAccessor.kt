package com.autotrading.mod.mixin

import net.minecraft.client.player.ClientInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(ClientInput::class)
interface ClientInputAccessor {
    @Accessor(value = "keyPresses", remap = false)
    fun getKeyPresses(): Input

    @Accessor(value = "keyPresses", remap = false)
    fun setKeyPresses(value: Input)

    @Accessor(value = "moveVector", remap = false)
    fun setMoveVector(value: Vec2)
}
