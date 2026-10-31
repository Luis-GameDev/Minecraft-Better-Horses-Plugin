# Block speed effects

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

This feature is disabled by default. It applies a two-second speed/slowness potion effect to a supported ridden mount while movement refreshes a matching block entry.

```yaml
settings:
  block-speed-effects:
    enabled: true
    entries:
      - block: SAND
        effect: SLOWNESS_II
        mount-type: horse
      - block: STONE_BRICKS
        effect: SPEED_II
        mount-type: donkey
```

Effects are `SLOWNESS_I`, `SLOWNESS_II`, `SPEED_I`, `SPEED_II` and `SPEED_III`. Mount inputs accept the supported type names. Donkey support must also be enabled under `settings.mount-types`. Invalid block/effect/type entries are ignored with a warning. The default entries cover ordinary horses on sand, red sand and suspicious sand; other species need explicit entries.

The old `sand-slowness` setting is obsolete and is not read. Use `settings.block-speed-effects`. After leaving the configured surface, the short effect expires unless refreshed elsewhere.
