# Locator

A Paper 1.21+ plugin that overrides the vanilla locator bar with custom waypoint tracking. Instead of using the built-in locator mechanics, this plugin sends waypoint packets directly to players, giving full control over who sees what.

Requires [PacketEvents](https://github.com/retrooper/packetevents) (bundled via shadow jar).

## Commands

All commands require the `locator.use` permission (default: op).

### Adding and removing locators

```
/locator add player <name>
```
Start tracking a player. Their position appears as a waypoint on the locator bar for all other players.

```
/locator remove player <name>
```
Stop tracking a player and remove their waypoint from everyone's locator bar.

### Modifying visibility

```
/locator modify player <name> exposeto <viewer>
```
Add a player to the visibility list. Switches to allowlist mode — only listed players can see the waypoint.

```
/locator modify player <name> hidefrom <viewer>
```
Remove a player from the visibility list. That player will no longer see the waypoint.

```
/locator modify player <name> exposetoall
```
Make the waypoint visible to everyone. Clears the visibility list.

```
/locator modify player <name> hidefromall
```
Hide the waypoint from everyone. Clears the visibility list.

### Color

```
/locator modify player <name> color <#RRGGBB>
```
Set the waypoint color using a hex color code (e.g. `#FF0000` for red). The color applies to both the main waypoint and any anchor waypoint.

```
/locator modify player <name> color reset
```
Reset the waypoint color back to default.

### Global config

```
/locator config
```
Show current config values.

```
/locator config stealth <enable|disable>
```
Toggle stealth mechanics globally. When enabled, tracked players can hide from the locator by:
- Sneaking
- Wearing a mob head (skeleton, wither skeleton, zombie, player, creeper, dragon, piglin)
- Wearing a carved pumpkin
- Having the Invisibility potion effect

```
/locator config anchor <enable|disable>
```
Toggle anchor waypoints globally. When enabled, a waypoint is left at the player's last position when they change dimensions (e.g. entering the Nether). The anchor is removed when the player returns to that dimension.

```
/locator config interval <ticks>
```
Set how often waypoint positions are updated. 1 tick = 50ms. Lower values are smoother but send more packets. Default: `1`.

## Configuration File

`plugins/locator/config.yml` is generated on first run:

| Key | Default | Description |
|-----|---------|-------------|
| `stealth-enabled` | `true` | Enable stealth mechanics (sneaking/mob head/pumpkin/invisibility) |
| `anchor-enabled` | `false` | Leave a waypoint at last position on dimension change |
| `projection-distance` | `50.0` | Distance to project waypoints from the viewer. Higher = smoother movement due to less integer rounding error |
| `update-interval` | `1` | Tick interval between waypoint position updates |
| `transmit-range` | `999999.0` | Waypoint transmit range restored when a locator is removed |

## API

Other plugins can interact with Locator through the `LocatorAPI` interface, registered via Bukkit's `ServicesManager`.

### Getting the API

```kotlin
val api = Bukkit.getServicesManager().load(LocatorAPI::class.java)
```

```java
LocatorAPI api = Bukkit.getServicesManager().load(LocatorAPI.class);
```

### Methods

#### Locator CRUD

| Method | Returns | Description |
|--------|---------|-------------|
| `addPlayerLocator(player)` | `Boolean` | Start tracking a player. Returns `false` if already tracked |
| `removePlayerLocator(player)` | `Boolean` | Stop tracking a player. Returns `false` if not tracked |
| `hasLocator(player)` | `Boolean` | Check if a player is being tracked |

#### Visibility

| Method | Returns | Description |
|--------|---------|-------------|
| `exposeTo(target, viewer)` | `Boolean` | Add `viewer` to `target`'s visibility list. Switches to allowlist mode |
| `hideFrom(target, viewer)` | `Boolean` | Remove `viewer` from `target`'s visibility list |
| `exposeToAll(target)` | `Boolean` | Make waypoint visible to everyone. Clears the list |
| `hideFromAll(target)` | `Boolean` | Hide waypoint from everyone. Clears the list |

#### Color

| Method | Returns | Description |
|--------|---------|-------------|
| `getLocatorColor(player)` | `Int?` | Get the waypoint color as an RGB int, or `null` for default |
| `setLocatorColor(player, color)` | `Unit` | Set waypoint color as RGB int (e.g. `0xFF0000`), or `null` to reset |

#### Global Config

| Method / Property | Type | Description |
|-------------------|------|-------------|
| `stealthEnabled` | `Boolean` | Get/set whether stealth mechanics are active |
| `anchorEnabled` | `Boolean` | Get/set whether anchor waypoints are active |
| `disableAnchorAndClear()` | `Unit` | Disable anchors and remove all existing anchor waypoints |
| `setInterval(ticks)` | `Unit` | Set the update interval and restart the update loop |

#### Query

| Method | Returns | Description |
|--------|---------|-------------|
| `getTrackedPlayerIds()` | `Set<UUID>` | Get the UUIDs of all currently tracked players |

### Example

```kotlin
// In your plugin's onEnable()
val locator = Bukkit.getServicesManager().load(LocatorAPI::class.java) ?: return

// Track a player and set their color to blue
locator.addPlayerLocator(player)
locator.setLocatorColor(player, 0x0000FF)

// Only let specific players see the waypoint
locator.exposeTo(target, viewer1)
locator.exposeTo(target, viewer2)

// Remove a viewer
locator.hideFrom(target, viewer1)

// Make visible to everyone again
locator.exposeToAll(target)
```

## How It Works

The plugin suppresses vanilla locator behavior by setting tracked players' `waypoint_transmit_range` attribute to 0. It then sends waypoint packets (protocol 0x88) directly to each viewer using PacketEvents, giving per-player control over visibility.

Waypoint positions are projected far from the viewer in the direction of the target. This reduces angular error from integer block coordinate rounding, making the waypoint indicator move smoothly on the locator bar.
