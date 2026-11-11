# MC Font Control — Product Requirements Document

| | |
|---|---|
| **Status** | Draft v0.17 (v1.0 scope built; verified on Windows 11 and macOS; Linux verification pending) |
| **Owner** | Jim Kitchen |
| **Last updated** | 2026-11-11 |
| **Source of truth** | This file (`docs/PRD.md`). Change it through pull requests; see [Maintaining this document](#maintaining-this-document). |

## 1. Summary

**MC Font Control** is a client-side mod for Minecraft: Java Edition 26.3 and later. It lets a player pick any font installed on their Windows or macOS machine (Linux best effort) and uses it everywhere the game draws text: menus, HUD, chat, tooltips, signs, books, name tags and text displays.

The mod is deliberately narrow. It does one thing — swap the game's font for a system font, safely and reversibly — and does it on top of vanilla's own FreeType `ttf` provider and resource-pack system rather than a custom renderer. It never needs to be on a server, never redistributes font files, and falls back to the vanilla font whenever anything goes wrong.

| | |
|---|---|
| **Product** | MC Font Control (mod ID `mcfontcontrol`) |
| **Type** | Client-only Minecraft mod (`"environment": "client"`) |
| **Baseline** | Minecraft Java 26.3.x, Java 25 |
| **Version support** | Latest Minecraft release only, best effort (D15) |
| **Loaders** | Fabric (primary); NeoForge (later, optional) |
| **Platforms** | Windows 11, macOS; Linux best effort (P2) |
| **Distribution** | Modrinth (primary home), CurseForge |
| **License** | MIT |

## 2. Problem, goals and non-goals

### Problem

Minecraft draws all text in its 8px bitmap font. Players who want a different look — for readability, accessibility, a cleaner aesthetic or simply personal taste — today have two poor options: build a resource pack by hand around a TTF file (copying, and often redistributing, the font) or use mods that replace the text renderer and break on each major rendering change. Neither lets a player simply choose a font already on their computer and see it everywhere.

### Goals

1. **Pick any installed font.** Discover every font on the system, including faces inside `.ttc` / `.otc` collections (most macOS system fonts) and the named weights of variable fonts (SF Pro, Segoe UI Variable).
2. **Apply it everywhere text is drawn**, immediately, with no restart.
3. **Preview before committing**, without touching the live game font.
4. **Never break the game.** Missing, corrupt, unreadable or unsupported fonts fall back to vanilla with one log warning, and every font change can be reverted automatically if the player can't read the result.
5. **Stay renderer-agnostic.** Work on both the OpenGL and the experimental Vulkan backend by going through vanilla's glyph and atlas pipeline.
6. **Survive the 26.x release train** with a thin mixin surface that is cheap to re-verify each snapshot.
7. **Respect servers.** Never break server resource packs that use custom glyphs for their UI.

### Non-goals

- Font packs, bundled fonts or downloading fonts.
- Colour themes, text effects or per-element fonts (e.g. a different font for chat vs. signs).
- Server-side behaviour or any requirement for the server to have the mod.
- Full Linux support at v1.0. Linux ships best effort (P2): discovery and rendering are implemented, but testing is lighter than on Windows and macOS.
- A custom rasterizer or MSDF renderer.
- **Complex text shaping.** Vanilla doesn't join or reorder glyphs, so scripts such as Arabic or Devanagari won't render correctly even with a system font that covers them.
- **Colour emoji.** Vanilla's glyph atlas is grayscale; colour glyphs are not supported.
- **Older Minecraft versions.** Only the latest release is supported (D15).
- Telemetry, network calls or auto-updaters.

Any of these can be revisited only by explicit owner decision.

### Competitive landscape

| Project | What it does | Where MC Font Control differs |
|---|---|---|
| **Font** (Modrinth) | Client-side mod (Fabric, Forge, NeoForge; 26.2 and 26.3 snapshots). The player drops `.ttf` / `.otf` files into a folder inside the game directory; it has a font manager (F7) with preview, size and bold toggles, and auto-disables a font that is deleted or unreadable. | Closest competitor. Fonts must be copied into its folder; no system-font discovery, `.ttc` collections or variable-font weights documented, and no auto-revert. |
| **FontSwap** (Modrinth, CurseForge) | Forge / NeoForge mod (up to 26.1.x) that imports a font file and restyles all text, with size, vertical offset, line spacing, faux bold and text-shadow controls. | Forge / NeoForge only and not yet on 26.3; imports font files rather than listing installed fonts. Some of its controls (line spacing, shadow colour) are out of our scope by design. |
| **Caxton** (MIT) | MSDF TrueType/OpenType rendering through a native library; real bold and italic; fonts added via resource packs. | Heavier: custom renderer and native library, and MSDF in-world text doesn't render under Iris. We stay on vanilla's renderer and pick from system fonts. |
| **Glyph** | Generates a resource pack from an imported TTF; the player enables the pack manually. | Copies the font into a pack and needs a manual step. We never copy fonts and apply in one click. |
| **Smooth Font** (CurseForge) | Older Forge mod that redraws all fonts smoothly and can use fonts from a folder in the game directory or installed on the computer. | Legacy Forge versions only; a full renderer replacement rather than vanilla's pipeline. |
| **Modern UI** (CurseForge) | Large UI framework with its own text layout engine, including BiDi text and sharper font atlases. | A whole UI overhaul, not a focused font picker; useful reference for what complex shaping would take (a non-goal for us). |

**Positioning:** pick any font already on your computer — including macOS collection faces and variable-font weights — preview it live, and apply it in one click with an automatic safety revert, without copying font files and on the game's own renderer.

## 3. Users and use cases

### Target users

- **Aesthetic players** who want the game to match their desktop or a favourite typeface.
- **Readability-focused players** — large monitors, high GUI scales, or a preference for a cleaner sans-serif — including players with low vision or dyslexia who rely on a specific system font.
- **Content creators and server players** who want a distinctive look in recordings without asking a server to install anything.
- **CJK and multilingual players** who want a native system font rather than vanilla's fallback glyphs.
- **Pixel-font fans** who want crisp, hard-edged text (crisp mode, P2).
- **Modpack players**, who receive the mod pre-installed with someone else's config.

### User stories

1. As a player, I open the mod's settings from Mod Menu, search "Avenir", pick *Avenir Next – Medium*, and see a live preview before I apply it.
2. As a player, I press **Apply** and immediately see the new font on the title screen, menus, HUD, chat, tooltips, books and signs — no restart.
3. As a player, I accidentally apply a font I can't read; I do nothing, and after 30 seconds the game reverts to my previous font on its own.
4. As a player, I adjust size, oversample and baseline shift until text looks crisp at my GUI scale.
5. As a player, I press **Reset to vanilla** and everything returns to the default font.
6. As a player, I install a new font in my OS, press **Rescan fonts**, and it appears in the list.
7. As a player, I uninstall a font I had selected; on next launch the game uses vanilla text and logs one warning — no crash, no error spam.
8. As a player, I join a server whose resource pack draws custom HUD icons, and they still look right with my font applied.
9. As a modpack player, I install a pack that ships someone else's MC Font Control config; the mod notices the config came from another system and quietly starts from vanilla, instead of failing to find their fonts.

## 4. Platform and technical constraints

Every item here must be checked against current Fabric and NeoForge 26.x documentation before code depends on it. Exact versions are looked up on the Fabric develop site, never guessed.

| Area | Requirement | Why it matters |
|---|---|---|
| Minecraft | Java Edition 26.3.x baseline (released 15 Sep 2026); track the next drop's snapshots as they ship | 26.x moved substantial rendering and GUI code; the mod must be re-verified each release. |
| Mappings | Mojang official names only; no Yarn, Intermediary or remapping step | The game ships unobfuscated since 26.1. |
| Java | 25 | Game minimum since 26.1. |
| IDE | IntelliJ IDEA 2025.3+ | Needed for working mixin support on Java 25. |
| Build | Gradle 9.x, current non-remapping Loom (`net.fabricmc.fabric-loom`) | Matches the unobfuscated toolchain. |
| Loader | Current Fabric Loader with matching Fabric API; NeoForge later via a `common` / `fabric` / `neoforge` split | Keeps loader-specific code thin. |
| Rendering | No raw OpenGL calls; use Blaze3D and vanilla's glyph/atlas pipeline | 26.2 added an experimental Vulkan backend and OpenGL is slated for removal. Vulkan on macOS runs through a translation layer. |
| Windowing and input | Use 26.3's input APIs | 26.3 replaced GLFW with SDL3 for window, keyboard and mouse handling; older keybind examples may not apply. |
| Environment | Client-only | Must never be required on a server or break vanilla server joins. |
| Font I/O | LWJGL FreeType bindings bundled with the game; **no AWT** | AWT's `GraphicsEnvironment` / `java.awt.Font` conflicts with the game's main-thread window handling on macOS (GLFW through 26.2, SDL3 from 26.3). |

**API hygiene.** Before touching `Font`, `FontManager`, `FontSet`, glyph providers, GUI classes or `Minecraft`, confirm current names and signatures against the 26.x porting primers. Where a signature is uncertain, flag it and request the decompiled source rather than guessing.

## 5. Functional requirements

Priority key: **P0** = required for v1.0 definition of done; **P1** = v1.0 if feasible; **P2** = later.

### 5.1 Font discovery (`SystemFontIndex`)

| ID | Requirement | Pri |
|---|---|---|
| FD-1 | Scan Windows `%WINDIR%\Fonts` and `%LOCALAPPDATA%\Microsoft\Windows\Fonts`. | P0 |
| FD-2 | Scan macOS `/System/Library/Fonts` (incl. `Supplemental`), `/Library/Fonts`, `~/Library/Fonts`. | P0 |
| FD-3 | Accept `.ttf`, `.otf`, `.ttc`, `.otc`; read family, style and face count via LWJGL FreeType. | P0 |
| FD-4 | Group faces into families with Regular / Bold / Italic / Bold Italic slots. | P0 |
| FD-5 | Scan on a background thread; never block the render thread. | P0 |
| FD-6 | Cache to `config/mcfontcontrol/font-index.json` keyed by path + mtime + size; rescan on demand. | P0 |
| FD-7 | On an unrecognized OS, or when no fonts are found, show a clear message in the UI without crashing. | P0 |
| FD-8 | Debug command that lists discovered families (developer aid, milestone 2). | P1 |
| FD-9 | Linux: recursively scan `/usr/share/fonts`, `/usr/local/share/fonts`, `~/.local/share/fonts` and legacy `~/.fonts`, following symlinks safely (no loops, no duplicates). | P2 |
| FD-10 | Linux: when running in a Flatpak launcher, also scan `/run/host/fonts`; if no host fonts are visible, say so in the UI and explain why. | P2 |
| FD-11 | Linux (optional): read extra font folders from fontconfig config files. Standard folders alone are acceptable for the first Linux release. | P2 |
| FD-12 | **Variable fonts:** list each named instance (e.g. SF Pro Light / Regular / Semibold / Bold) as a selectable style. FreeType addresses named instances through the upper bits of the face index (`faceIndex = (instance << 16) \| face`), so the same D1 mixin covers them. | P0 |
| FD-13 | **Hidden by default:** symbol fonts (symbol `cmap` encoding or no Latin coverage, e.g. Wingdings, Webdings) and colour fonts. Symbol fonts and colour fonts that also carry outline glyphs can be revealed with the **Show hidden fonts** toggle (UI-10). Bitmap-only colour fonts (e.g. Apple Color Emoji) stay hidden always, because they can't render in vanilla's grayscale atlas. macOS system-private fonts (family name starting with a dot) are never listed (D25). | P0 |

### 5.2 Applying the font (`RuntimeFontPack`)

| ID | Requirement | Pri |
|---|---|---|
| AP-1 | Register a built-in runtime resource pack, in memory or generated under `config/` — never in the user's `resourcepacks/`. Registered through a small mixin on the game's pack list unless Fabric API offers a native hook (D3). It sits above the player's own packs but below server-provided packs (D11). | P0 |
| AP-2 | Override `assets/minecraft/font/default.json` with the selected font first as a `ttf` provider, followed by the vanilla providers, so missing glyphs fall back to vanilla instead of tofu. | P0 |
| AP-3 | Optionally override `uniform.json` when `applyToUniform` is on. | P1 |
| AP-4 | Serve font bytes from the user's disk at load time. Never copy, bundle or redistribute font files. | P0 |
| AP-5 | Support face selection inside `.ttc` / `.otc` collections and variable-font named instances. Vanilla's `ttf` provider has no face-index field, so one mixin passes the index to `FT_New_Memory_Face` (D1). | P0 |
| AP-6 | Apply changes with a full resource reload; add a font-only reload only if Apply is measured as too slow (D2). No restart. | P0 |
| AP-7 | On any failure (missing file, FreeType error, corrupt font), fall back to vanilla and log exactly one clear warning. | P0 |
| AP-8 | Draw bold, italic and bold-italic text with the family's real faces instead of vanilla's simulated styles (1-pixel double draw, shear). With no heavier face, FreeType emboldens the outline; with no italic face, vanilla's slant is kept (D24). | P0 |
| AP-9 | Load `.otf` and other FreeType-supported formats through the `ttf` provider. If vanilla still rejects non-TrueType files, lift that check in the same mixin as AP-5 (D1). | P0 |
| AP-10 | **Crisp mode for pixel-style fonts**: optional hard-edged (non-antialiased) glyph rendering. FreeType renders 1-bit monochrome glyphs, expanded to full on/off coverage in the existing grayscale atlas, with glyph size and positions snapped to whole pixels at the current GUI scale. Must work in GUI and in-world text, on both renderers. See section 5.5. | P2 |
| AP-11 | **Protect server glyphs:** the system-font provider skips the Unicode private-use ranges (U+E000–U+F8FF, and the supplementary private-use planes if the provider's `skip` field can express them) so server packs' custom HUD glyphs and negative-space characters always come from the server pack or vanilla (D11). | P0 |
| AP-12 | **Other font mods:** if Caxton, Font or another known font-replacement mod is loaded, show a one-time notice and leave the game font alone until the player resolves the conflict. Keep the list of known mods in one place. | P1 |

### 5.3 Configuration

Stored in `config/mcfontcontrol.json`. Defaults: disabled, vanilla font.

| Field | Type | Purpose |
|---|---|---|
| `configVersion` | int | Schema version; migrate older files on load (D16). |
| `systemFingerprint` | string | Hash of the OS family and user home path this config was written on; used to detect a copied config (section 5.6). |
| `enabled` | bool | Master switch. Default `false`. |
| `fontPath` | string | Absolute path to the font file. |
| `faceIndex` | int | Face inside a collection file, plus the named-instance bits for variable fonts. |
| `familyName` | string | Display name; used to re-find the font if the path changes. |
| `styleName` | string | Display name of the face or named instance. |
| `size` | float | Glyph size; default derived from GUI scale. |
| `oversample` | float | Rasterization oversampling; default derived from GUI scale. |
| `shiftX`, `shiftY` | float | Baseline adjustments, clamped to usable ranges. |
| `applyToUniform` | bool | Also replace the uniform font. |
| `showHiddenFonts` | bool | Reveal hidden symbol and outline-colour fonts in the list (FD-13). Default `false`. |
| `pending` | object or null | Previous settings while a keep-or-revert confirmation is open (UI-11). If the game starts with this set, the change was never confirmed: revert to it. |
| `crispEdges` | bool | P2 (M8): hard-edged rendering for pixel-style fonts. Default off; ignored until the feature ships. |

There is no `antialias` field: vanilla always antialiases TTF glyphs and `oversample` controls smoothness (D5).

**Robustness (D16).** If the file is unreadable or invalid JSON, rename it to `mcfontcontrol.json.bak`, start from defaults and log one warning. Unknown fields are ignored; missing fields take defaults; a newer `configVersion` than the mod understands is treated as unreadable.

### 5.4 Config screen

| ID | Requirement | Pri |
|---|---|---|
| UI-1 | Opens from Mod Menu (Fabric) and the mods list (NeoForge). | P0 |
| UI-2 | Optional keybind to open the screen (unbound by default). | P1 |
| UI-3 | Searchable list of font families and a style picker (faces and variable-font named instances). | P0 |
| UI-4 | Size, oversample and baseline-shift sliders with clamped ranges. | P0 |
| UI-5 | Live preview panel: pangram, digits, symbols, and samples styled as chat, tooltip and sign text. | P0 |
| UI-6 | Preview renders with a private `Font` backed by a temporary `FontSet` before Apply, without touching the global font (D4). | P0 |
| UI-7 | **Apply**, **Reset to vanilla** and **Rescan fonts** buttons. | P0 |
| UI-8 | Vanilla widgets and screen conventions; all strings in `assets/mcfontcontrol/lang/en_us.json`. | P0 |
| UI-9 | **Crisp edges** toggle under the sliders (M8). | P2 |
| UI-10 | **Show hidden fonts** toggle on the font list. Revealed fonts are marked (e.g. "symbol") so the player knows why they were hidden. | P0 |
| UI-11 | **Keep-or-revert confirmation** after any change that alters the applied font (Apply, or Reset if it changes anything). See section 5.7. | P0 |

### 5.5 Future: crisp mode for pixel-style fonts (P2)

**Why.** Vanilla always antialiases TTF glyphs. That suits most fonts, but pixel-style fonts (bitmap-look TTFs, Minecraft-style recreations) come out soft and fuzzy. Players who choose those fonts want hard, square pixel edges.

**What the player gets.** A **Crisp edges** toggle in the config screen, shown under the size and oversample sliders. When it's on, the preview and the applied font use hard-edged glyphs. When the selected family looks like a pixel font (for example, it ships embedded bitmap strikes), the screen suggests turning it on, but never turns it on by itself.

**How (expected, verify before building).**

- **Rasterization:** a mixin on vanilla's TTF glyph rendering switches FreeType to monochrome output (`FT_LOAD_TARGET_MONO` / `FT_RENDER_MODE_MONO`) when crisp mode is on, and expands the 1-bit bitmap to 0 / 255 coverage so the existing grayscale atlas and render pipelines need no new texture format.
- **Pixel snapping:** glyph size, oversample and baseline shift snap to whole-pixel multiples of the current GUI scale; otherwise hard edges land between pixels and look uneven. Recompute on GUI-scale change.
- **Sampling:** confirm the glyph atlas is sampled without smoothing (nearest-neighbour) for crisp glyphs; if vanilla smooths it, crisp glyphs need their own atlas or sampler setting.
- **In-world text:** test signs, glow ink, text displays and name tags, with and without Iris and Sodium, since in-world text uses separate pipelines.

**Acceptance criteria.**

1. With a pixel-style TTF at GUI scales 1, 2, 3 and Auto, every glyph edge is fully on or off: no grey fringe in a screenshot.
2. Glyph strokes stay uniform width across a line of text (no 1px / 2px wobble from sub-pixel placement).
3. Toggling crisp mode updates the preview immediately and applies with the same reload path as any other change.
4. Turning it off restores vanilla antialiasing exactly.
5. Works on OpenGL and Vulkan, on Windows and macOS.

**Scope guard.** One global toggle only: no per-element crisp settings and no automatic switching.

### 5.6 New-system detection (D12)

**Why.** Configs travel: modpacks ship them, players copy game folders between computers, launchers sync instances. A config written on another system points at font files that don't exist here, possibly on another OS. Rather than half-working, the mod starts clean.

**How.**

- Every config the mod writes records a **system fingerprint** (`systemFingerprint`): a SHA-256 hash of the OS family (Windows / macOS / Linux) and the user's home folder path. Nothing is written outside the game folder, and only the hash is stored.
- On startup, if the stored fingerprint doesn't match this system (or the config has none), the mod treats it as a **new system**: it renames the old file to `mcfontcontrol.json.bak`, resets to defaults (vanilla font) and rescans fonts.
- **No dialog or toast.** The player just sees vanilla text and can pick a font as usual. Instead, the mod writes **one WARN line to the game log** so the reset can be traced, for example:
  `[MC Font Control] Config was written on a different system (OS family or user profile changed: macOS -> Windows); backed up to mcfontcontrol.json.bak and reset to defaults.`
  The log line names the OS families and which part changed, never the raw home path.
- Host name is deliberately **not** part of the fingerprint: renaming the computer would trigger a needless reset, and on macOS the name Java reports can change from network to network and can stall startup on a slow DNS lookup.

**What it catches and what it doesn't.**

| Situation | Result |
|---|---|
| Config moved to another OS | Reset (fingerprint differs) |
| Config moved to another user account or a computer with a different username | Reset |
| Modpack shipping the author's config | Reset, unless the author has the same OS and username |
| Computer renamed | No reset |
| Same game instance copied between launchers on one computer and account | No reset |
| Config moved between two computers with the same OS and username | No reset; if the font file is missing, the existing fallback applies: re-find by family name, else vanilla with one warning (AP-7) |
| OS reinstalled with the same username | No reset; the same font fallback covers any font that's gone |

**Out of scope.** Export/import of settings between machines; re-picking a font takes seconds.

### 5.7 Keep-or-revert confirmation (D13)

**Why.** A font can turn out unreadable after it's applied: too large, the wrong face, a symbol font, or a rendering problem on this machine. The player may not be able to read the screen well enough to undo it.

**Behaviour.**

1. When a change to the applied font takes effect, the mod saves the previous settings in `pending` and shows a dialog: **"Keep this font?"** with a visible countdown from **30 seconds**, a **Keep changes** button and a **Revert** button.
2. **Enter** or clicking **Keep changes** keeps the new font and clears `pending`.
3. **Esc**, clicking **Revert**, or the countdown reaching zero restores the previous settings and reloads.
4. The dialog itself renders in the **vanilla font** (using the same private-font technique as the preview, D4), so it stays readable whatever font was applied. If that turns out not to be possible, the timeout remains the safety net.
5. If the game closes or crashes while the dialog is open, the next launch finds `pending` set and reverts before showing the title screen.
6. The countdown keeps running when the game window loses focus, so a player who can't read the screen gets their old font back even if they click away.

**Scope.** Applies to Apply, Reset to vanilla (when it changes the applied font), toggling crisp mode, and slider changes once applied. It does not apply to preview-only changes, which never touch the global font.

## 6. Non-functional requirements

- **Performance targets (D14).** Measured on a typical mid-range machine:

  | Measure | Target |
  |---|---|
  | First font scan, ~1,000 font files | < 5 s, off the render thread |
  | Startup with a valid cache | < 0.5 s added to launch |
  | Apply (full resource reload, vanilla packs) | < 3 s |
  | Frame time with the applied font, after glyphs are cached | No measurable change vs. vanilla |

  Glyphs rasterize lazily (vanilla's provider already does); the mod never pre-bakes a whole font, so large CJK fonts with tens of thousands of glyphs stay cheap.
- **Memory (D14).** FreeType needs the whole font file in memory while it is in use. Extra heap at steady state is at most the size of the applied font file(s) plus 10 MB. While the config screen is open, the preview font may add one more font's worth, released when the screen closes. The font index never holds font bytes, only metadata.
- **Robustness.** The mod must survive a resource-pack reload, a GUI-scale change and a fullscreen toggle. A selected font that later disappears produces vanilla text and one warning, never an error loop. A corrupt config falls back to defaults (D16).
- **Compatibility.** Works on both the OpenGL and Vulkan backends. Joining vanilla servers is unaffected, and server resource packs keep their custom glyphs (AP-11). Tested with and without Sodium and Iris. Linux is best effort: supported, but tested on a single distro family.
- **Minimal invasiveness.** Mixins only for the face-index and `.otf` gaps, runtime pack registration, a reload hook (if needed), the embedded-bitmap render fix (D20), read-only accessors for the preview (D4), bold and italic faces (D24), in-world rendering fixes and (M8) crisp-mode rasterization. Each mixin carries a comment naming its target vanilla method and why it exists.
- **Clean uninstall.** Removing the mod leaves the game fully vanilla; the runtime pack lives outside `resourcepacks/`, so nothing lingers in the user's pack list.
- **Privacy.** No telemetry, network calls or auto-updaters. The font index stores only local paths and metadata. The system fingerprint is a one-way hash stored only in the local config, and the log never records the raw home path.
- **Licensing.** Font files are read in place and never copied, bundled or redistributed. The mod is MIT-licensed. Code from prior art is used only after its licence is checked: MIT projects (Caxton, SAH TTF Sign Fix) may be reused with attribution; GPL code (CustomFontFix) is reference-only and never copied; Glyph is treated as all rights reserved until its licence is confirmed.
- **Localization.** All user-facing strings in lang files; `en_us` ships first.
- **Quality and CI (D17).** Every pull request runs a GitHub Actions build. Font discovery, family grouping, hidden-font classification, config load/migrate/backup and system-fingerprint logic have unit tests that run without the game. Fabric's client game tests are used for in-game smoke tests and screenshot checks if they prove workable on 26.3 (verify in M1).
- **Version support and release (D15).** Best-effort support for the latest Minecraft release only; older versions are not maintained. Mod versions follow semantic versioning with the Minecraft version as build metadata (e.g. `1.0.0+26.3`). Releases publish to Modrinth (primary home, linked from the README) and CurseForge.

## 7. Risks and hard problems

| Risk | Impact | Mitigation |
|---|---|---|
| **Pixel grid and GUI scale** — TTF glyphs look blurry or misaligned at some scales | Text looks worse than vanilla; first impression fails | Derive `size` and `oversample` defaults from the current GUI scale and recompute on scale change. |
| **Metrics mismatch** — line height, ascent and advances differ from the 8px bitmap font | Clipped text in buttons, chat box, scoreboards | Clamp size and shift ranges so menus stay usable; document known clipping in the README. |
| **Fake bold and shadow** — vanilla's 1px offset looks wrong on vector glyphs | Bold text looks like doubled outlines | Real bold and italic faces, FreeType emboldening when the family has none (D24). The drop shadow keeps vanilla's 1-pixel offset. |
| **In-world text** — z-fighting and shader-pack issues on signs, glow ink, text displays and name tags, especially under Iris | Visible artifacts in the world | Dedicated milestone (M6); study CustomFontFix and SAH TTF Sign Fix; test with and without Iris and Sodium. |
| **`.ttc` / `.otf` / variable-font loading** — vanilla's `ttf` provider has no face-index field and may reject non-TrueType files | Most macOS system fonts, and the platform UI fonts, unusable | One mixin (D1) covering collection faces and named instances; confirm the target method against decompiled 26.3 source in M1. |
| **Server resource packs** — servers draw HUDs and menus with custom glyphs in the default font | Broken server UIs; players blame the mod | Skip private-use ranges (AP-11); rank server packs above ours (D11); test against a custom-glyph server pack. |
| **Unreadable font after Apply** | Player can't navigate back to settings | Keep-or-revert dialog with 30 s auto-revert, rendered in the vanilla font, surviving crashes (section 5.7). |
| **Copied configs** (modpacks, synced instances) | Broken or missing font on another machine | New-system detection, silent reset and one log line (section 5.6). |
| **Other font mods** loaded at the same time | Conflicting overrides, confusing results | Detect and step aside with a notice (AP-12). |
| **Release-to-release rendering churn** — ShaderC compilation, order-independent transparency, Vulkan path, grayscale glyph handling | Breakage on each release | Thin mixin surface; re-test glyph rendering on every snapshot and pre-release. |
| **OpenGL removal** | Any GL-specific code breaks | No raw GL calls; Blaze3D only. |
| **macOS threading** with AWT | Hangs or crashes on launch | No AWT; LWJGL FreeType only. |

## 8. Milestones

Each milestone is delivered as a pull request, runnable with `./gradlew runClient`, with complete files (including `build.gradle`, `gradle.properties`, `fabric.mod.json` and mixin JSON when they change) and a manual test checklist for Windows and macOS (and Linux once M5L is in scope) in the PR description. Any milestone that settles or changes something here updates this PRD in the same PR.

| # | Milestone | Exit criteria |
|---|---|---|
| M1 | Fabric skeleton on 26.3 | Builds and launches to the title screen; client-only metadata in place; exact toolchain versions recorded; D1 mixin target confirmed; GitHub Actions build on every PR; client game test viability checked. |
| M2 | Font discovery | Background scan, cache file, debug command lists families on Windows and macOS; variable-font named instances listed (FD-12); symbol and colour fonts classified and hidden (FD-13); unit tests for discovery and classification; scan meets the D14 targets. |
| M3 | Hard-coded font via runtime pack | A fixed `.ttf` renders across menus and HUD; vanilla fallback for missing glyphs and missing file; private-use ranges skipped and pack ordered below server packs (AP-11, D11), checked with a custom-glyph server pack. |
| M4 | `.ttc` face-index, variable-font and `.otf` support | Helvetica and Avenir faces, SF Pro named instances on macOS, Segoe UI Variable on Windows, and an `.otf` font all select and render correctly. |
| M5 | Config file and config screen | Search, style picker, sliders, show-hidden toggle, live preview via temporary `FontSet`, Apply / Reset / Rescan all working; keep-or-revert dialog (section 5.7); new-system detection (section 5.6); config versioning and backup (D16); Apply meets the D14 target. |
| M5L | Linux best effort | FD-9 and FD-10 done; a font applies end to end on one Ubuntu/Debian-family distro with both a native and a Flatpak launcher. About 1–2 days. Not a v1.0 blocker. |
| M6 | In-world text and Iris/Sodium | Signs, glow ink, text displays and name tags render without z-fighting, with and without Iris and Sodium. |
| M7 | Polish and release | Other-font-mod detection (AP-12); README with known limitations and Modrinth link; Modrinth (primary) and CurseForge listings; memory checked against D14; then the NeoForge go / no-go decision (D8). |
| M8 | Crisp mode for pixel fonts (P2) | AP-10 and section 5.5 acceptance criteria met. Starts after M6, since it reuses the in-world rendering work. Not a v1.0 blocker. |

**Critical path:** `.ttc` / `.otf` / variable-font loading (D1) is the biggest technical unknown and gates macOS usefulness and the platform UI fonts. Confirm the mixin target in M1 so the M4 approach is known before UI work starts.

## 9. Success criteria

### Definition of done (v1.0)

On a clean 26.3 Fabric install on **both Windows 11 and macOS**, a player can:

1. Open the config screen.
2. Search for and pick any installed font, including `.ttc` collection faces and variable-font weights.
3. Preview it, apply it, confirm it (or let it auto-revert), and see it immediately on the title screen, every menu, the HUD, chat, tooltips, books and signs.
4. Reset to vanilla.

The mod also:

- survives a resource-pack reload, a GUI-scale change and a fullscreen toggle;
- keeps server resource packs' custom glyphs intact;
- resets cleanly (and logs why) when its config is copied to another OS or user account;
- uninstalls cleanly;
- logs no errors when the chosen font file later disappears (one warning only).

Linux and crisp mode are not part of the v1.0 bar.

### Test matrix

| Dimension | Values |
|---|---|
| OS | Windows 11, macOS (Apple Silicon); Linux best effort (one Ubuntu/Debian-family distro, native + Flatpak launcher) |
| Renderer | OpenGL, Vulkan (experimental) |
| Mods | Vanilla Fabric; + Sodium; + Sodium + Iris with a shader pack; + Caxton or Font (conflict notice) |
| Fonts | Single `.ttf`, `.otf`, `.ttc` face, variable font (SF Pro / Segoe UI Variable), large CJK font, font with no Bold face, font with no Italic face, bold / italic / bold-italic text (chat formatting, server rank tags), symbol font (hidden, then shown), pixel-style font (M8) |
| GUI scale | 1, 2, 3, Auto |
| Events | Resource reload (F3+T), GUI-scale change, fullscreen toggle, font file deleted between launches, vanilla server join, server with a custom-glyph resource pack, keep-or-revert (Enter, Keep, Esc, Revert, 30 s timeout, game killed during countdown), config copied from another OS / another user account (WARN line logged, no dialog), computer renamed (no reset), corrupt config file |

### Post-launch signals (Modrinth)

No telemetry, so success is read from public signals: download and follower growth on Modrinth and CurseForge, crash reports filed per release, and the time to ship a compatible build after each Minecraft release.

## 10. Decisions and open questions

### Decisions

| # | Topic | Decision | Verify / when |
|---|---|---|---|
| D1 | `.ttc` face index, variable fonts and `.otf` | One mixin where vanilla opens the font with FreeType: pass `faceIndex` (including named-instance bits) to `FT_New_Memory_Face`, and lift any TrueType-only check so `.otf` loads. | **Target confirmed in M1 (26.3 source):** `TrueTypeGlyphProviderDefinition.load(ResourceManager)`. It calls `FT_New_Memory_Face(library, fontData, 0L, face)` with face index hard-coded to `0`, then throws unless `FT_Get_Font_Format(face)` is `"TrueType"`, so CFF `.otf` files are rejected. The record has no spare field, so the mixin looks the face index up by the provider's `file` identifier (which our runtime pack controls), changes the `0L` argument, and accepts `"CFF"` in the format check. **Built in M4:** `TrueTypeGlyphProviderDefinitionMixin` changes the face-index argument and accepts `"CFF"`, only for files served by our pack. Verified on Windows with a `.ttc` face (MS PGothic, face 2), Inter variable instances (Thin `0x10000`, Bold `0x70000`) and a CFF `.otf` (Source Han Sans JP). |
| D2 | Applying changes | Full resource reload on Apply. Add a font-only reload mixin only if Apply misses the D14 target. | **M5, Windows 11:** about 1.04 s per Apply with vanilla packs, under the 3 s target, so no font-only reload. A GUI-scale change with automatic oversample triggers the same reload once the scale has been stable for one second. |
| D3 | Runtime pack | Mixin that adds our own pack source to the game's pack list; pack is required and ordered per D11. | **Settled in M3:** Fabric API 0.166 has no hook for dynamic pack sources (its resource loader only registers packs stored in a mod jar). `MinecraftMixin` wraps the `new PackRepository(...)` call in `Minecraft.<init>` and appends our source. The pack is built fresh on every reload from the current config, so Apply needs no other hook. |
| D4 | Live preview | Private `Font` backed by its own `FontSet`, drawn through the 26.x prepared-text path and closed with the screen. Fallback: apply temporarily and roll back on Cancel. | **Built in M5:** `PreviewFont` opens the face itself and builds a `FontSet` of [selected face, then vanilla's providers from `mcfontcontrol:vanilla`], read through three accessor mixins (`Minecraft.fontManager`, `FontManager.fontSets`, `FontSet.allProviders`). The shared vanilla providers are closed by every resource reload, so the screen drops the preview while the loading overlay is up and rebuilds it afterwards. |
| D5 | Antialias | No antialias toggle: vanilla always antialiases, and its font definition has no such option; `oversample` controls smoothness. The opposite case, hard edges for pixel-style fonts, is planned as crisp mode (AP-10, section 5.5, M8). | — |
| D6 | Vulkan on macOS | No special code expected. OS × renderer row in the test matrix; re-test glyph rendering on every snapshot. | Ongoing. |
| D7 | Baseline | Minecraft Java 26.3.x (released 15 Sep 2026), Java 25. Toolchain at M1: Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.166.0+26.3`, Loom `1.19.4` (`net.fabricmc.fabric-loom`), Gradle `9.8.2`, JUnit `5.13.4`. Versions live in `gradle.properties`. | Recorded 2026-11-11 from the Fabric develop site and Fabric Maven; recheck each Minecraft release. |
| D8 | NeoForge | Loader-specific code isolated in its own package from M1. Port go / no-go after M7, based on Modrinth demand. | Fabric-only code is confined to `io.github.jekitchen.mcfontcontrol.fabric` (entry point, platform adapter, commands, keybind, Mod Menu); everything else is loader-independent. Decision waits for post-release Modrinth demand. |
| D9 | Licensing | MC Font Control is **MIT**. Caxton and SAH TTF Sign Fix are MIT (reuse with attribution). CustomFontFix is GPL-3.0-only (study only, never copy). Glyph's licence is unknown (treat as all rights reserved). | Confirm Glyph's licence before reading its code. |
| D10 | PRD home | `docs/PRD.md` in this repository is the single source of truth. Changes land through pull requests. | — |
| D11 | Server resource packs | Our pack ranks above the player's own packs but below server-provided packs, and our font provider skips the private-use ranges (AP-11). Server UI glyphs always win. | **Settled in M3:** the pack is `required`, default position TOP, not fixed. Vanilla's `Pack.Position.insert` puts it above the player's packs and below fixed-TOP packs, which is how server packs are configured (unit-tested against vanilla's own code). Vanilla merges `font/default.json` across packs with higher packs first, so our file lists only the system font and vanilla's providers stay underneath as the fallback (AP-2 needs no copy of vanilla's providers). Private-use characters are filtered by a wrapper provider instead of `skip` (open question 3). |
| D12 | Copied configs | Fingerprint = hash of OS family + user home path, stored in the config (no host name, nothing written outside the game folder). On mismatch: back up the config, reset to defaults, rescan, and log one WARN line; no dialog (section 5.6). | **Built in M5.** Stored as `<OS family>:<SHA-256 of OS family + home path>`. The OS family is kept in clear so the log line can name the change (e.g. `macOS -> Windows`); the home path is only hashed and never logged. Unit-tested. |
| D13 | Safety valve | Every change to the applied font needs confirmation within 30 s (Enter / Keep changes), otherwise it reverts; survives crashes via `pending` (section 5.7). Symbol and colour fonts are hidden unless the player turns on Show hidden fonts (FD-13, UI-10). | **Built in M5.** The dialog renders in the vanilla font: its text is styled with `mcfontcontrol:vanilla`, a font our pack defines as references to vanilla's glyph sets. The countdown runs on the wall clock in a client-tick handler, so it continues if the dialog is replaced or the window loses focus. Enter, Esc and the 30 s timeout are covered by a client game test. |
| D14 | Performance and memory | Targets in section 6: scan < 5 s, cached startup < 0.5 s, Apply < 3 s, steady-state heap ≤ font size + 10 MB. | **M7, Windows 11:** with the largest installed font applied (MingLiU-ExtB, 35.4 MB) the Java heap grew 5.6 MB against a 45.4 MB limit; the font file itself sits once in native memory, held by vanilla's provider (`MemoryGameTest`). **M5, Windows 11:** Apply about 1.04 s. **M3, Windows 11:** Apply (full reload, vanilla packs) 1.06 s in the client game test. **M2, Windows 11:** 359 font files, 467 faces, 189 families; cold scan 101 ms, cached rescan 33 ms (unit test `SystemFontsTest.fullScanMeetsTarget`, report in `build/reports/measurements/`). Apply and memory in M5. **macOS 26.6, Apple M4:** cold scan 74 ms (370 files, 2,611 faces, 140 listed families), cached rescan 7 ms; Apply 1.0–1.1 s on OpenGL and Vulkan; heap +6.3 to +7.2 MB with Songti SC (63.8 MB file). |
| D15 | Support and distribution | Best-effort support for the latest Minecraft release only. Semantic versioning with the Minecraft version as build metadata. Publish to Modrinth (primary home) and CurseForge. | Listings in M7. |
| D16 | Config file | `configVersion` field with migration on load; corrupt or too-new files are backed up to `.bak` and replaced with defaults; default is disabled. | **Built** (M3 store, M5 recovery); unit-tested. |
| D17 | Testing and CI | GitHub Actions build on every PR; unit tests for discovery, classification, config and system fingerprint; Fabric client game tests if viable on 26.3. | Set up in M1. |
| D18 | Identity | Mod ID `mcfontcontrol`; display name MC Font Control; Java package and Maven group `io.github.jekitchen.mcfontcontrol`; Modrinth and CurseForge slug `mc-font-control`. | Claim both slugs before M1 if possible; the search found no existing project with this name. |
| D19 | Symbol-map fonts | Fonts with no Unicode character map (classic Symbol fonts such as Wingdings) are never listed, even with Show hidden fonts on. Vanilla's provider requires a Unicode map, and these fonts place their glyphs in the private-use range that AP-11 skips, so they could never render. Fonts without Latin coverage and colour fonts with outlines stay revealable as FD-13 says. | Decided in M2. |
| D20 | Fonts with embedded bitmaps | `NativeImageMixin` adds `FT_LOAD_NO_BITMAP` when vanilla renders a glyph from one of our faces. Vanilla measures glyphs with that flag but renders without it, so fonts with embedded bitmap strikes (MS Gothic, SimSun, many CJK fonts) return a 1-bit bitmap and the game crashes with "Rendered glyph was not 8-bit grayscale". Found by the M4 game test; scoped to faces MC Font Control opened. | Decided in M4. |
| D21 | Runtime pack ID | The runtime pack's ID is `mcfontcontrol_runtime`, not the mod ID. Fabric already registers each mod's built-in resources as a pack named after the mod ID, and a second pack with that ID replaced it, so the mod's own lang file stopped loading. Found by the M5 game test. The oversample slider is labelled "Sharpness" in the UI; the config field stays `oversample`. | Decided in M5. |
| D22 | In-world text | No in-world mixins. The M6 game test shows signs, glowing signs, text displays and name tags in the system font with no z-fighting, on vanilla and with Sodium 0.9.2 + Iris 1.11.7 (shaders off). Thin strokes alias at a distance because the glyph atlas has no mipmaps; vanilla TTF fonts behave the same, so this is documented as a known limitation rather than fixed. Iris with a shader pack is a manual check. Sodium 0.9.3 betas are incompatible with Iris 1.11.7, so compatibility runs pin Sodium 0.9.2. | M6, Windows 11, OpenGL. Repeat on macOS and with a shader pack. |
| D23 | v1.0 scope status | Built: every P0 requirement, plus UI-2 (keybind), AP-3 (Unicode font toggle), AP-8 (real bold and italic faces, D24), AP-12 (other-font-mod detection) and FD-8 (debug command). FD-11 and crisp mode (M8) remain P2. Store listings (M7) need the owner: see `docs/RELEASING.md`. | 2026-11-11. AP-8 was first deferred, then built after the owner reviewed bold text on a real server (D24). |
| D24 | Bold and italic text | Bold and italic text in the system font is drawn with the family's own faces, as word processors do, instead of vanilla's simulation (bold = the same glyph drawn twice one GUI pixel apart; italic = a shear). On a smooth font the 1-pixel double draw leaves a gap between the copies, so bold looked like doubled outlines. **Faces:** `StyledFaces` picks, within the selected face's family and width (style name minus weight and slope words, so Condensed stays Condensed and a variable font's Display stays Display): bold = the next heavier face, CSS "bolder" (Regular→Bold, Light→Regular, Semibold→Black); italic = the italic nearest in weight; bold italic = the next heavier italic. **No heavier face:** FreeType's own synthetic bold (`FT_GlyphSlot_Embolden`, about 1/24 em) thickens the outline before rasterizing, with the advance widened to match (`TrueTypeGlyphProviderMixin` when measuring, `NativeImageMixin` when rendering). **No italic face:** vanilla's slant is kept, like a word processor's synthetic oblique. **Plumbing:** the runtime pack serves up to three extra fonts, `mcfontcontrol:bold`, `:italic` and `:bold_italic` (the styled face, then vanilla's providers); `FontMixin` wraps `Style.getFont()` in `Font.getGlyph` and the `StringSplitter` width lambda so bold or italic text in the default (or, with AP-3, uniform) font uses them, once loaded; their glyphs report a bold offset of 0 and clear the bold/italic flags when drawn, so vanilla adds neither the second copy nor the shear. Vanilla bitmap glyphs used as fallback keep vanilla's styling. Server fonts and every other font are untouched. The preview builds the same styled fonts and labels which face each style uses. **Memory:** each styled face loads its own file into native memory, as vanilla does for any `ttf` provider; bold italic shares the bold font when it would load the same face. | **Built after M7, Windows 11:** Arial and Segoe UI use their Bold, Italic and Bold Italic faces; single-face Algerian gets FreeType bold. `StyledTextGameTest` checks routing, single drawing and the emboldened advance; the D14 heap check with the 35 MB MingLiU-ExtB (no bold face) is 9.0 MB against a 45.4 MB limit. 2026-11-11. |
| D25 | macOS fonts | Verified on macOS 26.6 (Apple M4), OpenGL and Vulkan (MoltenVK). Three macOS-only fixes in discovery: (1) families whose name starts with a dot are Apple system-private fonts (`.SF Arabic`, `.LastResort`, `.CJK Symbols Fallback`, about 75 families) and are never listed, as Font Book does; (2) bitmap colour fonts (`sbix` / `CBDT`) count as having outlines only if most sampled glyphs have real outlines, because Apple Color Emoji carries a `glyf` table of empty placeholders (it was listed as revealable, against FD-13); (3) the named-instance cap rises from 256 to 1024, because the built-in SF Pro (`SFNS.ttf`) has 369 instances. The font-index cache version is bumped so old classifications are rescanned. The built-in SF Pro is listed under its file's family name, **System Font**; no alias is added. | — |

### Still open

1. **Linux font sources:** are standard folders plus the Flatpak path enough, or is FD-11 (fontconfig folders) needed? Decide from early Linux feedback.
2. **Glyph licence:** confirm before using it as a reference.
3. **Supplementary private-use planes (resolved in M3):** handled by `PrivateUseFilteringProvider`, a wrapper around our provider, which covers the BMP and both supplementary planes at no load cost.
   Original question: can the `ttf` provider's `skip` field cover U+F0000–U+10FFFD efficiently, or does AP-11 need a small mixin for those planes? (M3) *M1 finding:* `skip` accepts supplementary code points, but `TrueTypeGlyphProvider` stores it in an `IntArraySet` (linear lookup) and checks every character the font maps, so load cost grows with font size × skip size. The supplementary planes (~131,000 code points) are impractical through `skip`; even the BMP range (6,400) is slow for large CJK fonts. Expect a small filter mixin in the provider's constructor instead; decide in M3.
4. **Client game tests on 26.3:** do Fabric's client game tests and screenshot comparison work reliably enough to run in CI? (M1) *M1 finding:* a client game test (`src/gametest`) boots 26.3 to the title screen and takes a screenshot locally on Windows; CI runs it under Xvfb in a separate job. Close this once the CI job has proven stable over a few PRs; screenshot comparison is untested.

## Maintaining this document

- `docs/PRD.md` is the source of truth. Copies elsewhere (Claude Docs, project notes) are snapshots and may be out of date.
- Change it through a pull request. When a change settles a question, add or update a row in the decision log (section 10) in the same PR.
- Bump the version in the header and add a line to the change log below.
- Code that changes behaviour described here should update this file in the same PR.

## Change log

| Version | Date | Change |
|---|---|---|
| v0.1 | 2026-11-11 | Initial draft from the project brief. |
| v0.2 | 2026-11-11 | Linux best effort (P2); baseline moved to 26.3; MIT licence; open questions resolved into decisions D1–D9; competitive landscape; `antialias` field dropped; `.otf` requirement (AP-9). |
| v0.3 | 2026-11-11 | Crisp mode for pixel-style fonts (AP-10, section 5.5, M8). |
| v0.4 | 2026-11-11 | Moved to `docs/PRD.md` as source of truth (D10). Aligned AP-1/5/6, risks, performance, milestones and critical path with decisions D1–D4; SDL3 input note; UI-9; pixel font in test matrix. |
| v0.5 | 2026-11-11 | Gap review: server-pack glyph protection (AP-11, D11); variable-font named instances (FD-12); hidden symbol/colour fonts with Show hidden fonts toggle (FD-13, UI-10); keep-or-revert safety valve with 30 s auto-revert (section 5.7, D13); new-computer detection and config reset (section 5.6, D12); other-font-mod detection (AP-12); performance and memory targets (D14); latest-version-only support, semantic versioning, Modrinth primary + CurseForge (D15); config versioning and corrupt-file handling (D16); testing and CI (D17); complex shaping and colour emoji as non-goals; new user stories, risks, milestone exit criteria and test-matrix rows. |
| v0.6 | 2026-11-11 | New-system detection simplified: fingerprint is a hash of OS family + user home path stored in the config (no machine-ID file, no host name); silent reset with one WARN log line instead of a notice (UI-12 removed); section 5.6 adds a catches / doesn't-catch table. |
| v0.7 | 2026-11-11 | Identity decided (D18: mod ID, package, slug). Competitive landscape corrected and expanded: Font does have a preview; added FontSwap, Smooth Font and Modern UI. |
| v0.8 | 2026-11-11 | M1: exact toolchain versions recorded (D7, open question 1 closed); D1 mixin target confirmed against 26.3 source; `skip` performance finding for the private-use planes; client game test working locally. |
| v0.9 | 2026-11-11 | M2: D19 (symbol-map fonts never listed); D14 scan measured on Windows. |
| v0.10 | 2026-11-11 | M3: runtime pack settled (D3, D11); AP-2 met by vanilla's pack merge; private-use filtering by wrapper provider (open question 3 resolved); Apply measured. |
| v0.11 | 2026-11-11 | M4: D1 built and verified (collection face, variable instances, CFF `.otf`); D20 embedded-bitmap crash fix; mixin list updated. |
| v0.12 | 2026-11-11 | M5: config screen, live preview (D4), keep-or-revert (D13), new-system detection (D12), Apply timing (D2, D14); D21 pack ID and UI label. |
| v0.13 | 2026-11-11 | M5L: Linux folders (FD-9 incl. `XDG_DATA_HOME`), Flatpak host fonts and message (FD-10) built; FD-11 (fontconfig) not built, per open question 1. Awaits testing on a Linux VM. |
| v0.14 | 2026-11-11 | M6: in-world text verified without mixins, including with Sodium and Iris (D22); distance aliasing documented. |
| v0.15 | 2026-11-11 | M7: memory measured (D14); README with known limitations; `docs/RELEASING.md`; D8 status; D23 scope status (AP-8 deferred). |
| v0.16 | 2026-11-11 | AP-8 built and widened to italic, now P0 (D24): real bold, italic and bold-italic faces, FreeType bold when a family has none; D23 updated; risk, test matrix and mixin rule updated. |
| v0.17 | 2026-11-11 | macOS verification (D25): system-private and bitmap-emoji fonts never listed, SF Pro's 369 instances all listed; full game-test suite passes on macOS with OpenGL and Vulkan; macOS measurements recorded. |
