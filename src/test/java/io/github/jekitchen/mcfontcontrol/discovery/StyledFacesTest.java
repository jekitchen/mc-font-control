package io.github.jekitchen.mcfontcontrol.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Choosing bold and italic faces the way word processors do (PRD AP-8, D24). */
class StyledFacesTest {
	private static FaceInfo face(String style) {
		String s = style.toLowerCase();
		boolean italic = s.contains("italic") || s.contains("oblique");
		boolean bold = s.equals("bold") || s.equals("bold italic");
		return new FaceInfo("/fonts/Test-" + style.replace(' ', '-') + ".ttf", 0, "Test", style, bold, italic, "TrueType", Visibility.VISIBLE);
	}

	private static FontFamily family(String... styles) {
		List<FaceInfo> faces = Arrays.stream(styles).map(StyledFacesTest::face).toList();
		return FontFamily.group(faces, true).getFirst();
	}

	private static StyledFaces styles(FontFamily family, String selected) {
		return StyledFaces.of(Optional.of(family), family.findStyle(selected).orElseThrow());
	}

	@Test
	void classicFourStyleFamily() {
		StyledFaces s = styles(family("Regular", "Bold", "Italic", "Bold Italic"), "Regular");
		assertEquals("Bold", s.bold().face().style());
		assertFalse(s.bold().syntheticBold());
		assertEquals("Italic", s.italic().orElseThrow().face().style());
		assertEquals("Bold Italic", s.boldItalic().face().style());
		assertTrue(s.boldItalic().italic());
	}

	@Test
	void singleFaceIsEmboldenedAndSlantedByVanilla() {
		StyledFaces s = styles(family("Regular"), "Regular");
		assertEquals("Regular", s.bold().face().style());
		assertTrue(s.bold().syntheticBold());
		assertTrue(s.italic().isEmpty());
		assertEquals(s.bold(), s.boldItalic());
		assertFalse(s.boldItalic().italic(), "vanilla adds the slant");
	}

	@Test
	void italicWithoutBoldItalicIsEmboldened() {
		StyledFaces s = styles(family("Regular", "Italic"), "Regular");
		assertTrue(s.bold().syntheticBold());
		assertEquals("Italic", s.boldItalic().face().style());
		assertTrue(s.boldItalic().syntheticBold());
		assertTrue(s.boldItalic().italic());
	}

	@Test
	void boldIsTheNextHeavierWeightLikeCssBolder() {
		FontFamily f = family("Light", "Semilight", "Regular", "Semibold", "Bold", "Black");
		assertEquals("Bold", styles(f, "Regular").bold().face().style());
		assertEquals("Regular", styles(f, "Light").bold().face().style());
		assertEquals("Black", styles(f, "Semibold").bold().face().style());
		assertTrue(styles(f, "Black").bold().syntheticBold(), "nothing heavier than Black");
	}

	@Test
	void boldKeepsTheWidth() {
		FontFamily f = family("Regular", "Bold", "Condensed", "Condensed Bold");
		assertEquals("Condensed Bold", styles(f, "Condensed").bold().face().style());
		assertEquals("Bold", styles(f, "Regular").bold().face().style());
	}

	@Test
	void variableFontInstancesKeepTheirOpticalSize() {
		FontFamily f = family("Text", "Text Bold", "Display", "Display Bold", "Small Bold");
		assertEquals("Text Bold", styles(f, "Text").bold().face().style());
		assertEquals("Display Bold", styles(f, "Display").bold().face().style());
	}

	@Test
	void italicSelectedFaceIsItsOwnItalic() {
		StyledFaces s = styles(family("Regular", "Bold", "Italic", "Bold Italic"), "Italic");
		assertEquals("Italic", s.italic().orElseThrow().face().style());
		assertEquals("Bold Italic", s.bold().face().style());
		assertEquals("Bold Italic", s.boldItalic().face().style());
	}

	@Test
	void unknownFamilyFallsBackToSyntheticBold() {
		FaceInfo base = face("Regular");
		StyledFaces s = StyledFaces.of(Optional.empty(), base);
		assertEquals(base, s.bold().face());
		assertTrue(s.bold().syntheticBold());
		assertTrue(s.italic().isEmpty());
	}

	@Test
	void residueDropsWeightAndSlopeWords() {
		assertEquals("", StyleNames.residue("SemiBold Italic"));
		assertEquals("", StyleNames.residue("Regular"));
		assertEquals("condensed", StyleNames.residue("Condensed Bold"));
		assertEquals("display", StyleNames.residue("Display Semibold"));
		assertEquals("", StyleNames.residue("ExtraLight Oblique"));
	}

	@Test
	void bolderFollowsCss() {
		assertEquals(400, StyledFaces.bolder(100));
		assertEquals(400, StyledFaces.bolder(300));
		assertEquals(700, StyledFaces.bolder(400));
		assertEquals(700, StyledFaces.bolder(500));
		assertEquals(900, StyledFaces.bolder(600));
		assertEquals(900, StyledFaces.bolder(900));
	}
}
