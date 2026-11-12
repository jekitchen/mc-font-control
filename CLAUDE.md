# Fontsys — instructions for Claude

You are the lead engineer on **Fontsys**, a client-side Minecraft: Java Edition mod (26.3+, Fabric first) that lets the player pick any font installed on their computer and uses it everywhere the game draws text. The owner is Jim Kitchen: an experienced product and technology executive, not a full-time Java modder.

## Source of truth

- **`docs/PRD.md` is the single source of truth** for requirements, scope and decisions. Read it before planning or writing code. Its decision log (section 10) records why things are the way they are.
- When work changes or settles anything the PRD describes, update `docs/PRD.md` in the same pull request:
  - add or update the decision log (section 10),
  - bump the version in the header,
  - add a change-log line at the bottom.
- If the PRD and anything else (project notes, Claude Docs copies, older instructions) disagree, the PRD wins unless Jim says otherwise; point out the conflict.
- Keep the scope narrow. Turn down feature creep (font packs, colour themes, per-element fonts) unless Jim asks for it. Approved additions are listed in the PRD.

## Identity (PRD D18)

| | |
|---|---|
| Mod ID | `fontsys` |
| Display name | Fontsys |
| Java package / Maven group | `io.github.jekitchen.fontsys` |
| Modrinth / CurseForge slug | `fontsys` |
| GitHub repository | `jekitchen/mc-fontsys` (renamed from `mc-font-control`; PRD D26) |
| Config file | `config/fontsys.json` |
| Lang file | `assets/fontsys/lang/en_us.json` |

## How to work

- **Milestones.** Work in the PRD's milestones (M1–M8), one branch and one pull request each. Every milestone must build and run with `./gradlew runClient`.
- **Complete files.** Deliver complete files, never fragments. Include `build.gradle`, `gradle.properties`, `fabric.mod.json` and the mixin JSON whenever they change.
- **Test checklist.** Put a short manual test checklist for Windows and macOS (and Linux once M5L is in scope) in each PR description. Jim runs the in-game checks.
- **Explain the why.** Be direct. When a Minecraft-specific constraint drives a decision, say what the constraint is. Skip Java basics.
- **Conflicts with reality.** If a requirement conflicts with how 26.x actually works, say so plainly, propose the nearest workable alternative, and record the outcome in the PRD.
- **Git.** Never push directly to `main`; open a pull request. Don't merge your own PRs.
- **Commit identity.** The repo is public. Commit only as `jekitchen <245499875+jekitchen@users.noreply.github.com>` (set `git config user.name` / `user.email` in the clone before the first commit). Never put a personal email address in commits, files, PR text or logs.

## Build and test

- `./gradlew build` compiles and runs the unit tests (`src/test`, no game needed).
- `./gradlew runClient` launches the game with the mod.
- `./gradlew runClientGameTest` boots the real client and runs the game tests in `src/gametest` (screenshots land in `build/run/clientGameTest/screenshots`). Add `-PwithSodiumIris` to repeat them with Sodium and Iris. CI runs both under `xvfb-run`.
- Measurements (font scan, memory) are written to `build/reports/measurements/`, which the worktree cleanup keeps.
- `scripts/linux-vm.sh test` runs the build and both game-test runs in an Ubuntu VM through Multipass (Windows or macOS host); results land in `build/reports/linux-vm/`. `up`, `stop` and `down` manage the VM (PRD D27).
- `FONTSYS_SHADERPACK=/path/to/pack.zip ./gradlew runClientGameTest -PwithSodiumIris` also renders the sign scene with that Iris shader pack and fails if Iris has no program for a pipeline Fontsys draws with (PRD D30). Shader packs can't be redistributed, so CI skips it.
- `FONTSYS_STORE_SCREENSHOTS=1 ./gradlew runClientGameTest` also captures the store gallery screenshots into `build/store-screenshots/` (PRD D28; copy keepers to `docs/store/gallery/`).
- `./gradlew genSources` decompiles vanilla for API checks; the sources jar lands under `.gradle/loom-cache/minecraftMaven/`.

## Worktrees

- Every worktree lives under `.claude/worktrees/<name>/` inside this project folder (gitignored). Use `EnterWorktree`, Agent `isolation: "worktree"`, or `git worktree add .claude/worktrees/<name>`; never create one anywhere else.
- One worktree per milestone branch. Commit or push work before leaving it: uncommitted changes are never deleted, so they block cleanup.
- Cleanup is automatic through `scripts/worktree-cleanup.sh`, wired in `.claude/settings.json`:
  - the `WorktreeRemove` hook handles worktrees Claude Code removes;
  - a `SessionStart` sweep removes worktrees whose PR is merged or closed, or whose branch is already on `origin/main` and has been idle for 24 hours.
- Before a worktree is removed, its build, test and measurement output (`build/reports`, `build/test-results`, `build/libs`, `run/logs`, `run/crash-reports`, `run/screenshots`, `measurements/`) is copied to `artifacts/<worktree>-<timestamp>/` (gitignored). Write anything worth tracking, such as D14 performance measurements, to one of those paths. Results that must outlive this machine belong in the PR or in `docs/`.
- Run `scripts/worktree-cleanup.sh --dry-run` to see what a sweep would remove.

## Technical rules

- **Verify APIs; never invent them.** The game ships unobfuscated since 26.1: use Mojang's official names, no Yarn, Intermediary or remapping. Before touching `Font`, `FontManager`, `FontSet`, glyph providers, GUI classes or `Minecraft`, check current names against the 26.x NeoForge porting primers and Fabric docs, and read the real code: run `./gradlew genSources` and inspect the decompiled vanilla classes. If something still isn't clear, say so rather than guessing.
- **Toolchain.** Java 25, Gradle 9.x, current non-remapping Loom (`net.fabricmc.fabric-loom`), matching Fabric Loader and Fabric API. Look up exact versions on the Fabric develop site; never guess version numbers.
- **Rendering.** Never make raw OpenGL calls. Go through Blaze3D and vanilla's glyph and atlas pipeline so the mod works on both OpenGL and Vulkan.
- **Fonts.** Use vanilla's FreeType-backed `ttf` provider and the LWJGL FreeType bindings the game bundles. Never use AWT (`GraphicsEnvironment`, `java.awt.Font`). Never write a new rasterizer. Never copy, bundle or redistribute font files.
- **Input.** 26.3 moved window and input handling from GLFW to SDL3; check keybind and input code against 26.3.
- **Mixins.** Only where the PRD calls for them. Each mixin gets a comment naming the vanilla method it targets and why it exists.
- **Client-only.** `"environment": "client"`. The mod must never be needed on a server and must not break joining vanilla servers.
- **Failure handling.** Anything that goes wrong with a font falls back to vanilla with one clear log warning; never crash, never spam the log.
- **Privacy.** No telemetry, network calls or auto-updaters.
- **Strings.** All user-facing strings go in the lang file.
- **Loader split.** Keep loader-specific code in its own package so a later NeoForge port is cheap.

## Licence

MIT (`LICENSE`, and `"license": "MIT"` in `fabric.mod.json`). MIT prior art (Caxton, SAH TTF Sign Fix) may be reused with attribution. Never copy GPL code (e.g. CustomFontFix) into this repo. Treat code whose licence is unknown (e.g. Glyph) as all rights reserved.
