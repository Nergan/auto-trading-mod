package com.autotrading.mod.client

import com.autotrading.mod.logic.Cell
import com.autotrading.mod.logic.findPath
import com.autotrading.mod.logic.stand
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object Walker {
    data class Move(
        val forward: Boolean,
        val jump: Boolean,
        val sprint: Boolean,
        val yaw: Float,
        val pitch: Float,
        val arrived: Boolean,
    )

    private var path = emptyList<Cell>()
    private var index = 0
    private var goal: Cell? = null
    private var stuck = 0
    private var blind = 0
    private var lastX = 0.0
    private var lastZ = 0.0

    fun clear() {
        path = emptyList()
        index = 0
        goal = null
        stuck = 0
        blind = 0
    }

    /** Null means there is no path. [Move.arrived] means the target is in reach. */
    fun steer(level: Level, player: LocalPlayer, target: BlockPos, reach: Double): Move? {
        val aim = Vec3.atCenterOf(target)
        if (player.eyePosition.distanceTo(aim) <= reach) {
            clear()
            return look(player, aim, forward = false, jump = false, sprint = false, arrived = true)
        }
        val feet = feetCell(level, player)
        if (feet == null) {
            blind++
            if (blind > 20) return fail()
            return look(player, aim, forward = true, jump = true, sprint = false, arrived = false)
        }
        blind = 0
        val passable = open(level)
        val ground = floor(level)
        val standCell = nearestStand(level, target, reach) ?: return null
        val dest = if (manhattan(feet, standCell) > 24) hop(level, feet, standCell) ?: return null else standCell
        if (goal != dest) {
            val found = findPath(passable, ground, feet, dest) ?: return fail()
            if (found.isEmpty()) {
                goal = null
                return look(player, aim, forward = true, jump = false, sprint = false, arrived = false)
            }
            path = found
            index = 0
            goal = dest
            stuck = 0
            lastX = player.x
            lastZ = player.z
        }
        if (index >= path.size) {
            goal = null
            return look(player, aim, forward = false, jump = false, sprint = false, arrived = false)
        }
        val next = path[index]
        val flat = hypot(next.x + 0.5 - player.x, next.z + 0.5 - player.z)
        if (flat < 0.45 && kotlin.math.abs(player.blockY - next.y) <= 1) index++
        if (index >= path.size) {
            goal = null
            return look(player, aim, forward = false, jump = false, sprint = false, arrived = false)
        }
        val step = path[index]
        val moved = hypot(player.x - lastX, player.z - lastZ)
        if (moved < 0.04) stuck++ else stuck = 0
        lastX = player.x
        lastZ = player.z
        if (stuck > 30) return fail()
        val jump = step.y > feet.y
        val sprint = hypot(aim.x - player.x, aim.z - player.z) > 6
        return look(player, Vec3(step.x + 0.5, step.y + 0.2, step.z + 0.5), true, jump, sprint, false)
    }

    fun nudgeDoors(level: Level, player: LocalPlayer, tick: Int) {
        if (tick % 12 != 0) return
        val gameMode = Minecraft.getInstance().gameMode ?: return
        val yaw = Math.toRadians(player.yRot.toDouble())
        val ahead = BlockPos.containing(player.x - sin(yaw), player.y, player.z + kotlin.math.cos(yaw))
        for (pos in listOf(ahead, ahead.above(), ahead.below())) {
            val state = level.getBlockState(pos)
            if (!closedPassage(state)) continue
            val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
            gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
            return
        }
    }

    private fun feetCell(level: Level, player: LocalPlayer): Cell? {
        val open = open(level)
        val floor = floor(level)
        val x = player.blockX
        val y = player.blockY
        val z = player.blockZ
        for (dy in intArrayOf(0, 1, -1)) {
            val cell = Cell(x, y + dy, z)
            if (stand(open, floor, cell)) return cell
        }
        return null
    }

    private fun fail(): Move? {
        clear()
        return null
    }

    private fun hop(level: Level, from: Cell, to: Cell): Cell? {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val len = hypot(dx, dz).coerceAtLeast(1.0)
        val pos = BlockPos(from.x + (dx / len * 20).toInt(), from.y, from.z + (dz / len * 20).toInt())
        return nearestStand(level, pos, 4.0)
    }

    private fun nearestStand(level: Level, target: BlockPos, reach: Double): Cell? {
        val open = open(level)
        val floor = floor(level)
        val center = Vec3.atCenterOf(target)
        var best: Cell? = null
        var bestScore = Double.MAX_VALUE
        for (dx in -3..3) {
            for (dy in -3..3) {
                for (dz in -3..3) {
                    val cell = Cell(target.x + dx, target.y + dy, target.z + dz)
                    if (!stand(open, floor, cell)) continue
                    val spot = Vec3(cell.x + 0.5, cell.y + 1.62, cell.z + 0.5)
                    if (spot.distanceTo(center) > reach) continue
                    val score = spot.distanceToSqr(center)
                    if (score < bestScore) {
                        bestScore = score
                        best = cell
                    }
                }
            }
        }
        return best
    }

    private fun look(player: LocalPlayer, aim: Vec3, forward: Boolean, jump: Boolean, sprint: Boolean, arrived: Boolean): Move {
        val dx = aim.x - player.x
        val dz = aim.z - player.z
        val dy = aim.y - player.eyeY
        val yaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz)))).toFloat()
        return Move(forward, jump, sprint, yaw, pitch, arrived)
    }

    private fun manhattan(a: Cell, b: Cell) = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.z - b.z)

    private fun passage(state: BlockState) = state.block is DoorBlock || state.block is FenceGateBlock

    private fun closedPassage(state: BlockState): Boolean {
        val block = state.block
        return when (block) {
            is DoorBlock -> !state.getValue(DoorBlock.OPEN)
            is FenceGateBlock -> !state.getValue(FenceGateBlock.OPEN)
            else -> false
        }
    }

    private fun open(level: Level): (Int, Int, Int) -> Boolean = { x, y, z ->
        val pos = BlockPos(x, y, z)
        val state = level.getBlockState(pos)
        passage(state) || (state.fluidState.isEmpty && state.getCollisionShape(level, pos).isEmpty)
    }

    private fun floor(level: Level): (Int, Int, Int) -> Boolean = { x, y, z ->
        val pos = BlockPos(x, y, z)
        val state = level.getBlockState(pos)
        !passage(state) && state.fluidState.isEmpty && !state.getCollisionShape(level, pos).isEmpty
    }
}
