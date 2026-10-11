# Postprocessing shaders

BetterHorses can apply client postprocessing effects while configurable player and mount conditions match. The feature is disabled by default. It requires Minecraft **26.3** and server software exposing the supported Paper/Folia postprocessing API. On older versions or server software without that API, BetterHorses leaves this feature inactive and continues running normally.

Shader keys identify client resources. A custom key such as `betterhorses:mounted` requires `assets/betterhorses/post_effect/mounted.json` and its dependencies in the player's resource pack. BetterHorses does not distribute a resource pack or verify which custom resources a client has installed. Use keys available in the pack your players receive.

Enable the feature in `config.yml` and apply changes with `/horse reload`:

```yaml
postprocessing-shaders:
  enabled: true
  default-namespace: betterhorses
  rules:
    Mounted:
      default: "mounted"
      Gender:
        Male: "maleshader"
        Female: "femaleshader"
      Mounttype:
        Skeleton_Horse:
          Traited:
            default: "shadertraited"
            Hellmare: "fireshader"
            Frosthooves:
              default: "testshader"
              Placeholder:
                "%isCold%": "coldonfrosthoovesshader"
      Placeholder:
        "%inCombat%": "mountedandincombatshader"
```

`Frosthooves` uses a nested `default` because a YAML value cannot be both a scalar string and a section with children. Quote placeholder keys beginning with `%`.

Conditions refresh from events rather than a repeating polling task. Mounting, dismounting, player state and location changes, weather changes, respawning, reloading, and BetterHorses mount metadata changes request an update. Multiple requests for a player are combined into one check on the next tick, running on that player's owning thread. The plugin's world filter still applies. An existing `update-interval-ticks` setting from an older configuration is ignored.

Timed state changes may use one-shot checks scheduled from relevant events, such as when fire expires or a freezing boundary is reached. These checks do not run as a repeating polling loop.

## How rules combine

Every condition along a path must match. Sibling branches are evaluated independently, and **all matching effects remain enabled together**. `default` adds effects whenever its parent conditions match; it does not exclude a more specific matching branch.

In the example, a player riding a male skeleton horse with Frosthooves receives `mounted`, `maleshader`, `shadertraited`, and `testshader`. If `%isCold%` resolves to true, `coldonfrosthoovesshader` is added too. `%inCombat%` adds `mountedandincombatshader` independently when it resolves to true.

Nesting order does not affect matching. These rules express the same conditions and apply the same effect:

```yaml
Mounted:
  Mounttype:
    Skeleton_Horse:
      Traited:
        Frosthooves: "coldmount"
```

```yaml
Traited:
  Frosthooves:
    Mounted:
      Mounttype:
        Skeleton_Horse: "coldmount"
```

Effects can be strings or lists:

```yaml
Mounted:
  default: ["mounted", "betterhorses:colour_tint"]
  Sneaking: "steady_view"
```

Unqualified keys use `default-namespace`; fully qualified keys such as `betterhorses:colour_tint` keep their namespace. Repeated references to one key enable that effect once. Effects added by BetterHorses are ordered lexicographically for consistent composition, regardless of YAML nesting or insertion order. Pre-existing effects keep their order. Visual effects can influence one another when combined, so design and test the resource pack with those combinations in mind.

When a condition stops matching, BetterHorses removes the effects it previously added for that player. It does not clear unrelated effects from other plugins. Disabling or reloading the feature also updates its own effects.

On Folia, set `enabled: false` and run `/horse reload` before a live plugin unload, so cleanup can run on each player's owning region.

Invalid keys or unsupported conditions are skipped with a configuration warning; other valid branches still work. Rules are limited to 32 nesting levels and 4,096 shader rules to keep evaluation bounded.

## Conditions

State names and the built-in condition values are case-insensitive. Spaces, hyphens and underscores are interchangeable in built-in state, enum and trait names. World names match case-insensitively; permission nodes are passed to the permission system as written. Use condition groups as sections, with the desired value as a child key. A child can contain an effect, a list of effects, or further conditions.

