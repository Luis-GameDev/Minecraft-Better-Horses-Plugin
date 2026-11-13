# Horse Swimming

Allow mounted horses to float toward the surface and swim slowly through water.

---

## Configuration

This feature is disabled by default. Enable it in `config.yml`:

```yaml
settings:
  horse-can-swim: true
```

Use `/horse reload` after changing the setting. Already-mounted horses are included.

## Behavior

- Players steer normally while their mount swims at a limited speed.
- Releasing the movement keys lets the mount coast gently to a stop.
- A falling horse can dip below the surface before buoyancy brings it upward.
- The feature applies to horses and other enabled mount types in allowed worlds.
- Dry-land movement, horse stats, and saved horse data are unchanged.
- Disabling the feature or dismounting stops the buoyancy adjustment.

Dismount events are not changed. A sufficiently deep splash can still cause vanilla
to eject the rider, and other plugins can still dismount players. Once the horse
has no player rider, the swimming adjustment stops.
