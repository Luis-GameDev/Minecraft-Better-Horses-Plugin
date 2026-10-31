# /horsecreate

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Permission: `betterhorses.create` (default `op`). Only players can use the command.

```text
/horsecreate <health> <speed> <jump> [gender] [name] [trait] [growth] [mounttype] [neutered]
```

Health, speed and jump are required finite positive **raw internal stats**. Growth must be an integer from 1 to 10. The optional fields are positional:

| Field | Omitted or `null` default |
| --- | --- |
| `gender` | Random `male` or `female` |
| `name` | Localized `messages.horse` |
| `trait` | Random trait selection according to enabled traits and their chances |
| `growth` | `10` |
| `mounttype` | `horse` |
| `neutered` | `false` |

Every optional slot accepts case-insensitive `null` without shifting later fields. Required numeric fields do not accept it. Explicit traits and mount types must be enabled. The command's `none` trait value follows the existing no-override command behavior, so it does not force the API's explicit no-trait override.

```text
/horsecreate 20 0.3 0.7 null null null null donkey null
/horsecreate 30 0.25 0.8 female Daisy fireheart 10 horse false
```

The first example selects a donkey while defaulting the other optional fields. `neutered=true` is suppressed while the global neutering setting is disabled. The hidden individual size multiplier is assigned automatically and has no command/lore field.

See [Spawn](command_spawn.md), [Genetics](genetics.md), and [Random size](random-size.md).
