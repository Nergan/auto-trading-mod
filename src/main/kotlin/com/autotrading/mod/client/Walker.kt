package com.autotrading.mod.client

import com.autotrading.mod.logic.Cell
import com.autotrading.mod.logic.findPath
import com.autotrading.mod.logic.stand
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.IronBarsBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.ScaffoldingBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.WallBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
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
    private var goalKey = BlockPos.ZERO
    private var stuck = 0
    private var blind = 0
    private var lastX = 0.0
    private var lastZ = 0.0
    private var aim = Vec3.ZERO
    private var nudgedPos = BlockPos.ZERO
    private var nudgedUntil = 0

    fun clear() {
        path = emptyList()
        index = 0
        goal = null
        goalKey = BlockPos.ZERO
        stuck = 0
        blind = 0
        nudgedUntil = 0
    }

    /**
     * Null means there is no path. [Move.arrived] means the target can already be clicked
     * from the current eyes, using the game's reach and the blocks actually in the way.
     */
    fun steer(level: Level, player: LocalPlayer, target: BlockPos, focus: Entity?): Move? {
        val click = aimAt(level, player.eyePosition, target, focus, player)
        if (click != null) {
            clear()
            aim = click
            return look(player, click, forward = false, jump = false, sprint = false, arrived = true)
        }
        val feet = feetCell(level, player)
        if (feet == null) {
            blind++
            if (blind > 20) return fail()
            val fallback = Vec3.atCenterOf(target)
            return look(player, fallback, forward = true, jump = false, sprint = false, arrived = false)
        }
        blind = 0
        if (goal == null || goalKey != target) {
            goal = approach(level, player, feet, target, focus) ?: return null
            goalKey = target
            path = emptyList()
            index = 0
        }
        val dest = goal ?: return null
        val passable = open(level)
        val ground = floor(level)
        val hop = if (manhattan(feet, dest) > 24) hopToward(level, feet, dest) else dest
        if (path.isEmpty() || index >= path.size) {
            val found = findPath(passable, ground, feet, hop) ?: return fail()
            if (found.isEmpty()) {
                stuck++
                if (stuck > 20) return fail()
                return look(player, Vec3.atCenterOf(target), forward = true, jump = false, sprint = false, arrived = false)
            }
            path = found
            index = 0
            stuck = 0
            lastX = player.x
            lastZ = player.z
        }
        val next = path[index]
        val flat = hypot(next.x + 0.5 - player.x, next.z + 0.5 - player.z)
        if (flat < 0.45 && player.blockY == next.y) index++
        if (index >= path.size) {
            path = emptyList()
            if (hop != dest) goal = null
            return look(player, Vec3.atCenterOf(target), forward = false, jump = false, sprint = false, arrived = false)
        }
        val step = path[index]
        val moved = hypot(player.x - lastX, player.z - lastZ)
        if (moved < 0.04) stuck++ else stuck = 0
        lastX = player.x
        lastZ = player.z
        if (stuck > 40) return fail()
        val jump = needsJump(level, feet, step)
        val distance = hypot(target.x + 0.5 - player.x, target.z + 0.5 - player.z)
        val sprint = distance > 8 && step.y == feet.y && !jump
        val stepPos = BlockPos(step.x, step.y, step.z)
        val door = closedPassage(level.getBlockState(stepPos))
        val eyeOffset = player.eyeY - player.y
        aim = if (door) Vec3.atCenterOf(stepPos) else Vec3(step.x + 0.5, surface(level, step) + eyeOffset, step.z + 0.5)
        if (door) nudge(player, stepPos)
        return look(player, aim, true, jump, sprint, false)
    }

    fun blockHit(level: Level, player: LocalPlayer, pos: BlockPos): BlockHitResult? {
        val point = aimAt(level, player.eyePosition, pos, null, player) ?: return null
        val hit = clip(level, player.eyePosition, point, player)
        if (hit.type != HitResult.Type.MISS && sameTarget(level, hit.blockPos, pos)) return hit
        val face = faceToward(player.eyePosition, pos)
        return BlockHitResult(point, face, pos, false)
    }

    fun nudgeDoors(level: Level, player: LocalPlayer, tick: Int) {
        if (tick % 12 != 0) return
        val yaw = Math.toRadians(player.yRot.toDouble())
        val ahead = BlockPos.containing(player.x - sin(yaw), player.y, player.z + kotlin.math.cos(yaw))
        for (pos in listOf(ahead, ahead.above(), ahead.below())) {
            if (!closedPassage(level.getBlockState(pos))) continue
            nudge(player, pos)
            return
        }
    }

    private fun approach(level: Level, player: LocalPlayer, feet: Cell, target: BlockPos, focus: Entity?): Cell? {
        val reach = reach(player, focus)
        val radius = kotlin.math.ceil(reach).toInt().coerceIn(2, 6)
        val passable = open(level)
        val ground = floor(level)
        val ranked = ArrayList<Cell>()
        for (dx in -radius..radius) {
            for (dy in -2..2) {
                for (dz in -radius..radius) {
                    val cell = Cell(target.x + dx, target.y + dy, target.z + dz)
                    if (focus != null && cell.x == focus.blockX && cell.z == focus.blockZ && kotlin.math.abs(cell.y - focus.blockY) <= 1) continue
                    if (!stand(passable, ground, cell)) continue
                    val eye = Vec3(cell.x + 0.5, surface(level, cell) + (player.eyeY - player.y), cell.z + 0.5)
                    if (aimAt(level, eye, target, focus, player) == null) continue
                    ranked.add(cell)
                }
            }
        }
        ranked.sortBy { hypot(it.x + 0.5 - player.x, it.z + 0.5 - player.z) + kotlin.math.abs(it.y - feet.y) }
        for (cell in ranked.take(12)) {
            if (cell == feet) return cell
            if (findPath(passable, ground, feet, cell) != null) return cell
        }
        return null
    }

    private fun aimAt(level: Level, eye: Vec3, target: BlockPos, focus: Entity?, player: LocalPlayer): Vec3? {
        val reach = reach(player, focus)
        if (focus != null) {
            val box = focus.boundingBox
            val midX = (box.minX + box.maxX) / 2.0
            val midZ = (box.minZ + box.maxZ) / 2.0
            val points = listOf(
                focus.eyePosition,
                Vec3(midX, (box.minY + box.maxY) / 2.0, midZ),
                Vec3(midX, box.maxY - 0.15, midZ),
                Vec3(midX, box.minY + 0.2, midZ),
            )
            return points.firstOrNull { lineClear(level, eye, it, reach, player, null, 0) }
        }
        val center = Vec3.atCenterOf(target)
        val points = ArrayList<Vec3>()
        points.add(center)
        for (face in Direction.entries) {
            points.add(center.add(face.stepX * 0.45, face.stepY * 0.45, face.stepZ * 0.45))
        }
        return points.firstOrNull { lineClear(level, eye, it, reach, player, target, 0) }
    }

    private fun lineClear(
        level: Level,
        from: Vec3,
        to: Vec3,
        reach: Double,
        player: LocalPlayer,
        target: BlockPos?,
        skips: Int,
    ): Boolean {
        if (from.distanceTo(to) > reach) return false
        if (skips > 8) return false
        val hit = clip(level, from, to, player)
        if (hit.type == HitResult.Type.MISS) return true
        if (target != null && sameTarget(level, hit.blockPos, target)) return true
        if (from.distanceTo(hit.location) + 0.08 >= from.distanceTo(to)) return true
        if (blocksClick(level, hit.blockPos)) return false
        val step = to.subtract(from)
        val len = step.length().coerceAtLeast(0.001)
        val next = hit.location.add(step.x / len * 0.08, step.y / len * 0.08, step.z / len * 0.08)
        return lineClear(level, next, to, reach, player, target, skips + 1)
    }

    private fun clip(level: Level, from: Vec3, to: Vec3, player: LocalPlayer): BlockHitResult =
        level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))

    private fun reach(player: LocalPlayer, focus: Entity?) =
        if (focus != null) player.entityInteractionRange() else player.blockInteractionRange()

    private fun feetCell(level: Level, player: LocalPlayer): Cell? {
        val passable = open(level)
        val ground = floor(level)
        val x = player.blockX
        val y = player.blockY
        val z = player.blockZ
        for (dy in intArrayOf(0, 1, -1)) {
            val cell = Cell(x, y + dy, z)
            if (stand(passable, ground, cell)) return cell
        }
        return null
    }

    private fun needsJump(level: Level, from: Cell, to: Cell): Boolean {
        if (to.y <= from.y) return false
        if (climbs(level, from) || climbs(level, to)) return true
        if (surface(level, to) - surface(level, from) <= 0.6) return false
        val dest = level.getBlockState(BlockPos(to.x, to.y, to.z)).block
        val under = level.getBlockState(BlockPos(to.x, to.y - 1, to.z)).block
        return dest !is StairBlock && under !is StairBlock
    }

    private fun hopToward(level: Level, from: Cell, to: Cell): Cell {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val len = hypot(dx, dz).coerceAtLeast(1.0)
        val x = from.x + (dx / len * 16).toInt()
        val z = from.z + (dz / len * 16).toInt()
        val passable = open(level)
        val ground = floor(level)
        var best = Cell(x, from.y, z)
        var bestScore = Int.MAX_VALUE
        for (dx2 in -2..2) {
            for (dy in -2..2) {
                for (dz2 in -2..2) {
                    val cell = Cell(x + dx2, from.y + dy, z + dz2)
                    if (!stand(passable, ground, cell)) continue
                    val score = manhattan(cell, to)
                    if (score < bestScore) {
                        bestScore = score
                        best = cell
                    }
                }
            }
        }
        return best
    }

    private fun nudge(player: LocalPlayer, pos: BlockPos) {
        val now = player.tickCount
        if (pos == nudgedPos && now < nudgedUntil) return
        val gameMode = Minecraft.getInstance().gameMode ?: return
        nudgedPos = pos
        nudgedUntil = now + 16
        val face = faceToward(player.eyePosition, pos)
        val hit = BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
    }

    private fun fail(): Move? {
        clear()
        return null
    }

    private fun look(player: LocalPlayer, point: Vec3, forward: Boolean, jump: Boolean, sprint: Boolean, arrived: Boolean): Move {
        val dx = point.x - player.x
        val dz = point.z - player.z
        val dy = point.y - player.eyeY
        val yaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val horizontal = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        val pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat()
        return Move(forward, jump, sprint, yaw, pitch, arrived)
    }

    private fun faceToward(eye: Vec3, pos: BlockPos): Direction {
        val dx = eye.x - (pos.x + 0.5)
        val dy = eye.y - (pos.y + 0.5)
        val dz = eye.z - (pos.z + 0.5)
        val ax = kotlin.math.abs(dx)
        val ay = kotlin.math.abs(dy)
        val az = kotlin.math.abs(dz)
        return when {
            ax >= ay && ax >= az -> if (dx > 0) Direction.EAST else Direction.WEST
            ay >= ax && ay >= az -> if (dy > 0) Direction.UP else Direction.DOWN
            else -> if (dz > 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun surface(level: Level, cell: Cell): Double {
        topOf(level, BlockPos(cell.x, cell.y, cell.z))?.let { return it }
        topOf(level, BlockPos(cell.x, cell.y - 1, cell.z))?.let { return it }
        return cell.y.toDouble()
    }

    private fun topOf(level: Level, pos: BlockPos): Double? {
        val state = level.getBlockState(pos)
        val block = state.block
        if (block is FenceBlock || block is WallBlock || block is DoorBlock || block is FenceGateBlock) return null
        if (block is SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) return pos.y + 0.5
        if (block is StairBlock) return pos.y + 0.5
        if (block is CarpetBlock) return pos.y + 0.0625
        if (block is SnowLayerBlock) return pos.y + state.getValue(SnowLayerBlock.LAYERS) * 0.125
        if (block is TrapDoorBlock && !state.getValue(TrapDoorBlock.OPEN)) return pos.y + 0.1875
        if (block is BasePressurePlateBlock) return pos.y + 0.0625
        val shape = state.getCollisionShape(level, pos)
        if (shape.isEmpty) return null
        return pos.y + shape.bounds().maxY
    }

    private fun manhattan(a: Cell, b: Cell) = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.z - b.z)

    private fun climbs(level: Level, cell: Cell) = climbs(level.getBlockState(BlockPos(cell.x, cell.y, cell.z)))

    private fun climbs(state: BlockState): Boolean {
        val block = state.block
        return block is LadderBlock || block is VineBlock || block is ScaffoldingBlock || state.fluidState.`is`(FluidTags.WATER)
    }

    private fun soft(state: BlockState): Boolean {
        val block = state.block
        if (block is SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) return true
        if (block is StairBlock || block is CarpetBlock || block is SnowLayerBlock) return true
        if (block is TrapDoorBlock || block is BasePressurePlateBlock) return true
        return false
    }

    private fun blocksClick(level: Level, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        val block = state.block
        if (block is FenceBlock || block is WallBlock || block is IronBarsBlock || block is FenceGateBlock) return false
        return !state.getCollisionShape(level, pos).isEmpty
    }

    private fun sameTarget(level: Level, hit: BlockPos, target: BlockPos): Boolean {
        if (hit == target) return true
        val state = level.getBlockState(target)
        if (state.block !is ChestBlock || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false
        return ChestBlock.getConnectedBlockPos(target, state) == hit
    }

    private fun closedPassage(state: BlockState): Boolean {
        val block = state.block
        return when (block) {
            is DoorBlock -> block.type().canOpenByHand() && !state.getValue(DoorBlock.OPEN)
            is FenceGateBlock -> !state.getValue(FenceGateBlock.OPEN)
            else -> false
        }
    }

    private fun bodyFits(level: Level, state: BlockState, pos: BlockPos): Boolean {
        val block = state.block
        if (block is DoorBlock) return block.type().canOpenByHand() || state.getValue(DoorBlock.OPEN)
        if (block is FenceGateBlock) return true
        if (block is TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) return true
        if (climbs(state) || soft(state)) return true
        if (!state.fluidState.isEmpty) return state.fluidState.`is`(FluidTags.WATER)
        if (block is FenceBlock || block is WallBlock || block is IronBarsBlock) return false
        return state.getCollisionShape(level, pos).isEmpty
    }

    private fun supports(level: Level, state: BlockState, pos: BlockPos): Boolean {
        val block = state.block
        if (block is DoorBlock || block is FenceGateBlock || block is FenceBlock || block is WallBlock) return false
        if (climbs(state)) return true
        if (block is SlabBlock || block is StairBlock || block is CarpetBlock || block is SnowLayerBlock) return true
        if (block is TrapDoorBlock || block is BasePressurePlateBlock) return true
        if (state.fluidState.`is`(FluidTags.WATER)) return true
        return state.fluidState.isEmpty && !state.getCollisionShape(level, pos).isEmpty
    }

    private fun open(level: Level): (Int, Int, Int) -> Boolean = { x, y, z ->
        val pos = BlockPos(x, y, z)
        bodyFits(level, level.getBlockState(pos), pos)
    }

    private fun floor(level: Level): (Int, Int, Int) -> Boolean = { x, y, z ->
        val pos = BlockPos(x, y, z)
        supports(level, level.getBlockState(pos), pos)
    }
}
