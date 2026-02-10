package io.github.thebrightmountain.locator

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.UUID

/**
 * Handles saving and loading locator data to/from disk.
 */
class LocatorStorage(private val plugin: Plugin) {

    private val dataFile = File(plugin.dataFolder, "locators.yml")

    fun save(trackedPlayers: Map<UUID, LocatorData>) {
        val config = YamlConfiguration()

        for ((playerId, data) in trackedPlayers) {
            val path = "players.$playerId"

            config.set("$path.visibilityMode", data.visibilityMode.name)
            config.set("$path.visibilityList", data.visibilityList.map { it.toString() })
            if (data.color != null) {
                config.set("$path.color", String.format("#%06X", data.color))
            }

            // Save anchor location if present
            val anchorLoc = data.anchorLocation
            val anchorDim = data.anchorDimension
            if (anchorLoc != null && anchorDim != null) {
                config.set("$path.anchor.world", anchorDim)
                config.set("$path.anchor.x", anchorLoc.blockX)
                config.set("$path.anchor.y", anchorLoc.blockY)
                config.set("$path.anchor.z", anchorLoc.blockZ)
            }
        }

        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        config.save(dataFile)
        plugin.logger.info("Saved ${trackedPlayers.size} locator(s) to disk")
    }

    fun load(): Map<UUID, SavedLocatorData> {
        if (!dataFile.exists()) {
            return emptyMap()
        }

        val config = YamlConfiguration.loadConfiguration(dataFile)
        val result = mutableMapOf<UUID, SavedLocatorData>()

        val playersSection = config.getConfigurationSection("players")
        if (playersSection != null) {
            for (playerIdStr in playersSection.getKeys(false)) {
                try {
                    val playerId = UUID.fromString(playerIdStr)
                    val path = "players.$playerIdStr"

                    val visibilityMode = try {
                        VisibilityMode.valueOf(config.getString("$path.visibilityMode") ?: "EXPOSE_TO_ALL")
                    } catch (e: Exception) {
                        VisibilityMode.EXPOSE_TO_ALL
                    }

                    val visibilityList = config.getStringList("$path.visibilityList")
                        .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                        .toMutableSet()

                    // Load anchor location
                    var anchorLocation: Location? = null
                    var anchorDimension: String? = null
                    val anchorWorldName = config.getString("$path.anchor.world")
                    if (anchorWorldName != null) {
                        anchorDimension = anchorWorldName
                        val world = Bukkit.getWorld(anchorWorldName)
                        if (world != null) {
                            anchorLocation = Location(
                                world,
                                config.getInt("$path.anchor.x").toDouble(),
                                config.getInt("$path.anchor.y").toDouble(),
                                config.getInt("$path.anchor.z").toDouble()
                            )
                        }
                    }

                    val color = config.getString("$path.color")?.let { parseHexColor(it) }

                    result[playerId] = SavedLocatorData(
                        playerId = playerId,
                        visibilityMode = visibilityMode,
                        visibilityList = visibilityList,
                        anchorLocation = anchorLocation,
                        anchorDimension = anchorDimension,
                        color = color
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load locator data for $playerIdStr: ${e.message}")
                }
            }
        }

        plugin.logger.info("Loaded ${result.size} locator(s) from disk")
        return result
    }

    fun clear() {
        if (dataFile.exists()) {
            dataFile.delete()
        }
    }

    companion object {
        fun parseHexColor(hex: String): Int? {
            val stripped = hex.removePrefix("#")
            return try {
                stripped.toInt(16)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

/**
 * Represents saved locator data for a single player
 */
data class SavedLocatorData(
    val playerId: UUID,
    val visibilityMode: VisibilityMode,
    val visibilityList: MutableSet<UUID>,
    val anchorLocation: Location?,
    val anchorDimension: String?,
    val color: Int? = null
)
