# Releasing Fontsys

Checklist for a release (PRD D15, M7). Releases need the owner's go-ahead; nothing here is automated.

## Before the release

1. Every milestone PR is merged into `main` and CI is green, including both client game test runs (vanilla and Sodium + Iris).
2. The manual checks in the PRD test matrix (section 9) pass on Windows 11 and macOS. Key items:
   - OpenGL and Vulkan (Options → Video → Renderer).
   - Iris with a shader pack: signs, glow ink, text displays, name tags.
   - A server with a custom-glyph resource pack: its HUD glyphs still show.
   - A config copied from another computer: WARN line in the log, no dialog, vanilla font.
   - The game killed during the keep-or-revert countdown: the previous font is back at the next launch.
3. Set `mod_version` in `gradle.properties` (semantic versioning; the jar gets `+26.3` appended, e.g. `1.0.0+26.3`).
4. Update the PRD: status, decision log and change log.

## Build

```sh
./gradlew clean build
```

The release jar is `build/libs/fontsys-<version>+26.3.jar`. Don't publish the `-sources` jar as the main file.

## Publish

1. Tag the commit `v<version>` and create a GitHub release with the jar attached.
2. Modrinth (primary home), project slug `fontsys`:
   - Loader: Fabric. Game version: 26.3. Environment: client required, server unsupported.
   - Dependencies: Fabric API (required), Mod Menu (optional).
   - Link the Modrinth page from the README.
3. CurseForge, slug `fontsys`, same metadata.

## Listing copy

The description, summary, categories, gallery screenshots and form fields for both sites are in [`docs/store/`](store/README.md), with the disclosures Modrinth requires (PRD D28).
