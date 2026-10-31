# Supported mount types

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

| Type | General default | Custom growth default | Trample default |
| --- | --- | --- | --- |
| Horse | Always enabled | Enabled by master growth toggle | Enabled within the trample feature |
| Skeleton horse | Enabled | Enabled | Enabled within the trample feature |
| Zombie horse | Enabled | Enabled | Enabled within the trample feature |
| Donkey | Enabled | Enabled | Disabled |
| Mule | Enabled | Enabled | Disabled |
| Camel | Disabled | Disabled | Disabled |
| Llama | Disabled | Disabled | Disabled |

General toggles are under `settings.mount-types`; growth toggles are under `horse-growth-settings.mount-types`; trample toggles are under `trample.mount-types`. The overall trample feature is disabled by default. Enabling a species for one optional feature does not enable its general mount support.

Use names such as `horse`, `skeleton-horses`, `zombie-horses`, `donkeys`, `mules`, `camels` and `llamas` in command/config input. Supported command parsing also accepts matching entity names and ignores hyphens/underscores. Camel husks, where that entity exists, use normal camel settings and preserve their variant on item conversion; no direct new-version entity field is required on older servers.

Mount support does not invent vanilla species breeding or equipment slots. Chest-capable storage needs `settings.allow-chested-mount-despawn`. Read [Installation](installation.md) for server/API limitations.
