# Genetic stats

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Health, speed and jump are stored as genetic values. The normal breeding calculation averages each parent pair, adds a random positive or negative mutation, and clamps it to the configured positive maximum. Configure `mutation-factor` and `max-stats`; enabled non-horse types have their own subsections.

For example, parental health values 20 and 30 average to 25. With `mutation-factor.health: 3.0`, the attempted mutation lies within ±3 HP before the maximum clamp. Mutation factors are absolute raw-stat changes, not percentages. A health maximum of 300 limits the result even when the attempted mutation would be larger.

Training modifies gameplay through separate modifiers. Displayed/stored genetic stats and breeding do not accumulate those bonuses. An active temporary movement ability must also be removed before genetic values are read for persistence or inheritance.

`stats.display-mode: RAW` shows internal speed/jump values. `BLOCKS` formats estimated blocks per second and jump height; it does not change the underlying genetic values or `/horsecreate` numeric input.

Traits are newly selected from enabled configured chances; they are not a guaranteed copy of a parent's trait. The [individual size multiplier](random-size.md) is independent of both parents and is applied after growth scaling.
