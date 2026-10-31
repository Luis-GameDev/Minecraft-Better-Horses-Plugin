# /horse spawn

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`/horse spawn` restores the horse item held in your main hand. Permission: `betterhorses.spawn.command` (default `true`). Right-click spawning additionally requires `settings.allow-rightclick-spawn: true` and `betterhorses.spawn.rightclick`.

The item must use the configured horse-item material and contain valid saved mount data. The mount type must be enabled. Successful restoration preserves the saved owner when valid; the spawning player is the fallback owner. Equipment, allowed chest contents, training, trait/ability cooldowns and the hidden size multiplier survive conversion.

The source is consumed only after successful creation and an accepted spawn event. Protection cancellation, invalid data, or unreadable inventory payloads leave it available. A stacked item containing chest storage is consumed as a whole after successful restoration to prevent duplicating stored contents.

See [Despawn](command_despawn.md), [API conversion](API.md#conversion-and-cancellation), and [Mount types](mount‐types.md).
