# Horse traits

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`traits.enabled` is the master toggle. Individual trait sections control availability, chance and ability settings. Item creation and breeding try enabled configured chances in their configured order and stop at the first successful roll; the values are individual roll probabilities, not a normalized weight table. A trait is not guaranteed to be inherited from a parent. An explicit API `traitOverride: "none"` suppresses random trait selection.

Active swap-key traits use the player's swap-hands key (usually **F**) while mounted. `betterhorses.trait.use` and the corresponding `betterhorses.trait.<key>` permission are required. Trait receipt uses `betterhorses.trait.receive` plus the trait's permission. Particle indicators are separately controlled by `settings.trait-particle-indicator` and each trait's `particle` value.

| Key | Behavior / trigger |
| --- | --- |
| `hellmare` | Swap-key fire trail, configurable duration/radius/cooldown |
| `fireheart` | Passive fire resistance for an eligible mount and rider |
| `dashboost` | Swap-key temporary speed increase |
| `featherhooves` | Passive slow-falling support |
| `frosthooves` | Freezes suitable water beneath the ridden mount |
| `kickback` | Swap-key knockback of eligible nearby targets |
| `ghosthorse` | Swap-key temporary invisibility for horse and rider |
| `skyburst` | Jump/landing ability launching nearby targets |
| `revenantcurse` | Swap-key curse that retaliates against permitted attackers |
| `undead` | Lethal-damage skeleton transformation; restore using the configured item |
| `heavenhooves` | Jump-powered upward movement, with configurable strength/particles |

Ability cooldowns persist through item conversion, preventing a fresh entity UUID from bypassing a timer. Temporary movement bonuses must not become genetic or permanent after unload. Passive eligibility is reconciled while active, including idle riders and configuration/permission changes. Existing effects from other sources are retained when BetterHorses can distinguish them from its own.

Protection cancellation applies to corresponding ability damage checks. On Folia, delayed actors/targets must remain owned, valid, in range and in the expected riding context. Undead replacement failure leaves the original mount and inventory available; the restoration item is consumed only after successful replacement. A chest's cargo excludes separately tracked saddle/equipment slots.

See [Permissions](Permissions.md), [API events](API.md#event-reference), and [Rider buffs](rider‐buffs.md).
