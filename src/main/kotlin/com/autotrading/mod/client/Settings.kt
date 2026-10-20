package com.autotrading.mod.client

import com.autotrading.mod.logic.Recipe
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import java.nio.file.Files

data class Mark(val dimension: String, val x: Int, val y: Int, val z: Int)

data class VillagerMark(val dimension: String, val uuid: String, val x: Int, val y: Int, val z: Int)

class Route {
    val inputs = ArrayList<Mark>()
    val villagers = ArrayList<VillagerMark>()
    val outputs = ArrayList<Mark>()

    fun ready() = inputs.isNotEmpty() && villagers.isNotEmpty() && outputs.isNotEmpty()
}

object Settings {
    var enabled = false
    var showPanel = true
    var corner = "top-right"
    val pickup = ArrayList<String>()
    var costA = "minecraft:melon"
    var costACount = 1
    var costB = ""
    var costBCount = 0
    var result = "minecraft:emerald"
    var resultCount = 1
    val deposit = ArrayList<String>()
    private val worlds = HashMap<String, Route>()

    fun recipe() = Recipe(costA, costACount, costB, costBCount, result, resultCount)

    fun route(world: String): Route = worlds.getOrPut(world) { Route() }

    fun knownItems(): Boolean {
        val ids = pickup + deposit + listOf(costA, result) + if (costB.isBlank()) emptyList() else listOf(costB)
        return ids.all { it.isNotBlank() && itemExists(it) }
    }

    fun load() {
        val file = file()
        if (!Files.exists(file)) {
            defaults()
            return
        }
        val root = JsonParser.parseString(Files.readString(file)).asJsonObject
        enabled = root.bool("enabled", false)
        showPanel = root.bool("showPanel", true)
        corner = root.str("corner", "top-right")
        pickup.clear()
        pickup.addAll(root.strings("pickup"))
        if (pickup.isEmpty()) pickup.add("minecraft:melon")
        costA = root.str("costA", "minecraft:melon")
        costACount = root.int("costACount", 1).coerceAtLeast(1)
        costB = root.str("costB", "")
        costBCount = root.int("costBCount", 0).coerceAtLeast(0)
        result = root.str("result", "minecraft:emerald")
        resultCount = root.int("resultCount", 1).coerceAtLeast(1)
        deposit.clear()
        deposit.addAll(root.strings("deposit"))
        if (deposit.isEmpty()) deposit.add("minecraft:emerald")
        worlds.clear()
        if (!root.has("worlds")) return
        val saved = root.getAsJsonObject("worlds")
        for (entry in saved.entrySet()) {
            val route = Route()
            val body = entry.value.asJsonObject
            route.inputs.addAll(body.marks("inputs"))
            route.outputs.addAll(body.marks("outputs"))
            route.villagers.addAll(body.villagers("villagers"))
            worlds[entry.key] = route
        }
    }

    fun save() {
        val root = JsonObject()
        root.addProperty("enabled", enabled)
        root.addProperty("showPanel", showPanel)
        root.addProperty("corner", corner)
        root.add("pickup", strings(pickup))
        root.addProperty("costA", costA)
        root.addProperty("costACount", costACount)
        root.addProperty("costB", costB)
        root.addProperty("costBCount", costBCount)
        root.addProperty("result", result)
        root.addProperty("resultCount", resultCount)
        root.add("deposit", strings(deposit))
        val saved = JsonObject()
        for ((key, route) in worlds) {
            val body = JsonObject()
            body.add("inputs", route.inputs.map { it.json() }.toJson())
            body.add("outputs", route.outputs.map { it.json() }.toJson())
            body.add("villagers", route.villagers.map { it.json() }.toJson())
            saved.add(key, body)
        }
        root.add("worlds", saved)
        val file = file()
        Files.createDirectories(file.parent)
        Files.writeString(file, GsonBuilder().setPrettyPrinting().create().toJson(root))
    }

    fun worldKey(minecraft: Minecraft): String {
        val server = minecraft.singleplayerServer
        if (server != null) return "sp:" + server.worldData.levelName
        return "mp:" + (minecraft.currentServer?.ip ?: "remote")
    }

    private fun defaults() {
        pickup.clear()
        pickup.add("minecraft:melon")
        deposit.clear()
        deposit.add("minecraft:emerald")
    }

    private fun file() = FabricLoader.getInstance().configDir.resolve("autotrading.json")

    private fun itemExists(id: String): Boolean {
        val parsed = Identifier.tryParse(id) ?: return false
        return BuiltInRegistries.ITEM.containsKey(parsed)
    }
}

private fun JsonObject.bool(name: String, fallback: Boolean) = if (has(name)) get(name).asBoolean else fallback

private fun JsonObject.str(name: String, fallback: String) = if (has(name)) get(name).asString else fallback

private fun JsonObject.int(name: String, fallback: Int) = if (has(name)) get(name).asInt else fallback

private fun JsonObject.strings(name: String): List<String> {
    if (!has(name)) return emptyList()
    return getAsJsonArray(name).map { it.asString.trim() }.filter { it.isNotEmpty() }
}

private fun JsonObject.marks(name: String): List<Mark> {
    if (!has(name)) return emptyList()
    return getAsJsonArray(name).map { element ->
        val body = element.asJsonObject
        Mark(body.get("dimension").asString, body.get("x").asInt, body.get("y").asInt, body.get("z").asInt)
    }
}

private fun JsonObject.villagers(name: String): List<VillagerMark> {
    if (!has(name)) return emptyList()
    return getAsJsonArray(name).map { element ->
        val body = element.asJsonObject
        VillagerMark(
            body.get("dimension").asString,
            body.get("uuid").asString,
            body.get("x").asInt,
            body.get("y").asInt,
            body.get("z").asInt,
        )
    }
}

private fun Mark.json() = JsonObject().apply {
    addProperty("dimension", dimension)
    addProperty("x", x)
    addProperty("y", y)
    addProperty("z", z)
}

private fun VillagerMark.json() = JsonObject().apply {
    addProperty("dimension", dimension)
    addProperty("uuid", uuid)
    addProperty("x", x)
    addProperty("y", y)
    addProperty("z", z)
}

private fun strings(values: List<String>): JsonArray {
    val array = JsonArray()
    values.forEach { array.add(it) }
    return array
}

private fun List<JsonObject>.toJson(): JsonArray {
    val array = JsonArray()
    forEach { array.add(it) }
    return array
}
