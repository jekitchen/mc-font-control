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

	/** The mip chain of one block, with the contrast {@link #boost}. */
	public static int[][] chain(int[] coverage, int width, int height) {
		return chain(coverage, width, height, true);
	}

	/**
	 * The mip chain of one block. Level 0 is {@code coverage} itself, uploaded unchanged. Each
	 * further level starts from the mean of the coverage beneath it (every texel the rounded
	 * mean of the four beneath it in the level before). Coverage is linear (D36), so averaging
	 * it is what the GPU's own filter would do: distant text fades evenly instead of losing
	 * whole strokes. With {@code boost}, levels 1 and up are then darkened ({@link #boost}) so
	 * distant strokes keep their contrast.
	 *
	 * @param coverage row-major coverage, 0 to 255, {@code width * height} values
	 * @param width    a multiple of {@link #BLOCK}
	 * @param height   a multiple of {@link #BLOCK}
	 * @param boost    whether to apply {@link #boost}; false only to measure the plain mean
	 * @return {@link #LEVELS} arrays; level {@code n} is {@code (width >> n) * (height >> n)}
	 */
	public static int[][] chain(int[] coverage, int width, int height, boolean boost) {
		if (width % BLOCK != 0 || height % BLOCK != 0 || coverage.length != width * height) {
			throw new IllegalArgumentException("block " + width + "x" + height + " is not aligned to " + BLOCK);
		}
		int[][] levels = new int[LEVELS][];
		levels[0] = coverage;
		int[] mean = coverage;
		for (int level = 1; level < LEVELS; level++) {
			mean = halve(mean, width >> (level - 1), height >> (level - 1));
			if (boost) {
				int[] boosted = new int[mean.length];
				for (int i = 0; i < mean.length; i++) {
					boosted[i] = boost(level, mean[i]);
				}
				levels[level] = boosted;
			} else {
				levels[level] = mean;
			}
		}
		return levels;
	}

	/**
	 * The contrast boost for level {@code level} (1 and up): the mean coverage times
	 * {@code 1 + level / 2}, so 1.5, 2 and 2.5, capped at full.
	 *
	 * <p>Why: the mean is the true average, but a thin stroke averaged over 4, 16 or 64 texels
	 * turns pale, and vanilla's text shader drops every pixel under 10% alpha
	 * ({@code core/text.fsh}). With the plain mean, text 8 blocks away had about 77% of 1.1.0's
	 * ink and text 20 blocks away about half. The gain grows with the level because each level
	 * spreads a stroke over four times the area. {@code DistantTextGameTest} compared it with a
	 * gamma curve, an "any sample hits ink" curve, a mean/max blend and twice this gain: this
	 * one brings distant text back to at least 1.1.0's darkness while keeping strokes apart, and
	 * it shimmers even less than the plain mean, because fewer pixels sit at the 10% cutoff.
	 * Zero stays zero, so the margins stay transparent and neighbors still never bleed.
	 */
	static int boost(int level, int mean) {
		return Math.min(255, (mean * (2 + level) + 1) / 2);
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
