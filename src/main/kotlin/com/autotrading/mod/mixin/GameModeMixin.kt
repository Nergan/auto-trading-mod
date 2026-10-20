package com.autotrading.mod.mixin

import com.autotrading.mod.client.Session
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode::class)
abstract class GameModeMixin {
    @Inject(method = ["useItemOn"], at = [At("HEAD")])
    private fun rememberBlock(
        player: LocalPlayer,
        hand: InteractionHand,
        hit: BlockHitResult,
        callback: CallbackInfoReturnable<InteractionResult>,
    ) {
        Session.rememberBlock(hit.blockPos, hand, player)
    }

    @Inject(method = ["interact"], at = [At("HEAD")])
    private fun rememberEntity(
        player: Player,
        entity: Entity,
        hit: EntityHitResult,
        hand: InteractionHand,
        callback: CallbackInfoReturnable<InteractionResult>,
    ) {
        Session.rememberEntity(entity)
    }
}
