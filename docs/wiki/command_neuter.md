# /horse neuter

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`/horse neuter` permanently neuters the horse item in your main hand. Permission: `betterhorses.neuter` (default `true`). A neutered mount cannot breed, and its saved status survives item/entity conversion.

`horse-neutering.enabled` defaults to `true`. Setting it to `false` blocks new neutering through commands, API item creation, and the Standard branch's shears interaction. Reloading removes the `neuter` route, completion and generated help entry; enabling and reloading restores them. Existing neutered mounts retain their permanent status.

Standard also supports optional **live shears neutering**: enable `horse-neutering.shears-enabled`, then sneak and right-click your tamed mount with shears in the main hand. The Folia branch does not include this built-in interaction. Read [Neutering](neutering.md) for ownership, durability and cancellation details.

See [Despawn](command_despawn.md), [Spawn](command_spawn.md), and [Breeding](breeding.md).
