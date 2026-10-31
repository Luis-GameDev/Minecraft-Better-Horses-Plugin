# Training

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Training is enabled by default and has three independently configurable categories:

| Category | Earned through | Gameplay bonus |
| --- | --- | --- |
| Riding | Distance travelled while riding | Movement speed |
| Brushing | Using the configured brush item, respecting cooldown | Jump strength |
| Feeding | A permitted, successfully consumed vanilla food, respecting cooldown | Maximum health |

The current defaults use 100 riding blocks per 1% progress, one brushing unit per 1%, and two feeding food-value units per 1%. Brushing grants two units per use. Brushing and feeding cooldowns default to 180 seconds. Each category's default bonus is +0.2% of its genetic stat per percentage point of completed progress, up to the category's completed progress cap.

`training.enabled` is the master toggle. Configure category toggles, rates, items, food values and bonuses under `training.categories`. A configured food value does not make a species eat that item: unused carrots, full-health/no-effect food clicks and cancelled interactions do not earn feeding progress. The player also needs `betterhorses.training` and the underlying interaction permissions.

Progress and relevant cooldown timestamps persist on mounts and horse items. Bonuses are separate gameplay modifiers; stored/displayed stats and breeding use genetic values. Disabling a category removes its gameplay contribution while keeping progress available for future re-enabling.

Item progress display is controlled by `training.lore`, the item lore layout, and `language.yml`'s **top-level** `training-lore` section. Progress-bar length/complete grouping are config values; filled/empty characters and colors are language values. Private Standard inspection displays only enabled categories.
