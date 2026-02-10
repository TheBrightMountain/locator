package io.github.thebrightmountain.locator

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Public API for the Locator plugin.
 *
 * Other plugins can access this via Bukkit's ServicesManager:
 * ```
 * val api = Bukkit.getServicesManager().load(LocatorAPI::class.java)
 * api?.addPlayerLocator(somePlayer)
 * ```
 */
interface LocatorAPI {

    // Locator CRUD
    fun addPlayerLocator(player: Player): Boolean
    fun removePlayerLocator(player: Player): Boolean
    fun hasLocator(player: Player): Boolean

    // Visibility
    fun exposeTo(target: Player, viewer: Player): Boolean
    fun hideFrom(target: Player, viewer: Player): Boolean
    fun exposeToAll(target: Player): Boolean
    fun hideFromAll(target: Player): Boolean

    // Color (RGB int, null = default)
    fun getLocatorColor(player: Player): Int?
    fun setLocatorColor(player: Player, color: Int?)

    // Global config
    var stealthEnabled: Boolean
    var anchorEnabled: Boolean
    fun disableAnchorAndClear()
    fun setInterval(ticks: Long)

    // Query
    fun getTrackedPlayerIds(): Set<UUID>
}
