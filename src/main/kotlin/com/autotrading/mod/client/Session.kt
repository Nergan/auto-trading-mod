package com.autotrading.mod.client

import com.autotrading.mod.logic.OfferView
import com.autotrading.mod.logic.Pile
import com.autotrading.mod.logic.Price
import com.autotrading.mod.logic.Recipe
import com.autotrading.mod.logic.Step
import com.autotrading.mod.logic.canPay
import com.autotrading.mod.logic.fit
import com.autotrading.mod.logic.keepsReserve
import com.autotrading.mod.logic.keepsSale
import com.autotrading.mod.logic.nextStep
import com.autotrading.mod.logic.saleFits
import com.autotrading.mod.logic.StockKind
import com.autotrading.mod.logic.definitelyEmpty
import com.autotrading.mod.logic.noteEmpty
import com.autotrading.mod.logic.noteInStock
import com.autotrading.mod.logic.shouldFetch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.npc.villager.AbstractVillager
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.Container
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.EnderChestBlock
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult

object Session {
    private enum class Phase { DISABLED, INPUTS, VILLAGERS, OUTPUTS }
    private enum class Job { IDLE, CHOOSE, WALK, OPEN, TAKE, TRADE, PUT, WAIT }
    private enum class Mode { FETCH, SELL, STORE }

    private var phase = Phase.DISABLED
    private val draftInputs = ArrayList<Mark>()
    private val draftVillagers = ArrayList<VillagerMark>()
    private val draftOutputs = ArrayList<Mark>()

    private var job = Job.IDLE
    private var mode = Mode.FETCH
    private var cursor = 0
    private var clock = 0
    private var waitUntil = 0
    private var actionKey = ""
    private var move: Walker.Move? = null
    private var appliedJoin = false
    private var previous: Screen? = null
    private var pendingPos: BlockPos? = null
    private var pendingAtTick = 0
    private var pendingEntity: Entity? = null
    private var openedByMod = false
    private var reopenChat = false
    private var restoreChat = false
    private var interacted = false
    private var openSince = 0
    private var pendingSlot = -1
    private var pendingCount = -1
    private var pendingAt = 0
    private var tradeStep = 0
    private var tradeFails = 0
    private var tradeMarked = 0
    private var tradeState = 0
    private var tradeSnapshot = 0
    private var resumeSell = -1
    private var sawLocked = false
    private val told = HashSet<String>()
    private var autoJumpWas: Boolean? = null

    private var inputs = emptyList<Mark>()
    private var villagers = emptyList<VillagerMark>()
    private var outputs = emptyList<Mark>()

    fun statusText(): String = when {
        Settings.enabled && stopped() -> "autotrading: stop"
        Settings.enabled -> "autotrading: enabled"
        phase == Phase.INPUTS -> "autotrading: setting input containers..."
        phase == Phase.VILLAGERS -> "autotrading: setting trade villagers..."
        phase == Phase.OUTPUTS -> "autotrading: setting output containers..."
        else -> "autotrading: disabled"
    }

    fun statusColor(): Int = when {
        Settings.enabled && stopped() -> 0xFFFFAA00.toInt()
        Settings.enabled -> 0xFF55FF55.toInt()
        phase == Phase.DISABLED -> 0xFFFF5555.toInt()
        else -> 0xFFFFFF55.toInt()
    }

    fun canToggleFromStatus(): Boolean {
        if (Settings.enabled) return true
        if (stopped()) return false
        return phase == Phase.DISABLED && Minecraft.getInstance().level != null
    }

