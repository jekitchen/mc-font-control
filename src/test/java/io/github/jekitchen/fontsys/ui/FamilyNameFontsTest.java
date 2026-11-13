package io.github.jekitchen.fontsys.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The size a font-list name is drawn at in its own font (PRD D49). */
class FamilyNameFontsTest {
	@Test
	void ordinaryNamesUseTheDefaultSize() {
		// Ascenders at 0.75 em and descenders at 0.21 em, like Arial's "Arial" or Segoe UI's "Segoe UI".
		assertEquals(10F, FamilyNameFonts.fitSize(2048, 1536, -430));
		assertEquals(10F, FamilyNameFonts.fitSize(1000, 700, 0), "no descenders");
	}

	@Test
	void tallOrDeepNamesShrinkToFitTheRow() {
		// Ascenders at 1.1 em: 9 / 1.1 = 8.18, rounded down to a half size.
		assertEquals(8F, FamilyNameFonts.fitSize(1000, 1100, -200));
		// Descenders at 0.6 em, as in some script fonts: 4 / 0.6 = 6.67.
		assertEquals(6.5F, FamilyNameFonts.fitSize(1000, 700, -600));
	}

	@Test
	void extremeNamesFallBelowTheMinimum() {
		assertTrue(FamilyNameFonts.fitSize(1000, 2000, -1000) < FamilyNameFonts.MIN_SIZE, "a name twice the em tall stays in the normal font");
	}
}
