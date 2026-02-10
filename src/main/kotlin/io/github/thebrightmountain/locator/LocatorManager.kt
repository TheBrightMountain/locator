package io.github.thebrightmountain.locator

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.color.Color
import com.github.retrooper.packetevents.protocol.world.waypoint.EmptyWaypointInfo
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint
import com.github.retrooper.packetevents.protocol.world.waypoint.Vec3iWaypointInfo
import com.github.retrooper.packetevents.protocol.world.waypoint.WaypointIcon
import com.github.retrooper.packetevents.util.Either
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint.Operation
import kotlin.math.sqrt
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocatorManager(private val plugin: JavaPlugin) : LocatorAPI {

    private val trackedPlayers = ConcurrentHashMap<UUID, LocatorData>()
    private val storage = LocatorStorage(plugin)
    private var updateTask: BukkitTask? = null

    override var stealthEnabled: Boolean = true
    override var anchorEnabled: Boolean = false
    var projectionDistance: Double = 50.0
        private set
    var updateInterval: Long = 1L
        private set
    var transmitRange: Double = 999999.0
        private set

    // ==================== Lifecycle ====================

    fun start() {
        loadConfig()
        loadData()
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { updateWaypoints() }, 0L, updateInterval)
        plugin.logger.info("Locator tracking system started")
    }

    fun stop() {
        updateTask?.cancel()
        updateTask = null
        for ((_, data) in trackedPlayers) {
            untrackAllViewers(data)
        }
        saveConfig()
        saveData()
        trackedPlayers.clear()
        plugin.logger.info("Locator tracking system stopped")
    }

    private fun loadConfig() {
        val config = plugin.config
        stealthEnabled = config.getBoolean("stealth-enabled", true)
        anchorEnabled = config.getBoolean("anchor-enabled", false)
        projectionDistance = config.getDouble("projection-distance", 50.0)
        updateInterval = config.getLong("update-interval", 1L)
        transmitRange = config.getDouble("transmit-range", 999999.0)
    }

    fun saveConfig() {
        val config = plugin.config
        config.set("stealth-enabled", stealthEnabled)
        config.set("anchor-enabled", anchorEnabled)
        config.set("projection-distance", projectionDistance)
        config.set("update-interval", updateInterval)
        config.set("transmit-range", transmitRange)
        plugin.saveConfig()
    }

    fun saveData() {
        storage.save(trackedPlayers)
    }

    private fun loadData() {
        val saved = storage.load()

        for ((playerId, savedData) in saved) {
            val data = LocatorData(
                trackedPlayerId = playerId,
                visibilityMode = savedData.visibilityMode,
                visibilityList = savedData.visibilityList,
                anchorLocation = savedData.anchorLocation,
                anchorDimension = savedData.anchorDimension,
                color = savedData.color
            )
            Bukkit.getPlayer(playerId)?.let {
                data.lastWorldName = it.world.name
                data.lastLocation = it.location.clone()
            }
            trackedPlayers[playerId] = data
        }
        plugin.logger.info("Loaded ${saved.size} locator(s) from disk")
    }

    // ==================== Player Locator CRUD ====================

    override fun addPlayerLocator(player: Player): Boolean {
        if (trackedPlayers.containsKey(player.uniqueId)) return false

        hideFromVanillaLocator(player)

        val data = LocatorData(
            trackedPlayerId = player.uniqueId,
            lastWorldName = player.world.name
        )
        trackedPlayers[player.uniqueId] = data

        val waypointId = getWaypointUUID(player.uniqueId)
        for (viewer in Bukkit.getOnlinePlayers()) {
            if (shouldViewerSeeMain(data, viewer)) {
                sendWaypointPacket(viewer, waypointId, player.location, Operation.TRACK, data.color)
                data.activeViewers.add(viewer.uniqueId)
            }
        }

        plugin.logger.info("Added locator for player ${player.name}")
        return true
    }

    override fun removePlayerLocator(player: Player): Boolean {
        val data = trackedPlayers.remove(player.uniqueId) ?: return false
        untrackAllViewers(data)
        restoreWaypointTransmitRange(player)
        plugin.logger.info("Removed locator for player ${player.name}")
        return true
    }

    override fun hasLocator(player: Player): Boolean {
        return trackedPlayers.containsKey(player.uniqueId)
    }

    fun getLocatorData(player: Player): LocatorData? {
        return trackedPlayers[player.uniqueId]
    }

    override fun setLocatorColor(player: Player, color: Int?) {
        val data = trackedPlayers[player.uniqueId] ?: return
        data.color = color
        // Re-track all active viewers so they get the new color
        for (viewerId in data.activeViewers.toSet()) {
            val viewer = Bukkit.getPlayer(viewerId) ?: continue
            sendUntrackPacket(viewer, getWaypointUUID(data.trackedPlayerId))
            data.activeViewers.remove(viewerId)
        }
        for (viewerId in data.anchorViewers.toSet()) {
            val viewer = Bukkit.getPlayer(viewerId) ?: continue
            sendUntrackPacket(viewer, getAnchorUUID(data.trackedPlayerId))
            data.anchorViewers.remove(viewerId)
        }
        // The update loop will re-TRACK with the new color on the next tick
    }

    override fun getLocatorColor(player: Player): Int? {
        return trackedPlayers[player.uniqueId]?.color
    }

    override fun getTrackedPlayerIds(): Set<UUID> {
        return trackedPlayers.keys.toSet()
    }

    // ==================== Visibility Control ====================

    override fun exposeTo(target: Player, viewer: Player): Boolean {
        val data = trackedPlayers[target.uniqueId] ?: return false
        data.exposeTo(viewer.uniqueId)
        refreshAllViewers(data)
        return true
    }

    override fun hideFrom(target: Player, viewer: Player): Boolean {
        val data = trackedPlayers[target.uniqueId] ?: return false
        data.hideFrom(viewer.uniqueId)
        refreshAllViewers(data)
        return true
    }

    override fun exposeToAll(target: Player): Boolean {
        val data = trackedPlayers[target.uniqueId] ?: return false
        data.exposeToAll()
        refreshAllViewers(data)
        return true
    }

    override fun hideFromAll(target: Player): Boolean {
        val data = trackedPlayers[target.uniqueId] ?: return false
        data.hideFromAll()
        refreshAllViewers(data)
        return true
    }

    override fun setInterval(ticks: Long) {
        updateInterval = ticks
        updateTask?.cancel()
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { updateWaypoints() }, 0L, updateInterval)
    }

    override fun disableAnchorAndClear() {
        anchorEnabled = false
        for ((_, data) in trackedPlayers) {
            clearAnchor(data)
        }
    }

    // ==================== Player Events ====================

    fun handlePlayerJoin(player: Player) {
        val ownData = trackedPlayers[player.uniqueId]
        if (ownData != null) {
            hideFromVanillaLocator(player)
            ownData.lastWorldName = player.world.name
            ownData.lastLocation = player.location.clone()
        }

        for ((_, data) in trackedPlayers) {
            if (shouldViewerSeeMain(data, player)) {
                val trackedPlayer = Bukkit.getPlayer(data.trackedPlayerId)
                if (trackedPlayer != null && trackedPlayer.isOnline) {
                    sendWaypointPacket(player, getWaypointUUID(data.trackedPlayerId), trackedPlayer.location, Operation.TRACK, data.color)
                    data.activeViewers.add(player.uniqueId)
                }
            }
            val anchorLoc = data.anchorLocation
            if (anchorLoc != null && shouldViewerSeeAnchor(data, player)) {
                sendStaticWaypointPacket(player, getAnchorUUID(data.trackedPlayerId), anchorLoc, Operation.TRACK, data.color)
                data.anchorViewers.add(player.uniqueId)
            }
        }
    }

    fun handlePlayerQuit(player: Player) {
        for ((_, data) in trackedPlayers) {
            data.activeViewers.remove(player.uniqueId)
            data.anchorViewers.remove(player.uniqueId)
        }
    }

    fun handleWorldChange(player: Player, fromWorldName: String) {
        val data = trackedPlayers[player.uniqueId] ?: return

        // Check if player returned to anchor dimension
        if (data.anchorDimension == player.world.name) {
            clearAnchor(data)
        }

        if (anchorEnabled) {
            clearAnchor(data)
            val lastLoc = data.lastLocation
            if (lastLoc != null) {
                data.anchorLocation = lastLoc.clone()
                data.anchorDimension = fromWorldName
            }
        }

        data.lastWorldName = player.world.name
    }

    // ==================== Core Update Loop ====================

    private fun updateWaypoints() {
        val onlinePlayers = Bukkit.getOnlinePlayers()

        for ((playerId, data) in trackedPlayers) {
            val player = Bukkit.getPlayer(playerId)

            // Update last known position for anchor placement
            if (player != null && player.isOnline) {
                data.lastWorldName = player.world.name
                data.lastLocation = player.location.clone()
            }

            // --- Main waypoint ---
            val waypointUUID = getWaypointUUID(playerId)
            if (player != null && player.isOnline) {
                val playerLoc = player.location
                for (viewer in onlinePlayers) {
                    val shouldSee = shouldViewerSeeMain(data, viewer)
                    val isTracked = viewer.uniqueId in data.activeViewers

                    when {
                        shouldSee && !isTracked -> {
                            sendWaypointPacket(viewer, waypointUUID, playerLoc, Operation.TRACK, data.color)
                            data.activeViewers.add(viewer.uniqueId)
                        }
                        shouldSee && isTracked -> {
                            sendWaypointPacket(viewer, waypointUUID, playerLoc, Operation.UPDATE, data.color)
                        }
                        !shouldSee && isTracked -> {
                            sendUntrackPacket(viewer, waypointUUID)
                            data.activeViewers.remove(viewer.uniqueId)
                        }
                    }
                }
            } else {
                for (viewerId in data.activeViewers.toSet()) {
                    Bukkit.getPlayer(viewerId)?.let { sendUntrackPacket(it, waypointUUID) }
                }
                data.activeViewers.clear()
            }

            // --- Anchor waypoint ---
            val anchorLoc = data.anchorLocation
            val anchorUUID = getAnchorUUID(playerId)

            if (anchorLoc != null) {
                for (viewer in onlinePlayers) {
                    val shouldSee = shouldViewerSeeAnchor(data, viewer)
                    val isTracked = viewer.uniqueId in data.anchorViewers

                    when {
                        shouldSee && !isTracked -> {
                            sendStaticWaypointPacket(viewer, anchorUUID, anchorLoc, Operation.TRACK, data.color)
                            data.anchorViewers.add(viewer.uniqueId)
                        }
                        !shouldSee && isTracked -> {
                            sendUntrackPacket(viewer, anchorUUID)
                            data.anchorViewers.remove(viewer.uniqueId)
                        }
                    }
                }
            } else {
                for (viewerId in data.anchorViewers.toSet()) {
                    Bukkit.getPlayer(viewerId)?.let { sendUntrackPacket(it, anchorUUID) }
                }
                data.anchorViewers.clear()
            }
        }
    }

    // ==================== Visibility Helpers ====================

    private fun shouldViewerSeeMain(data: LocatorData, viewer: Player): Boolean {
        val trackedPlayer = Bukkit.getPlayer(data.trackedPlayerId) ?: return false
        if (!trackedPlayer.isOnline) return false
        if (viewer.world != trackedPlayer.world) return false
        return data.isVisibleToWithStealth(viewer.uniqueId, stealthEnabled)
    }

    private fun shouldViewerSeeAnchor(data: LocatorData, viewer: Player): Boolean {
        val anchorDim = data.anchorDimension ?: return false
        if (viewer.world.name != anchorDim) return false
        if (viewer.uniqueId == data.trackedPlayerId) return false
        return data.isVisibleTo(viewer.uniqueId)
    }

    private fun refreshViewerState(data: LocatorData, viewer: Player) {
        val player = Bukkit.getPlayer(data.trackedPlayerId)
        val waypointUUID = getWaypointUUID(data.trackedPlayerId)

        val isTracked = viewer.uniqueId in data.activeViewers
        if (player != null && player.isOnline && shouldViewerSeeMain(data, viewer)) {
            if (!isTracked) {
                sendWaypointPacket(viewer, waypointUUID, player.location, Operation.TRACK, data.color)
                data.activeViewers.add(viewer.uniqueId)
            }
        } else if (isTracked) {
            sendUntrackPacket(viewer, waypointUUID)
            data.activeViewers.remove(viewer.uniqueId)
        }

        val anchorLoc = data.anchorLocation
        val anchorUUID = getAnchorUUID(data.trackedPlayerId)
        val isAnchorTracked = viewer.uniqueId in data.anchorViewers
        if (anchorLoc != null && shouldViewerSeeAnchor(data, viewer)) {
            if (!isAnchorTracked) {
                sendStaticWaypointPacket(viewer, anchorUUID, anchorLoc, Operation.TRACK, data.color)
                data.anchorViewers.add(viewer.uniqueId)
            }
        } else if (isAnchorTracked) {
            sendUntrackPacket(viewer, anchorUUID)
            data.anchorViewers.remove(viewer.uniqueId)
        }
    }

    private fun refreshAllViewers(data: LocatorData) {
        for (viewer in Bukkit.getOnlinePlayers()) {
            refreshViewerState(data, viewer)
        }
    }

    private fun untrackAllViewers(data: LocatorData) {
        val waypointUUID = getWaypointUUID(data.trackedPlayerId)
        for (viewerId in data.activeViewers.toSet()) {
            Bukkit.getPlayer(viewerId)?.let { sendUntrackPacket(it, waypointUUID) }
        }
        data.activeViewers.clear()

        val anchorUUID = getAnchorUUID(data.trackedPlayerId)
        for (viewerId in data.anchorViewers.toSet()) {
            Bukkit.getPlayer(viewerId)?.let { sendUntrackPacket(it, anchorUUID) }
        }
        data.anchorViewers.clear()
    }

    private fun clearAnchor(data: LocatorData) {
        val anchorUUID = getAnchorUUID(data.trackedPlayerId)
        for (viewerId in data.anchorViewers.toSet()) {
            Bukkit.getPlayer(viewerId)?.let { sendUntrackPacket(it, anchorUUID) }
        }
        data.anchorViewers.clear()
        data.anchorLocation = null
        data.anchorDimension = null
    }

    // ==================== Packet Sending ====================

    private fun projectLocation(viewerLoc: Location, targetLoc: Location): Vector3i {
        val dx = targetLoc.x - viewerLoc.x
        val dy = targetLoc.y - viewerLoc.y
        val dz = targetLoc.z - viewerLoc.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        if (dist < 0.01) {
            return Vector3i(targetLoc.blockX, targetLoc.blockY, targetLoc.blockZ)
        }

        val scale = projectionDistance / dist
        return Vector3i(
            (viewerLoc.x + dx * scale).toInt(),
            (viewerLoc.y + dy * scale).toInt(),
            (viewerLoc.z + dz * scale).toInt()
        )
    }

    private fun makeIcon(colorRgb: Int?): WaypointIcon {
        val color = if (colorRgb != null) Color(colorRgb) else null
        return WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, color)
    }

    private fun sendWaypointPacket(viewer: Player, waypointId: UUID, targetLocation: Location, operation: Operation, colorRgb: Int? = null) {
        val pos = projectLocation(viewer.location, targetLocation)
        val waypoint = TrackedWaypoint(Either.createLeft(waypointId), makeIcon(colorRgb), Vec3iWaypointInfo(pos))
        PacketEvents.getAPI().playerManager.sendPacket(viewer, WrapperPlayServerWaypoint(operation, waypoint))
    }

    private fun sendStaticWaypointPacket(viewer: Player, waypointId: UUID, location: Location, operation: Operation, colorRgb: Int? = null) {
        val pos = Vector3i(location.blockX, location.blockY, location.blockZ)
        val waypoint = TrackedWaypoint(Either.createLeft(waypointId), makeIcon(colorRgb), Vec3iWaypointInfo(pos))
        PacketEvents.getAPI().playerManager.sendPacket(viewer, WrapperPlayServerWaypoint(operation, waypoint))
    }

    private fun sendUntrackPacket(viewer: Player, waypointId: UUID) {
        val waypoint = TrackedWaypoint(Either.createLeft(waypointId), makeIcon(null), EmptyWaypointInfo.EMPTY)
        PacketEvents.getAPI().playerManager.sendPacket(viewer, WrapperPlayServerWaypoint(Operation.UNTRACK, waypoint))
    }

    // ==================== UUID Generation ====================

    private fun getWaypointUUID(playerUUID: UUID): UUID {
        return UUID.nameUUIDFromBytes("locator_main_$playerUUID".toByteArray(StandardCharsets.UTF_8))
    }

    private fun getAnchorUUID(playerUUID: UUID): UUID {
        return UUID.nameUUIDFromBytes("locator_anchor_$playerUUID".toByteArray(StandardCharsets.UTF_8))
    }

    // ==================== Vanilla Locator Control ====================

    private fun hideFromVanillaLocator(player: Player) {
        player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)?.baseValue = 0.0
    }

    private fun restoreWaypointTransmitRange(player: Player) {
        player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)?.baseValue = transmitRange
    }
}
