# MC Font Control

A client-side Minecraft: Java Edition mod (26.3+, Fabric) that lets you pick any font installed on your computer and uses it everywhere the game draws text: menus, HUD, chat, tooltips, signs, books, name tags and text displays.

- **Status:** in development, not yet released. Requirements and decisions live in the [PRD](docs/PRD.md).
- **Downloads:** Modrinth (primary) and CurseForge, once released.
- **Licence:** [MIT](LICENSE).

## Features

- Lists every font installed on your computer, including the faces inside `.ttc` collections (most macOS system fonts) and the named weights of variable fonts such as SF Pro, Segoe UI Variable and Inter.
- Shows a live preview before anything changes, with chat, tooltip and sign samples.
- Applies the font everywhere with one click, no restart.
- Gives you 30 seconds to confirm a new font, or it switches back on its own. The confirmation dialog always uses the normal Minecraft font, so you can read it whatever you picked.
- Adjusts size, sharpness and baseline offset.
- Uses the game's own font renderer, so it works alongside Sodium and Iris.
- Never copies or bundles font files: fonts are read from where your system keeps them.
- Is client-only. Servers don't need it, and server resource packs keep their custom HUD glyphs.

## Using it

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.3, then drop the MC Font Control jar into `mods/`.
2. Open the settings from [Mod Menu](https://modrinth.com/mod/modmenu), or bind a key to **Open font settings** under Options → Controls → Key Binds → MC Font Control.
3. Search for a font, pick a style, check the preview and press **Apply**. Press **Enter** or **Keep changes** to keep it.
4. **Reset to vanilla** switches back to the normal font. **Rescan fonts** picks up fonts you installed while the game was running.

Debug commands: `/mcfontcontrol fonts [filter]` lists discovered families; `/mcfontcontrol rescan` rescans.

Settings are stored in `config/mcfontcontrol.json`. The font list cache is `config/mcfontcontrol/font-index.json`.

## Supported systems

| System | Status |
|---|---|
| Windows 11 | Supported |
| macOS | Supported |
| Linux | Best effort: standard font folders and Flatpak launchers |

## Known limitations

- **Complex scripts** such as Arabic and Devanagari don't render correctly. Minecraft doesn't join or reorder glyphs, whatever font is used.
- **Colour emoji** fonts can't be used. Minecraft's text renderer is greyscale. Colour fonts are hidden unless you turn on **Show hidden fonts**, and then only their outline glyphs are drawn.
- **Classic Symbol fonts** (Wingdings, Webdings) are never listed. They have no Unicode character map, so Minecraft can't draw them.
- **Bold text** uses Minecraft's simulated bold (a 1-pixel offset), not the font's real Bold face.
- **Text far away in the world** (signs, name tags) can look thin or broken at a distance. Minecraft's glyph atlas has no mipmaps; built-in TrueType fonts behave the same.
- **Large text sizes** can be clipped in tight UI spots such as buttons and the scoreboard. Lower the size if that happens.
- **Other font mods:** if Caxton, Font, FontSwap or Modern UI is installed, MC Font Control turns itself off and tells you, rather than fighting over the font.
- **Copied configs:** a config written on another computer, OS or user account is set aside as `mcfontcontrol.json.bak`, and the mod starts from the vanilla font.

## Building

Requires Java 25. The Gradle wrapper downloads everything else.

```sh
./gradlew build                               # compile and run unit tests; jar in build/libs/
./gradlew runClient                           # launch Minecraft with the mod
./gradlew runClientGameTest                   # boot the client and run the automated game tests
./gradlew runClientGameTest -PwithSodiumIris  # the same, with Sodium and Iris installed
```

Game test screenshots land in `build/run/clientGameTest/screenshots/`. Performance and memory measurements land in `build/reports/measurements/`.

## Credits

MC Font Control uses Minecraft's own FreeType-based font provider. Prior art studied for this project: Caxton and SAH TTF Sign Fix (MIT). No code from GPL projects is included.
