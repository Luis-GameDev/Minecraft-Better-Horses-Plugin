# /horse reload

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

`/horse reload` reloads configuration and language files. Permission: `betterhorses.reload` (default `op`); console use is supported.

Reload reconciles enabled listeners and training, cleans up temporary trait state, refreshes shader rules, updates aliases, and rebuilds available `/horse` routes and generated help. It applies the global neutering toggle without restarting the server. Supported mounts are reconciled on their appropriate server/entity threads.

Use a server restart to replace the plugin JAR. A configuration reload does not upgrade already-loaded classes or supply APIs absent from the server. Review console warnings after a reload, especially YAML and optional shader diagnostics.

See [Configuration](configuration.md) and [Info](command_info.md).
