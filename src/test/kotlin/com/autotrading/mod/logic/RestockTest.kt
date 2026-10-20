package com.autotrading.mod.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestockTest {
    @Test
    fun freshStockIsWorthAVisit() {
        assertFalse(definitelyEmpty(StockNote(), 10_000, 5_000))
    }

    @Test
    fun nightAfterTheFirstEmptyVisitWaitsForWork() {
        val note = noteEmpty(StockNote(), StockKind.VILLAGER, 1_000, 13_000)
        assertTrue(definitelyEmpty(note, 3_000, 15_000))
        assertFalse(definitelyEmpty(note, 14_000, 26_000))
    }

    @Test
    fun workHoursAfterTheFirstEmptyVisitAreNotCertain() {
        val note = noteEmpty(StockNote(), StockKind.VILLAGER, 1_000, 3_000)
        assertTrue(definitelyEmpty(note, 1_000, 3_000))
        assertFalse(definitelyEmpty(note, 1_001, 3_001))
        val atClosing = noteEmpty(StockNote(), StockKind.VILLAGER, 8_999, 8_999)
        assertTrue(definitelyEmpty(atClosing, 9_000, 9_000))
    }

    @Test
    fun secondRestockWaitsForTheShortGap() {
        val bought = noteEmpty(noteInStock(emptyAt(1_000, 3_000), StockKind.VILLAGER, 1_100, 3_100), StockKind.VILLAGER, 1_200, 3_200)
        assertEquals(1, bought.seen)
        assertEquals(1_000, bought.restockGame)
        assertTrue(definitelyEmpty(bought, 1_200, 3_200))
        assertTrue(definitelyEmpty(bought, 3_400, 5_400))
        assertFalse(definitelyEmpty(bought, 3_401, 5_401))
    }

    @Test
    fun gapDoesNotSlideWhenCheckedAgain() {
        val bought = noteEmpty(noteInStock(emptyAt(1_000, 3_000), StockKind.VILLAGER, 1_100, 3_100), StockKind.VILLAGER, 1_200, 3_200)
        assertEquals(bought, noteEmpty(bought, StockKind.VILLAGER, 2_000, 4_000))
    }

    @Test
    fun aMissedWindowDropsTheCount() {
        val bought = noteEmpty(noteInStock(emptyAt(1_000, 3_000), StockKind.VILLAGER, 1_100, 3_100), StockKind.VILLAGER, 1_200, 3_200)
        val again = noteEmpty(bought, StockKind.VILLAGER, 3_401, 5_401)
        assertEquals(0, again.seen)
        assertEquals(3_401, again.emptyGame)
        assertTrue(again.empty)
    }

    @Test
    fun thirdRestockWaitsForTheDay() {
        val twice = boughtTwice()
        assertEquals(2, twice.seen)
        assertTrue(definitelyEmpty(twice, 5_000, 7_000))
        assertTrue(definitelyEmpty(twice, 12_001, 14_001))
        assertFalse(definitelyEmpty(twice, 26_000, 26_000))
    }

    @Test
    fun sleepCanOpenTheNextDayBeforeTheGameTimeGap() {
        val night = StockNote(seen = 2, empty = true, emptyGame = 2_000, emptyDay = 22_000, restockGame = 1_000)
        assertTrue(definitelyEmpty(night, 2_100, 25_000))
        assertFalse(definitelyEmpty(night, 2_100, 26_000))
    }

    @Test
    fun clockMovedBackwardsIsNotTrusted() {
        val note = emptyAt(5_000, 8_000)
        assertFalse(definitelyEmpty(note, 100, 8_000))
        assertFalse(definitelyEmpty(note, 5_000, 100))
        val reset = noteEmpty(note, StockKind.VILLAGER, 10, 10)
        assertEquals(0, reset.seen)
        assertEquals(10, reset.emptyGame)
    }

    @Test
    fun soldOutWandererIsNeverRevisited() {
        val empty = noteEmpty(StockNote(), StockKind.WANDERER, 1, 3_000)
        assertTrue(definitelyEmpty(empty, 99_999, 5_000))
        val stocked = noteInStock(empty, StockKind.WANDERER, 2, 3_000)
        assertFalse(definitelyEmpty(stocked, 2, 3_000))
        val sold = noteEmpty(stocked, StockKind.WANDERER, 3, 3_000)
        assertTrue(definitelyEmpty(sold, 3, 3_001))
        assertEquals(StockKind.WANDERER, noteEmpty(sold, StockKind.VILLAGER, 4, 4_000).kind)
    }

    @Test
    fun stockAfterAFullDayStartsANewCycle() {
        val full = StockNote(seen = 2, empty = true, emptyGame = 1_000, emptyDay = 20_000, restockGame = 1_000)
        val again = noteInStock(full, StockKind.VILLAGER, 1_500, 26_000)
        assertEquals(1, again.seen)
        assertFalse(again.empty)
    }

    private fun emptyAt(game: Long, day: Long) = noteEmpty(StockNote(), StockKind.VILLAGER, game, day)

    private fun boughtTwice(): StockNote {
        val first = noteEmpty(noteInStock(emptyAt(1_000, 3_000), StockKind.VILLAGER, 1_100, 3_100), StockKind.VILLAGER, 1_200, 3_200)
        val second = noteInStock(first, StockKind.VILLAGER, 3_401, 5_401)
        return noteEmpty(second, StockKind.VILLAGER, 3_500, 5_500)
    }
}
