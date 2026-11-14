# Changelog

All notable changes to Fontsys. Versions follow [semantic versioning](https://semver.org/); the jar adds the Minecraft version as build metadata (for example `1.0.0+26.3`).

## [1.2.0] - unreleased

### Added

- **NeoForge support.** Fontsys now also runs on NeoForge for Minecraft 26.3 (NeoForge 26.3.0.103 or later), with the same features. It's the same jar for Fabric and NeoForge: drop it into either. On NeoForge, open the settings with F7, from Font Settings, or from the Config button in the mods list; Fabric API isn't needed there.
- **"Keep this font?" says what's changing.** Above the samples, the dialog lists the font you're switching from and to, and every setting you changed, such as size 10 → 12 or Crisp edges Off → On, so you know exactly what you're keeping.
- **The settings title matches the logo.** "sys" in "Fontsys" at the top of the settings screen is drawn in the logo's green; "Font" stays white.
- **Font names in their own font.** The font list shows each name in its own font, as word processors' font menus do, so you can see what a font looks like before you pick it. Names a font can't draw, such as symbol and emoji fonts, stay in the normal font.
- **Hinting: Full, Light or None.** A new setting on the Advanced screen chooses how letters are fitted to your screen's pixels. **Full**, the default and what you had before, follows the font's own instructions and is the crispest. **Light** only lines letters up vertically and keeps the designer's shapes and spacing, as on most Linux desktops. **None** draws the exact outlines, softest and most faithful, as on a Mac. The preview shows the difference before you apply it. Crisp edges keeps its own hinting, so the setting is grayed out while it's on.
- **Clearer text at a distance.** Signs, name tags and text displays in your font stay dark and whole as you walk away, instead of breaking up into missing pixels and shimmering. Menus, chat, the HUD and close-up text look exactly as before, and Crisp edges stays hard-edged.

### Changed

- **Advanced settings get their own screen.** Shift X, Shift Y and Replace Unicode font, which most fonts don't need, moved to **Advanced...** on the settings screen, leaving more room for the preview. Its preview shows your font on a button next to Minecraft's, so you can line them up, and **Reset to defaults** puts those settings back. Changes there are applied with **Apply** as before, and the button shows how many you changed, such as **Advanced (1)...**.

## [1.1.0] - 2026-11-13

### Added

- **Instance fonts folder.** Fonts in the instance's own `fonts/` folder (next to `mods/`) are listed alongside system fonts, marked **Instance**, so you can use a font in one instance without installing it on your computer. The mod creates the folder on start-up, and the new **Fonts folder** button opens it.
- The font list rescans each time the settings screen opens.
- **Crisp edges** for pixel-style fonts: glyphs are drawn with hard, square pixels and no smoothing, in menus and in the world. While it's on, sharpness follows the GUI scale so every font pixel lands on whole screen pixels.
- The preview recognizes pixel-style fonts and suggests Crisp edges, and a font size that keeps every pixel square. It never changes a setting by itself.
- **Done asks before throwing away changes.** If you changed the settings but didn't press Apply, Done (or Esc) asks whether to apply them, discard them, or go back.
- **F7 opens the settings** by default. You can change the key under Options → Controls → Key Binds → Fontsys.
- **Fontsys Settings...** button in Options → Language → Font Settings, next to Force Unicode Font, so you can find the settings without Mod Menu.
- **Size comparison in the preview:** a sample at the next size down, the current size and the next size up. Click one to use it. With Crisp edges and a pixel font, it steps between sizes that keep pixels square.
- **Samples on the "Keep this font?" screen:** a sentence, look-alike characters (Il1 O0 rn m), digits, bold and italic in your new font, then chat, sign and tooltip samples, so you can judge it before you keep it.
- **Recent fonts:** the last five fonts you applied and kept are listed first, marked **Recent**.
- **Drag and drop:** drop one or more font files onto the settings screen to copy them into the instance's fonts folder. The first one is selected once it's listed.
- A **×** button clears the font search.
- Mod Menu lists links to Fontsys on **Modrinth** and **CurseForge**, and to **Buy Me a Coffee**.
- Tooltips explaining Size, Sharpness, Shift X and Y, Show hidden fonts, Rescan fonts and Reset to vanilla.

### Changed

- **Fuller, cleaner text.** The smooth edges of letters are now blended by how much of each pixel the letter covers. Before, edges were dimmed twice, so light text on dark backgrounds (most menus, chat and the HUD) looked thin and gray around the edges.
- **Thinner text shadow.** With a smooth font, the drop shadow now sits half as far from the text, in whole screen pixels, so letters no longer look doubled at high GUI scales. Crisp edges keeps Minecraft's classic shadow. Glowing sign outlines get thinner in the same way.
- A config copied from another computer is kept if its font is in the instance's `fonts/` folder, since that font travels with the instance. Other copied configs still reset to the vanilla font.
- The **Also replace the Unicode font** checkbox is now **Replace Unicode font**, with the full explanation in its tooltip, to make room for Crisp edges.
- Long font names in the list are shortened with "…" instead of running under the **Instance** and hidden-font tags.
- **Buttons regrouped:** Fonts folder and Rescan fonts on the left, Reset to vanilla on its own in the middle, Apply and Done on the right. Reset to vanilla is no longer cut off.
- When another font mod turns Fontsys off, the settings screen says so in a banner, with what to do.
- US spelling throughout ("color" fonts in the list, not "colour").

### Fixed

- **Apply** now lights up as soon as you tick or untick **Replace Unicode font**; before, it waited for another change.
- When the settings open with **F7**, you can type a font name straight away: the cursor starts in the search box.
- Titles on screens you return to after changing the font (such as Font Settings) are no longer cut short.
- The tooltips on **Replace Unicode font** and **Crisp edges** never appeared. They now show, and the Unicode one says where Force Unicode Font is (Options → Language → Font Settings).

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
