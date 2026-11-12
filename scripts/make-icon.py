"""Draws the Fontsys icon: blocky pixel letters with a hard Minecraft-style shadow beside smooth
letters in Inter (SIL Open Font License). Minecraft's text, upgraded.

Two designs:
  aa        a pixel "A" next to a smooth "a"
  wordmark  "Font" in pixel letters, "sys" below in smooth letters

    python scripts/make-icon.py [--design aa|wordmark] [--preview FILE] [--inter PATH]

Without --preview, writes docs/store/icon/icon-512.png, icon-400.png (CurseForge avatar),
icon-128.png and the mod's own src/main/resources/assets/fontsys/icon.png (128 px, Mod Menu).
With --preview, writes only FILE: the 512 px icon beside 128, 64 and 32 px copies.
"""
import argparse
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SS = 4                      # supersampling factor
SIZE = 512 * SS

BACKGROUND = ((46, 64, 92), (22, 31, 47))
PIXEL_COLOUR = (236, 238, 242)
PIXEL_SHADOW = (78, 86, 104)
SMOOTH_FILL = ((120, 236, 186), (52, 196, 150))

# Pixel letters drawn for this icon (not taken from Minecraft's font). Rows top to bottom.
PIXEL_A = [
    ".#####.",
    "##...##",
    "##...##",
    "#######",
    "#######",
    "##...##",
    "##...##",
    "##...##",
]
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


def background():
    icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=104 * SS, fill=255)
    icon.paste(vertical_gradient((SIZE, SIZE), *BACKGROUND), (0, 0), mask)
    return icon, mask


def pixel_text(icon, rows_per_letter, left, top, cell, gap_cells=1):
    """Draws pixel letters with Minecraft's hard shadow: the same shape offset down-right, darker."""
    draw = ImageDraw.Draw(icon)
    offset = round(cell * 0.5)
    for dx, colour in ((offset, PIXEL_SHADOW), (0, PIXEL_COLOUR)):
        x0 = left
        for rows in rows_per_letter:
            for r, line in enumerate(rows):
                for c, ch in enumerate(line):
                    if ch == "#":
                        x = x0 + c * cell + dx
                        y = top + r * cell + dx
                        draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=colour)
            x0 += (len(rows[0]) + gap_cells) * cell


def pixel_width(rows_per_letter, cell, gap_cells=1):
    cols = sum(len(rows[0]) for rows in rows_per_letter) + gap_cells * (len(rows_per_letter) - 1)
    return cols * cell


def smooth_text(icon, text, font, x, baseline):
    """Smooth letters with a soft shadow and a green gradient fill."""
    glyph = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(glyph).text((x, baseline), text, font=font, fill=255, anchor="ls")
    shadow = glyph.filter(ImageFilter.GaussianBlur(10 * SS))
    icon.paste(Image.new("RGBA", (SIZE, SIZE), (10, 14, 22, 150)), (6 * SS, 8 * SS), shadow)
    icon.paste(vertical_gradient((SIZE, SIZE), *SMOOTH_FILL), (0, 0), glyph)


def draw_aa(font_path):
    s = SS
    icon, mask = background()
    baseline = 366 * s
    cell = 25 * s
    pixel_text(icon, [PIXEL_A], 60 * s, baseline - len(PIXEL_A) * cell, cell)
    smooth_text(icon, "a", inter(font_path, 340 * s), 278 * s, baseline)
    return clip(icon, mask)


def draw_wordmark(font_path):
    s = SS
    icon, mask = background()
    letters = [PIXEL_FONT[c] for c in "Font"]
    cell = 18 * s
    width = pixel_width(letters, cell)
    top = 100 * s
    pixel_text(icon, letters, (SIZE - width) // 2 - round(cell * 0.25), top, cell)

    # "sys" centred under "Font", about as wide, sharing its left and right edges visually.
    font = inter(font_path, 200 * s)
    left, _, right, _ = font.getbbox("sys", anchor="ls")
    baseline = 388 * s
    smooth_text(icon, "sys", font, (SIZE - (right - left)) // 2 - left, baseline)
    return clip(icon, mask)


def clip(icon, mask):
    clipped = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    clipped.paste(icon, (0, 0), mask)
    return clipped


def preview(big, path):
    sheet = Image.new("RGBA", (512 + 40 + 128 + 40 + 64 + 40 + 32 + 20, 512), (240, 240, 240, 255))
    sheet.paste(big, (0, 0), big)
    x = 552
    for px in (128, 64, 32):
        small = big.resize((px, px), Image.LANCZOS)
        sheet.paste(small, (x, (512 - px) // 2), small)
        x += px + 40
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--design", choices=["aa", "wordmark"], default="aa")
    parser.add_argument("--preview")
    parser.add_argument("--inter")
    args = parser.parse_args()
    font_path = inter_path(args.inter)
    big = (draw_wordmark if args.design == "wordmark" else draw_aa)(font_path).resize((512, 512), Image.LANCZOS)
    if args.preview:
        preview(big, args.preview)
        print("Wrote", args.preview)
        return
    out = ROOT / "docs/store/icon"
    out.mkdir(parents=True, exist_ok=True)
    for px in (512, 400, 128):
        big.resize((px, px), Image.LANCZOS).save(out / f"icon-{px}.png", optimize=True)
    mod_icon = ROOT / "src/main/resources/assets/fontsys/icon.png"
    big.resize((128, 128), Image.LANCZOS).save(mod_icon, optimize=True)
    print("Wrote", out, "and", mod_icon.relative_to(ROOT))


if __name__ == "__main__":
    main()
