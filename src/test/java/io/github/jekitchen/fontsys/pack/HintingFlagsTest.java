package io.github.jekitchen.fontsys.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.jekitchen.fontsys.config.Hinting;
import io.github.jekitchen.fontsys.discovery.PixelGridTest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/** The hinting setting's FreeType flags, and what FreeType does with them (PRD D53). */
class HintingFlagsTest {
	/** vanilla's {@code TrueTypeGlyphProvider.loadGlyph} flags: NO_BITMAP | BITMAP_METRICS_ONLY */
	private static final int VANILLA_MEASURE = 4194312;
	/** {@code NativeImageMixin}'s render flags before hinting: vanilla's RENDER plus NO_BITMAP */
	private static final int RENDER = FreeType.FT_LOAD_RENDER | FreeType.FT_LOAD_NO_BITMAP;
	private static final String SAMPLE = "Hamburgefonstiv@0";

	private static Path windowsFont(String file) {
		return Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", file);
	}

	@Test
	void eachModeMapsToItsFlags() {
		assertEquals(0, HintingFlags.loadFlags(Hinting.FULL), "Full is vanilla's flags, unchanged");
		assertEquals(FreeType.FT_LOAD_TARGET_LIGHT, HintingFlags.loadFlags(Hinting.LIGHT));
		assertEquals(65536, HintingFlags.LOAD_TARGET_LIGHT, "FT_LOAD_TARGET_(FT_RENDER_MODE_LIGHT)");
		assertEquals(FreeType.FT_LOAD_NO_HINTING, HintingFlags.loadFlags(Hinting.NONE));
		assertEquals(0, HintingFlags.loadFlags(null), "missing reads as Full");
	}

	@Test
	void facesCarryTheirHintingAndCrispWins() {
		PixelGridTest.withFace(windowsFont("arial.ttf"), face -> {
			try {
				assertEquals(0, SystemFaces.loadFlags(face), "not ours: vanilla's flags");
				SystemFaces.add(face, false, false, Hinting.LIGHT);
				assertEquals(HintingFlags.LOAD_TARGET_LIGHT, SystemFaces.loadFlags(face));
				SystemFaces.remove(face.address());
				SystemFaces.add(face, false, false, Hinting.NONE);
				assertEquals(FreeType.FT_LOAD_NO_HINTING, SystemFaces.loadFlags(face));
				SystemFaces.remove(face.address());
				SystemFaces.add(face, false, true, Hinting.NONE);
				assertEquals(CrispGlyphs.LOAD_TARGET_MONO, SystemFaces.loadFlags(face), "crisp mode keeps its own hinting");
				SystemFaces.remove(face.address());
				SystemFaces.add(face, false, false);
				assertEquals(0, SystemFaces.loadFlags(face), "the old overload is Full");
			} finally {
				SystemFaces.remove(face.address());
			}
			assertEquals(0, SystemFaces.loadFlags(face), "removed: vanilla's flags again");
		});
	}

	/**
	 * Measured as {@code TrueTypeGlyphProviderMixin} does and rendered as {@code NativeImageMixin}
	 * does, every glyph has the same size and position in every mode: vanilla throws if the
	 * rendered bitmap doesn't match the measured one, and a different bearing misplaces it.
	 */
	@Test
	void measuredAndRenderedGlyphsMatchInEveryMode() {
		for (String file : new String[] {"arial.ttf", "segoeui.ttf"}) {
			for (boolean bold : new boolean[] {false, true}) {
				PixelGridTest.withFace(windowsFont(file), face -> {
					for (Hinting hinting : Hinting.values()) {
						SystemFaces.add(face, bold, false, hinting);
						try {
							checkMeasureMatchesRender(face, file + (bold ? " bold " : " ") + hinting);
						} finally {
							SystemFaces.remove(face.address());
						}
					}
				});
			}
		}
	}

