# /horse despawn

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`/horse despawn` converts the supported mount you are riding into a persistent horse item. Permission: `betterhorses.despawn` (default `true`). A failed conversion or cancelled despawn event must leave the original mount available.

Ownership enforcement depends on `settings.restrict-mounting-to-owner`. It is `false` by default. When enabled, the owner is required; the native Bukkit owner is used when the BetterHorses owner key is missing.

Chest-capable mounts carrying a chest cannot be stored by default. Set `settings.allow-chested-mount-despawn: true` to preserve the chest and its contents in the item. Saddles and armor are stored separately from chest cargo. Serialization failure blocks conversion rather than accepting empty cargo.

See [Spawn](command_spawn.md), [Permissions](Permissions.md), and [Mount types](mount‐types.md).