    fun toggleEnabled() {
        if (!canToggleFromStatus()) return
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null) return
        Settings.enabled = !Settings.enabled
        onEnabledChanged(minecraft)
        Settings.save()
    }

    fun counterText(): Component? = when (phase) {
        Phase.INPUTS -> Component.translatable("autotrading.counter.containers", draftInputs.size)
        Phase.VILLAGERS -> Component.translatable("autotrading.counter.villagers", draftVillagers.size)
        Phase.OUTPUTS -> Component.translatable("autotrading.counter.containers", draftOutputs.size)
        else -> null
    }

    fun actionText(): Component? =
        if (Settings.enabled && actionKey.isNotEmpty()) Component.translatable(actionKey) else null

    fun actionLabel() = if (phase == Phase.DISABLED) "set trading" else "cancel"

    fun applyOn(): Boolean = !Settings.enabled && phase != Phase.DISABLED && Minecraft.getInstance().level != null

    fun actionOn(): Boolean = !Settings.enabled && Minecraft.getInstance().level != null

    fun toggleSetup() {
        if (!actionOn()) return
        pendingPos = null
        draftInputs.clear()
        draftVillagers.clear()
        draftOutputs.clear()
        phase = if (phase == Phase.DISABLED) Phase.INPUTS else Phase.DISABLED
    }

    fun apply() {
        if (!applyOn()) return
        pendingPos = null
        val minecraft = Minecraft.getInstance()
        when (phase) {
            Phase.INPUTS -> {
                if (draftInputs.isEmpty()) {
                    Alerts.show("autotrading.notice.need_target")
                    return
                }
                phase = Phase.VILLAGERS
            }
            Phase.VILLAGERS -> {
                if (draftVillagers.isEmpty()) {
                    Alerts.show("autotrading.notice.need_target")
                    return
                }
                phase = Phase.OUTPUTS
            }
            Phase.OUTPUTS -> {
                if (draftOutputs.isEmpty()) {
                    Alerts.show("autotrading.notice.need_target")
                    return
                }
                val route = Settings.route(Settings.worldKey(minecraft))
                route.inputs.clear()
                route.inputs.addAll(draftInputs)
                route.villagers.clear()
                route.villagers.addAll(draftVillagers)
                route.outputs.clear()
                route.outputs.addAll(draftOutputs)
                Settings.save()
                draftInputs.clear()
                draftVillagers.clear()
                draftOutputs.clear()
                phase = Phase.DISABLED
            }
            Phase.DISABLED -> Unit
        }
    }

    fun rememberBlock(pos: BlockPos, hand: InteractionHand, player: Player) {
        if (player.getItemInHand(hand).item is BlockItem) return
        pendingPos = pos
        pendingAtTick = clock
    }

    fun rememberEntity(entity: Entity) {
        pendingEntity = entity
    }

    fun overrideInput(keyboard: net.minecraft.world.entity.player.Input): net.minecraft.world.entity.player.Input? {
        if (!Settings.enabled) return null
        if (keyboard.forward() || keyboard.backward() || keyboard.left() || keyboard.right() || keyboard.jump() || keyboard.shift() || keyboard.sprint()) {
            Alerts.lock()
        }
        val forced = move
        val minecraft = Minecraft.getInstance()
        if (forced == null || !forced.forward || held(minecraft)) {
            return net.minecraft.world.entity.player.Input.EMPTY
        }
        return net.minecraft.world.entity.player.Input(true, false, false, false, forced.jump, false, forced.sprint)
    }

    fun eatKeys(minecraft: Minecraft) {
        if (!Settings.enabled) return
        val options = minecraft.options
        val keys = listOf(
            options.keyAttack,
            options.keyUse,
            options.keyDrop,
            options.keyInventory,
            options.keySwapOffhand,
            options.keyPickItem,
        ) + options.keyHotbarSlots
        var pressed = false
        for (key in keys) {
            if (key.isDown) pressed = true
            key.setDown(false)
            key.consumeClick()
        }
        if (pressed) Alerts.lock()
    }

    fun onEnabledChanged(minecraft: Minecraft) {
        if (!Settings.enabled) {
            halt(minecraft)
            return
        }
        if (phase != Phase.DISABLED) {
            Settings.enabled = false
            Alerts.show("autotrading.notice.busy")
            Settings.save()
            halt(minecraft)
            return
        }
        if (!Settings.knownItems()) {
            Settings.enabled = false
            Alerts.show("autotrading.notice.bad_item")
            Settings.save()
            halt(minecraft)
            return
        }
        val route = Settings.route(Settings.worldKey(minecraft))
        if (!route.ready()) {
            Settings.enabled = false
            Alerts.show("autotrading.notice.need_route")
            Settings.save()
            halt(minecraft)
            return
        }
        told.clear()
        cursor = 0
        resumeSell = -1
        job = Job.CHOOSE
        Walker.clear()
        move = null
    }

    fun tick(minecraft: Minecraft) {
        clock++
        capture(minecraft)
        val player = minecraft.player
        if (player == null) {
            appliedJoin = false
            move = null
            if (phase != Phase.DISABLED) {
                phase = Phase.DISABLED
                draftInputs.clear()
                draftVillagers.clear()
                draftOutputs.clear()
            }
            return
        }
        if (!appliedJoin) {
            appliedJoin = true
            if (Settings.enabled) onEnabledChanged(minecraft)
        }
        if (restoreChat && minecraft.gui.screen() == null) {
            restoreChat = false
            minecraft.gui.setScreen(ChatScreen("", false))
        }
        if (!player.isAlive || player.isDeadOrDying) {
            if (Settings.enabled) {
                Settings.enabled = false
                Settings.save()
                Alerts.show("autotrading.notice.died")
                halt(minecraft)
            }
            return
        }
        if (!Settings.enabled) {
            restoreAutoJump(minecraft)
            move = null
            return
        }
        suppressAutoJump(minecraft)
        if (away(minecraft) && minecraft.gui.screen() is PauseScreen) minecraft.gui.setScreen(null)
        if (held(minecraft)) {
            move = null
            return
        }
        val level = minecraft.level ?: return
        when (job) {
            Job.IDLE, Job.CHOOSE -> choose(minecraft, player)
            Job.WALK -> walk(minecraft, player, level)
            Job.OPEN -> open(minecraft, player, level)
            Job.TAKE -> take(minecraft, player)
            Job.TRADE -> trade(minecraft, player)
            Job.PUT -> put(minecraft, player)
            Job.WAIT -> if (clock >= waitUntil) job = Job.CHOOSE
        }
        val look = move
        if (look != null) {
            player.yRot = look.yaw
            player.xRot = look.pitch.coerceIn(-90f, 90f)
            player.yHeadRot = look.yaw
            player.setYBodyRot(look.yaw)
        }
    }

    private fun choose(minecraft: Minecraft, player: Player) {
        if (!routeReady(minecraft)) {
            val route = Settings.route(Settings.worldKey(minecraft))
            val elsewhere = route.inputs.isNotEmpty() || route.villagers.isNotEmpty() || route.outputs.isNotEmpty()
            Alerts.show(if (elsewhere) "autotrading.notice.dimension" else "autotrading.notice.need_route")
            park(40, "autotrading.action.waiting")
            return
        }
        when (plan(player)) {
            Step.FETCH -> startWalk(minecraft, Mode.FETCH, 0)
            Step.SELL -> startWalk(minecraft, Mode.SELL, 0)
            Step.STORE -> startWalk(minecraft, Mode.STORE, 0)
            Step.WAIT -> {
                if (emptySlots(player) == 0) Alerts.show("autotrading.notice.full_inventory")
                park(40, "autotrading.action.waiting")
            }
        }
    }

    private fun walk(minecraft: Minecraft, player: net.minecraft.client.player.LocalPlayer, level: net.minecraft.world.level.Level) {
        actionKey = "autotrading.action.walking"
        val pos = targetPos(level) ?: run {
            skip(minecraft)
            return
        }
        val focus = if (mode == Mode.SELL) villagers.getOrNull(cursor)?.let { findVillager(level, it) } else null
        val steered = Walker.steer(level, player, pos, focus)
        if (steered == null) {
            Alerts.show("autotrading.notice.path")
            skip(minecraft)
            return
        }
        move = steered
        if (steered.arrived) {
            move = steered.copy(forward = false, jump = false, sprint = false)
            beginOpen()
            return
        }
        if (steered.forward) Walker.nudgeDoors(level, player, clock)
    }

    private fun beginOpen() {
        job = Job.OPEN
        interacted = false
        openedByMod = false
        openSince = clock
        tradeStep = 0
        tradeFails = 0
        pendingSlot = -1
    }

    private fun open(minecraft: Minecraft, player: net.minecraft.client.player.LocalPlayer, level: net.minecraft.world.level.Level) {
        val screen = minecraft.gui.screen()
        if (screen is ChatScreen) {
            reopenChat = true
            minecraft.gui.setScreen(null)
            return
        }
        if (screen is AbstractContainerScreen<*>) {
            openedByMod = true
            job = when (mode) {
                Mode.FETCH -> Job.TAKE
                Mode.SELL -> Job.TRADE
                Mode.STORE -> Job.PUT
            }
            actionKey = when (mode) {
                Mode.FETCH -> "autotrading.action.taking"
                Mode.SELL -> "autotrading.action.trading"
                Mode.STORE -> "autotrading.action.putting"
            }
            pendingAt = clock + Settings.paceTicks()
            tradeStep = 0
            return
        }
        if (screen != null) return
        if (!interacted) {
            if (clock - openSince < Settings.paceTicks()) return
            if (!interact(minecraft, player, level)) {
                once("missing:" + targetKey()) {
                    Alerts.show(if (mode == Mode.SELL) "autotrading.notice.missing_villager" else "autotrading.notice.missing_container")
                }
                skip(minecraft)
                return
            }
            interacted = true
            openSince = clock
            return
        }
        if (clock - openSince > Settings.paceTicks() * 4 + 20) {
            once("missing:" + targetKey()) {
                Alerts.show(if (mode == Mode.SELL) "autotrading.notice.missing_villager" else "autotrading.notice.missing_container")
            }
            skip(minecraft)
        }
    }

    private fun take(minecraft: Minecraft, player: Player) {
        actionKey = "autotrading.action.taking"
        if (pendingSlot >= 0) {
            if (clock < pendingAt) return
            pendingSlot = -1
        }
        if (clock < pendingAt) return
        val menu = player.containerMenu
        val available = menu.slots.withIndex().filter { (_, entry) ->
            entry.container !== player.inventory && Settings.pickup.contains(idOf(entry.item)) && !entry.item.isEmpty
        }
        val slot = available.firstOrNull { (_, entry) ->
            keepsReserve(emptySlots(player), mergeRoom(player, idOf(entry.item)), entry.item.count, entry.item.maxStackSize)
        }
        if (slot == null) {
            closeWork(minecraft)
            if (available.isEmpty()) skip(minecraft) else afterInputs(minecraft, player)
            return
        }
        clickSlot(minecraft, player, slot.index)
        pendingSlot = slot.index
        pendingAt = clock + Settings.paceTicks()
    }

    private fun trade(minecraft: Minecraft, player: Player) {
        actionKey = "autotrading.action.trading"
        val screen = minecraft.gui.screen()
        if (screen !is MerchantScreen) {
            once("villager:" + targetKey()) { Alerts.show("autotrading.notice.missing_villager") }
            skip(minecraft)
            return
        }
        if (clock < pendingAt) return
        val menu = screen.menu
        val recipe = Settings.recipe()
        if (tradeStep == 1) {
            val result = menu.slots.getOrNull(2)?.item
            val ready = menu.stateId != tradeState || clock - tradeMarked >= Settings.paceTicks() * 3
            if (result != null && !result.isEmpty && ready) {
                val staged = listOf(0, 1).map { index -> pileOf(menu.slots.getOrNull(index)?.item) }
                val prices = prices(recipe)
                val stagedEnough = prices.all { price ->
                    staged.sumOf { if (it.item == price.item) it.count else 0 } >= price.count
                }
                if (stagedEnough) {
                    if (!keepsSale(piles(player), staged, prices, idOf(result), result.count, result.maxStackSize.coerceAtLeast(1))) {
                        deferTrade(minecraft, player)
                        return
                    }
                    tradeSnapshot = count(player, recipe.result)
                    clickSlot(minecraft, player, 2)
                    tradeStep = 2
                    pendingAt = clock + Settings.paceTicks()
                    return
                }
            }
            if (clock - tradeMarked < Settings.paceTicks() * 4) return
            tradeFails++
            tradeStep = 0
            pendingAt = clock + Settings.paceTicks()
            if (tradeFails >= 4) skip(minecraft)
            return
        }
        if (tradeStep == 2) {
            if (count(player, recipe.result) > tradeSnapshot) tradeFails = 0 else tradeFails++
            tradeStep = 0
            pendingAt = clock + Settings.paceTicks()
            if (tradeFails >= 4) skip(minecraft)
            return
        }
        val payable = canPay(count(player, recipe.costA), count(player, recipe.costB), recipe)
        if (!payable || !resultFits(player)) {
            deferTrade(minecraft, player)
            return
        }
        var match = -1
        var locked = false
        for (index in menu.offers.indices) {
            when (fit(view(menu.offers[index]), recipe)) {
                com.autotrading.mod.logic.OfferFit.MATCH -> {
                    match = index
                    break
                }
                com.autotrading.mod.logic.OfferFit.LOCKED -> locked = true
                com.autotrading.mod.logic.OfferFit.DIFFERENT -> Unit
            }
        }
        if (match < 0) {
            if (locked) {
                sawLocked = true
                rememberOffer(minecraft, false)
            } else {
                once("offer:" + targetKey()) { Alerts.show("autotrading.notice.no_offer") }
            }
            skip(minecraft)
            return
        }
        rememberOffer(minecraft, true)
        selectOffer(screen, match)
        tradeStep = 1
        tradeState = menu.stateId
        tradeMarked = clock
        pendingAt = clock + Settings.paceTicks()
    }

    private fun selectOffer(screen: MerchantScreen, index: Int) {
        runCatching {
            val field = MerchantScreen::class.java.getDeclaredField("shopItem")
            field.isAccessible = true
            field.setInt(screen, index)
        }
        val menu = screen.menu
        menu.setSelectionHint(index)
        menu.tryMoveItems(index)
        Minecraft.getInstance().connection?.send(ServerboundSelectTradePacket(index))
    }

    private fun put(minecraft: Minecraft, player: Player) {
        actionKey = "autotrading.action.putting"
        if (Settings.deposit.sumOf { count(player, it) } == 0) {
            closeWork(minecraft)
            afterOutputs(minecraft, player)
            return
        }
        val menu = player.containerMenu
        if (pendingSlot >= 0) {
            if (clock < pendingAt) return
            val now = menu.slots.getOrNull(pendingSlot)?.item?.count ?: 0
            val rejected = now >= pendingCount && pendingCount > 0
            pendingSlot = -1
            if (rejected) {
                closeWork(minecraft)
                cursor++
                if (cursor >= outputs.size) {
                    Alerts.show("autotrading.notice.full_output")
                    park(80, "autotrading.action.waiting")
                } else {
                    job = Job.WALK
                    Walker.clear()
                }
            }
            return
        }
        if (clock < pendingAt) return
        val slot = menu.slots.withIndex().firstOrNull { (_, entry) ->
            entry.container === player.inventory && Settings.deposit.contains(idOf(entry.item))
        }
        if (slot == null) {
            closeWork(minecraft)
            afterOutputs(minecraft, player)
            return
        }
        pendingSlot = slot.index
        pendingCount = slot.value.item.count
        pendingAt = clock + Settings.paceTicks()
        clickSlot(minecraft, player, slot.index)
    }

    private fun interact(minecraft: Minecraft, player: net.minecraft.client.player.LocalPlayer, level: net.minecraft.world.level.Level): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        if (mode == Mode.SELL) {
            val mark = villagers.getOrNull(cursor) ?: return false
            val villager = findVillager(level, mark) ?: return false
            val hit = EntityHitResult(villager, villager.eyePosition)
            gameMode.interact(player, villager, hit, InteractionHand.MAIN_HAND)
            return true
        }
        val mark = (if (mode == Mode.FETCH) inputs else outputs).getOrNull(cursor) ?: return false
        val pos = BlockPos(mark.x, mark.y, mark.z)
        val hit = Walker.blockHit(level, player, pos) ?: return false
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        return true
    }

    private fun targetPos(level: net.minecraft.world.level.Level): BlockPos? {
        if (mode == Mode.SELL) {
            val mark = villagers.getOrNull(cursor) ?: return null
            val villager = findVillager(level, mark)
            if (villager != null) {
                val pos = villager.blockPosition()
                villagers = villagers.toMutableList().also {
                    it[cursor] = mark.copy(x = pos.x, y = pos.y, z = pos.z)
                }
                return pos
            }
            return BlockPos(mark.x, mark.y, mark.z)
        }
        val mark = (if (mode == Mode.FETCH) inputs else outputs).getOrNull(cursor) ?: return null
        return BlockPos(mark.x, mark.y, mark.z)
    }

    private fun findVillager(level: net.minecraft.world.level.Level, mark: VillagerMark): AbstractVillager? {
        val box = AABB(mark.x - 64.0, mark.y - 32.0, mark.z - 64.0, mark.x + 65.0, mark.y + 32.0, mark.z + 65.0)
        return level.getEntities(EntityTypeTest.forClass(AbstractVillager::class.java), box) { entity ->
            entity.stringUUID.equals(mark.uuid, true)
        }.firstOrNull()
    }

    private fun skip(minecraft: Minecraft) {
        closeWork(minecraft)
        cursor++
        if (mode == Mode.SELL) {
            val resolved = openVillager(minecraft, cursor)
            if (resolved < 0) {
                val player = minecraft.player ?: return
                afterVillagers(minecraft, player)
                return
            }
            cursor = resolved
            job = Job.WALK
            Walker.clear()
            return
        }
        val size = when (mode) {
            Mode.FETCH -> inputs.size
            Mode.SELL -> villagers.size
            Mode.STORE -> outputs.size
        }
        if (cursor < size) {
            job = Job.WALK
            Walker.clear()
            return
        }
        val player = minecraft.player ?: return
        when (mode) {
            Mode.FETCH -> afterInputs(minecraft, player)
            Mode.SELL -> afterVillagers(minecraft, player)
            Mode.STORE -> afterOutputs(minecraft, player)
        }
    }

    private fun afterInputs(minecraft: Minecraft, player: Player) {
        val recipe = Settings.recipe()
        val payable = canPay(count(player, recipe.costA), count(player, recipe.costB), recipe)
        when {
            payable && resultFits(player) -> {
                val index = resumeSell.takeIf { it in villagers.indices } ?: 0
                resumeSell = -1
                startWalk(minecraft, Mode.SELL, index)
            }
            Settings.deposit.sumOf { count(player, it) } > 0 -> startWalk(minecraft, Mode.STORE, 0)
            else -> park(40, "autotrading.action.waiting")
        }
    }

    private fun afterVillagers(minecraft: Minecraft, player: Player) {
        val recipe = Settings.recipe()
        val payable = canPay(count(player, recipe.costA), count(player, recipe.costB), recipe)
        when {
            Settings.deposit.sumOf { count(player, it) } > 0 -> startWalk(minecraft, Mode.STORE, 0)
            !payable && shouldFetch(emptySlots(player), hasPartialPickup(player)) -> startWalk(minecraft, Mode.FETCH, 0)
            openVillager(minecraft, 0) < 0 -> park(100, "autotrading.action.restock")
            sawLocked -> park(80, "autotrading.action.restock")
            else -> park(40, "autotrading.action.waiting")
        }
    }

    private fun afterOutputs(minecraft: Minecraft, player: Player) {
        val recipe = Settings.recipe()
        if (resumeSell in villagers.indices &&
            canPay(count(player, recipe.costA), count(player, recipe.costB), recipe) &&
            resultFits(player)
        ) {
            val index = resumeSell
            resumeSell = -1
            startWalk(minecraft, Mode.SELL, index)
            return
        }
        when (plan(player)) {
            Step.FETCH -> startWalk(minecraft, Mode.FETCH, 0)
            Step.SELL -> {
                resumeSell = -1
                startWalk(minecraft, Mode.SELL, 0)
            }
            Step.STORE -> startWalk(minecraft, Mode.STORE, 0)
            Step.WAIT -> park(if (sawLocked) 80 else 40, if (sawLocked) "autotrading.action.restock" else "autotrading.action.waiting")
        }
    }

    private fun startWalk(minecraft: Minecraft, next: Mode, index: Int) {
        if (!routeReady(minecraft)) {
            val route = Settings.route(Settings.worldKey(minecraft))
            val elsewhere = route.inputs.isNotEmpty() || route.villagers.isNotEmpty() || route.outputs.isNotEmpty()
            Alerts.show(if (elsewhere) "autotrading.notice.dimension" else "autotrading.notice.need_route")
            park(40, "autotrading.action.waiting")
            return
        }
        if (next == Mode.SELL) {
            if (index == 0) {
                sawLocked = false
                resumeSell = -1
            }
            val resolved = openVillager(minecraft, index)
            if (resolved < 0) {
                val holder = minecraft.player
                if (holder != null && Settings.deposit.sumOf { count(holder, it) } > 0) startWalk(minecraft, Mode.STORE, 0)
                else park(100, "autotrading.action.restock")
                return
            }
            mode = next
            cursor = resolved
            job = Job.WALK
            Walker.clear()
            actionKey = "autotrading.action.walking"
            return
        }
        mode = next
        cursor = index
        job = Job.WALK
        Walker.clear()
        actionKey = "autotrading.action.walking"
    }

    /** Next villager who might have the offer. A blocked villager is not pathed to. */
    private fun openVillager(minecraft: Minecraft, start: Int): Int {
        val clocks = worldClocks(minecraft) ?: return start.takeIf { it in villagers.indices } ?: -1
        val (game, day) = clocks
        val world = Settings.worldKey(minecraft)
        for (index in start until villagers.size) {
            val note = Settings.stock(world, villagers[index].uuid)
            if (!definitelyEmpty(note, game, day)) return index
        }
        return -1
    }

    private fun rememberOffer(minecraft: Minecraft, inStock: Boolean) {
        val clocks = worldClocks(minecraft) ?: return
        val mark = villagers.getOrNull(cursor) ?: return
        val (game, day) = clocks
        val entity = minecraft.level?.let { findVillager(it, mark) }
        val kind = if (entity is WanderingTrader) StockKind.WANDERER else StockKind.VILLAGER
        val world = Settings.worldKey(minecraft)
        val note = Settings.stock(world, mark.uuid)
        val next = if (inStock) noteInStock(note, kind, game, day) else noteEmpty(note, kind, game, day)
        if (next == note) return
        Settings.putStock(world, mark.uuid, next)
        Settings.save()
    }

    /** Game time and the overworld clock. A missing clock must not invent a long skip. */
    private fun worldClocks(minecraft: Minecraft): Pair<Long, Long>? {
        val level = minecraft.level ?: return null
        val game = level.gameTime
        val day = level.overworldClockTime
        if (day <= 0L && game > 0L) return null
        return game to day
    }

    private fun routeReady(minecraft: Minecraft): Boolean {
        val level = minecraft.level ?: return false
        val dim = level.dimension().identifier().toString()
        val route = Settings.route(Settings.worldKey(minecraft))
        inputs = route.inputs.filter { it.dimension == dim }
        villagers = route.villagers.filter { it.dimension == dim }
        outputs = route.outputs.filter { it.dimension == dim }
        return inputs.isNotEmpty() && villagers.isNotEmpty() && outputs.isNotEmpty()
    }

    private fun plan(player: Player): Step {
        val recipe = Settings.recipe()
        return nextStep(
            emptySlots(player),
            hasPartialPickup(player),
            canPay(count(player, recipe.costA), count(player, recipe.costB), recipe),
            resultFits(player),
            Settings.deposit.sumOf { count(player, it) },
        )
    }

    private fun park(ticks: Int, key: String) {
        resumeSell = -1
        job = Job.WAIT
        waitUntil = clock + ticks
        actionKey = key
        move = null
        Walker.clear()
    }

    private fun closeWork(minecraft: Minecraft) {
        if (!openedByMod && minecraft.gui.screen() !is AbstractContainerScreen<*>) return
        openedByMod = false
        minecraft.player?.closeContainer()
        if (reopenChat) {
            reopenChat = false
            restoreChat = true
        }
    }

    private fun halt(minecraft: Minecraft) {
        job = Job.IDLE
        move = null
        Walker.clear()
        restoreAutoJump(minecraft)
        openedByMod = false
        reopenChat = false
        restoreChat = false
        pendingSlot = -1
        resumeSell = -1
        if (minecraft.gui.screen() is AbstractContainerScreen<*>) minecraft.player?.closeContainer()
    }

    private fun clickSlot(minecraft: Minecraft, player: Player, slot: Int) {
        val menu = player.containerMenu
        minecraft.gameMode?.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player)
    }

    private fun capture(minecraft: Minecraft) {
        val screen = minecraft.gui.screen()
        if (screen === previous) return
        previous = screen
        if (Settings.enabled || phase == Phase.DISABLED || screen == null) return
        if (screen is InventoryScreen || screen is CreativeModeInventoryScreen) return
        val level = minecraft.level ?: return
        val dim = level.dimension().identifier().toString()
        if ((phase == Phase.INPUTS || phase == Phase.OUTPUTS) && screen is AbstractContainerScreen<*> && screen !is MerchantScreen) {
            val pos = pendingPos ?: return
            if (clock - pendingAtTick > 100 || !containerBlock(level, pos)) {
                pendingPos = null
                return
            }
            pendingPos = null
            val stored = canonicalChest(level, pos)
            val mark = Mark(dim, stored.x, stored.y, stored.z)
            val list = if (phase == Phase.INPUTS) draftInputs else draftOutputs
            if (list.none { it == mark }) list.add(mark)
        }
        if (phase == Phase.VILLAGERS && screen is MerchantScreen) {
            val entity = pendingEntity as? AbstractVillager ?: return
            pendingEntity = null
            val pos = entity.blockPosition()
            val uuid = entity.stringUUID
            val mark = VillagerMark(dim, uuid, pos.x, pos.y, pos.z)
            if (draftVillagers.none { sameVillager(it, uuid, pos) }) draftVillagers.add(mark)
        }
    }

    private fun canonicalChest(level: net.minecraft.world.level.Level, pos: BlockPos): BlockPos {
        val state = level.getBlockState(pos)
        if (state.block !is ChestBlock || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos
        val other = ChestBlock.getConnectedBlockPos(pos, state)
        return if (other.asLong() < pos.asLong()) other else pos
    }

    private fun sameVillager(saved: VillagerMark, uuid: String, pos: BlockPos): Boolean {
        if (saved.uuid.equals(uuid, true)) return true
        return saved.x == pos.x && saved.y == pos.y && saved.z == pos.z
    }

    private fun suppressAutoJump(minecraft: Minecraft) {
        val option = minecraft.options.autoJump()
        if (autoJumpWas == null) autoJumpWas = option.get()
        if (option.get()) option.set(false)
    }

    private fun restoreAutoJump(minecraft: Minecraft) {
        val previous = autoJumpWas ?: return
        minecraft.options.autoJump().set(previous)
        autoJumpWas = null
    }

    private fun stopped(): Boolean = Minecraft.getInstance().gui.screen() is PauseScreen

    private fun containerBlock(level: net.minecraft.world.level.Level, pos: BlockPos): Boolean {
        if (level.getBlockEntity(pos) is Container) return true
        val block = level.getBlockState(pos).block
        return block is CraftingTableBlock || block is EnderChestBlock
    }

    /** The window is in the background and the route should keep running. */
    private fun away(minecraft: Minecraft) = Settings.enabled && !minecraft.isWindowActive

    /** Esc pauses the route. A pause menu opened by leaving the window does not. */
    private fun held(minecraft: Minecraft): Boolean {
        if (away(minecraft)) return false
        return minecraft.isPaused || foreign(minecraft)
    }

    private fun foreign(minecraft: Minecraft): Boolean {
        val screen = minecraft.gui.screen() ?: return false
        if (screen is ChatScreen) return false
        val working = job == Job.OPEN || job == Job.TAKE || job == Job.TRADE || job == Job.PUT
        if (screen is AbstractContainerScreen<*> && (openedByMod || working)) return false
        return true
    }

    private fun targetKey(): String = when (mode) {
        Mode.SELL -> villagers.getOrNull(cursor)?.uuid ?: "none"
        Mode.FETCH -> inputs.getOrNull(cursor)?.let { "${it.x},${it.y},${it.z}" } ?: "none"
        Mode.STORE -> outputs.getOrNull(cursor)?.let { "${it.x},${it.y},${it.z}" } ?: "none"
    }

    private fun once(key: String, alert: () -> Unit) {
        if (told.add(key)) alert()
    }

    private fun emptySlots(player: Player) = stacks(player).count { it.isEmpty }

    private fun hasPartialPickup(player: Player): Boolean =
        stacks(player).any { stack ->
            !stack.isEmpty && Settings.pickup.contains(idOf(stack)) && stack.count < stack.maxStackSize
        }

    private fun mergeRoom(player: Player, id: String): Int =
        stacks(player).filter { idOf(it) == id }.sumOf { (it.maxStackSize - it.count).coerceAtLeast(0) }

    private fun count(player: Player, id: String): Int {
        if (id.isBlank()) return 0
        return stacks(player).filter { idOf(it) == id }.sumOf { it.count }
    }

    private fun deferTrade(minecraft: Minecraft, player: Player) {
        tradeStep = 0
        closeWork(minecraft)
        resumeSell = cursor
        if (Settings.deposit.sumOf { count(player, it) } > 0) startWalk(minecraft, Mode.STORE, 0)
        else afterVillagers(minecraft, player)
    }

    private fun resultFits(player: Player): Boolean {
        val recipe = Settings.recipe()
        return saleFits(piles(player), prices(recipe), recipe.result, recipe.resultCount, stackLimit(player, recipe.result))
    }

    private fun prices(recipe: Recipe): List<Price> = buildList {
        if (recipe.costA.isNotBlank() && recipe.costACount > 0) add(Price(recipe.costA, recipe.costACount))
        if (recipe.costB.isNotBlank() && recipe.costBCount > 0) add(Price(recipe.costB, recipe.costBCount))
    }

    private fun piles(player: Player): List<Pile> = stacks(player).map { pileOf(it) }

    private fun pileOf(stack: ItemStack?): Pile {
        if (stack == null || stack.isEmpty) return Pile("", 0, 1)
        return Pile(idOf(stack), stack.count, stack.maxStackSize.coerceAtLeast(1))
    }

    private fun stackLimit(player: Player, id: String): Int {
        stacks(player).firstOrNull { !it.isEmpty && idOf(it) == id }?.let { return it.maxStackSize.coerceAtLeast(1) }
        val parsed = Identifier.tryParse(id) ?: return 64
        if (!BuiltInRegistries.ITEM.containsKey(parsed)) return 64
        return ItemStack(BuiltInRegistries.ITEM.getValue(parsed)).maxStackSize.coerceAtLeast(1)
    }

    /** Chest shift-click and the trade result only land in these 36 slots. */
    private fun stacks(player: Player): List<ItemStack> = player.inventory.nonEquipmentItems

    private fun idOf(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        return BuiltInRegistries.ITEM.getKey(stack.item).toString()
    }

    private fun view(offer: MerchantOffer): OfferView {
        val second = offer.costB
        return OfferView(
            idOf(offer.costA),
            offer.costA.count,
            if (second.isEmpty) "" else idOf(second),
            second.count,
            idOf(offer.result),
            offer.result.count,
            offer.isOutOfStock,
        )
    }
}