	private static void checkMeasureMatchesRender(FT_Face face, String what) {
		int flags = SystemFaces.loadFlags(face);
		boolean bold = SystemFaces.isSyntheticBold(face);
		for (int ppem : new int[] {8, 13, 16, 20, 30}) {
			FreeType.FT_Set_Pixel_Sizes(face, ppem, ppem);
			for (char c : SAMPLE.toCharArray()) {
				int index = (int) FreeType.FT_Get_Char_Index(face, c);
				if (bold) {
					assertEquals(0, FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_NO_BITMAP | flags));
					assertEquals(0, SyntheticBold.emboldenAndRender(face, false));
				} else {
					assertEquals(0, FreeType.FT_Load_Glyph(face, index, VANILLA_MEASURE | flags));
				}
				int[] measured = box(face);
				if (bold) {
					assertEquals(0, FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_NO_BITMAP | flags));
					assertEquals(0, SyntheticBold.emboldenAndRender(face));
				} else {
					assertEquals(0, FreeType.FT_Load_Glyph(face, index, RENDER | flags));
				}
				int[] rendered = box(face);
				assertEquals(List.of(measured[0], measured[1], measured[2], measured[3], measured[4]),
					List.of(rendered[0], rendered[1], rendered[2], rendered[3], rendered[4]),
					"width, rows, left, top, advance of '" + c + "' at " + ppem + "px, " + what);
				assertEquals(FreeType.FT_PIXEL_MODE_GRAY, face.glyph().bitmap().pixel_mode(), "8-bit, as vanilla requires");
			}
		}
	}

	private static int[] box(FT_Face face) {
		FT_GlyphSlot slot = face.glyph();
		FT_Bitmap bitmap = slot.bitmap();
		return new int[] {bitmap.width(), bitmap.rows(), slot.bitmap_left(), slot.bitmap_top(), (int) slot.advance().x()};
	}

	/**
	 * What FreeType 2.14 does with each mode for a TrueType font (recorded in D53): Light runs
	 * the autohinter instead of the font's instructions, and None keeps fractional advances.
	 */
	@Test
	void trueTypeFontsDrawDifferentlyInEachMode() {
		for (String file : new String[] {"arial.ttf", "segoeui.ttf"}) {
			PixelGridTest.withFace(windowsFont(file), face -> {
				FreeType.FT_Set_Pixel_Sizes(face, 16, 16);
				String full = render(face, 0);
				String light = render(face, HintingFlags.LOAD_TARGET_LIGHT);
				String none = render(face, FreeType.FT_LOAD_NO_HINTING);
				assertNotEquals(full, light, file + ": Light differs from Full");
				assertNotEquals(light, none, file + ": None differs from Light");
				assertNotEquals(full, none, file + ": None differs from Full");
				assertEquals(render(face, HintingFlags.LOAD_TARGET_LIGHT | FreeType.FT_LOAD_FORCE_AUTOHINT), light,
					file + ": Light is the autohinter");
				assertEquals(render(face, HintingFlags.LOAD_TARGET_LIGHT | FreeType.FT_LOAD_NO_AUTOHINT), full,
					file + ": without the autohinter, Light would be Full");
				assertTrue(advances(face, 0).stream().allMatch(a -> a % 64 == 0), file + ": Full advances are whole pixels");
				assertTrue(advances(face, HintingFlags.LOAD_TARGET_LIGHT).stream().allMatch(a -> a % 64 == 0), file + ": so are Light's");
				assertTrue(advances(face, FreeType.FT_LOAD_NO_HINTING).stream().anyMatch(a -> a % 64 != 0), file + ": None's are fractional");
			});
		}
	}

	/** CFF ({@code .otf}) fonts: the Adobe engine already hints lightly, so Light draws exactly like Full. */
	@Test
	void cffFontsDrawTheSameInFullAndLight() {
		Optional<Path> cff = cffFont();
		assumeTrue(cff.isPresent(), "no CFF .otf font installed");
		PixelGridTest.withFace(cff.get(), face -> {
			FreeType.FT_Set_Pixel_Sizes(face, 16, 16);
			assertEquals(render(face, 0), render(face, HintingFlags.LOAD_TARGET_LIGHT), cff.get() + ": Light is Full");
			assertNotEquals(render(face, 0), render(face, FreeType.FT_LOAD_NO_HINTING), cff.get() + ": None differs");
		});
	}

	/** Every glyph of the sample, rendered with these flags, as one string of sizes, bearings and pixels. */
	private static String render(FT_Face face, int flags) {
		StringBuilder out = new StringBuilder();
		for (char c : SAMPLE.toCharArray()) {
			assertEquals(0, FreeType.FT_Load_Glyph(face, (int) FreeType.FT_Get_Char_Index(face, c), RENDER | flags));
			FT_GlyphSlot slot = face.glyph();
			FT_Bitmap bitmap = slot.bitmap();
			out.append(bitmap.width()).append('x').append(bitmap.rows()).append('@').append(slot.bitmap_left()).append(',').append(slot.bitmap_top()).append(':');
			int stride = Math.abs(bitmap.pitch());
			ByteBuffer buffer = bitmap.rows() > 0 && stride > 0 ? bitmap.buffer(stride * bitmap.rows()) : null;
			for (int i = 0; buffer != null && i < buffer.capacity(); i++) {
				out.append(Integer.toHexString(buffer.get(i) & 0xFF));
			}
			out.append(';');
		}
		return out.toString();
	}

	private static List<Long> advances(FT_Face face, int flags) {
		List<Long> advances = new ArrayList<>();
		for (char c : SAMPLE.toCharArray()) {
			assertEquals(0, FreeType.FT_Load_Glyph(face, (int) FreeType.FT_Get_Char_Index(face, c), VANILLA_MEASURE | flags));
			advances.add(face.glyph().advance().x());
		}
		return advances;
	}

	/** The first CFF-flavored .otf in the usual font folders, if any. */
	private static Optional<Path> cffFont() {
		List<Path> roots = List.of(
			Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts"),
			Path.of(System.getProperty("user.home"), "AppData", "Local", "Microsoft", "Windows", "Fonts"),
			Path.of("/usr/share/fonts"),
			Path.of("/Library/Fonts"));
		for (Path root : roots) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(root, 4)) {
				for (Path file : (Iterable<Path>) files.filter(p -> p.toString().toLowerCase().endsWith(".otf"))::iterator) {
					String[] format = {null};
					PixelGridTest.withFace(file, face -> format[0] = FreeType.FT_Get_Font_Format(face));
					if ("CFF".equals(format[0])) {
						return Optional.of(file);
					}
				}
			} catch (IOException | RuntimeException e) {
				// try the next folder
			}
		}
		return Optional.empty();
	}
}
