package io.github.thebrightmountain.locator

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class LocatorCommand(private val manager: LocatorManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "add" -> handleAdd(sender, args)
            "remove" -> handleRemove(sender, args)
            "modify" -> handleModify(sender, args)
            "config" -> handleConfig(sender, args)
            else -> sendUsage(sender)
        }

        return true
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("Usage: /locator add [player|point] <name>")
            return
        }

        when (args[1].lowercase()) {
            "player" -> addPlayer(sender, args[2])
            "point" -> sender.sendMessage("Point tracking not yet implemented")
            else -> sender.sendMessage("Unknown type: ${args[1]}. Use 'player' or 'point'")
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("Usage: /locator remove [player|point] <name>")
            return
        }

        when (args[1].lowercase()) {
            "player" -> removePlayer(sender, args[2])
            "point" -> sender.sendMessage("Point tracking not yet implemented")
            else -> sender.sendMessage("Unknown type: ${args[1]}. Use 'player' or 'point'")
        }
    }

    private fun handleModify(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage("Usage: /locator modify [player|point] <name> <option> [value]")
            sender.sendMessage("Options: style, color, exposeto, hidefrom, exposetoall, hidefromall")
            return
        }

        when (args[1].lowercase()) {
            "player" -> modifyPlayer(sender, args)
            "point" -> sender.sendMessage("Point tracking not yet implemented")
            else -> sender.sendMessage("Unknown type: ${args[1]}. Use 'player' or 'point'")
        }
    }

    private fun handleConfig(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("Usage: /locator config [stealth|anchor|interval] <value>")
            val stealthStatus = if (manager.stealthEnabled) "enabled" else "disabled"
            val anchorStatus = if (manager.anchorEnabled) "enabled" else "disabled"
            sender.sendMessage("Stealth: $stealthStatus | Anchor: $anchorStatus | Interval: ${manager.updateInterval} ticks")
            return
        }

        val setting = args[1].lowercase()

        if (args.size < 3) {
            when (setting) {
                "stealth" -> sender.sendMessage("Stealth: ${if (manager.stealthEnabled) "enabled" else "disabled"}")
                "anchor" -> sender.sendMessage("Anchor: ${if (manager.anchorEnabled) "enabled" else "disabled"}")
                "interval" -> sender.sendMessage("Update interval: ${manager.updateInterval} ticks")
                else -> sender.sendMessage("Unknown config option: $setting")
            }
            return
        }

        val value = args[2].lowercase()

        when (setting) {
            "stealth", "anchor" -> {
                val enabled = when (value) {
                    "enable", "on", "true" -> true
                    "disable", "off", "false" -> false
                    else -> {
                        sender.sendMessage("Usage: /locator config $setting [enable|disable]")
                        return
                    }
                }
                when (setting) {
                    "stealth" -> {
                        manager.stealthEnabled = enabled
                        val state = if (enabled) "enabled" else "disabled"
                        sender.sendMessage("Stealth $state globally (sneaking/mob head/pumpkin/invisibility)")
                    }
                    "anchor" -> {
                        if (enabled) {
                            manager.anchorEnabled = true
                        } else {
                            manager.disableAnchorAndClear()
                        }
                        val state = if (enabled) "enabled" else "disabled"
                        sender.sendMessage("Anchor $state globally (leaves waypoint at last position when changing dimension)")
                    }
                }
            }
            "interval" -> {
                val ticks = value.toLongOrNull()
                if (ticks == null || ticks < 1) {
                    sender.sendMessage("Interval must be a positive integer (in ticks). 1 tick = 50ms")
                    return
                }
                manager.setInterval(ticks)
                sender.sendMessage("Update interval set to $ticks tick(s)")
            }
            else -> sender.sendMessage("Unknown config option: $setting. Use 'stealth', 'anchor', or 'interval'")
        }
    }

    private fun modifyPlayer(sender: CommandSender, args: Array<out String>) {
        val targetName = args[2]
        val option = args[3].lowercase()

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            sender.sendMessage("Player '$targetName' is not online")
            return
        }

        if (!manager.hasLocator(target)) {
            sender.sendMessage("Player '${target.name}' doesn't have an active locator")
            return
        }

        when (option) {
            "exposeto" -> {
                if (args.size < 5) {
                    sender.sendMessage("Usage: /locator modify player <name> exposeto <viewer>")
                    return
                }
                val viewer = Bukkit.getPlayer(args[4])
                if (viewer == null) {
                    sender.sendMessage("Viewer '${args[4]}' is not online")
                    return
                }
                if (manager.exposeTo(target, viewer)) {
                    sender.sendMessage("${target.name}'s locator is now visible to ${viewer.name}")
                }
            }
            "hidefrom" -> {
                if (args.size < 5) {
                    sender.sendMessage("Usage: /locator modify player <name> hidefrom <viewer>")
                    return
                }
                val viewer = Bukkit.getPlayer(args[4])
                if (viewer == null) {
                    sender.sendMessage("Viewer '${args[4]}' is not online")
                    return
                }
                if (manager.hideFrom(target, viewer)) {
                    sender.sendMessage("${target.name}'s locator is now hidden from ${viewer.name}")
                }
            }
            "exposetoall" -> {
                if (manager.exposeToAll(target)) {
                    sender.sendMessage("${target.name}'s locator is now visible to everyone")
                }
            }
            "hidefromall" -> {
                if (manager.hideFromAll(target)) {
                    sender.sendMessage("${target.name}'s locator is now hidden from everyone")
                }
            }
            "color" -> {
                if (args.size < 5) {
                    val data = manager.getLocatorData(target)
                    val current = data?.color?.let { String.format("#%06X", it) } ?: "default"
                    sender.sendMessage("${target.name}'s locator color: $current")
                    sender.sendMessage("Usage: /locator modify player <name> color <#hex|reset>")
                    return
                }
                val colorArg = args[4].lowercase()
                if (colorArg == "reset" || colorArg == "default") {
                    manager.setLocatorColor(target, null)
                    sender.sendMessage("${target.name}'s locator color reset to default")
                } else {
                    val rgb = LocatorStorage.parseHexColor(colorArg)
                    if (rgb == null) {
                        sender.sendMessage("Invalid hex color: ${args[4]}. Use format #RRGGBB (e.g. #FF0000)")
                        return
                    }
                    manager.setLocatorColor(target, rgb)
                    sender.sendMessage("${target.name}'s locator color set to ${String.format("#%06X", rgb)}")
                }
            }
            "style" -> sender.sendMessage("Style modification not yet implemented")
            else -> sender.sendMessage("Unknown option: $option")
        }
    }

    private fun addPlayer(sender: CommandSender, playerName: String) {
        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            sender.sendMessage("Player '$playerName' is not online")
            return
        }

        if (manager.hasLocator(target)) {
            sender.sendMessage("Player '${target.name}' already has an active locator")
            return
        }

        if (manager.addPlayerLocator(target)) {
            sender.sendMessage("Added locator for player '${target.name}'")
        } else {
            sender.sendMessage("Failed to add locator for player '${target.name}'")
        }
    }

    private fun removePlayer(sender: CommandSender, playerName: String) {
        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            sender.sendMessage("Player '$playerName' is not online")
            return
        }

        if (!manager.hasLocator(target)) {
            sender.sendMessage("Player '${target.name}' doesn't have an active locator")
            return
        }

        if (manager.removePlayerLocator(target)) {
            sender.sendMessage("Removed locator for player '${target.name}'")
        } else {
            sender.sendMessage("Failed to remove locator for player '${target.name}'")
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("Usage: /locator [add|remove|modify|config] ...")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("add", "remove", "modify", "config").filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                when (args[0].lowercase()) {
                    "add", "remove", "modify" -> listOf("player", "point").filter { it.startsWith(args[1].lowercase()) }
                    "config" -> listOf("stealth", "anchor", "interval").filter { it.startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "add" -> {
                        if (args[1].lowercase() == "player") {
                            Bukkit.getOnlinePlayers()
                                .filter { !manager.hasLocator(it) }
                                .map { it.name }
                                .filter { it.lowercase().startsWith(args[2].lowercase()) }
                        } else emptyList()
                    }
                    "remove", "modify" -> {
                        if (args[1].lowercase() == "player") {
                            Bukkit.getOnlinePlayers()
                                .filter { manager.hasLocator(it) }
                                .map { it.name }
                                .filter { it.lowercase().startsWith(args[2].lowercase()) }
                        } else emptyList()
                    }
                    "config" -> {
                        when (args[1].lowercase()) {
                            "stealth", "anchor" -> listOf("enable", "disable").filter { it.startsWith(args[2].lowercase()) }
                            "interval" -> listOf("1", "2", "3").filter { it.startsWith(args[2]) }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            4 -> {
                if (args[0].lowercase() == "modify" && args[1].lowercase() == "player") {
                    listOf("style", "color", "exposeto", "hidefrom", "exposetoall", "hidefromall")
                        .filter { it.startsWith(args[3].lowercase()) }
                } else {
                    emptyList()
                }
            }
            5 -> {
                if (args[0].lowercase() == "modify" && args[1].lowercase() == "player") {
                    when (args[3].lowercase()) {
                        "color" -> {
                            listOf("reset", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF")
                                .filter { it.lowercase().startsWith(args[4].lowercase()) }
                        }
                        "exposeto", "hidefrom" -> {
                            val target = Bukkit.getPlayer(args[2])
                            Bukkit.getOnlinePlayers()
                                .filter { it != target }
                                .map { it.name }
                                .filter { it.lowercase().startsWith(args[4].lowercase()) }
                        }
                        else -> emptyList()
                    }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
