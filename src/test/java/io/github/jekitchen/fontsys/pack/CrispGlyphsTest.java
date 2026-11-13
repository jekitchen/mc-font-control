package io.github.jekitchen.fontsys.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.PixelGridTest;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/** Crisp mode (PRD AP-10, D34): render-time layout and monochrome glyphs. */
class CrispGlyphsTest {
	/** vanilla's {@code TrueTypeGlyphProvider.loadGlyph} flags: NO_BITMAP | BITMAP_METRICS_ONLY */
	private static final int VANILLA_MEASURE = 4194312;

	private static FontSettings settings(float oversample, float shiftX, boolean crisp) {
		return new FontSettings(true, "x.ttf", 0, "X", "Regular", 10, oversample, shiftX, 0, false, crisp);
	}

	@Test
	void crispModeFollowsTheGuiScale() {
		assertEquals(3, settings(5, 0, true).renderOversample(3), "crisp ignores a manual sharpness");
		assertTrue(settings(5, 0, true).followsGuiScale(), "so a GUI-scale change reloads it");
		assertEquals(5, settings(5, 0, false).renderOversample(3));
		assertEquals(2, settings(FontSettings.AUTO_OVERSAMPLE, 0, false).renderOversample(2));
		assertEquals(1, settings(FontSettings.AUTO_OVERSAMPLE, 0, true).renderOversample(0), "never below 1");
	}

	@Test
	void crispShiftsSnapToWholeScreenPixels() {
		assertEquals(2f / 3, settings(0, 0.5f, true).renderShift(0.5f, 3), 1e-6, "0.5 GUI units = 1.5 px at scale 3: rounds to 2 px");
		assertEquals(0.5f, settings(0, 0.5f, true).renderShift(0.5f, 2), 1e-6, "exactly 1 px at scale 2");
		assertEquals(0.5f, settings(0, 0.5f, false).renderShift(0.5f, 3), 1e-6, "smooth text keeps the exact shift");
	}

	@Test
	void unpacksOneBitRowsMostSignificantBitFirst() {
		ByteBuffer row = ByteBuffer.wrap(new byte[] {(byte) 0b1010_0000, (byte) 0b0000_0001});
		List<Integer> bits = new ArrayList<>();
		for (int x = 0; x < 16; x++) {
			bits.add(CrispGlyphs.monoBit(row, 0, x));
		}
		assertEquals(List.of(255, 0, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255), bits);
		assertEquals(255, CrispGlyphs.threshold((byte) 200));
		assertEquals(0, CrispGlyphs.threshold((byte) 127));
	}

	@Test
	void monochromeGlyphsMatchTheMeasuredSizeAndHaveNoGray() {
		Path arial = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "arial.ttf");
		checkFont(arial, false);
		checkFont(arial, true);
		PixelGridTest.pixelFont().ifPresent(font -> checkFont(font, false));
	}

	/** Measures each glyph as vanilla does in crisp mode, renders it, and checks size and coverage. */
	private static void checkFont(Path file, boolean syntheticBold) {
		PixelGridTest.withFace(file, face -> {
			for (int ppem : new int[] {8, 16, 30}) {
				FreeType.FT_Set_Pixel_Sizes(face, ppem, ppem);
				for (char c : "AgW@%ej".toCharArray()) {
					int index = (int) FreeType.FT_Get_Char_Index(face, c);
					int[] size = measure(face, index, syntheticBold);
					int[] on = {0};
					boolean ok = CrispGlyphs.render(face, index, syntheticBold, size[0], size[1], (x, y, coverage) -> {
						assertTrue(coverage == 0 || coverage == 255, "gray pixel in '" + c + "'");
						on[0] += coverage == 255 ? 1 : 0;
					});
					assertTrue(ok);
					FT_GlyphSlot slot = face.glyph();
					String what = file.getFileName() + " '" + c + "' at " + ppem + "px" + (syntheticBold ? " bold" : "");
					assertEquals(size[0], slot.bitmap().width(), "width of " + what);
					assertEquals(size[1], slot.bitmap().rows(), "height of " + what);
					assertTrue(on[0] > 0, "no pixels in " + what);
				}
			}
		});
	}

	/** {@code TrueTypeGlyphProviderMixin}'s crisp measurement. */
	private static int[] measure(FT_Face face, int index, boolean syntheticBold) {
		int target = CrispGlyphs.LOAD_TARGET_MONO;
		if (syntheticBold) {
			assertEquals(0, FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_NO_BITMAP | target));
			assertEquals(0, SyntheticBold.emboldenAndRender(face, true));
		} else {
			assertEquals(0, FreeType.FT_Load_Glyph(face, index, VANILLA_MEASURE | target));
		}
		FT_GlyphSlot slot = face.glyph();
		return new int[] {slot.bitmap().width(), slot.bitmap().rows()};
	}

	@Test
	void pixelFontsStayOnTheirGridWhenCrisp() {
		Path font = PixelGridTest.pixelFont().orElse(null);
		assumeTrue(font != null, "set FONTSYS_PIXEL_FONT to a pixel-style .ttf");
		PixelGridTest.withFace(font, face -> {
			int cells = io.github.jekitchen.fontsys.discovery.PixelGrid.detect(face).orElseThrow();
			// At twice the grid every design pixel is a 2×2 block: every on-run has even length.
			FreeType.FT_Set_Pixel_Sizes(face, cells * 2, cells * 2);
			int index = (int) FreeType.FT_Get_Char_Index(face, 'A');
			int[] size = measure(face, index, false);
			int[][] img = new int[size[1]][size[0]];
			CrispGlyphs.render(face, index, false, size[0], size[1], (x, y, c) -> img[y][x] = c);
			for (int[] row : img) {
				int run = 0;
				for (int x = 0; x <= row.length; x++) {
					if (x < row.length && row[x] == 255) {
						run++;
					} else {
						assertEquals(0, run % 2, "uneven pixel in a row of " + font.getFileName());
						run = 0;
					}
				}
			}
		});
	}
}
