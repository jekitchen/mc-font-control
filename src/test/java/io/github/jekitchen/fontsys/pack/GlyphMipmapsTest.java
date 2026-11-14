package io.github.jekitchen.fontsys.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Layout and mip chains of the mipmapped glyph pages (PRD D50). */
class GlyphMipmapsTest {
	@Test
	void blocksAreAlignedToTheSmallestLevelWithRoomForTheMargins() {
		assertEquals(8, GlyphMipmaps.BLOCK, "4 levels: one texel of the 1/8 level");
		assertEquals(4, GlyphMipmaps.MARGIN);
		assertEquals(8, GlyphMipmaps.blockSize(0), "an empty glyph still gets a block");
		assertEquals(16, GlyphMipmaps.blockSize(1));
		assertEquals(16, GlyphMipmaps.blockSize(8));
		assertEquals(24, GlyphMipmaps.blockSize(9));
		assertEquals(48, GlyphMipmaps.blockSize(40));
		for (int size = 0; size < 300; size++) {
			int block = GlyphMipmaps.blockSize(size);
			assertEquals(0, block % GlyphMipmaps.BLOCK, "aligned at " + size);
			assertTrue(block >= size + 2 * GlyphMipmaps.MARGIN, "margins fit at " + size);
			assertTrue(block < size + 2 * GlyphMipmaps.MARGIN + GlyphMipmaps.BLOCK, "no wasted block at " + size);
		}
	}

	@Test
	void levelZeroIsTheGlyphItself() {
		int[] coverage = glyph(16, 24);
		int[][] chain = GlyphMipmaps.chain(coverage, 16, 24);
		assertEquals(GlyphMipmaps.LEVELS, chain.length);
		assertSame(coverage, chain[0], "full-size texels are uploaded unchanged");
		assertEquals(8 * 12, chain[1].length);
		assertEquals(4 * 6, chain[2].length);
		assertEquals(2 * 3, chain[3].length);
	}

	@Test
	void eachLevelAveragesTheFourTexelsBeneath() {
		int[] level = GlyphMipmaps.halve(new int[] {
			255, 0, 255, 255,
			0, 0, 255, 255,
			128, 128, 10, 20,
			128, 128, 30, 41,
		}, 4, 4);
		assertArrayEquals(new int[] {64, 255, 128, 25}, level, "rounded means");
	}

	@Test
	void coverageFadesEvenlyInsteadOfDroppingStrokes() {
		// A one-texel vertical stroke: nearest-neighbor sampling at half size keeps it or loses
		// it depending on where it falls; the mean keeps half of it either way, and the boost
		// darkens that half the same way wherever the stroke falls.
		for (int column = 0; column < 2; column++) {
			int[] stroke = new int[8 * 8];
			for (int y = 0; y < 8; y++) {
				stroke[y * 8 + 2 + column] = 255;
			}
			int[] plain = GlyphMipmaps.chain(stroke, 8, 8, false)[1];
			int[] boosted = GlyphMipmaps.chain(stroke, 8, 8)[1];
			for (int y = 0; y < 4; y++) {
				assertEquals(128, plain[y * 4 + 1], "column " + column + ", row " + y);
				assertEquals(192, boosted[y * 4 + 1], "column " + column + ", row " + y);
			}
		}
	}

	@Test
	void withoutTheBoostTotalCoverageIsKeptAtEveryLevel() {
		int[] coverage = glyph(32, 40);
		int[][] chain = GlyphMipmaps.chain(coverage, 32, 40, false);
		long full = sum(chain[0]);
		for (int level = 1; level < chain.length; level++) {
			long scaled = sum(chain[level]) << (2 * level);
			assertTrue(Math.abs(scaled - full) <= ((long) level * chain[level].length) << (2 * level), "level " + level + ": " + scaled + " vs " + full);
		}
	}

	@Test
	void theBoostDarkensSmallerLevelsMoreAndKeepsEmptyAndFull() {
		for (int level = 1; level < GlyphMipmaps.LEVELS; level++) {
			assertEquals(0, GlyphMipmaps.boost(level, 0), "empty stays empty at level " + level);
			assertEquals(255, GlyphMipmaps.boost(level, 255), "full stays full at level " + level);
			int previous = -1;
			for (int mean = 0; mean <= 255; mean++) {
				int boosted = GlyphMipmaps.boost(level, mean);
				assertTrue(boosted >= mean, "never lighter: level " + level + ", mean " + mean);
				assertTrue(boosted >= previous, "monotonic: level " + level + ", mean " + mean);
				previous = boosted;
			}
		}
		assertEquals(96, GlyphMipmaps.boost(1, 64), "x1.5 at 1/2 size");
		assertEquals(128, GlyphMipmaps.boost(2, 64), "x2 at 1/4 size");
		assertEquals(160, GlyphMipmaps.boost(3, 64), "x2.5 at 1/8 size");
		assertTrue(GlyphMipmaps.boost(3, 20) > GlyphMipmaps.boost(2, 20));
		assertTrue(GlyphMipmaps.boost(2, 20) > GlyphMipmaps.boost(1, 20));
	}

	@Test
	void theBoostLeavesLevelZeroAlone() {
		int[] coverage = glyph(16, 16);
		int[] copy = coverage.clone();
		int[][] chain = GlyphMipmaps.chain(coverage, 16, 16);
		assertSame(coverage, chain[0]);
		assertArrayEquals(copy, chain[0], "level 0 is uploaded byte for byte as before");
	}

	@Test
	void marginsStayTransparentSoNeighborsNeverBleed() {
		int width = GlyphMipmaps.blockSize(20);
		int height = GlyphMipmaps.blockSize(30);
		int[] block = new int[width * height];
		for (int y = 0; y < 30; y++) {
			for (int x = 0; x < 20; x++) {
				block[(y + GlyphMipmaps.MARGIN) * width + x + GlyphMipmaps.MARGIN] = 255;
			}
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				boolean inGlyph = x >= GlyphMipmaps.MARGIN && x < GlyphMipmaps.MARGIN + 20 && y >= GlyphMipmaps.MARGIN && y < GlyphMipmaps.MARGIN + 30;
				assertEquals(inGlyph ? 255 : 0, block[y * width + x], "texel " + x + "," + y);
			}
		}
		int[][] chain = GlyphMipmaps.chain(block, width, height);
		for (int level = 0; level < chain.length; level++) {
			// Every level is exactly the block's size at that level, so it never spills into a
			// neighbor's block.
			assertEquals((width >> level) * (height >> level), chain[level].length);
		}
		// The bilinear filter reads up to half a texel of the sampled level past the glyph's
		// edge: at the smallest level that is MARGIN full-size texels, still this block's own.
		assertEquals(GlyphMipmaps.BLOCK / 2, GlyphMipmaps.MARGIN);
	}

	@Test
	void unalignedBlocksAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> GlyphMipmaps.chain(new int[12 * 8], 12, 8));
		assertThrows(IllegalArgumentException.class, () -> GlyphMipmaps.chain(new int[8 * 8 - 1], 8, 8));
	}

	private static int[] glyph(int width, int height) {
		int[] coverage = new int[width * height];
		for (int i = 0; i < coverage.length; i++) {
			coverage[i] = (i * 37 + i / width * 11) % 256;
		}
		return coverage;
	}

	private static long sum(int[] values) {
		long total = 0;
		for (int v : values) {
			total += v;
		}
		return total;
	}
}
