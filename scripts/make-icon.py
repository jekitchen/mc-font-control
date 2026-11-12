"""Draws the Fontsys icon and logo: "Font" in blocky pixel letters with Minecraft's hard shadow,
and "sys" in smooth Inter (SIL Open Font License). Minecraft's text, upgraded.

    python scripts/make-icon.py [--preview FILE] [--inter PATH]

Writes:
  docs/store/icon/icon-512.png, icon-400.png (CurseForge avatar), icon-128.png
  src/main/resources/assets/fontsys/icon.png   (128 px, shown by Mod Menu)
  docs/store/logo/fontsys-logo.png             (1280x320, "Font" and "sys" side by side)
With --preview, writes only FILE: the icon at 512, 128, 64 and 32 px above the logo.
Needs Pillow and the Inter variable font (found in the usual font folders, or pass --inter).
"""
import argparse
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SS = 4  # supersampling factor

BACKGROUND = ((46, 64, 92), (22, 31, 47))
PIXEL_COLOUR = (236, 238, 242)
PIXEL_SHADOW = (78, 86, 104)
SMOOTH_FILL = ((120, 236, 186), (52, 196, 150))

# Pixel letters drawn for Fontsys (not taken from Minecraft's font). 7 rows each; the last row
# is the baseline, and "o" and "n" are 5 rows tall (the x-height).
PIXEL_FONT = {
    "F": ["#####",
          "#....",
          "#....",
          "####.",
          "#....",
          "#....",
          "#...."],
    "o": [".....",
          ".....",
          ".###.",
          "#...#",
          "#...#",
          "#...#",
          ".###."],
    "n": [".....",
          ".....",
          "####.",
          "#...#",
          "#...#",
          "#...#",
          "#...#"],
    "t": [".#..",
          ".#..",
          "####",
          ".#..",
          ".#..",
          ".#..",
          "..##"],
}
FONT = [PIXEL_FONT[c] for c in "Font"]
ROWS = 7


def inter_path(given):
    if given:
        return given
    for folder in (Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft/Windows/Fonts", Path("C:/Windows/Fonts"),
                   Path.home() / "Library/Fonts", Path.home() / ".local/share/fonts"):
        for f in folder.glob("Inter*Variable*.ttf"):
            if "Italic" not in f.name:
                return str(f)
    sys.exit("Inter not found; pass --inter with the path to Inter's variable font.")


def inter(path, size, weight=800):
    font = ImageFont.truetype(path, size)
    axes = font.get_variation_axes()
    font.set_variation_by_axes([32, weight] if len(axes) == 2 else [weight])
    return font


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (1, 256))
    for y in range(256):
        t = y / 255
        img.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img.resize(size)


def canvas(width, height, radius):
    """A rounded rectangle with the background gradient, and its mask."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=radius, fill=255)
    img.paste(vertical_gradient((width, height), *BACKGROUND), (0, 0), mask)
    return img, mask


def pixel_width(cell, gap_cells=1):
    return (sum(len(rows[0]) for rows in FONT) + gap_cells * (len(FONT) - 1)) * cell


def draw_pixel_font(img, left, top, cell, gap_cells=1):
    """"Font" in pixel letters with Minecraft's hard shadow: the same shape offset down-right, darker."""
    draw = ImageDraw.Draw(img)
    offset = round(cell * 0.5)
    for d, colour in ((offset, PIXEL_SHADOW), (0, PIXEL_COLOUR)):
        x0 = left
        for rows in FONT:
            for r, line in enumerate(rows):
                for c, ch in enumerate(line):
                    if ch == "#":
                        x = x0 + c * cell + d
                        y = top + r * cell + d
                        draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=colour)
            x0 += (len(rows[0]) + gap_cells) * cell


def draw_smooth(img, text, font, x, baseline):
    """Smooth letters with a soft shadow and the green gradient fill."""
    size = img.size
    glyph = Image.new("L", size, 0)
    ImageDraw.Draw(glyph).text((x, baseline), text, font=font, fill=255, anchor="ls")
    shadow = glyph.filter(ImageFilter.GaussianBlur(10 * SS))
    img.paste(Image.new("RGBA", size, (10, 14, 22, 150)), (6 * SS, 8 * SS), shadow)
    img.paste(vertical_gradient(size, *SMOOTH_FILL), (0, 0), glyph)


def clip(img, mask):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def draw_icon(font_path):
    """Square icon: "Font" above, "sys" below, both centred."""
    s = SS
    size = 512 * s
    img, mask = canvas(size, size, 104 * s)
    cell = 18 * s
    draw_pixel_font(img, (size - pixel_width(cell)) // 2 - round(cell * 0.25), 100 * s, cell)
    font = inter(font_path, 200 * s)
    left, _, right, _ = font.getbbox("sys", anchor="ls")
    draw_smooth(img, "sys", font, (size - (right - left)) // 2 - left, 388 * s)
    return clip(img, mask).resize((512, 512), Image.LANCZOS)


def draw_logo(font_path):
    """Horizontal logo: "Font" and "sys" side by side on one baseline, sharing the x-height."""
    s = SS
    width, height = 1280 * s, 320 * s
    img, mask = canvas(width, height, 56 * s)
    cell = 22 * s
    baseline = 226 * s
    # Inter's x-height matches the pixel letters' five-row x-height.
    x_height = inter(font_path, 1000).getbbox("x", anchor="ls")
    font = inter(font_path, round(5 * cell * 1000 / -x_height[1]))
    left, _, right, _ = font.getbbox("sys", anchor="ls")
    gap = 2 * cell
    total = pixel_width(cell) + gap + (right - left)
    x = (width - total) // 2
    draw_pixel_font(img, x, baseline - ROWS * cell, cell)
    draw_smooth(img, "sys", font, x + pixel_width(cell) + gap - left, baseline)
    return clip(img, mask).resize((1280, 320), Image.LANCZOS)


def preview(icon, logo, path):
    sheet = Image.new("RGBA", (1280, 512 + 40 + 320), (240, 240, 240, 255))
    sheet.paste(icon, (0, 0), icon)
    x = 552
    for px in (128, 64, 32):
        small = icon.resize((px, px), Image.LANCZOS)
        sheet.paste(small, (x, (512 - px) // 2), small)
        x += px + 40
    sheet.paste(logo, (0, 552), logo)
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview")
    parser.add_argument("--inter")
    args = parser.parse_args()
    font_path = inter_path(args.inter)
    icon = draw_icon(font_path)
    logo = draw_logo(font_path)
    if args.preview:
        preview(icon, logo, args.preview)
        print("Wrote", args.preview)
        return
    icons = ROOT / "docs/store/icon"
    icons.mkdir(parents=True, exist_ok=True)
    for px in (512, 400, 128):
        icon.resize((px, px), Image.LANCZOS).save(icons / f"icon-{px}.png", optimize=True)
    icon.resize((128, 128), Image.LANCZOS).save(ROOT / "src/main/resources/assets/fontsys/icon.png", optimize=True)
    logos = ROOT / "docs/store/logo"
    logos.mkdir(parents=True, exist_ok=True)
    logo.save(logos / "fontsys-logo.png", optimize=True)
    print("Wrote docs/store/icon, docs/store/logo and src/main/resources/assets/fontsys/icon.png")


if __name__ == "__main__":
    main()
