# How BetterHorses works

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Each supported mount can carry persistent gender, genetic health/speed/jump, growth, training, traits, ownership, neutering, texture references, and a hidden size multiplier. Horse items preserve the relevant data while a mount is stored.

Use `/horse despawn` while riding a tamed mount, then `/horse spawn` with the resulting item in your main hand. Right-click spawning is also available when enabled. Stored equipment is restored, and chest storage is preserved only when chest-capable despawning is enabled. A failed conversion must leave the source available.

[Breeding](breeding.md) combines parental genetic stats with mutation and optional traits. [Training](training.md) supplies separate gameplay bonuses; it does not inflate stored genetic stats or the values inherited by foals. [Growth](Growth.md) changes size over nine timed intervals. [Individual size](random-size.md) multiplies the result and is not inherited.

Permissions and the world filter apply to player actions. Ownership restrictions are optional and disabled by default; enable `settings.restrict-mounting-to-owner` when players should be prevented from riding or storing other owners' tamed mounts.

On Standard, sneaking with an empty main hand opens a private inspection card. Optional shears neutering uses a separate setting. See [Inspection](horse-inspection.md) and [Neutering](neutering.md).