| Condition | Values / meaning |
| --- | --- |
| `Mounted` | Riding a supported mount type enabled in BetterHorses. |
| `Unmounted` | Not riding a supported, enabled BetterHorses mount. |
| `Sneaking`, `Sprinting`, `Swimming`, `Gliding`, `Flying` | The matching player movement state. |
| `OnFire`, `Frozen` | The player is burning or fully frozen. |
| `Gender` | `Male` or `Female`, from the ridden mount's BetterHorses data. |
| `Mounttype` | `Horse`, `Skeleton_Horse`, `Zombie_Horse`, `Camel`, `Llama`, `Mule`, `Donkey`. |
| `Traited` | `default` for any trait, or a trait name such as `Hellmare`, `Frosthooves`, `Fireheart`, `Featherhooves`, `Dashboost`, `Kickback`, `Ghosthorse`, `Heavenhooves`. |
| `Owner` | `Self`, `Other`, or `None`, according to the ridden mount's stored owner. |
| `Growth` | `Baby` for custom growth stages below 10, or `Adult` at stage 10. When custom growth is disabled for that mount type, or no custom stage exists, its vanilla baby/adult age applies. |
| `Neutered` | `Yes` or `No`, according to the ridden mount's BetterHorses data. |
| `World` | The player's current world name or UUID. |
| `Biome` | The player's current biome name, for example `SNOWY_PLAINS`. |
| `Weather` | `Dry`, `Rain`, or `Thunder` in the player's world. `Rain` excludes thunderstorms. |
| `Environment` | `NORMAL`, `NETHER`, or `THE_END`. |
| `GameMode` | `SURVIVAL`, `CREATIVE`, `ADVENTURE`, or `SPECTATOR`. |
| `Permission` | A permission node the player must have. |
| `Placeholder` | A PlaceholderAPI expression, optionally followed by an expected result. |

`Gender`, `Mounttype`, `Traited`, `Owner`, `Growth`, and `Neutered` require a supported, enabled ridden mount, even if no explicit `Mounted` condition surrounds them. Other conditions also work while unmounted. A condition on a player state describes the player, rather than the mount.

For example, apply effects to players in rainy weather, to baby-mount owners, or to players with a permission:

```yaml
rules:
  Weather:
    Rain:
      World:
        world: "rain_view"
  Owner:
    Self:
      Growth:
        Baby: "young_mount"
  Permission:
    betterhorses.shaders.special:
      Flying: "flight_view"
```

## PlaceholderAPI

Placeholder conditions require PlaceholderAPI and the expansion providing each placeholder. Without that dependency, those branches do not match. An unresolved placeholder also does not match.

A string or list directly below a placeholder matches when its result is `true`, `yes`, `on`, or `1`, ignoring case and surrounding whitespace. A nested `default` has the same truthy behavior. For other values, use explicit result keys:

```yaml
rules:
  Placeholder:
    "%isCold%": "cold_view"
    "%player_health%":
      "5": "low_health_view"
    "%some_state%":
      default: "state_active"
      "warning":
        Mounted: "mounted_warning"
```

Explicit result checks compare the placeholder's text, rather than interpreting a number or expression. Use an expansion that returns the desired boolean when you need thresholds or more complex conditions.

## Updating integration state

Bukkit and PlaceholderAPI do not provide a general event for every placeholder result or permission change. Direct API changes such as `setFireTicks` or `setFreezeTicks` can also happen without an event. If an integration changes that state without a supported player or world event, it must request a refresh after the change:

```java
BetterHorsesAPI.refreshShaders(player);
```

`refreshShaders(Player)` can be called from an asynchronous thread. It schedules evaluation on the player's owning thread and combines repeated requests into one update. It safely does nothing while shaders are disabled or unsupported. For example, a combat integration should call it when entering or leaving combat if it changes the result of `%inCombat%`.

An integration that directly changes a mount's BetterHorses persistent data can refresh its riders after the mutation:

```java
BetterHorsesAPI.refreshMountShaders(mount);
```

`refreshMountShaders(Entity)` must be called on the mount's owning thread: the main server thread on standard Paper, or the mount's entity/region thread on Folia. Keep the persistent data mutation on that same thread. BetterHorses already requests updates for its own metadata changes.
