package io.github.jekitchen.fontsys.discovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** D27: glyph names only hide a font when they clearly name something other than the letter. */
class GlyphNamesTest {
	@Test
	void theLetterAndItsVariantsCount() {
		assertTrue(GlyphNames.couldBe("A", "A"));
		assertTrue(GlyphNames.couldBe("a", "a"));
		assertTrue(GlyphNames.couldBe("A.ss01", "A"));
		assertTrue(GlyphNames.couldBe("a_alt", "a"));
	}

	@Test
	void missingOrNumberedNamesSayNothing() {
		assertTrue(GlyphNames.couldBe(null, "A"));
		assertTrue(GlyphNames.couldBe("", "A"));
		assertTrue(GlyphNames.couldBe("uni0041", "A"));
		assertTrue(GlyphNames.couldBe("u0061", "a"));
		assertTrue(GlyphNames.couldBe("glyph36", "A"));
		assertTrue(GlyphNames.couldBe("cid00034", "A"));
		assertTrue(GlyphNames.couldBe("g12", "a"));
		assertTrue(GlyphNames.couldBe("36", "A"));
	}

	@Test
	void otherGlyphsDoNot() {
		// URW D050000L (dingbats) and Standard Symbols PS (Greek), shipped with most Linux desktops.
		assertFalse(GlyphNames.couldBe("a10", "A"));
		assertFalse(GlyphNames.couldBe("a60", "a"));
		assertFalse(GlyphNames.couldBe("Alpha", "A"));
		assertFalse(GlyphNames.couldBe("alpha", "a"));
		assertFalse(GlyphNames.couldBe("AE", "A"));
	}
}
