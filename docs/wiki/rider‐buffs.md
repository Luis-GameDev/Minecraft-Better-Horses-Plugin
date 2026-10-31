# Rider buffs

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Two optional features are disabled by default:

```yaml
settings:
  rider-invulnerable: false
  mounted-damage-boost:
    enabled: false
    percentage: 25.0
```

Rider invulnerability requires `betterhorses.rider.invincible`. Mounted melee damage boost requires `betterhorses.rider.damageboost` and adds the configured percentage while the player rides a supported enabled mount. The default percentage is 25% when the feature is enabled. Projectile damage is not the melee boost's trigger.

Both individual permissions and `betterhorses.rider.*` default to `true`, but permission grants alone do not turn on a disabled setting. These buffs are separate from [traits](traits.md) and [training](training.md). The configured world filter applies to the gameplay listeners.
