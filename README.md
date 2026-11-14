# Fontsys

<p align="center"><img src="docs/store/logo/fontsys-logo.png" alt="Fontsys" width="640"></p>

A client-side Minecraft: Java Edition mod (26.3+, Fabric) that lets you pick any font installed on your computer and uses it everywhere the game draws text: menus, HUD, chat, tooltips, signs, books, name tags and text displays.

- **Status:** 1.2.0 for Minecraft 26.3, on Fabric and NeoForge. See the [changelog](CHANGELOG.md); requirements and decisions live in the [PRD](docs/PRD.md).
- **Downloads:** [Modrinth](https://modrinth.com/mod/fontsys) (primary) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fontsys).
- **License:** [MIT](LICENSE).
- **Support:** if Fontsys is useful to you, you can [buy me a coffee](https://buymeacoffee.com/jekitchen).

## Features

- Lists every font installed on your computer, including the faces inside `.ttc` collections (most macOS system fonts) and the named weights of variable fonts such as SF Pro, Segoe UI Variable and Inter.
- Also lists fonts you put in the instance's own `fonts/` folder, which the mod creates on first start, for one-off fonts you don't want to install on your computer.
- Uses the font's own Bold, Italic and Bold Italic faces for styled text, the way a word processor does, instead of Minecraft's simulated bold and slant.
- Shows a live preview before anything changes, with bold and italic, chat, tooltip and sign samples.
- Applies the font everywhere with one click, no restart.
- Gives you 30 seconds to confirm a new font, or it switches back on its own. The confirmation dialog always uses the normal Minecraft font, so you can read it whatever you picked.
- Adjusts size and sharpness, with fine-tuning (baseline offset, the Unicode font) on an **Advanced** screen.
- Blends the smooth edges of letters by true coverage and uses a lighter drop shadow, so light text on dark menus keeps its full weight and doesn't look doubled.
- **Crisp edges** for pixel-style fonts: hard, square pixels with no smoothing, like Minecraft's own font. The preview spots pixel fonts and suggests it, with a size that keeps every pixel square.
- Uses the game's own font renderer, so it works alongside Sodium and Iris.
- Never bundles font files: fonts are read from where your system keeps them, or from the instance's `fonts/` folder. The only copy it makes is when you drop a font file onto the settings screen, into that folder.
- Is client-only. Servers don't need it, and server resource packs keep their custom HUD glyphs.

## Using it

1. Drop the Fontsys jar into the `mods/` folder of a Minecraft 26.3 instance. From 1.2.0 the same jar works on both loaders:
   - **Fabric:** with [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
   - **NeoForge:** with [NeoForge](https://neoforged.net/) 26.3.0.103 or later. Fabric API isn't needed.
2. Open the settings with **F7**, from **Fontsys Settings...** in Options → Language → Font Settings, or from [Mod Menu](https://modrinth.com/mod/modmenu) (on NeoForge, from Mods → Fontsys → Config). You can change the key under Options → Controls → Key Binds → Fontsys. On a Mac laptop, press **Fn + F7**.
3. Search for a font, pick a style, check the preview and press **Apply**. The preview also shows the next size down and up; click one to use it. The fonts you used last are listed first, marked **Recent**, and each one remembers the settings you kept it with.
4. The **Keep this font?** screen shows samples in your new font. Press **Enter** or **Keep changes** to keep it; otherwise it switches back after 30 seconds. If you press **Done** without applying, you're asked whether to apply or discard your changes.
5. **Reset to vanilla** switches back to the normal font. **Rescan fonts** picks up fonts you installed while the game was running. Hover over a setting to see what it does.
6. **Crisp edges** turns off smoothing for pixel-style fonts. While it's on, sharpness follows your GUI scale.
7. **Advanced...** opens settings most fonts don't need: **Shift X** and **Shift Y** move the letters if they sit off-center on buttons, **Replace Unicode font** uses your font when Force Unicode Font is on, and **Hinting** (Full, Light or None) chooses how tightly letters snap to the screen's pixels: Full is the crispest, Light keeps the font's own shapes, None is the softest. Its preview shows a button in your font next to one in Minecraft's font. Changes there take effect when you press **Apply** back on the settings screen, and the button shows how many settings you changed, such as **Advanced (1)...**.
8. **Fonts folder** opens this instance's `fonts/` folder (next to `mods/`), or drag font files onto the settings screen to copy them there; the first one you drop is selected. `.ttf`, `.otf`, `.ttc` and `.otc` files there are listed only in this instance and marked **Instance**. The setting is saved relative to the instance, so it keeps working if you copy or move the instance.

Debug commands: `/fontsys fonts [filter]` lists discovered families; `/fontsys rescan` rescans.

Settings are stored in `config/fontsys.json`. The font list cache is `config/fontsys/font-index.json`.

## Supported systems

| System | Status |
|---|---|
| Windows 11 | Supported |
| macOS | Supported |
| Linux | Best effort: standard font folders and Flatpak launchers; tested on Ubuntu 26.04 |

## Known limitations

- **Complex scripts** such as Arabic and Devanagari don't render correctly. Minecraft doesn't join or reorder glyphs, whatever font is used.
- **Color emoji** fonts can't be used. Minecraft's text renderer is grayscale. Color fonts are hidden unless you turn on **Show hidden fonts**, and then only their outline glyphs are drawn.
- **macOS system font names:** the copy of SF Pro built into macOS calls itself **System Font**. It is listed under that name and also as **SF Pro**; both are the same font. If you have installed SF Pro from Apple, that one is listed as SF Pro instead. Apple's private system fonts (names starting with a dot, such as `.SF Arabic` or `.LastResort`) and Apple Color Emoji are never listed.
- **Classic Symbol fonts** (Wingdings, Webdings) are never listed. They have no Unicode character map, so Minecraft can't draw them.
- **Fonts without a Bold or Italic face:** bold text is thickened by FreeType, as word processors do; italic text uses Minecraft's slant. The preview shows which applies.
- **Text far away in the world** (signs, name tags) fades as it gets smaller, as any distant texture does, and becomes hard to read beyond about 20 blocks. Fontsys gives its glyphs mipmaps so it fades evenly rather than breaking up; Minecraft's own fonts, and fonts in Crisp edges mode, still break up at a distance.
- **Large text sizes** can be clipped in tight UI spots such as buttons and the scoreboard. Lower the size if that happens.
- **Other font mods:** if Caxton, Font, FontSwap or Modern UI is installed, Fontsys turns itself off and tells you, rather than fighting over the font.
- **Copied configs:** a config written on another computer, OS or user account is set aside as `fontsys.json.bak`, and the mod starts from the vanilla font. The exception is a font from the instance's `fonts/` folder: it travels with the instance, so the config is kept.

## Building

Requires Java 25. The Gradle wrapper downloads everything else.

```sh
./gradlew build                               # compile and run unit tests; the one jar, for Fabric and NeoForge, in build/libs/
./gradlew runClient                           # launch Minecraft with the mod
./gradlew runClientGameTest                   # boot the client and run the automated game tests
./gradlew runClientGameTest -PwithSodiumIris  # the same, with Sodium and Iris installed
./gradlew runClientGameTest --args="--graphicsBackend vulkan"  # the same, on the Vulkan renderer
./gradlew runProductionClientGameTest         # the game tests against the built jar on a production-style Fabric client
./gradlew :neoforge:runNeoforgeClient         # launch Minecraft on NeoForge
./gradlew :neoforge:neoforgeSmokeTest         # boot the NeoForge client with the built jar, apply a font, check it and quit
```

The jar carries both `fabric.mod.json` and `META-INF/neoforge.mods.toml`; each loader reads its own file. The `neoforge/` subproject supplies the NeoForge entry point and metadata, and compiles the shared code against NeoForge's Minecraft to check it is identical to the Fabric compile.

Game test screenshots land in `build/run/clientGameTest/screenshots/`. Performance and memory measurements land in `build/reports/measurements/`.

## Credits

Fontsys uses Minecraft's own FreeType-based font provider. Prior art studied for this project: Caxton and SAH TTF Sign Fix (MIT). No code from GPL projects is included.
