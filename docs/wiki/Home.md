# BetterHorses 7.1

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

BetterHorses adds persistent horse items, genetic breeding, training, growth, individual size variation, traits, and optional rider/combat features to supported mounts.

Start with [Installation](installation.md), [How it works](how‐it‐works.md), [Configuration](configuration.md), and [Permissions](Permissions.md). Developers should use the [API reference](API.md).

| Feature | Standard | Folia |
| --- | --- | --- |
| Horse items, breeding, training and traits | Yes | Yes |
| Custom growth and hidden individual size | Yes, when the relevant server API exists | Yes, when the relevant server API exists |
| `/horse neuter` and global neutering toggle | Yes | Yes |
| Private empty-hand inspection | Yes | Not included in the Folia branch |
| Optional live shears neutering | Yes | Not included in the Folia branch |
| Optional postprocessing shaders | Supported Paper API on Minecraft 26.3 | Supported Folia/Paper API on Minecraft 26.3 |

Horses are always enabled. Skeleton horses, zombie horses, donkeys and mules are enabled in the shipped mount configuration; camels and llamas are optional. See [Mount types](mount‐types.md).

Use [Neutering](neutering.md), [Private inspection](horse-inspection.md), and [Random size](random-size.md) for the interactions and settings introduced in the 7.1 development work. [FAQ](FAQ.md) covers common configuration questions.
