package com.autotrading.mod.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogisticsTest {
    @Test
    fun fillsEverySlotExceptOne() {
        assertEquals(true, keepsReserve(5, 0, 64, 64))
        assertEquals(true, keepsReserve(2, 0, 64, 64))
        assertEquals(false, keepsReserve(1, 0, 64, 64))
        assertEquals(false, keepsReserve(2, 0, 128, 64))
    }

    @Test
    fun toppingUpDoesNotSpendTheReserve() {
        assertEquals(true, keepsReserve(1, 10, 10, 64))
        assertEquals(false, keepsReserve(1, 4, 64, 64))
    }

    @Test
    fun lapStartsByFillingThenSellingThenStoring() {
        assertEquals(Step.FETCH, nextStep(4, false, true, true, 0))
        assertEquals(Step.SELL, nextStep(1, false, true, true, 3))
        assertEquals(Step.STORE, nextStep(0, false, false, false, 3))
        assertEquals(Step.WAIT, nextStep(0, false, false, false, 0))
    }

    @Test
    fun fullInventoryWouldDropTheUnsoldStack() {
        val slots = List(36) { Pile("minecraft:melon", 64, 64) }
        assertEquals(false, saleFits(slots, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }

    @Test
    fun oneEmptySlotBringsTheUnsoldStackBack() {
        val slots = List(35) { Pile("minecraft:melon", 64, 64) } + Pile("", 0, 1)
        assertEquals(true, saleFits(slots, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }

    @Test
    fun exactPriceFreesItsOwnSlot() {
        val slots = listOf(Pile("minecraft:melon", 1, 64)) + List(35) { Pile("minecraft:dirt", 64, 64) }
        assertEquals(true, saleFits(slots, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }

    @Test
    fun partialStackWithNoEmptySlotDropsTheRest() {
        val slots = listOf(Pile("minecraft:melon", 5, 64)) + List(35) { Pile("minecraft:dirt", 64, 64) }
        assertEquals(false, saleFits(slots, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }

    @Test
    fun existingResultStackDoesNotNeedAnEmptySlot() {
        val slots = listOf(Pile("minecraft:emerald", 10, 64)) + List(35) { Pile("minecraft:melon", 64, 64) }
        assertEquals(true, saleFits(slots, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }

    @Test
    fun pullOrderDecidesWhetherTheRemainderFits() {
        val fullFirst = MutableList(36) { Pile("minecraft:dirt", 64, 64) }
        fullFirst[0] = Pile("minecraft:melon", 50, 64)
        fullFirst[9] = Pile("minecraft:melon", 64, 64)
        assertEquals(false, saleFits(fullFirst, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))

        val partialFirst = MutableList(36) { Pile("minecraft:dirt", 64, 64) }
        partialFirst[9] = Pile("minecraft:melon", 1, 64)
        partialFirst[10] = Pile("minecraft:melon", 64, 64)
        assertEquals(true, saleFits(partialFirst, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }

    @Test
    fun stagedRemainderIsNotClickedWhenItCannotReturn() {
        val afterPull = List(35) { Pile("minecraft:melon", 64, 64) } + Pile("", 0, 1)
        val staged = listOf(Pile("minecraft:melon", 64, 64))
        assertEquals(false, keepsSale(afterPull, staged, listOf(Price("minecraft:melon", 1)), "minecraft:emerald", 1, 64))
    }
}