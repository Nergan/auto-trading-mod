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
}