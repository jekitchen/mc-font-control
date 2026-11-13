package io.github.jekitchen.fontsys.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.font.GlyphInfo;
import org.junit.jupiter.api.Test;

/** Linear coverage (PRD D36) and the thinner drop shadow (D37). */
class TextQualityTest {
	@Test
	void coverageIsUploadedAsWhiteWithAlpha() {
		assertEquals(0x00FFFFFF, ColorAtlasGlyph.texel(0), "fully outside: transparent");
		assertEquals(0x80FFFFFF, ColorAtlasGlyph.texel(128), "half covered: white at half alpha, not gray");
		assertEquals(0xFFFFFFFF, ColorAtlasGlyph.texel(255));
	}

	@Test
	void shadowIsHalfAGuiUnitInWholeScreenPixels() {
		assertEquals(1.0F, TextShadow.offset(1), "one pixel at GUI scale 1, as vanilla");
		assertEquals(1 / 2.0F, TextShadow.offset(2), 1e-6, "1 px");
		assertEquals(2 / 3.0F, TextShadow.offset(3), 1e-6, "2 px");
		assertEquals(2 / 4.0F, TextShadow.offset(4), 1e-6, "2 px");
		assertEquals(3 / 5.0F, TextShadow.offset(5), 1e-6, "3 px");
		assertEquals(3 / 6.0F, TextShadow.offset(6), 1e-6, "3 px");
		assertEquals(1.0F, TextShadow.offset(0), "an unset scale falls back to vanilla");
	}

	@Test
	void thinShadowKeepsTheOtherMetrics() {
		GlyphInfo base = GlyphInfo.simple(7.5F);
		GlyphInfo thin = TextShadow.thin(base);
		assertEquals(7.5F, thin.getAdvance());
		assertEquals(base.getBoldOffset(), thin.getBoldOffset());
		assertEquals(base.getAdvance(true), thin.getAdvance(true));
	}
}
