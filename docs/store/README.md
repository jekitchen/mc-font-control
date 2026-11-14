# Store listings: Modrinth and CurseForge

Everything needed to fill in the Fontsys project pages. The publishing steps themselves are in [`docs/RELEASING.md`](../RELEASING.md).

| File | Use |
|---|---|
| [`description.md`](description.md) | Long description: paste into Modrinth's Description tab and CurseForge's description editor. Modrinth takes Markdown as is. On CurseForge, switch the description editor from WYSIWYG to **Markdown** first, or the page shows the raw Markdown. |
| [`gallery/`](gallery/) | Gallery screenshots, 1920×1080 PNG, all under 2.1 MB. |
| [`icon/`](icon/) | Icon: `icon-512.png`, `icon-400.png` (CurseForge avatar size), `icon-128.png`. |
| [`logo/`](logo/) | `fontsys-logo.png`, 1280×320: the horizontal logo, used at the top of the README. `fontsys-social.png`, 1280×640: the GitHub social preview (repository Settings → General → Social preview), with everything inside GitHub's 80 px safe border. |

## Project details

| Field | Value |
|---|---|
| Name | Fontsys |
| Summary (Modrinth) / short description (CurseForge) | Use any font installed on your computer for all of Minecraft's text, with real bold and italic, a live preview and an automatic revert. |
| Slug | `fontsys` |
| License | MIT |
| Source | https://github.com/jekitchen/mc-fontsys |
| Issues | https://github.com/jekitchen/mc-fontsys/issues |
| Loaders | Fabric, NeoForge (one file for both; PRD D54) |
| Game version | 26.3 |
| Environment | Client: required. Server: unsupported (Modrinth: "Client-side only"). |
| Dependencies | Modrinth: Fabric API required, Mod Menu optional. CurseForge: Fabric API optional, Mod Menu optional. Dependencies cover the whole file, not one loader; see PRD D54. |
| Modrinth categories | Utility |
| CurseForge categories | Utility & QoL (main), Cosmetic |

## Gallery

Upload in this order. Each image needs a title; descriptions are optional. Mark the first one as featured.

| File | Title | Description |
|---|---|---|
| `01-title-inter.png` | Your font, everywhere | The title screen in Inter, a font installed on this computer. |
| `02-title-vanilla.png` | The same screen in vanilla | Minecraft's built-in font, for comparison. |
| `03-config-preview.png` | Live preview | Search your fonts and preview one (here Georgia) with bold, italic, chat, tooltip and sign samples, and side by side at the next size down and up, before applying it. |
| `04-world-chat-inter.png` | Chat, HUD, signs and name tags | Inter in the world. Bold rank tags and the italic whisper use Inter's real Bold and Italic faces. |
| `05-world-chat-vanilla.png` | The same scene in vanilla | Vanilla bold draws each letter twice, one pixel apart. |
| `06-signs-georgia.png` | Any font, including serifs | Signs, glow ink, a text display and name tags in Georgia. |
| `07-tooltip-inter.png` | Tooltips | An enchanted item's tooltip in Inter; the lore line uses Inter's italic face. |
| `08-keep-georgia.png` | Keep this font? | Before you keep a font, samples show it in a sentence, look-alike characters, bold, italic, chat, a sign and a tooltip. The dialog itself stays in Minecraft's font. |
| `09-advanced.png` | Advanced settings | Fine-tuning most fonts don't need: baseline shifts, hinting and the Unicode font, with a preview of your font on a button next to Minecraft's. |

The screenshots are real game renders captured by `StoreScreenshotsGameTest` on Windows 11 (Minecraft 26.3, GUI scale Auto for the menus and 3 in the world). To retake them, for example after a UI change:

```sh
FONTSYS_STORE_SCREENSHOTS=1 ./gradlew runClientGameTest   # output: build/store-screenshots/
```

They use Inter and Georgia when installed (Inter is free under the SIL Open Font License; Georgia ships with Windows and macOS).

## Disclosures and policies

- **Modrinth: AI disclosure.** Modrinth's [content rules](https://modrinth.com/legal/rules#generative-ai) (section 6, since August 2026) require the "Contains AI-generated content" disclosure when "a substantial portion of the project's code is a product of AI output", or when the description relies on generative AI. Both apply to Fontsys: turn on the code and text disclosures in the project's **Disclosures** tab. The same section says projects "cannot be entirely or primarily comprised of content created or derived from generative AI output"; check with Modrinth support how that applies before submitting.
- **Modrinth: images.** Icons, gallery images and images in the description may not be "created or derived from generative AI output". The gallery screenshots are real renders of the game. The owner approved the icon and logo for Modrinth (below).
- **Modrinth: other disclosures.** Fontsys sends no data anywhere (no telemetry, no network calls), so no telemetry disclosure applies.
- **CurseForge.** Its moderation guidelines ask for an original project avatar (not a blank square, not a generic game graphic) and a disclaimer on AI-modified showcase images that could misrepresent the mod. The gallery screenshots are unmodified renders.

## Icon and logo

"Font" in blocky pixel letters with Minecraft's hard drop shadow, and "sys" in smooth Inter ExtraBold: Minecraft's text, upgraded. The icon stacks the two words; the logo sets them side by side on one baseline, with Inter's x-height matched to the pixel letters'. The pixel letters are drawn for Fontsys, not taken from Minecraft's font; Inter is under the SIL Open Font License, which allows its use in a logo. The icon is legible down to 32×32. At 128×128 it is also the mod's own icon (`assets/fontsys/icon.png`, shown by Mod Menu).

`scripts/make-icon.py` draws all of them (Pillow, with Inter installed) and writes every file; `--preview FILE` writes a review sheet without touching them.

**Both stores:** the owner reviewed the icon, logo and screenshots against Modrinth's rules (section 6) and approved them for Modrinth and CurseForge (PRD D28). The AI-content disclosures above still apply.
