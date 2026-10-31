# Trampling

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`trample.enabled` defaults to `false`. When enabled, a sufficiently fast supported ridden mount damages and knocks back eligible entities ahead of it. The rider requires `betterhorses.trample` (default `true`). Species toggles are independent from general mount support.

```yaml
trample:
  enabled: false
  mount-types:
    horse: true
    skeleton-horses: true
    zombie-horses: true
    camels: false
    llamas: false
    mules: false
    donkeys: false
  damage: 4.0
  knockback: 1.5
  radius: 1.2
  cooldown-ticks: 20
  affect-players: true
  affect-mobs: true
  hit-angle-degrees: 90.0
  min-speed: 0.15
```

`HorseTrampleEvent` fires for each candidate before damage, knockback and the hit cooldown. Listeners can cancel all three or override damage, a cloned knockback vector and cooldown ticks. The normal damage/protection event must also accept the hit. Delayed targets are revalidated; Folia applies the hit only while rider, mount and target are owned by the executing region.

See [API events](API.md#horse-trample-event) and [Permissions](Permissions.md).
