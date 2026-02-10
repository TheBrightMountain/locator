package io.github.thebrightmountain.locator

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin

class Locator : JavaPlugin() {

    lateinit var locatorManager: LocatorManager
        private set

    override fun onLoad() {
        // PacketEvents must be created during onLoad
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        @Suppress("DEPRECATION")
        PacketEvents.getAPI().settings
            .checkForUpdates(false)
            .bStats(false)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        saveDefaultConfig()

        locatorManager = LocatorManager(this)
        locatorManager.start()

        // Register API for other plugins
        server.servicesManager.register(LocatorAPI::class.java, locatorManager, this, ServicePriority.Normal)

        // Register command
        val command = getCommand("locator")
        if (command != null) {
            val locatorCommand = LocatorCommand(locatorManager)
            command.setExecutor(locatorCommand)
            command.tabCompleter = locatorCommand
        } else {
            logger.severe("Failed to register /locator command!")
        }

        // Register event listener
        server.pluginManager.registerEvents(LocatorListener(this, locatorManager), this)

        logger.info("Locator plugin enabled!")
    }

    override fun onDisable() {
        server.servicesManager.unregisterAll(this)

        if (::locatorManager.isInitialized) {
            locatorManager.stop()
        }

        // Terminate PacketEvents
        PacketEvents.getAPI().terminate()

        logger.info("Locator plugin disabled!")
    }
}
