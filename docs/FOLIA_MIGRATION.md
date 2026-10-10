# BetterHorses Folia migration inventory and architecture

## Baseline

The migration branch was created from commit `3af19d8405bf1c77f4ea265c50c25022cd917dce`.
The implementation follows Paper's scheduler split: an entity scheduler follows a moving entity,
a region scheduler owns location/block work, and the global scheduler is reserved for lifecycle
coordination. No global scheduler is treated as a replacement for a server main thread.

## Architecture

* `BetterHorsesScheduler` is the scheduling boundary. It exposes entity-following, location-region,
  global, and async methods and owns every returned task through `TaskRegistry`.
* `HorseGrowthManager` is event driven. Entity add/spawn/mount events start one one-second entity
  task per relevant loaded mount. That task combines timestamp-based growth and the lightweight
  trait indicator, eliminating two world scans. Entity retirement/removal cancels and forgets it.
* Trample starts on mount and ends on dismount/logout/retirement. Nearby target mutations are
  dispatched to each target's entity scheduler and revalidated there.
* Hellmare and Frost Hooves block mutations and restoration execute on the location's region.
  Dash, Ghost Horse, cooldown UI, landing detection and remount callbacks follow their entity.
* Cross-region knockback is a two-step operation: discover from the mount context, then execute and
  revalidate in the target context. No region thread blocks waiting for another region.
* Shared registries use UUID keys and concurrent atomic map operations. Listener reconciliation is
  synchronized. World filter settings are published as one immutable volatile snapshot.
* Shutdown and feature reload cancel owned tasks idempotently. Cleanup no longer scans worlds.
* Existing package names, synchronous public methods, PDC keys, item layout, commands and
  permissions remain intact. `toHorse` and `toItem` now fail fast when called outside their entity
  owner's context rather than silently corrupting state; callers can use `Entity#getScheduler()`.
* Paper implements the same modern scheduler APIs, so the output remains a single Paper/Folia JAR.

## Audit inventory

| Component | Current issue | Required solution | Risk | Status |
|---|---|---|---|---|
| `me/luisgamedev/betterhorses/BetterHorses.java` | Reload/lifecycle state visible to concurrent regions | Synchronized registration, immutable volatile world-filter publication, owned task shutdown | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/api/BetterHorse.java` | Public wrappers expose live data or persistence schema | Preserve packages/PDC keys and document owning-thread contract | High | Migrated/audited |
| `me/luisgamedev/betterhorses/api/BetterHorseItem.java` | Public wrappers expose live data or persistence schema | Preserve packages/PDC keys and document owning-thread contract | High | Migrated/audited |
| `me/luisgamedev/betterhorses/api/BetterHorseKeys.java` | Public wrappers expose live data or persistence schema | Preserve packages/PDC keys and document owning-thread contract | High | Migrated/audited |
| `me/luisgamedev/betterhorses/api/BetterHorsesAPI.java` | Entity spawn/remove, inventory and serialization require region ownership | Event/command region confinement, explicit API ownership guard, success-before-consume transaction order | High | Migrated/audited |
| `me/luisgamedev/betterhorses/api/HorseItemTextureData.java` | Public wrappers expose live data or persistence schema | Preserve packages/PDC keys and document owning-thread contract | High | Migrated/audited |
| `me/luisgamedev/betterhorses/api/events/BetterHorseAbilityUseEvent.java` | Custom cancellable event invoked by region-owned operations | Retain synchronous event contract in the initiating owning context | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/api/events/BetterHorseBreedEvent.java` | Custom cancellable event invoked by region-owned operations | Retain synchronous event contract in the initiating owning context | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/api/events/BetterHorseDespawnEvent.java` | Custom cancellable event invoked by region-owned operations | Retain synchronous event contract in the initiating owning context | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/api/events/BetterHorseNeuterEvent.java` | Custom cancellable event invoked by region-owned operations | Retain synchronous event contract in the initiating owning context | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/api/events/BetterHorseSpawnEvent.java` | Custom cancellable event invoked by region-owned operations | Retain synchronous event contract in the initiating owning context | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/CustomHorseCommand.java` | Player/entity/inventory access is thread-affine | Player commands execute in player region; console paths avoid live entity access | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/DespawnCommand.java` | Entity spawn/remove, inventory and serialization require region ownership | Event/command region confinement, explicit API ownership guard, success-before-consume transaction order | High | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/HorseCommand.java` | Player/entity/inventory access is thread-affine | Player commands execute in player region; console paths avoid live entity access | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/HorseCommandCompleter.java` | Player/entity/inventory access is thread-affine | Player commands execute in player region; console paths avoid live entity access | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/HorseCreateTabCompleter.java` | Player/entity/inventory access is thread-affine | Player commands execute in player region; console paths avoid live entity access | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/HorseInfoCommand.java` | Player/entity/inventory access is thread-affine | Player commands execute in player region; console paths avoid live entity access | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/HorseNeuterCommand.java` | Player/entity/inventory access is thread-affine | Player commands execute in player region; console paths avoid live entity access | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/commands/RespawnCommand.java` | Entity spawn/remove, inventory and serialization require region ownership | Event/command region confinement, explicit API ownership guard, success-before-consume transaction order | High | Migrated/audited |
| `me/luisgamedev/betterhorses/growing/GrowthScale.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/growing/HorseGrowthManager.java` | Full-world scan on a global Bukkit timer | Entity-add/spawn/mount tracking; entity-following timestamp progression and indicators | High | Migrated/audited |
| `me/luisgamedev/betterhorses/language/LanguageManager.java` | Reload/lifecycle state visible to concurrent regions | Synchronized registration, immutable volatile world-filter publication, owned task shutdown | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/BlockSpeedEffectListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseBreedListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseFeedListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseItemBlockerListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseJumpListener.java` | Delayed entity/block work and cross-entity effects | Entity-following tasks, region block tasks, target revalidation and lifecycle cleanup | High | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseMountListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorsePermissionListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseSpawnListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseStepHeightListener.java` | Static UI/effect tasks and cleanup scans | Concurrent UUID state and entity-owned cleanup without world scans | High | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseTrainingBrushingListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/HorseTrainingRidingListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/ListenerLifecycle.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/ListenerRegistrationManager.java` | Reload/lifecycle state visible to concurrent regions | Synchronized registration, immutable volatile world-filter publication, owned task shutdown | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/MountedDamageBoostListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/PassiveTraitListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/RevenantCurseListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/RiderInvulnerableListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/RightClickListener.java` | Entity spawn/remove, inventory and serialization require region ownership | Event/command region confinement, explicit API ownership guard, success-before-consume transaction order | High | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/TraitActivationListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/TraitCleanupListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/TrampleListener.java` | Per-tick global online-player scan and shared mutable maps | Mount/dismount-driven entity tasks; concurrent cooldown claims; target scheduler dispatch | High | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/UndeadTraitListener.java` | Delayed entity/block work and cross-entity effects | Entity-following tasks, region block tasks, target revalidation and lifecycle cleanup | High | Migrated/audited |
| `me/luisgamedev/betterhorses/listeners/WorldCommandListener.java` | Handlers can execute concurrently in independent regions | Keep live Bukkit access event/entity-confined; avoid retained mutable entity state | Medium | Migrated/audited |
| `me/luisgamedev/betterhorses/scheduler/BetterHorsesScheduler.java` | No legacy equivalent; central ownership required | Explicit entity, region, global and async scheduler gateway with lifecycle cancellation | High | Migrated/audited |
| `me/luisgamedev/betterhorses/scheduler/ScheduledTaskHandle.java` | No legacy equivalent; central ownership required | Explicit entity, region, global and async scheduler gateway with lifecycle cancellation | High | Migrated/audited |
| `me/luisgamedev/betterhorses/scheduler/TaskRegistry.java` | No legacy equivalent; central ownership required | Explicit entity, region, global and async scheduler gateway with lifecycle cancellation | High | Migrated/audited |
| `me/luisgamedev/betterhorses/training/TrainingManager.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/traits/TraitParticleResolver.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/traits/TraitRegistry.java` | Delayed entity/block work and cross-entity effects | Entity-following tasks, region block tasks, target revalidation and lifecycle cleanup | High | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/AttributeResolver.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/CooldownDisplay.java` | Static UI/effect tasks and cleanup scans | Concurrent UUID state and entity-owned cleanup without world scans | High | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/HorseArmorUtils.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/HorseDataValidator.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/MountConfig.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/PermissionUtils.java` | Utility/config reads used concurrently by region handlers | Keep helpers stateless/immutable and perform Bukkit access only from owner context | Low | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/SupportedMountType.java` | Entity spawn/remove, inventory and serialization require region ownership | Event/command region confinement, explicit API ownership guard, success-before-consume transaction order | High | Migrated/audited |
| `me/luisgamedev/betterhorses/utils/WorldAccessPolicy.java` | Reload/lifecycle state visible to concurrent regions | Synchronized registration, immutable volatile world-filter publication, owned task shutdown | Medium | Migrated/audited |

