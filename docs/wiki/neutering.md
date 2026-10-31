# Neutering

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Neutering is permanent saved fertility state. A neutered mount cannot breed. Its horse item displays the configured neutered lore line; capture, respawn and undead transformation preserve the state.

```yaml
horse-neutering:
  enabled: true
  shears-enabled: false
```

`enabled` exists in both branches and defaults to `true`. Setting it to `false` blocks **new** neutering throughout the plugin, including a requested neutered flag in API item creation or `/horsecreate`. Reload removes `/horse neuter` from registered routes, suggestions and generated help. Existing neutered mounts are not made fertile again. The `shears-enabled` option belongs to Standard only.

## Horse items

Hold a horse item in your main hand and run `/horse neuter`. `betterhorses.neuter` is required and defaults to `true`. Cancellation of `BetterHorseNeuterEvent` prevents the mutation. The handler rechecks the global gate after listeners return, including when a listener reloads the configuration.

## Live shears interaction (Standard only)

Enable both settings, sneak, and right-click a **tamed** enabled mount with shears in the main hand. The player needs `betterhorses.neuter` and ownership, or `betterhorses.bypass` for another owner's horse. A missing stored owner falls back to the native owner.

A successful action clears love mode, marks the mount neutered, applies normal shears wear, plays the shearing sound and gives harmless hurt feedback. Unbreaking, unbreakable tools and Creative mode are respected. Cancelling the unified neuter event or tool-damage event prevents the action and its wear/feedback. An already neutered horse is not charged again.

The live event is delivered once through the deprecated compatibility subtype `BetterHorseEntityNeuterEvent`, sharing handlers and cancellation with `BetterHorseNeuterEvent`. Item-only integrations must check `event.isItem()` before reading the nullable item. See [API events](API.md#neutering-events).
