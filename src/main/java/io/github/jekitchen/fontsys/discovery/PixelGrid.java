package io.github.jekitchen.fontsys.discovery;

import java.nio.ByteBuffer;
import java.util.OptionalInt;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Outline;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;

/**
 * Recognises pixel-style fonts, so the config screen can suggest crisp mode (PRD 5.5, AP-10).
 *
 * <p>A pixel font is drawn on a coarse square grid: its outlines have no curves, and every
 * point sits on a multiple of the grid's cell size in font units. The grid size is how many
 * cells make up the em. At a font size equal to that number, or a multiple of it, each design
 * pixel covers whole screen pixels.
 */
public final class PixelGrid {
	/** Pixel fonts are drawn on grids of at most this many cells per em. */
	static final int MAX_CELLS = 32;
	/** Below this, every simple font would look like a pixel font. */
	static final int MIN_CELLS = 4;
	private static final String SAMPLE = "AEHMOSWaegmosw0258";

	private PixelGrid() {
	}

	/**
	 * Cells per em if the face looks like a pixel font. Loads a few glyphs unscaled, so call it
	 * before the face is shared with a glyph provider, or while holding the face's lock.
	 */
	public static OptionalInt detect(FT_Face face) {
		int unitsPerEm = Short.toUnsignedInt(face.units_per_EM());
		if (unitsPerEm == 0) {
			return OptionalInt.empty(); // bitmap-only font: no outlines to inspect
		}
		long cell = 0;
		int glyphs = 0;
		for (int i = 0; i < SAMPLE.length(); i++) {
			long index = FreeType.FT_Get_Char_Index(face, SAMPLE.charAt(i));
			if (index == 0 || FreeType.FT_Load_Glyph(face, (int) index, FreeType.FT_LOAD_NO_SCALE | FreeType.FT_LOAD_NO_BITMAP) != 0) {
				continue;
			}
			FT_GlyphSlot slot = face.glyph();
			if (slot == null || slot.format() != FreeType.FT_GLYPH_FORMAT_OUTLINE) {
				continue;
			}
			FT_Outline outline = slot.outline();
			int points = Short.toUnsignedInt(outline.n_points());
			if (points == 0) {
				continue;
			}
			ByteBuffer tags = outline.tags();
			FT_Vector.Buffer coords = outline.points();
			for (int p = 0; p < points; p++) {
				if ((tags.get(p) & FreeType.FT_CURVE_TAG_ON) == 0) {
					return OptionalInt.empty(); // a curve control point
				}
				FT_Vector v = coords.get(p);
				cell = gcd(cell, gcd(v.x(), v.y()));
			}
			glyphs++;
		}
		return glyphs >= 3 ? cells(unitsPerEm, cell) : OptionalInt.empty();
	}

	/** Cells per em for a cell size in font units, if it is a plausible pixel grid. */
	static OptionalInt cells(int unitsPerEm, long cell) {
		if (cell <= 0 || unitsPerEm % cell != 0) {
			return OptionalInt.empty();
		}
		long cells = unitsPerEm / cell;
		return cells >= MIN_CELLS && cells <= MAX_CELLS ? OptionalInt.of((int) cells) : OptionalInt.empty();
	}

	/**
	 * The font size to suggest: the multiple of the grid closest to the current size, within the
	 * size range, or half the grid, which stays whole screen pixels at even GUI scales. Empty if
	 * no such size is in range.
	 */
	public static OptionalInt suggestedSize(int cells, float current, float min, float max) {
		int best = -1;
		for (int size = 1; size <= max; size++) {
			boolean onGrid = size % cells == 0 || size * 2 == cells;
			if (onGrid && size >= min && (best < 0 || Math.abs(size - current) < Math.abs(best - current))) {
				best = size;
			}
		}
		return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
	}

	static long gcd(long a, long b) {
		a = Math.abs(a);
		b = Math.abs(b);
		while (b != 0) {
			long t = a % b;
			a = b;
			b = t;
		}
		return a;
	}
}
