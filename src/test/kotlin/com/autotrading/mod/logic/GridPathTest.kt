package com.autotrading.mod.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GridPathTest {
    @Test
    fun walksAcrossAFloor() {
        val path = findPath(::air, ::solid, Cell(0, 1, 0), Cell(3, 1, 0))
        assertEquals(listOf(Cell(1, 1, 0), Cell(2, 1, 0), Cell(3, 1, 0)), path)
    }

    @Test
    fun stopsAtAWall() {
        assertNull(findPath(::corridor, ::solidLane, Cell(0, 1, 0), Cell(3, 1, 0)))
    }

    @Test
    fun walksAPlatformWithNothingBelow() {
        val path = findPath(::platform, ::platformFloor, Cell(0, 5, 0), Cell(2, 5, 0))
        assertEquals(listOf(Cell(1, 5, 0), Cell(2, 5, 0)), path)
    }

    @Test
    fun stepsUpOneBlock() {
        val path = findPath(::air, ::step, Cell(0, 1, 0), Cell(1, 2, 0))
        assertEquals(listOf(Cell(1, 2, 0)), path)
    }

    private fun air(x: Int, y: Int, z: Int) = y > 0

    private fun solid(x: Int, y: Int, z: Int) = y == 0

    private fun corridor(x: Int, y: Int, z: Int) = y > 0 && z == 0 && !(x == 1 && y == 1)

    private fun solidLane(x: Int, y: Int, z: Int) = y == 0 && z == 0

    private fun step(x: Int, y: Int, z: Int) = y == 0 || (x == 1 && y == 1)

    private fun platform(x: Int, y: Int, z: Int) = y >= 5

    private fun platformFloor(x: Int, y: Int, z: Int) = y == 5
}
