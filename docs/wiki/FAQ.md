# FAQ

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

## Why is a mount or command unavailable?

Check the installed Standard/Folia artifact, Java 21, general mount toggle, player permission and world filter. `/horse info` additionally needs `debug.enabled`. `/horse neuter` is deliberately absent when global neutering is disabled. [Permissions](Permissions.md) lists the exact defaults.

## Can I neuter a live horse with shears?

Yes on Standard: enable `horse-neutering.shears-enabled`, keep the global setting enabled, and sneak/right-click a tamed owned mount with main-hand shears. Folia currently includes item neutering but does not include this built-in live interaction. [Neutering](neutering.md) explains durability, ownership and event cancellation.

## Why are sizes different, and why is no size number in the item?

The default hidden size factor is ±10%, independent of parents. It is saved on each horse and item and intentionally omitted from lore/inspection. Custom growth also defaults to a maximum size of 1.3. Servers without `SCALE`, including 1.20.4, retain these data but cannot display size changes. See [Growth](Growth.md) and [Random size](random-size.md).

## Why do some food clicks give no training?

The species must actually accept and consume the food. A configured food value does not override vanilla feeding rules, and cancelled or no-effect interactions do not award progress. Training/feeding permissions, category settings and cooldowns also apply. [Training](training.md) covers the rates.

## Are trained stats or traits inherited?

Breeding uses genetic stats without training modifiers. Traits are newly rolled according to enabled chances, and size receives an independent roll. Same-gender breeding is disabled by default; neutered mounts remain infertile. [Genetics](genetics.md) and [Breeding](breeding.md) explain the calculation.

## Does storage reset cooldowns or ownership?

Saved ownership and relevant breeding/training/ability timers follow the mount through conversion. Respawning uses a valid stored owner, falling back to the spawning player. Ownership enforcement itself is optional and disabled by default. Never assume a default installation prevents other players from riding/storing a tamed horse.

## Why did a chested horse fail to spawn?

Protection cancellation, invalid metadata or an unreadable stored inventory blocks restoration and retains the source. Legacy Paper equipment bytes require a compatible decoder; convert those items on Paper before transferring to Spigot. See [Spawn](command_spawn.md) and [Installation](installation.md).

## Which configuration examples should I copy?

Use the correct branch's [current config](config.yml.md), preserve YAML nesting from [language.yml](language.yml.md), and apply changes with `/horse reload`. Shader flow maps require commas; [the shader guide](postprocessing‐shaders.md) includes a valid Quick Start. Placeholder conditions additionally require PlaceholderAPI and the relevant expansion.
