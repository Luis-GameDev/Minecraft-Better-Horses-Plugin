# Permissions

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

The table is derived from the 7.1 descriptors. `op` means operators; an omitted Bukkit descriptor default also means `op`. Explicit permission-manager grants/denials and wildcard parents can change the effective result.

| Permission | Descriptor default | Branch | Purpose |
| --- | --- | --- | --- |
| `betterhorses.*` | `op` | Both | Grants access to all BetterHorses permissions |
| `betterhorses.trait.*` | `true` | Both | Grants access to all traits |
| `betterhorses.rider.*` | `true` | Both | Grants access to all rider bonuses |
| `betterhorses.spawn.*` | `true` | Both | Allows the player to spawn a horse in every possible way |
| `betterhorses.mount` | `true` | Both | Allows the player to mount a horse |
| `betterhorses.feed` | `true` | Both | Allows the player to feed a horse |
| `betterhorses.breed` | `true` | Both | Allows the player to breed a horse |
| `betterhorses.tame` | `true` | Both | Allows the player to tame a horse |
| `betterhorses.leash` | `true` | Both | Allows the player to leash a horse |
| `betterhorses.bypass` | `op` | Both | Allows riding horses of other players |
| `betterhorses.trample` | `true` | Both | Allows for the players horse to use the trample feature |
| `betterhorses.spawn.rightclick` | `true` | Both | Allows spawning a horse by rightclicking the horse item |
| `betterhorses.training` | `true` | Both | Allows for the players horse to be trained by the player |
| `betterhorses.cooldown.bypass` | `false` | Both | Allows the player to bypass breeding cooldowns |
| `betterhorses.rider.invincible` | `true` | Both | Allows the rider to use the rider invincibility feature |
| `betterhorses.rider.damageboost` | `true` | Both | Allows the rider to use the mounted damage boost feature |
| `betterhorses.trait.use` | `true` | Both | Allows the rider to use an active trait if enabled |
| `betterhorses.trait.receive` | `true` | Both | Allows the rider to be able to get traited horses when breeding if enabled |
| `betterhorses.trait.hellmare` | `true` | Both | Allows the rider to use the trait hellmare if enabled |
| `betterhorses.trait.fireheart` | `true` | Both | Allows the rider to use the trait fireheart if enabled |
| `betterhorses.trait.featherhooves` | `true` | Both | Allows the rider to use the trait featherhooves if enabled |
| `betterhorses.trait.dashboost` | `true` | Both | Allows the rider to use the trait dashboost if enabled |
| `betterhorses.trait.kickback` | `true` | Both | Allows the rider to use the trait kickback if enabled |
| `betterhorses.trait.ghosthorse` | `true` | Both | Allows the rider to use the trait ghosthorse if enabled |
| `betterhorses.trait.heavenhooves` | `true` | Both | Allows the rider to use the trait heavenhooves if enabled |
| `betterhorses.trait.undead` | `true` | Both | Allows the rider to use the trait undead if enabled |
| `betterhorses.trait.frosthooves` | `true` | Both | Allows the rider to use the trait frosthooves if enabled |
| `betterhorses.trait.skyburst` | `true` | Both | Allows the rider to use the trait skyburst if enabled |
| `betterhorses.trait.revenantcurse` | `true` | Both | Allows the rider to use the trait revenantcurse if enabled |
| `betterhorses.info` | `true` | Both | Allows the player to use the /horse info command |
| `betterhorses.neuter` | `true` | Both | Allows using /horse neuter and the optional shears interaction on owned horses |
| `betterhorses.inspect` | `true` | Standard | Allows viewing private horse stats with sneak and an empty-hand right-click |
| `betterhorses.create` | `op` | Both | Allows using /horsecreate |
| `betterhorses.reload` | `op` | Both | Allows reloading the BetterHorses configuration |
| `betterhorses.spawn.command` | `true` | Both | Allows using the /horse spawn command |
| `betterhorses.despawn` | `true` | Both | Allows using the /horse despawn command |

`betterhorses.cooldown.bypass` defaults to **false**, while operators receiving `betterhorses.*` also receive its granted child. `betterhorses.bypass` has the implicit operator default and covers riding and Standard live-shears ownership exceptions; it is not granted to ordinary players by default.

Using a trait requires both `betterhorses.trait.use` and its individual trait node. Receiving one requires `betterhorses.trait.receive` and the corresponding node. The trait wildcard includes Frosthooves, Skyburst and Revenant Curse. Settings still control whether each permitted feature is enabled.

Standard's `betterhorses.inspect` controls the private empty-hand card. Live shears neutering requires `betterhorses.neuter` and ownership, or the bypass permission, as well as both neutering settings. Folia's current built-in neutering interaction is item-only. See [Neutering](neutering.md).
