package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphBitmap;

/**
 * A system-font glyph bitmap whose coverage Fontsys can read on the CPU, so it can be placed on
 * a mipmapped atlas page for clearer distant in-world text (PRD D50).
 */
public interface MipmappableBitmap extends GlyphBitmap {
	/** False in crisp mode (D34), whose glyphs must stay hard-edged at every distance. */
	boolean mipmapped();

	/**
	 * Renders the glyph and passes each pixel's coverage (0 to 255) to {@code sink}, for
	 * {@code 0 <= x < getPixelWidth()} and {@code 0 <= y < getPixelHeight()}.
	 *
	 * @return false if nothing was rendered: FreeType failed or the font was closed by a reload
	 */
	boolean render(CoverageSink sink);

	/** Receives one pixel's coverage. */
	@FunctionalInterface
	interface CoverageSink {
		void set(int x, int y, int coverage);
	}
}
