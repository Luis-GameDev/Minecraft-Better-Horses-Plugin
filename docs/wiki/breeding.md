# Breeding

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Bring two tamed supported mounts together, allow them to become mature enough, and use vanilla food accepted for their species to enter love mode. Horses normally use golden carrots or golden apples. Parents must be fertile and outside the relevant breeding cooldown; opposite genders are required unless same-gender breeding is enabled.

The foal's health, speed and jump derive from parental **genetic** values, followed by configured mutation and maxima. Training bonuses are excluded. The child receives a gender, optional newly rolled trait, and a newly rolled hidden size multiplier. Size is never inherited. A successful child is initialized with API-recognizable persistent stat/type data.

`settings.breeding-cooldown` defaults to 300 seconds. `settings.male-ignore-cooldown` defaults to `true`; `betterhorses.cooldown.bypass` can bypass the breeding timer. Neutered parents cannot breed even if they entered love mode before neutering. Cancelled breed events must not change parent ages or cooldowns.

Custom growth starts a normally supported foal at stage 1; the ride/breed threshold and final stage 10 are separate milestones. Species rules still apply: enabling a type does not invent vanilla breeding combinations for normally non-breeding species.

See [Genetics](genetics.md), [Growth](Growth.md), [Neutering](neutering.md), and [Mount types](mount‐types.md).
