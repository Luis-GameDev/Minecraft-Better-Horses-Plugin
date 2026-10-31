# Configuration guide

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

BetterHorses reads two files in its plugin data directory:

- [config.yml](config.yml.md) controls gameplay, feature toggles, mount types, lore layout, textures and compatibility-dependent features.
- [language.yml](language.yml.md) controls messages, item labels, inspection text and progress-bar appearance.

Use `/horse reload` after editing. New default entries are added when configurations are updated; existing values are retained. Malformed YAML is reported rather than repaired automatically. Existing shader rules are preserved rather than merged with example rules.

Feature guides: [Growth](Growth.md), [Random size](random-size.md), [Training](training.md), [Traits](traits.md), [Neutering](neutering.md), [Inspection](horse-inspection.md), [Block speed effects](block‐speed‐effects.md), [Trampling](trampling.md), [Rider buffs](rider‐buffs.md), and [Shaders](postprocessing‐shaders.md).

The default files below describe 7.1 development. Check the [branch differences](config.yml.md#branch-differences) before copying Standard-only settings into a Folia installation.
