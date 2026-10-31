# 7.1 audit fixes and documentation coverage

The Standard and Folia fix PRs address the following defects in the 7.1 development code. The companion documentation PR targets `main` and supplies reviewed sources for the separate GitHub wiki. The ordinary `main` plugin code remains its existing 7.0 line until code changes are merged there separately.

| Finding | Result in the fix branches | Regression evidence |
| --- | --- | --- |
| C01 | Standard lifecycle registration no longer requires a Paper-only event class | Spigot listener-signature check and 1.20.4 linkage scan |
| C02 | Equipment uses portable Bukkit serialization; unreadable legacy data fails closed | Portable item round trip, rejected payloads and API conversion tests |
| C03 | Legacy and modern particle/potion names resolve without linking missing fields | Compatibility resolver tests and API binary scan |
| C04 | Shears wear uses legacy enchantment names and a guarded max-damage capability | Runtime capability tests and 1.20.4 API scan |
| C05 | Cancelled replacement spawns retain the original undead mount and restoration item | Cancelled-spawn and post-commit failure tests |
| C06 | Undead cargo excludes equipment slots; old arrays cannot recreate saddles | New/legacy cargo restoration tests |
| C07 | Dashboost uses an owned modifier rather than rewriting a native genetic base | Unload recovery and training/base invariants |
| C08 | Ability cooldown timestamps follow capture and respawn through PDC | Conversion to a new entity UUID preserves the timer |
| C09 | Mount ownership falls back to the native owner when stored ownership is missing/invalid | Native owner, bypass and malformed owner tests |
| C10 | Feeding progress requires permitted native food and an observed feeding result | Consumed/unused/cancelled/Creative/duplicate interaction tests |
| C11 | Already-cancelled breeding and rejected API results do not commit parent state | Parent cooldown/age and event dispatch tests |
| C12 | Bred foals receive genetic keys and mount type before API lookup | Immediate API recognition and stat tests |
| C13 | Growth setters validate stages and reconcile age, lock and final size | Juvenile/adult threshold and occupied-growth tests |
| C14 | Corrupt equipment/chest payloads and failed serialization cannot produce an incomplete conversion | Source retention, invalid lengths/types and failed capture tests |
| C15 | Folia shutdown submits no disabled-plugin entity work; temporary Paper/Folia changes use transient modifiers | Disabled/racing scheduler, step-height ownership and durable recovery tests |
| C16 | Deferred Folia hits recheck actor/target ownership and range | Foreign rider, moved target and valid-hit tests |
| C17 | Deferred undead remounting skips foreign or displaced passengers | Foreign-passenger remount test |
| C18 | Ghosthorse cleanup clears tracking and retains unrelated replacement invisibility | Reload tracking/effect ownership tests |
| C19 | Eligible idle riders receive bounded refreshed passive effects; lost eligibility clears owned effects | Idle Fireheart and permission revocation tests |
| C20 | Cancelled damage does not apply curse retaliation | Cancelled damage test |
| C21 | Alias refresh removes identity-owned old entries and preserves other plugins' commands | Actual command-map replacement/removal tests |
| C22 | The chested-spawn failure message exists and explains source retention | Default language resource check |
| Additional compatibility correction | Standard actionbar output uses the shaded Bukkit audience bridge | Spigot 1.20.4 compiled-member scan |

The API binary scans compare compiled Standard classes with official Spigot and Paper 1.20.4 API JARs. This catches missing classes, methods and fields; it does not simulate a complete Minecraft server. Full JUnit suites, deployable shaded JAR tasks and artifact checks supplement that scan. No exhaustive live multiplayer/server-fork matrix is claimed.

On Paper/Folia, newly applied movement/step-height adjustments use non-persistent modifiers. Cleanup touches a live entity only from its owning thread. A foreign-region live plugin unload may leave a bounded potion effect or transient modifier in memory until native expiry/unload or owner-thread reconciliation; cleanup does not submit forbidden tasks or permanently rewrite the saved native base. Portable Spigot fallback state records permit later recovery. Original values which were never recorded by an older plugin version cannot be reconstructed reliably by guessing.

## Documentation coverage

`wiki-coverage.json` records the 27 existing wiki pages reviewed. The updated source set adds private inspection, neutering/shears and random-size pages and replaces the sidebar navigation. The reference includes:

- Current API signatures, nullable wrappers, cancellation rollback, unified neutering targets and trample overrides.
- Correct configuration/language nesting and source-matched default blocks; valid shader flow-map examples.
- Complete permission nodes and descriptor defaults, growth timing/thresholds and actual branch-specific artifact names.
- World filtering, owner enforcement, chest/equipment preservation, training and persistent cooldown behavior.
- Standard live shears neutering, its durability/cancellation rules, and the global neutering switch.
- Hidden independent sizes, legacy migration and persistence; optional `null` command arguments.
- Existing-page links, developer navigation and the exact limits of ownership bypass permissions.

Concrete Java documentation examples compile against both final APIs. Fenced YAML examples parse, relative page targets resolve, and permission/default tables are checked against the source descriptors/resources. `export_wiki.py` validates and exports wiki-ready links. See [WIKI_SYNC.md](WIKI_SYNC.md) for publication into GitHub's separate wiki repository; merging ordinary repository documentation does not itself update the live wiki.
