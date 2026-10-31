# Custom growth

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`horse-growth-settings.enabled` controls custom growth. Regular horses are included; optional species also require their growth toggle and their general mount toggle. Skeleton horses, zombie horses, donkeys and mules have growth enabled in the shipped defaults. Camels and llamas can be opted in.

Newborn stage 1 progresses through **nine equal time intervals** to stage 10. `time-until-adult` is the total configured minutes from stage 1 to stage 10; its minimum effective value is one minute. Growth pauses while occupied and resumes without counting that mounted time. Disabling custom growth returns to vanilla age handling.

`ride-and-breed-threshhold` is the existing configuration spelling. It defaults to `7` and permits riding/breeding at that custom stage; reaching it does not finish visual growth. An API growth-stage reduction also reconciles the mount's juvenile/adult state to this threshold.

The intended visible scale before individual variation is:

```text
max-size × (0.5 + 0.5 × (stage - 1) / 9)
```

Thus stage 1 is 50% and stage 10 is 100% of `max-size`. Baby-model compensation depends on mount type and Minecraft version, so becoming a vanilla adult contributes only the next growth increment rather than an extra size jump. The hidden [random-size multiplier](random-size.md) is applied last, then the final scale is bounded to the server attribute range.

```yaml
horse-growth-settings:
  enabled: true
  time-until-adult: 3
  ride-and-breed-threshhold: 7
  max-size: 1.3
```

Minecraft 1.20.4 has no `SCALE` attribute. Growth metadata can remain saved there, but the visual scaling requires a server exposing that attribute. [Random size](random-size.md) also works independently when custom growth is disabled.
