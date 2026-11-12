# Changelog

All notable changes to Fontsys. Versions follow [semantic versioning](https://semver.org/); the jar adds the Minecraft version as build metadata (for example `1.0.0+26.3`).

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

See the [README](README.md#known-limitations) for known limitations.
