# BetterHorses — Standard 7.1 development

Persistent mount items, genetic breeding, training, traits, custom growth and individual hidden size variation for Minecraft servers.

The complete, reviewable [7.1 documentation](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/Home.md) is kept under `docs/wiki` on main. Branch-specific source is in [Standard](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/tree/version/7.1) and [Folia](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/tree/folia/version/7.1). The [published GitHub wiki](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/wiki) is a separate Git repository; [wiki synchronization instructions](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/WIKI_SYNC.md) explain how reviewed source pages become live there.

## Builds and compatibility

| Branch | Server | Build task | Current deployable filename |
| --- | --- | --- | --- |
| `version/7.1` | Standard Bukkit/Spigot or Paper, Minecraft 1.20.4+ with feature API limits | `./gradlew shadowJar` | `BetterHorses-7.1-all.jar` |
| `folia/version/7.1` | Folia | `./gradlew verifyPluginJar` | `BetterHorses-7.0-Folia.jar` |

Both require **Java 21**. The Folia branch retains version 7.0 in its current build metadata despite its 7.1 development branch name. Install the matching shaded artifact from `BetterHorses/build/libs/`; install one artifact at a time. See [Installation](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/installation.md) for optional PlaceholderAPI, resource-pack requirements and legacy equipment conversion.

Visible growth/random size needs `SCALE`, which Minecraft 1.20.4 does not expose; size data remains saved. Optional postprocessing requires Minecraft **26.3** and the supported Paper/Folia API. Other forks/releases require their own runtime validation; compilation does not certify every combination.

## Features

- Store supported ridden mounts as horse items with their genetic stats, health, ownership, training, traits, equipment and relevant cooldowns. Optional chest storage is disabled by default; conversion failure preserves the source.
- Breed genetic health/speed/jump with configured mutation and maxima. Training supplies separate gameplay bonuses and does not inflate inherited/stored genetic stats.
- Custom growth progresses from stage 1 to 10 over nine intervals. The default ride/breed threshold is stage 7; size continues increasing to stage 10.
- Hidden individual sizes default to ±10%, persist on horse and item, apply after growth, and are freshly rolled for offspring rather than inherited.
- Eleven configurable traits cover fire resistance/trails, slow falling, speed boosts, ice, knockback, invisibility, jump abilities, curse retaliation and undead transformation.
- Optional block speed effects, trample hits, rider buffs, particle/cooldown displays, item textures/lore layouts, MiniMessage and PlaceholderAPI integration.
- World blacklist/whitelist and per-action permissions. Ownership restriction is **disabled by default**; enable `settings.restrict-mounting-to-owner` to enforce ownership for riding and despawning. `betterhorses.bypass` exempts riding; live shears neutering also accepts it.
- Private empty-hand inspection and optional live shears neutering are available in both Standard and Folia.

## Neutering, inspection and size defaults

```yaml
horse-neutering:
  enabled: true
  shears-enabled: false

random-size-factor:
  enabled: true
  factor: 0.1
```

When global neutering is false, new neutering is blocked through commands, shears and newly created API/command items. `/horse neuter` is removed from routing, suggestions and generated help after reload. Previously neutered horses remain infertile.

Enable shears neutering, then sneak/right-click a tamed owned mount with main-hand shears. `betterhorses.neuter` is required; `betterhorses.bypass` permits another owner's mount. Normal durability, Unbreaking, unbreakable tools and Creative mode are respected; event cancellation prevents state, wear and feedback. Sneak/right-click with an empty main hand opens the private inspection card when `horse-inspection.enabled` and `betterhorses.inspect` allow it. Inspection is limited to tamed mounts by default; set `horse-inspection.only-tamed: false` to allow untamed mounts. See [Neutering](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/wiki/command_neuter) and [Configuration](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/wiki/config.yml#horse-inspection).

The size multiplier is omitted from item lore and inspection. Missing legacy values are initialized once; capture/respawn, restart and undead transformation preserve it. Changing the factor does not reroll existing data. Disabling sizing keeps stored values and stops applying its own variation while preserving unrelated external changes. See [Random size](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/random-size.md).

## Commands

| Command | Permission | Notes |
| --- | --- | --- |
| `/horse spawn` | `betterhorses.spawn.command` | Restore main-hand horse item |
| `/horse despawn` | `betterhorses.despawn` | Store ridden tamed mount; ownership restriction is configurable |
| `/horse neuter` | `betterhorses.neuter` | Permanent item neutering; registered only when enabled |
| `/horse info` | `betterhorses.info` | Also requires `debug.enabled` |
| `/horse reload` | `betterhorses.reload` | Operator default; console supported |
| `/horsecreate` | `betterhorses.create` | Operator default; player-only |

```text
/horsecreate <health> <speed> <jump> [gender] [name] [trait] [growth] [mounttype] [neutered]
/horsecreate 20 0.3 0.7 null null null null donkey null
```

All six optional arguments accept case-insensitive `null`, keeping their position and using the omitted default. Required numbers remain raw finite positive stats. Defaults are random gender, localized horse name, configured random trait selection, growth 10, horse type and not neutered. See [Command reference](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/command_horsecreate.md) and [Permissions](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/Permissions.md).

## Configuration and API

Edit `plugins/BetterHorses/config.yml` and `language.yml`, then `/horse reload`. See the complete [config reference](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/config.yml.md), [language reference](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/language.yml.md), [training](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/training.md), [growth](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/Growth.md), and [shader guide](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/postprocessing‐shaders.md). The obsolete `sand-slowness` setting is replaced by `settings.block-speed-effects`.

```java
ItemStack item = BetterHorsesAPI.createHorseItem(
        30.0, 0.25, 0.8, "female", "Daisy",
        player, null, false, "fireheart", false, 10,
        SupportedMountType.HORSE);
BetterHorse horse = BetterHorsesAPI.getBetterHorse(mount);
if (horse != null) {
    horse.setSpeed(0.3);
}
```

`getBetterHorse` returns a nullable direct wrapper. Creation has 12- and 13-argument overloads, with the last overload adding texture data. Synchronous entity/inventory operations require their owning thread on Folia. For custom spawns, use the cancellation-returning dispatcher and roll back before consuming the source when cancelled. Unified neuter events require item/entity target guards; trample events expose damage, knockback and cooldown overrides. The [API reference](https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin/blob/main/docs/wiki/API.md) covers these contracts and shader refresh methods.
