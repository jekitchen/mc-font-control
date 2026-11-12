package io.github.jekitchen.mcfontcontrol.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

	@Test
	void macCollectionAndVariableFonts() throws IOException {
		Path helvetica = Path.of("/System/Library/Fonts/Helvetica.ttc");
		List<FaceInfo> faces = read(helvetica);
		assertTrue(faces.size() >= 4);
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
