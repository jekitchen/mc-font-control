# Releasing Fontsys

Checklist for a release (PRD D15, D29). Releases need the owner's go-ahead. Pushing the tag builds the jar and drafts the GitHub release; everything else is manual.

## Before the release

1. Every milestone PR is merged into `main` and CI is green, including both client game test runs (vanilla and Sodium + Iris).
2. The manual checks in the PRD test matrix (section 9) pass on Windows 11 and macOS. Key items:
   - OpenGL and Vulkan (Options → Video → Renderer).
   - Iris with a shader pack: signs, glow ink, text displays, name tags. `FONTSYS_SHADERPACK=/path/to/pack.zip ./gradlew runClientGameTest -PwithSodiumIris` automates part of this check: it loads the pack, fails if Iris has no program for a pipeline Fontsys draws with, and saves `shaderpack-*` screenshots (PRD D30).
   - A server with a custom-glyph resource pack: its HUD glyphs still show.
   - A config copied from another computer: WARN line in the log, no dialog, vanilla font.
   - The game killed during the keep-or-revert countdown: the previous font is back at the next launch.
3. Set `mod_version` in `gradle.properties` (semantic versioning; the jar gets `+26.3` appended, e.g. `1.0.0+26.3`).
4. In `CHANGELOG.md`, replace "unreleased" in the version's heading with the release date (`## [1.0.0] - 2026-12-01`). The release notes are taken from that section.
5. Update the PRD: status, decision log and change log.
6. Merge the release PR into `main`.

## Build

```sh
./gradlew clean build
```

The release jar is `build/libs/fontsys-<version>+26.3.jar`. Don't publish the `-sources` jar as the main file.

## Publish

1. Tag the merge commit on `main` and push the tag:
   ```sh
   git tag v1.0.0 && git push origin v1.0.0
   ```
   The `release` workflow checks that the tag matches `mod_version`, builds and tests, and creates a **draft** GitHub release with the jar and the changelog section as notes. Review it on GitHub and press Publish.
2. Publishing the GitHub release runs the `publish` workflow (PRD D31). It uploads the release's jar to Modrinth and CurseForge as version `<mod_version>+<minecraft>` (e.g. `1.0.1+26.3`), named "Fontsys <version>", with the release notes as changelog:
   - Loader: Fabric. Game versions: `publish_game_versions` in `gradle.properties`. Java 25.
   - Dependencies: Fabric API (required), Mod Menu (optional).
   - It needs, in the repository's Actions settings: variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` (the numeric or base-62 project IDs shown on each project page, not the slug), and secrets `MODRINTH_TOKEN` (a Modrinth personal access token with the "Create versions" scope) and `CURSEFORGE_TOKEN` (a CurseForge API token from the account's API tokens page). A store whose project ID variable is empty is skipped.
   - The CurseForge upload runs even if the Modrinth one fails (PRD D46); the run is then marked failed. Fix the cause and rerun the failed store from Actions → publish → Run workflow, with the tag and that store only, so the other store doesn't get a duplicate.
3. Check both project pages. The upload doesn't touch the project description: if `docs/store/description.md` changed, paste it into both sites by hand (on CurseForge, in the Markdown editor; see [`docs/store/`](store/README.md)). Modrinth's AI-content disclosures (D28) are project settings, so they carry over to new versions.

Manual upload, if the workflow is unavailable: the same jar and metadata through each site's upload form (environment: client required, server unsupported).

## Listing copy

The description, summary, categories, gallery screenshots and form fields for both sites are in [`docs/store/`](store/README.md), with the disclosures Modrinth requires (PRD D28).
