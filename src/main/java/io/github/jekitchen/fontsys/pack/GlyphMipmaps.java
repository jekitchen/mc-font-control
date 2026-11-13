package io.github.jekitchen.fontsys.pack;

/**
 * Layout and CPU mip chains for Fontsys's mipmapped glyph pages (PRD D50). No game
 * dependencies, so the unit tests cover it.
 *
 * <p>Each glyph gets its own block, aligned to {@link #BLOCK} texels, with at least
 * {@link #MARGIN} transparent texels around the glyph. Because blocks are aligned to the
 * coarsest level's texel, every mip level of a block covers only that block, and the margin
 * keeps the GPU's bilinear filter (which reads up to one texel past the sample point, at the
 * level it samples) inside the block. So neighboring glyphs never bleed into each other, even
 * at the smallest level.
 */
public final class GlyphMipmaps {
	/** Mip levels per page: full size, 1/2, 1/4 and 1/8. */
	public static final int LEVELS = 4;
	/** One texel of the smallest level, in full-size texels. */
	public static final int BLOCK = 1 << (LEVELS - 1);
	/** Transparent texels on each side of a glyph: half a texel of the smallest level. */
	public static final int MARGIN = BLOCK / 2;

	private GlyphMipmaps() {
	}

	/** Width or height of the block for a glyph of {@code size} texels, margins included. */
	public static int blockSize(int size) {
		return (size + 2 * MARGIN + BLOCK - 1) / BLOCK * BLOCK;
	}

	/**
	 * The mip chain of one block. Level 0 is {@code coverage} itself; each further level halves
	 * the one before, every texel the rounded mean of the four beneath it. Coverage is linear
	 * (D36), so averaging it is what the GPU's own filter would do: distant text fades evenly
	 * instead of losing whole strokes.
	 *
	 * @param coverage row-major coverage, 0 to 255, {@code width * height} values
	 * @param width    a multiple of {@link #BLOCK}
	 * @param height   a multiple of {@link #BLOCK}
	 * @return {@link #LEVELS} arrays; level {@code n} is {@code (width >> n) * (height >> n)}
	 */
	public static int[][] chain(int[] coverage, int width, int height) {
		if (width % BLOCK != 0 || height % BLOCK != 0 || coverage.length != width * height) {
			throw new IllegalArgumentException("block " + width + "x" + height + " is not aligned to " + BLOCK);
		}
		int[][] levels = new int[LEVELS][];
		levels[0] = coverage;
		for (int level = 1; level < LEVELS; level++) {
			levels[level] = halve(levels[level - 1], width >> (level - 1), height >> (level - 1));
		}
		return levels;
	}

	/** One 2x2 box-filter step; {@code width} and {@code height} are even. */
	static int[] halve(int[] source, int width, int height) {
		int w = width / 2;
		int h = height / 2;
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			int top = 2 * y * width;
			int bottom = top + width;
			for (int x = 0; x < w; x++) {
				int sum = source[top + 2 * x] + source[top + 2 * x + 1] + source[bottom + 2 * x] + source[bottom + 2 * x + 1];
				out[y * w + x] = (sum + 2) >> 2;
			}
		}
		return out;
	}
}
