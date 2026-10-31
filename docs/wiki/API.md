# BetterHorses developer API

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Use `me.luisgamedev.betterhorses.api` and `me.luisgamedev.betterhorses.api.events`. Declare BetterHorses as a dependency/soft dependency appropriate to your integration. API operations require the plugin to be enabled.

## Calling threads and failure

Entity, inventory, item mutation and synchronous event calls must run on the appropriate server thread. Standard uses the main server thread. Folia requires the relevant entity/inventory owner's thread; `toHorse` checks player ownership and `toItem` checks mount ownership and throws `IllegalStateException` for a wrong region. A global/async callback is not automatically an entity-owner callback. Schedule through the server's supported entity scheduler and revalidate actor/target ownership after any delay.

`refreshShaders(Player)` is explicitly safe to request asynchronously; evaluation is scheduled on the player's thread. `refreshMountShaders(Entity)` requires the mount's owning thread. These exceptions do not make the rest of the API thread-safe.

## Create horse items

```java
ItemStack item = BetterHorsesAPI.createHorseItem(
        30.0, 0.25, 0.8, "female", "Daisy",
        player, null, false, "fireheart", false, 10,
        SupportedMountType.HORSE);
```

The required argument order is:

```java
createHorseItem(double health, double speed, double jump, String gender,
                String name, Player owner, Inventory targetInventory,
                boolean dropIfFull, String traitOverride, boolean isNeutered,
                Integer growthStage, SupportedMountType mountType)
```

A second overload appends `HorseItemTextureData textureData`. There is no nine-argument overload. Nullable arguments are name, owner, inventory, trait override, growth stage, mount type and texture data; primitive arguments are never nullable. Health/speed/jump must be finite positive raw values, gender must be valid, and a provided growth stage must be 1–10. A null growth stage defaults to 10; an invalid provided stage throws `IllegalArgumentException`. Null mount type defaults to HORSE.

Null trait override permits configured random selection. Explicit `"none"` suppresses a trait. An explicit enabled trait can be assigned; global/individual trait settings still apply. The global neutering gate suppresses a requested new neutered flag when disabled. The hidden size multiplier is initialized automatically and is not added to lore.

The method returns the created item. A null target inventory leaves delivery to the caller. With an inventory, the method attempts delivery; `dropIfFull` controls dropping leftovers at an inventory with a usable location. Use a valid located inventory for automatic dropping, or handle the returned item yourself with a null target.

## Nullable wrappers

```java
BetterHorse horse = BetterHorsesAPI.getBetterHorse(mount);
if (horse != null) {
    horse.setSpeed(0.3);
}
BetterHorseItem stored = BetterHorsesAPI.getBetterHorse(item);
if (stored != null) {
    stored.getHealth().ifPresent(value -> player.sendMessage("Genetic HP: " + value));
}
```

Both `getBetterHorse` overloads return a **nullable direct wrapper**, not `Optional<BetterHorse>` or `Optional<BetterHorseItem>`. A wrapper's individual optional getters still return `Optional`. Use `isBetterHorse(Entity)` and `isHorseItem(ItemStack)` for recognition checks.

| Wrapper | Methods |
| --- | --- |
| `BetterHorse` | `getHandle`; genetic `getMaxHealth`/`setMaxHealth`, `getSpeed`/`setSpeed`, `getJump`/`setJump`; optional `getTrait`, `getGrowthStage`; `setTrait`, `setGrowthStage`; texture get/set |
| `BetterHorseItem` | `getHandle`; optional `getHealth`, `getSpeed`, `getJump`, `getMountType`; texture get/set |

Stat setters require finite positive values and keep genetic/training data synchronized. Growth setters reconcile age and size with the configured threshold. These wrappers do not promise async safety. Hidden size persistence is handled automatically by normal spawn/birth/conversion paths.

## Conversion and cancellation

`toHorse(ItemStack, Player)` returns a spawned `AbstractHorse` or null on invalid/unreadable restoration. A valid saved owner is retained; the supplied player is the fallback owner. `toItem(AbstractHorse, Player ownerOverride)` returns an item or null on failed serialization. Conversion helpers do not by themselves complete a player's command transaction: the caller handles event dispatch, source consumption, inventory delivery and mount removal.

```java
// Run on the player/mount owning thread.
AbstractHorse restored = BetterHorsesAPI.toHorse(source, player);
if (restored != null) {
    ItemStack snapshot = source.clone();
    snapshot.setAmount(1);
    boolean cancelled = BetterHorsesAPI.callCancellableSpawnEvent(
            restored, snapshot, BetterHorseSpawnEvent.SpawnCause.ITEM);
    if (cancelled) {
        restored.remove();
    } else {
        // Commit source consumption only here, after revalidating your transaction.
    }
}
```

