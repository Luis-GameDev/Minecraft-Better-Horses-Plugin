# /horse info

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`/horse info` prints administrator/debug information for the ridden horse or the horse item in your main hand. It requires `betterhorses.info` (default `true`) **and** `debug.enabled: true`. The route is unavailable when debugging is disabled.

Customize its templates under `messages.horse-info.item`, `messages.horse-info.mounted-horse`, and `messages.horse-info.no-target` in `language.yml`. These are children of `messages`, not top-level sections. Debug output can expose raw stored data; it is distinct from the private player-facing inspection card.

See [Reload](command_reload.md), [Language settings](language.yml.md), and [Inspection](horse-inspection.md).
