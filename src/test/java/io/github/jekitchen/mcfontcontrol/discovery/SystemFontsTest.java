package io.github.jekitchen.mcfontcontrol.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Reads real installed fonts through LWJGL FreeType. Each check is skipped when the font is
 * not on this machine, so the suite also passes on CI runners without these fonts.
 */
class SystemFontsTest {
	private static List<FaceInfo> read(Path file) throws IOException {
		assumeTrue(Files.isRegularFile(file), "not installed: " + file);
		try (FreeTypeFaceReader reader = new FreeTypeFaceReader()) {
			return reader.read(file);
		}
	}

	private static Path windowsFont(String name) {
		return Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", name);
	}

	@Test
	void readsSingleTrueTypeFace() throws IOException {
		List<FaceInfo> faces = read(windowsFont("arial.ttf"));
		assertEquals(1, faces.size());
		FaceInfo arial = faces.getFirst();
		assertEquals("Arial", arial.family());
		assertEquals("Regular", arial.style());
		assertEquals("TrueType", arial.format());
		assertEquals(Visibility.VISIBLE, arial.visibility());
	}

	@Test
	void readsEveryFaceInACollection() throws IOException {
		List<FaceInfo> faces = read(windowsFont("cambria.ttc"));
		assertTrue(faces.size() >= 2, "expected Cambria and Cambria Math");
		assertTrue(faces.stream().anyMatch(f -> f.faceIndex() == 1));
	}

	@Test
	void listsVariableFontNamedInstances() throws IOException {
		List<FaceInfo> faces = read(windowsFont("SegUIVar.ttf"));
		assertTrue(faces.size() > 3, "expected several named instances, got " + faces.size());
		assertTrue(faces.stream().allMatch(FaceInfo::isNamedInstance));
		assertTrue(faces.stream().anyMatch(f -> f.style().contains("Semibold")));
	}

	@Test
	void hidesSymbolFonts() throws IOException {
		List<FaceInfo> faces = read(windowsFont("wingding.ttf"));
		assertFalse(faces.getFirst().visibility().isShown(true));
	}

	@Test
	void colourEmojiIsHiddenButRevealable() throws IOException {
		List<FaceInfo> faces = read(windowsFont("seguiemj.ttf"));
		assertEquals(Visibility.COLOR, faces.getFirst().visibility());
	}

	private static Path macFont(String name) {
		return Path.of("/System/Library/Fonts", name);
	}

	@Test
	void macReadsEveryFaceInACollection() throws IOException {
		List<FaceInfo> faces = read(macFont("Helvetica.ttc"));
		assertEquals(6, faces.size());
		assertEquals("Bold", faces.get(1).style());
		assertEquals(1, faces.get(1).faceIndex());
		assertTrue(faces.stream().allMatch(f -> f.visibility() == Visibility.VISIBLE));
	}

	/** SF Pro's system copy has 369 named instances (widths x weights); none may be dropped. */
	@Test
	void macListsEveryVariableFontNamedInstance() throws IOException {
		List<FaceInfo> faces = read(macFont("SFNS.ttf"));
		assertTrue(faces.size() > 256, "expected every SF Pro instance, got " + faces.size());
		assertTrue(faces.stream().allMatch(FaceInfo::isNamedInstance));
		assertTrue(faces.stream().anyMatch(f -> f.style().equals("Semibold")));
		assertEquals(Visibility.VISIBLE, faces.getFirst().visibility());
	}

	/** D25: the built-in SF Pro is listed as "System Font" and as "SF Pro", with the same styles. */
	@Test
	void macBuiltInSfProListedUnderBothNames() throws IOException {
		List<FaceInfo> faces = new java.util.ArrayList<>(read(macFont("SFNS.ttf")));
		faces.addAll(read(macFont("SFNSItalic.ttf")));
		List<FontFamily> families = FontFamily.group(faces, false);
		FontFamily system = families.stream().filter(f -> f.name().equals("System Font")).findFirst().orElseThrow();
		FontFamily sfPro = families.stream().filter(f -> f.name().equals("SF Pro")).findFirst().orElseThrow();
		assertEquals(system.styles().size(), sfPro.styles().size());
		assertEquals("Regular", sfPro.defaultStyle().style());
	}

	@Test
	void macBitmapColourEmojiIsNeverListed() throws IOException {
		List<FaceInfo> faces = read(macFont("Apple Color Emoji.ttc"));
		assertTrue(faces.stream().noneMatch(f -> f.visibility().isShown(true)));
	}

	@Test
	void macSystemPrivateFontsAreNeverListed() throws IOException {
		FaceInfo lastResort = read(macFont("LastResort.otf")).getFirst();
		assertEquals(Visibility.SYSTEM_PRIVATE, lastResort.visibility());
		assertFalse(lastResort.visibility().isShown(true));
	}

	@Test
	void macSymbolFonts() throws IOException {
		assertFalse(read(macFont("Supplemental/Wingdings.ttf")).getFirst().visibility().isShown(true));
		assertEquals(Visibility.NO_LATIN, read(macFont("Symbol.ttf")).getFirst().visibility());
	}

	@Test
	void macReadsCffOpenType() throws IOException {
		FaceInfo stix = read(macFont("Supplemental/STIXGeneral.otf")).getFirst();
		assertEquals("CFF", stix.format());
		assertEquals(Visibility.VISIBLE, stix.visibility());
	}

	/** Avenir Next's FreeType bold flags disagree with its style names; the names must win. */
	@Test
	void macAvenirNextStyleSlots() throws IOException {
		FontFamily avenir = FontFamily.group(read(macFont("Avenir Next.ttc")), false).getFirst();
		assertEquals("Regular", avenir.defaultStyle().style());
		StyledFaces styles = StyledFaces.of(Optional.of(avenir), avenir.defaultStyle());
		assertEquals("Bold", styles.bold().face().style());
		assertEquals("Italic", styles.italic().orElseThrow().face().style());
		assertEquals("Bold Italic", styles.boldItalic().face().style());
	}

	/** Full scan of this machine's font folders, timed against the D14 target (< 5 s cold). */
	@Test
	void fullScanMeetsTarget() throws IOException {
		List<Path> dirs = FontDirectories.forCurrentSystem();
		assumeTrue(dirs.stream().anyMatch(Files::isDirectory), "no font folders on this OS");
		FontScanner scanner = new FontScanner();
		FontIndex index;
		try (FreeTypeFaceReader reader = new FreeTypeFaceReader()) {
			index = scanner.scan(dirs, FontIndex.empty(), reader::read);
		}
		FontScanner.Stats cold = scanner.lastStats();
		List<FontFamily> families = FontFamily.group(index.allFaces(), false);
		scanner.scan(dirs, index, file -> {
			throw new AssertionError("cached rescan must not read " + file);
		});
		FontScanner.Stats warm = scanner.lastStats();

		String report = String.format("os=%s files=%d unreadable=%d faces=%d families=%d coldMs=%d warmMs=%d%n",
			OsFamily.current(), cold.files(), cold.failed(), index.allFaces().size(), families.size(), cold.millis(), warm.millis());
		System.out.print("[measurement] font scan: " + report);
		Path out = Path.of("build", "reports", "measurements");
		Files.createDirectories(out);
		Files.writeString(out.resolve("font-scan.txt"), report);
		assertTrue(cold.millis() < 5_000, "cold scan took " + cold.millis() + " ms");
		assertTrue(warm.millis() < 500, "cached scan took " + warm.millis() + " ms");
	}
}
