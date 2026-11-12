# Changelog

All notable changes to Fontsys. Versions follow [semantic versioning](https://semver.org/); the jar adds the Minecraft version as build metadata (for example `1.0.0+26.3`).

## [1.1.0] - unreleased

### Added

- **Instance fonts folder.** Fonts in the instance's own `fonts/` folder (next to `mods/`) are listed alongside system fonts, marked **Instance**, so you can use a font in one instance without installing it on your computer. The new **Fonts folder** button opens it.
- The font list rescans each time the settings screen opens.
- **Crisp edges** for pixel-style fonts: glyphs are drawn with hard, square pixels and no smoothing, in menus and in the world. While it's on, sharpness follows the GUI scale so every font pixel lands on whole screen pixels.
- The preview recognises pixel-style fonts and suggests Crisp edges, and a font size that keeps every pixel square. It never changes a setting by itself.
- **Done asks before throwing away changes.** If you changed the settings but didn't press Apply, Done (or Esc) asks whether to apply them, discard them, or go back.

### Changed

- **Fuller, cleaner text.** The smooth edges of letters are now blended by how much of each pixel the letter covers. Before, edges were dimmed twice, so light text on dark backgrounds (most menus, chat and the HUD) looked thin and grey around the edges.
- **Thinner text shadow.** With a smooth font, the drop shadow now sits half as far from the text, in whole screen pixels, so letters no longer look doubled at high GUI scales. Crisp edges keeps Minecraft's classic shadow. Glowing sign outlines get thinner in the same way.
- A config copied from another computer is kept if its font is in the instance's `fonts/` folder, since that font travels with the instance. Other copied configs still reset to the vanilla font.
- The **Also replace the Unicode font** checkbox is now **Replace Unicode font**, with the full explanation in its tooltip, to make room for Crisp edges.
- Long font names in the list are shortened with "…" instead of running under the **Instance** and hidden-font tags.

### Fixed

- **Apply** now lights up as soon as you tick or untick **Replace Unicode font**; before, it waited for another change.

## [1.0.1] - 2026-11-12

### Fixed

- **Signs with shader packs.** With an Iris shader pack on, sign text in a system font drew as long streaks across the screen. Fontsys glyphs now draw through the same render pipelines as Minecraft's own font, which shader packs support.
- **Name tags** in a system font were slightly brighter than with the normal font when seen through walls. They now match.

## [1.0.0] - 2026-11-11

First release, for Minecraft 26.3 on Fabric.

### Features

- **Any installed font, everywhere.** Pick a font from your computer and Minecraft uses it for menus, the HUD, chat, tooltips, signs, books, name tags and text displays. No restart.
- **Every face.** Lists the faces inside `.ttc` collections (most macOS system fonts), the named weights of variable fonts (Segoe UI Variable, SF Pro, Inter) and `.otf` fonts.
- **Real bold and italic.** Bold and italic text uses the font's own Bold, Italic and Bold Italic faces. If a font has no bold face, FreeType thickens it, as word processors do.
- **Live preview** with bold, italic, chat, tooltip and sign samples, before anything changes.
- **Safe to try.** A new font must be confirmed within 30 seconds or it switches back. The confirmation dialog always uses the normal Minecraft font, and the revert also happens if the game is closed mid-countdown.
- **Size, sharpness and baseline offset**, and an option to replace the Unicode font too.
- **Mod Menu** integration and a key binding to open the settings.
- **Works with Sodium and Iris**, on OpenGL and Vulkan.
- **Client-only.** Servers don't need it, and server resource packs keep their custom HUD glyphs.
- **Steps aside** if another font mod (Caxton, Font, FontSwap, Modern UI) is installed.
- **Copied configs reset safely.** A config from another computer or account is set aside and the vanilla font is used.

### Supported systems

- Windows 11 and macOS.
- Linux, best effort: standard font folders and Flatpak launchers; tested on Ubuntu 26.04.

See the [README](https://github.com/jekitchen/mc-fontsys#known-limitations) for known limitations.
