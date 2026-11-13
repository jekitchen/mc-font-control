package io.github.jekitchen.fontsys.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** The preview's size strip (PRD UI-15). */
class SizeStripTest {
	private static List<Float> sizes(float size, OptionalInt cells, boolean crisp) {
		return SizeStrip.sizes(size, cells, crisp, 5, 20);
	}

	@Test
	void oneSizeEitherSide() {
		assertEquals(List.of(9F, 10F, 11F), sizes(10, OptionalInt.empty(), false));
		assertEquals(List.of(9.5F, 10.5F, 11.5F), sizes(10.5F, OptionalInt.empty(), false), "half sizes keep their half");
	}

	@Test
	void onlySizesInRange() {
		assertEquals(List.of(5F, 6F), sizes(5, OptionalInt.empty(), false));
		assertEquals(List.of(19F, 20F), sizes(20, OptionalInt.empty(), false));
		assertEquals(List.of(5.5F, 6.5F), sizes(5.5F, OptionalInt.empty(), false), "4.5 is below the minimum");
	}

	@Test
	void crispPixelFontsStepBetweenSquarePixelSizes() {
		// An 8-cell grid: 8 and 16 keep pixels square (4 is below the minimum).
		assertEquals(List.of(8F, 16F), sizes(8, OptionalInt.of(8), true));
		assertEquals(List.of(8F, 10F, 16F), sizes(10, OptionalInt.of(8), true), "off the grid: the grid sizes either side");
		assertEquals(List.of(8F, 16F), sizes(16, OptionalInt.of(8), true));
		// A 16-cell grid: 8 (half the grid) and 16.
		assertEquals(List.of(8F, 12F, 16F), sizes(12, OptionalInt.of(16), true));
	}

	@Test
	void pixelFontsWithoutCrispAndCrispNonPixelFontsUseWholeSteps() {
		assertEquals(List.of(7F, 8F, 9F), sizes(8, OptionalInt.of(8), false));
		assertEquals(List.of(9F, 10F, 11F), sizes(10, OptionalInt.empty(), true));
	}
}
