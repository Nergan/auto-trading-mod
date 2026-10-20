package com.autotrading.mod.logic

import java.util.PriorityQueue

data class Cell(val x: Int, val y: Int, val z: Int)

/**
 * Feet positions. [open] is a block the body can occupy, [floor] is a block that supports feet.
 * A partial floor such as a bottom slab supports the cell it occupies, even with air below.
 * shortcut: one-block jumps and drops up to three. Ladders and water are handled by the callbacks.
 */
fun findPath(
    open: (Int, Int, Int) -> Boolean,
    floor: (Int, Int, Int) -> Boolean,
    start: Cell,
    goal: Cell,
    maxNodes: Int = 4000,
): List<Cell>? {
    if (!stand(open, floor, start) || !stand(open, floor, goal)) return null
    if (start == goal) return emptyList()

    data class Entry(val cell: Cell, val g: Int)

    val best = HashMap<Cell, Int>()
    val parent = HashMap<Cell, Cell>()
    val queue = PriorityQueue<Entry>(compareBy { it.g + heuristic(it.cell, goal) })
    best[start] = 0
    queue.add(Entry(start, 0))
    var seen = 0

    while (queue.isNotEmpty() && seen < maxNodes) {
        val entry = queue.poll()
        if (entry.g != best[entry.cell]) continue
        seen++
        if (entry.cell == goal) return rebuild(parent, start, goal)
        for ((next, step) in neighbors(open, floor, entry.cell)) {
            val nextCost = entry.g + step
            if (nextCost >= (best[next] ?: Int.MAX_VALUE)) continue
            best[next] = nextCost
            parent[next] = entry.cell
            queue.add(Entry(next, nextCost))
        }
    }
    return null
}

internal fun stand(open: (Int, Int, Int) -> Boolean, floor: (Int, Int, Int) -> Boolean, cell: Cell): Boolean {
    if (!open(cell.x, cell.y, cell.z) || !open(cell.x, cell.y + 1, cell.z)) return false
    return floor(cell.x, cell.y - 1, cell.z) || floor(cell.x, cell.y, cell.z)
}

private fun neighbors(open: (Int, Int, Int) -> Boolean, floor: (Int, Int, Int) -> Boolean, cell: Cell): List<Pair<Cell, Int>> {
    val found = ArrayList<Pair<Cell, Int>>(12)
    val steps = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    for ((dx, dz) in steps) {
        val nx = cell.x + dx
        val nz = cell.z + dz
        val flat = Cell(nx, cell.y, nz)
        if (stand(open, floor, flat)) found.add(flat to 1)
        val up = Cell(nx, cell.y + 1, nz)
        if (stand(open, floor, up) && open(cell.x, cell.y + 2, cell.z)) found.add(up to 2)
        for (drop in 1..3) {
            val down = Cell(nx, cell.y - drop, nz)
            if (!stand(open, floor, down)) continue
            var clear = true
            for (k in 1 until drop) {
                if (!open(nx, cell.y - k, nz)) clear = false
            }
            if (clear) found.add(down to 1)
        }
    }
    return found
}

private fun heuristic(a: Cell, b: Cell): Int =
    kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y) + kotlin.math.abs(a.z - b.z)

private fun rebuild(parent: Map<Cell, Cell>, start: Cell, goal: Cell): List<Cell> {
    val path = ArrayList<Cell>()
    var cursor = goal
    while (cursor != start) {
        path.add(cursor)
        cursor = parent[cursor] ?: return emptyList()
    }
    path.reverse()
    return path
}
