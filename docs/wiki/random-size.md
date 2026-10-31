# Hidden individual size

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

```yaml
random-size-factor:
  enabled: true
  factor: 0.1
```

This feature is enabled by default. `factor: 0.1` draws a multiplier between 0.9 and 1.1, allowing a horse up to 10% smaller or larger than its growth-calculated size. The effective factor is bounded to 0.0–0.99; invalid nonfinite input falls back to 0.1.

Each mount receives its own persistent value. Missing legacy values are assigned once. Items keep the same value through capture, failed spawn attempts, successful respawn and restart; undead transformations retain it as well. Newborns receive a fresh independent value, including crossbreeds, so parental size is not inherited. Changing the configured factor affects new assignments rather than rerolling existing mounts.

The value is stored on the horse and its horse item, but is absent from item lore and the inspection card. It multiplies the result **after** growth-stage calculation and baby-model compensation. It also applies to vanilla growth when custom growth is disabled. Repeated updates recompute from the base calculation rather than compounding the multiplier.

Disabling the feature retains the saved multiplier. Without custom growth, it removes a still-owned applied size once and leaves a scale subsequently changed by another plugin alone. Enabling it again reuses the stored multiplier. Servers without `SCALE`, including 1.20.4, retain the data without displaying the variation.

See [Growth](Growth.md) and [Horse items](how‐it‐works.md).