## Compatibility

* **Compile target:** Paper API 1.20.6, Java 21, one plugin artifact.
* **Declared platforms:** Paper 1.20.6-compatible builds and corresponding Folia API builds.
* **Optional integration:** PlaceholderAPI 2.11.6 remains a soft dependency.
* **ProtocolLib:** not used; Ghost Horse uses bounded vanilla invisibility effects.
* **Runtime validation limitation:** this workspace did not include Paper or Folia server binaries.
  Therefore startup, gameplay, legacy-item and genuine multi-region validation remain mandatory
  before a release is published; compilation alone is not reported as runtime verification.

## Manual runtime test protocol

1. Start clean Paper and Folia 1.20.6 servers on Java 21 with the built JAR and default config.
2. On Folia, keep two players at least 1,000 blocks apart so their regions tick independently.
3. Spawn every supported mount type in both regions; round-trip item conversion including armor,
   saddle, llama/chested inventory, ownership, growth, training and every persisted trait.
4. Exercise every active/passive trait while crossing regions/worlds; remove entities and disconnect
   riders during active tasks. Inspect logs for ownership exceptions.
5. Exercise cross-region Trample/Kickback/Skyburst targets at a region boundary.
6. Run 100 `/horse reload` cycles while growth, UI, Trample and temporary traits are active. Confirm
   task counts settle, listeners do not duplicate, and temporary effects are restored.
7. Repeat chunk load/unload, death, transformations, cancelled public events, full inventories and
   simultaneous spawn/despawn attempts. Confirm no duplication or lost items.
8. Profile task count and allocation rate with many mounts. Expected steady state is one task per
   tracked relevant mount, only while loaded, plus tasks for currently mounted Trample riders and
   active temporary effects; there are no periodic global entity/player scans.

## Performance result

The old one-second full-world trait scan, growth full-world scan and per-tick online-player Trample
scan were removed. Growth and particles share a one-second mount task; Trample exists only for active
riders. State is retired on entity removal and plugin shutdown. The remaining performance concern to
measure on a real server is scheduler overhead with unusually large numbers of simultaneously loaded
mounts; the combined task deliberately bounds that overhead to one task per tracked mount.
