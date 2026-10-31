# Installation and compatibility

> This reference describes the 7.1 development branches. Use an artifact containing those changes; a published 7.0 build may expose fewer features. Standard-only interactions are labelled explicitly.

Use **Java 21** for both artifacts, including when the Minecraft server itself is version 1.20.4. Java 17 cannot load the Java 21 plugin classes.

| Branch/artifact | Intended server | Current Gradle output |
| --- | --- | --- |
| `version/7.1` | Standard Bukkit/Spigot or Paper, Minecraft 1.20.4 and later with feature-specific API limits | `BetterHorses/build/libs/BetterHorses-7.1-all.jar` |
| `folia/version/7.1` | Folia; this artifact directly uses Folia scheduling APIs | `BetterHorses/build/libs/BetterHorses-7.0-Folia.jar` |

The Folia development branch currently retains `7.0` in its build metadata. The branch name and the JAR filename are different identifiers. Use the filename actually produced by that checkout. Do not install both artifacts together. The ordinary unshaded Java JAR is not the deployable Standard artifact.

1. Stop the server and back up the plugin data and worlds before replacing an existing installation.
2. Place the matching shaded JAR in `plugins/` and start the server.
3. Edit `plugins/BetterHorses/config.yml` and `language.yml`.
4. Apply configuration changes with `/horse reload` and verify the console output.

For a local build, enter `BetterHorses/` and run `./gradlew shadowJar` for Standard, or `./gradlew verifyPluginJar` for Folia. Build dependencies require their configured Maven repositories.

The compatibility target does not make every optional feature available on every server. Minecraft 1.20.4 has no `SCALE` attribute, so visible custom growth and individual size variation are unavailable there; persistent size data is retained. Client shaders require Minecraft 26.3 and the supported post-effects API. Textures may also depend on the client's version and resource pack.

PlaceholderAPI is optional. Install the required expansions when using placeholders. BetterHorses does not supply a client resource pack. Runtime validation across every server fork and Minecraft release remains separate from compilation/unit tests.

New equipment serialization is portable through Bukkit. Legacy equipment encoded by Paper's byte API can be read on compatible Paper/Folia; an unreadable legacy payload on Spigot is rejected without consuming the horse item. Convert that item on a compatible Paper server before moving it to Spigot.