`callCancellableSpawnEvent` returns **true when cancelled**. The legacy void `callSpawnEvent` dispatches an event but discards that return value; prefer the cancellation-returning helper for transactional spawning. `callDespawnEvent(horse, resultItem)` likewise returns true when cancelled. After `toItem`, abort on null or cancellation and retain the live horse. Commit mount removal and item delivery only after successful conversion and the cancellation check; revalidate the transaction and handle any failed commit without duplicating or losing the source.

New equipment is serialized through a portable Bukkit format. Legacy Paper equipment bytes still need a compatible decoder. Unsupported/corrupt equipment or chest payloads fail closed, retaining the source rather than restoring empty storage. Genetic stats, current health, training, relevant cooldowns, equipment, ownership, textures, neutering and the hidden size multiplier are preserved through successful conversion.

`normalizeStoredStats(ItemStack, Player viewer)` migrates eligible legacy item stats and missing size data and reports whether the item was changed; call it while owning the containing inventory. It is not a general corruption-repair service.

## Texture data

`HorseItemTextureData(Integer customModelData, String itemModel, String citString, String modelString)` is an immutable holder. It provides `empty()`, `isEmpty()` and getters. Null/empty values remove optional texture references when applied. `getConfiguredTextureData()`, `getTextureData(item)` and `applyTextureData(item, data)` expose the item utilities; both wrappers also expose texture get/set.

When `settings.texture.override-texture-data` is true, configured values override provided/stored texture data. Resource-pack requirements and newer item-model API support remain client/server dependent. BetterHorses does not distribute a resource pack.

## Event reference

All events are synchronous. On Folia, execution ownership remains part of each event's contract. Store IDs or immutable snapshots for later work and reschedule/revalidate before touching entities. Every event exposes cancellation and a Bukkit HandlerList.

| Event | Targets and writable data |
| --- | --- |
| `BetterHorseSpawnEvent` | `getHorse`, nullable `getSourceItem`, `getCause` (NATURAL/ITEM/API); cancel before caller commits |
| `BetterHorseDespawnEvent` | `getHorse`, nullable `getResultItem`; cancellation prevents caller removal/delivery |
| `BetterHorseBreedEvent` | `getChild`, father/mother; mutable health, speed, jump, gender and nullable trait; supply valid finite stats |
| `BetterHorseAbilityUseEvent` | `getPlayer`, `getMount`, mutable trait key; deprecated/legacy `getHorse` can be null for a non-Horse AbstractHorse |
| `BetterHorseNeuterEvent` | Item or live target; guarded nullable accessors described below |
| `BetterHorseEntityNeuterEvent` | Deprecated live-target subtype sharing unified handlers/cancellation |
| `HorseTrampleEvent` | Rider, horse, target; mutable damage, copied knockback vector and cooldown ticks |

## Neutering events

```java
@EventHandler(ignoreCancelled = true)
public void onNeuter(BetterHorseNeuterEvent event) {
    if (event.isItem()) {
        ItemStack item = event.getHorseItem();
        // Item-target logic; setHorseItem is available only for this target kind.
    } else if (event.isEntity()) {
        AbstractHorse horse = event.getHorse();
        // Live-target logic; getHorseItem() is null here.
    }
}
```

`getTargetType`, `isItem` and `isEntity` discriminate targets. The opposite target accessor returns null. `setHorseItem` rejects live targets. `getPlayer` and `getHand` identify the actor/hand. Standard live shears dispatches one compatibility subtype instance so old entity listeners and unified listeners share cancellation/priority ordering. Cancelling prevents live state changes, durability, sound and hurt feedback. Existing item-only listeners must guard the target before dereferencing the item. The Folia branch includes the event classes but does not currently provide the built-in shears interaction.

## Horse trample event

`getPlayer`/`getRider`, `getHorse` and `getTarget` expose each hit. `setDamage` requires a finite nonnegative value; `setCooldownTicks` requires a nonnegative integer. `getKnockback()` returns a clone: modify it and call `setKnockback` to apply a finite vector. Cancelling prevents the hit's damage, knockback and cooldown. Protection damage checks still apply. Folia revalidates region ownership for the rider, horse and target before committing pending hits.

```java
@EventHandler(ignoreCancelled = true)
public void onTrample(HorseTrampleEvent event) {
    event.setDamage(event.getDamage() * 0.5);
    event.setKnockback(event.getKnockback().multiply(0.5));
    event.setCooldownTicks(40);
}
```

## Persistent keys and refresh

`BetterHorseKeys` contains typed key constants for genetic stats, current health, base stats, owner/name/type, growth, training/cooldowns, equipment/chest data, textures, undead state and hidden size. Prefer wrappers and conversion helpers to manual PDC manipulation; manually copied genetic or temporary state can violate invariants.

After a direct owned-thread mount-data mutation, call `BetterHorsesAPI.refreshMountShaders(mount)` if shader rules depend on it. An integration changing permission or placeholder state can call `refreshShaders(player)` safely from any thread. See [Shader integration](postprocessing‐shaders.md#updating-integration-state).
