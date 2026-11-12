# mc-font-control

Minecraft: Java Edition mod (26.3+) that lets you use any TTF/OTF font installed on your computer for all in-game text. Supports Windows and macOS; Linux is best effort.

- **Status:** in development. Requirements and decisions live in the [PRD](docs/PRD.md).
- **Downloads:** Modrinth (primary) and CurseForge, once released.
- **Licence:** [MIT](LICENSE).

## Building

Requires Java 25. The Gradle wrapper downloads everything else.

```sh
./gradlew build                # compile and run unit tests; jar in build/libs/
./gradlew runClient            # launch Minecraft with the mod
./gradlew runClientGameTest    # boot the client and run the automated game tests
```
