"""Draws the Fontsys icon: a blocky pixel "A" with a hard Minecraft-style shadow next to a
smooth "a" in Inter (SIL Open Font License). Minecraft's text, upgraded.

    python scripts/make-icon.py [path/to/Inter-VariableFont.ttf]

Writes docs/store/icon/icon-512.png, icon-400.png (CurseForge avatar), icon-128.png and the
mod's own src/main/resources/assets/fontsys/icon.png (128 px, shown by Mod Menu).
"""
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SS = 4                      # supersampling factor
SIZE = 512 * SS

# A 7x8 pixel "A" with two-pixel strokes, drawn for this icon.
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


def inter_path():
    if len(sys.argv) > 1:
        return sys.argv[1]
    for folder in (Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft/Windows/Fonts", Path("C:/Windows/Fonts"),
                   Path.home() / "Library/Fonts", Path.home() / ".local/share/fonts"):
        for f in folder.glob("Inter*Variable*.ttf"):
            if "Italic" not in f.name:
                return str(f)
    sys.exit("Inter not found; pass the path to Inter's variable font.")


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (1, 256))
    for y in range(256):
        t = y / 255
        img.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img.resize(size)


def draw():
    s = SS
    icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # Rounded-square background with a soft top-to-bottom gradient.
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=104 * s, fill=255)
    icon.paste(vertical_gradient((SIZE, SIZE), (46, 64, 92), (22, 31, 47)), (0, 0), mask)

    baseline = 366 * s
    cell = 25 * s

    # Pixel "A" with Minecraft's hard shadow: the same shape one cell down and right, darker.
    a_left = 60 * s
    a_top = baseline - len(PIXEL_A) * cell
    layer = ImageDraw.Draw(icon)
    for dx, dy, colour in ((cell // 2, cell // 2, (78, 86, 104)), (0, 0, (236, 238, 242))):
        for row, line in enumerate(PIXEL_A):
            for col, ch in enumerate(line):
                if ch == "#":
                    x = a_left + col * cell + dx
                    y = a_top + row * cell + dy
                    layer.rectangle((x, y, x + cell - 1, y + cell - 1), fill=colour)

    # Smooth "a" in Inter ExtraBold, with a soft shadow.
    font = ImageFont.truetype(inter_path(), 340 * s)
    font.set_variation_by_axes([32, 800]) if len(font.get_variation_axes()) == 2 else font.set_variation_by_axes([800])
    glyph_left = 278 * s
    glyph = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(glyph).text((glyph_left, baseline), "a", font=font, fill=255, anchor="ls")
    shadow = glyph.filter(ImageFilter.GaussianBlur(10 * s))
    icon.paste(Image.new("RGBA", (SIZE, SIZE), (10, 14, 22, 150)), (6 * s, 8 * s), shadow)
    fill = vertical_gradient((SIZE, SIZE), (120, 236, 186), (52, 196, 150))
    icon.paste(fill, (0, 0), glyph)

    # Keep everything inside the rounded square.
    clipped = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    clipped.paste(icon, (0, 0), mask)
    return clipped


def main():
    big = draw()
    out = ROOT / "docs/store/icon"
    out.mkdir(parents=True, exist_ok=True)
    for px in (512, 400, 128):
        big.resize((px, px), Image.LANCZOS).save(out / f"icon-{px}.png", optimize=True)
    mod_icon = ROOT / "src/main/resources/assets/fontsys/icon.png"
    big.resize((128, 128), Image.LANCZOS).save(mod_icon, optimize=True)
    print("Wrote", out, "and", mod_icon.relative_to(ROOT))


if __name__ == "__main__":
    main()
