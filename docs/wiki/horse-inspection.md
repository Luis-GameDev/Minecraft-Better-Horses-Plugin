# Private horse inspection (Standard)

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

This built-in interaction exists in the Standard `version/7.1` branch. The Folia branch currently does not include inspection displays.

Sneak and right-click a supported enabled horse with an **empty main hand**. `betterhorses.inspect` defaults to `true`. Only the inspecting player sees the temporary card, which follows the horse as a passenger. Inspecting another horse replaces your previous card.

```yaml
horse-inspection:
  enabled: true
  duration-seconds: 10
  height-offset: 0.5
  view-range: 16
```

The card uses the configured stat units, trait labels and enabled training categories. Its text and order are controlled by top-level `horse-inspection.lines` and `horse-inspection.values` in `language.yml`. The hidden size multiplier has no player-facing line.

Inspection is unavailable while the mount is in love mode. Mounting or breeding closes related displays so they do not occupy the horse during those actions. Expiry, leaving range, disconnect, death, entity/chunk/world unload, reload and shutdown also remove them. Displays are private, nonpersistent entities; tagged stale displays are cleaned when relevant entities load.

See [Language settings](language.yml.md) and [Shears neutering](neutering.md).
