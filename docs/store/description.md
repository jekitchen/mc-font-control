Pick any font installed on your computer and Minecraft uses it for all of its text: menus, HUD, chat, tooltips, signs, books, name tags and text displays.

## Features

- **Every installed font.** Fontsys lists every font on your computer, including the faces inside `.ttc` collections (most macOS system fonts) and the named weights of variable fonts such as Segoe UI Variable, SF Pro and Inter.
- **Real bold and italic.** Bold and italic text uses the font's own Bold, Italic and Bold Italic faces, the way a word processor does, instead of Minecraft's doubled-up bold and slant. Server rank tags and chat formatting look clean and solid.
- **Fonts just for this instance.** Drop a font into the instance's `fonts` folder (the **Fonts folder** button opens it) and it's listed alongside your system fonts, without installing it on your computer.
- **Live preview.** See the font with chat, tooltip, sign, bold and italic samples before anything changes.
- **One click, no restart.** Apply the font and it's used everywhere straight away.
- **Can't get stuck.** You have 30 seconds to confirm a new font, or it switches back on its own. The confirmation dialog always uses the normal Minecraft font, so you can read it whatever you picked.
- **Fine-tuning.** Adjust size, sharpness and baseline offset.
- **Crisp pixel fonts.** Turn on **Crisp edges** and pixel-style fonts get hard, square pixels with no smoothing, like Minecraft's own font. The preview spots pixel fonts and suggests it.
- **Works with Sodium and Iris.** Fontsys uses Minecraft's own font renderer, so it works alongside them and on both OpenGL and Vulkan.
- **Client-only.** Servers don't need it. Server resource packs keep their custom HUD icons.
- **Your fonts stay where they are.** Fontsys reads fonts from where your system keeps them. It never bundles font files and makes no network connections. The only copy it makes is when you drop a font onto the settings screen, into the instance's fonts folder.

## How to use

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api), then drop the Fontsys jar into your `mods` folder.
2. Open the settings with **F7**, from **Fontsys Settings...** in Options → Language → Font Settings, or from [Mod Menu](https://modrinth.com/mod/modmenu).
3. Search for a font, pick a style, check the preview and press **Apply**. Press **Enter** or **Keep changes** to keep it.
4. **Reset to vanilla** switches back to the normal font at any time.
5. **Fonts folder** opens this instance's `fonts` folder. Fonts you put there are listed with an **Instance** tag.

## Supported systems

- **Windows 11** and **macOS**: supported.
- **Linux**: best effort. Standard font folders and Flatpak launchers; tested on Ubuntu 26.04.

## Known limitations

- **Complex scripts** such as Arabic and Devanagari don't render correctly. Minecraft doesn't join or reorder glyphs, whatever font is used.
- **Color emoji fonts** can't be used, because Minecraft's text renderer is grayscale. They're hidden unless you turn on **Show hidden fonts**.
- **Symbol fonts** such as Wingdings are never listed, because Minecraft can't draw them.
- **Fonts without a Bold or Italic face:** bold text is thickened by FreeType, as word processors do; italic text uses Minecraft's slant. The preview shows which applies.
- **Text far away in the world** (signs, name tags) can look thin at a distance. Minecraft's glyph atlas has no mipmaps; built-in fonts behave the same.
- **Large text sizes** can be clipped in tight spots such as buttons and the scoreboard. Lower the size if that happens.
- **Other font mods:** if Caxton, Font, FontSwap or Modern UI is installed, Fontsys turns itself off and tells you, rather than fighting over the font.

## Source and issues

Fontsys is open source under the MIT license: [github.com/jekitchen/mc-fontsys](https://github.com/jekitchen/mc-fontsys). Please report problems in the [issue tracker](https://github.com/jekitchen/mc-fontsys/issues).
