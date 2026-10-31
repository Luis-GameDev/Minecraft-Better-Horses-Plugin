# Vanilla behavior adjustments

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`settings.fix-step-height: true` adjusts mounted supported horses so they can step from a path block onto a full-height block. The original value is tracked and restored on dismount/cleanup; durable recovery handles unavailable entities instead of leaving a temporary persisted change behind.

`settings.mountable-skeleton-horses: true` marks skeleton horses tamed for normal riding, including applicable spawn-egg spawns. It does not grant a player a bypass permission or enable other disabled mount types.

Permissions independently govern mounting, feeding, breeding, taming and leashing. When ownership restriction is enabled, missing BetterHorses owner data falls back to the native owner. The feature has a `betterhorses.bypass` exception and is disabled by default.

These settings adjust existing supported entities; they do not add server APIs absent from an older Minecraft version. See [Installation](installation.md) and [Permissions](Permissions.md).
