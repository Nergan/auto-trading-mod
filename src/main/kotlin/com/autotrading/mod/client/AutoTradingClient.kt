package com.autotrading.mod.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

class AutoTradingClient : ClientModInitializer {
    override fun onInitializeClient() {
        Settings.load()
        ClientTickEvents.START_CLIENT_TICK.register { Session.eatKeys(it) }
        ClientTickEvents.END_CLIENT_TICK.register { Session.tick(it) }
    }
}
