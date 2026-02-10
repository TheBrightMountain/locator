package io.github.thebrightmountain.locator

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class VisibilityMode {
    EXPOSE_TO_ALL,
    HIDE_FROM_ALL,
    EXPOSE_TO_LIST
}

data class LocatorData(
    val trackedPlayerId: UUID,
    var visibilityMode: VisibilityMode = VisibilityMode.EXPOSE_TO_ALL,
    val visibilityList: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
    var anchorLocation: Location? = null,
    var anchorDimension: String? = null,
    var lastWorldName: String? = null,
    var lastLocation: Location? = null,
    /** Waypoint color as RGB int (null = default white) */
    var color: Int? = null,
    val activeViewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
    val anchorViewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
) {

    companion object {
        private val MOB_HEADS = setOf(
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.ZOMBIE_HEAD,
            Material.PLAYER_HEAD,
            Material.CREEPER_HEAD,
            Material.DRAGON_HEAD,
            Material.PIGLIN_HEAD
        )
    }

    fun isCurrentlyStealthed(): Boolean {
        val player = Bukkit.getPlayer(trackedPlayerId) ?: return false
        if (player.isSneaking) return true
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return true
        val helmet = player.inventory.getItem(EquipmentSlot.HEAD)
        if (helmet.type == Material.CARVED_PUMPKIN) return true
        if (helmet.type in MOB_HEADS) return true
        return false
    }

    fun isVisibleTo(viewerId: UUID): Boolean {
        if (viewerId == trackedPlayerId) return false
        return when (visibilityMode) {
            VisibilityMode.EXPOSE_TO_ALL -> true
            VisibilityMode.HIDE_FROM_ALL -> false
            VisibilityMode.EXPOSE_TO_LIST -> viewerId in visibilityList
        }
    }

    fun isVisibleToWithStealth(viewerId: UUID, globalStealthEnabled: Boolean): Boolean {
        if (globalStealthEnabled && isCurrentlyStealthed()) return false
        return isVisibleTo(viewerId)
    }

    fun exposeTo(viewerId: UUID) {
        visibilityList.add(viewerId)
        if (visibilityMode != VisibilityMode.EXPOSE_TO_LIST) {
            visibilityMode = VisibilityMode.EXPOSE_TO_LIST
        }
    }

    fun hideFrom(viewerId: UUID) {
        visibilityList.remove(viewerId)
    }

    fun exposeToAll() {
        visibilityMode = VisibilityMode.EXPOSE_TO_ALL
        visibilityList.clear()
    }

    fun hideFromAll() {
        visibilityMode = VisibilityMode.HIDE_FROM_ALL
        visibilityList.clear()
    }
}
