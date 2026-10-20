package com.autotrading.mod.logic

enum class StockKind {
    VILLAGER,
    WANDERER,
}

/**
 * What the mod has seen for one villager.
 * [seen] is how many restocks were witnessed after an empty trade: 0, 1, or 2.
 * [restockGame] is the earliest game time at which that restock could have happened.
 */
data class StockNote(
    val kind: StockKind = StockKind.VILLAGER,
    val seen: Int = 0,
    val empty: Boolean = false,
    val emptyGame: Long = 0,
    val emptyDay: Long = 0,
    val restockGame: Long = 0,
)

const val DAY_TICKS = 24_000L
const val WORK_START = 2_000L
const val WORK_END = 9_000L
const val SECOND_GAP = 2_400L
const val CYCLE_GAP = 12_000L

fun noteInStock(note: StockNote, kind: StockKind, gameTime: Long, dayTime: Long): StockNote {
    val kept = withKind(note, kind)
    if (!kept.empty) return kept
    if (gameTime < kept.emptyGame || dayTime < kept.emptyDay) return kept.copy(empty = false)
    return kept.copy(empty = false, seen = nextSeen(kept, gameTime, dayTime), restockGame = earliestRestock(kept, gameTime, dayTime))
}

fun noteEmpty(note: StockNote, kind: StockKind, gameTime: Long, dayTime: Long): StockNote {
    val kept = withKind(note, kind)
    if (kept.kind == StockKind.WANDERER) {
        return if (kept.empty) kept else kept.copy(empty = true, emptyGame = gameTime, emptyDay = dayTime)
    }
    if (!kept.empty) return kept.copy(empty = true, emptyGame = gameTime, emptyDay = dayTime)
    if (gameTime < kept.emptyGame || dayTime < kept.emptyDay || !definitelyEmpty(kept, gameTime, dayTime)) {
        return kept.copy(seen = 0, empty = true, emptyGame = gameTime, emptyDay = dayTime, restockGame = 0)
    }
    return kept
}

/** True only when a loaded villager still could not have restocked the wanted offer. */
fun definitelyEmpty(note: StockNote, gameTime: Long, dayTime: Long): Boolean {
    if (!note.empty) return false
    if (note.kind == StockKind.WANDERER) return true
    if (gameTime < note.emptyGame || dayTime < note.emptyDay) return false
    val gap = gapFor(note.seen)
    val cooldownOpen = note.seen <= 0 || gameTime > note.restockGame + gap
    val dayRolled = dayIndex(dayTime) > dayIndex(note.emptyDay)
    if (note.seen > 0 && !cooldownOpen && !dayRolled) return true
    return !workAfterAllowed(note, gameTime, dayTime, gap, cooldownOpen, dayRolled)
}

private fun withKind(note: StockNote, kind: StockKind): StockNote =
    if (kind == StockKind.WANDERER && note.kind != StockKind.WANDERER) note.copy(kind = StockKind.WANDERER) else note

private fun gapFor(seen: Int) = when {
    seen >= 2 -> CYCLE_GAP
    seen == 1 -> SECOND_GAP
    else -> 0L
}

private fun nextSeen(note: StockNote, gameTime: Long, dayTime: Long): Int {
    val newCycle = note.seen >= 2 && (dayIndex(dayTime) > dayIndex(note.emptyDay) || gameTime > note.restockGame + CYCLE_GAP)
    val base = if (newCycle) 0 else note.seen
    return (base + 1).coerceAtMost(2)
}

/** Earliest game time the restock we are looking at could have happened. Later than this would skip a villager who already has stock. */
private fun earliestRestock(note: StockNote, gameTime: Long, dayTime: Long): Long {
    val byGap = when {
        note.seen <= 0 -> note.emptyGame
        note.seen == 1 -> note.restockGame + SECOND_GAP + 1
        else -> note.restockGame + CYCLE_GAP + 1
    }
    val byDay = if (dayIndex(dayTime) > dayIndex(note.emptyDay)) note.emptyGame + 1 else Long.MAX_VALUE
    val earliest = minOf(byGap, byDay).coerceAtLeast(note.emptyGame)
    return if (earliest > gameTime) note.emptyGame.coerceAtMost(gameTime) else earliest
}

private fun workAfterAllowed(
    note: StockNote,
    gameTime: Long,
    dayTime: Long,
    gap: Long,
    cooldownOpen: Boolean,
    dayRolled: Boolean,
): Boolean {
    if (note.seen <= 0) return workHappened(note.emptyDay, dayTime)
    val gamePassed = gameTime - note.emptyGame
    val dayPassed = dayTime - note.emptyDay
    if (dayPassed > gamePassed + 40) {
        val from = if (!cooldownOpen && dayRolled) dayIndex(note.emptyDay) * DAY_TICKS + DAY_TICKS - 1 else note.emptyDay
        return workHappened(from, dayTime)
    }
    if (!cooldownOpen && dayRolled) return workHappened(dayIndex(note.emptyDay) * DAY_TICKS + DAY_TICKS - 1, dayTime)
    val cooldownEndsAtDay = note.emptyDay + ((note.restockGame + gap) - note.emptyGame).coerceAtLeast(0)
    return workHappened(cooldownEndsAtDay, dayTime)
}

/** A work tick strictly after [fromDay] and at or before [toDay]. A backwards clock is not certain. */
private fun workHappened(fromDay: Long, toDay: Long): Boolean {
    if (toDay < fromDay) return true
    var day = dayIndex(fromDay)
    val last = dayIndex(toDay)
    if (last > day + 1) return true
    while (day <= last) {
        val start = day * DAY_TICKS + WORK_START
        val end = day * DAY_TICKS + WORK_END
        val lo = maxOf(fromDay + 1, start)
        val hi = minOf(toDay, end - 1)
        if (lo <= hi) return true
        day++
    }
    return false
}

private fun dayIndex(dayTime: Long) = Math.floorDiv(dayTime, DAY_TICKS)
