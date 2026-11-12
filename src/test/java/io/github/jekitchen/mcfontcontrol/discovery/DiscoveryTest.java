package io.github.jekitchen.mcfontcontrol.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Font discovery without the game or FreeType (PRD D17). */
class DiscoveryTest {
	private static FaceInfo face(String family, String style, boolean bold, boolean italic, Visibility v) {
		return new FaceInfo("/fonts/" + family + "-" + style + ".ttf", 0, family, style, bold, italic, "TrueType", v);
	}

	@Test
	void osFamilyFromOsName() {
		assertEquals(OsFamily.WINDOWS, OsFamily.fromOsName("Windows 11"));
		assertEquals(OsFamily.MACOS, OsFamily.fromOsName("Mac OS X"));
		assertEquals(OsFamily.LINUX, OsFamily.fromOsName("Linux"));
		assertEquals(OsFamily.OTHER, OsFamily.fromOsName("FreeBSD"));
		assertEquals(OsFamily.OTHER, OsFamily.fromOsName(null));
	}

	@Test
	void windowsDirectories() {
		List<Path> dirs = FontDirectories.forOs(OsFamily.WINDOWS,
			Map.of("WINDIR", "C:\\Windows", "LOCALAPPDATA", "C:\\Users\\p\\AppData\\Local"), Path.of("C:\\Users\\p"));
		assertEquals(Path.of("C:\\Windows", "Fonts"), dirs.get(0));
		assertEquals(Path.of("C:\\Users\\p\\AppData\\Local", "Microsoft", "Windows", "Fonts"), dirs.get(1));
	}

	@Test
	void macDirectories() {
		List<Path> dirs = FontDirectories.forOs(OsFamily.MACOS, Map.of(), Path.of("/Users/p"));
		assertEquals(List.of(Path.of("/System/Library/Fonts"), Path.of("/Library/Fonts"), Path.of("/Users/p/Library/Fonts")), dirs);
	}

	@Test
	void linuxDirectories() {
		List<Path> dirs = FontDirectories.forOs(OsFamily.LINUX, Map.of(), Path.of("/home/p"));
		assertTrue(dirs.contains(Path.of("/home/p/.local/share/fonts")));
		assertTrue(dirs.contains(Path.of("/home/p/.fonts")));
		assertTrue(dirs.contains(Path.of("/usr/share/fonts")));
		assertTrue(dirs.contains(Path.of("/usr/local/share/fonts")));
		List<Path> xdg = FontDirectories.forOs(OsFamily.LINUX, Map.of("XDG_DATA_HOME", "/data"), Path.of("/home/p"));
		assertTrue(xdg.contains(Path.of("/data/fonts")));
		List<Path> flatpak = FontDirectories.forOs(OsFamily.LINUX, Map.of("FLATPAK_ID", "org.prismlauncher.PrismLauncher"), Path.of("/home/p"));
		assertTrue(flatpak.contains(Path.of("/run/host/fonts")));
	}

	@Test
	void walkerSurvivesSymlinkLoops(@TempDir Path dir) throws IOException {
		Path fonts = Files.createDirectories(dir.resolve("fonts/sub"));
		Files.writeString(fonts.resolve("a.ttf"), "x");
		try {
			Files.createSymbolicLink(fonts.resolve("loop"), dir.resolve("fonts"));
			Files.createSymbolicLink(dir.resolve("fonts/alias.ttf"), fonts.resolve("a.ttf"));
		} catch (IOException | UnsupportedOperationException | SecurityException e) {
			org.junit.jupiter.api.Assumptions.assumeTrue(false, "symlinks not permitted here");
		}
		List<Path> found = FontFileWalker.find(List.of(dir.resolve("fonts")));
		assertEquals(1, found.size(), "loop followed once and alias de-duplicated: " + found);
	}

	@Test
	void unknownOsHasNoDirectories() {
		assertTrue(FontDirectories.forOs(OsFamily.OTHER, Map.of(), Path.of("/home/p")).isEmpty());
	}

	@Test
	void walkerFindsFontFilesOnceAndIgnoresOthers(@TempDir Path dir) throws IOException {
		Files.createDirectories(dir.resolve("sub/deeper"));
		Files.writeString(dir.resolve("a.TTF"), "x");
		Files.writeString(dir.resolve("sub/b.otf"), "x");
		Files.writeString(dir.resolve("sub/deeper/c.ttc"), "x");
		Files.writeString(dir.resolve("readme.txt"), "x");
		// The same root twice must not produce duplicates.
		List<Path> found = FontFileWalker.find(List.of(dir, dir, dir.resolve("missing")));
		assertEquals(3, found.size());
		assertTrue(found.stream().allMatch(FontFileWalker::isFontFile));
	}

	@Test
	void classification() {
		assertEquals(Visibility.VISIBLE, Visibility.classify(true, true, true, false, true));
		assertEquals(Visibility.NO_LATIN, Visibility.classify(true, true, false, false, true));
		assertEquals(Visibility.COLOR, Visibility.classify(true, true, true, true, true));
		// Bitmap-only colour font (e.g. Apple Color Emoji): never shown.
		assertEquals(Visibility.UNSUPPORTED, Visibility.classify(false, true, true, true, true));
		// Symbol-only character map (e.g. Wingdings): never shown.
		assertEquals(Visibility.UNSUPPORTED, Visibility.classify(true, false, false, false, true));
		assertFalse(Visibility.NO_LATIN.isShown(false));
		assertTrue(Visibility.NO_LATIN.isShown(true));
		assertTrue(Visibility.COLOR.isShown(true));
		assertFalse(Visibility.UNSUPPORTED.isShown(true));
	}

	@Test
	void groupsFamiliesWithSlots() {
		List<FaceInfo> faces = List.of(
			face("Avenir Next", "Bold", true, false, Visibility.VISIBLE),
			face("Avenir Next", "Regular", false, false, Visibility.VISIBLE),
			face("Avenir Next", "Italic", false, true, Visibility.VISIBLE),
			face("Avenir Next", "Bold Italic", true, true, Visibility.VISIBLE),
			face("Avenir Next", "Demi Bold", true, false, Visibility.VISIBLE),
			face("avenir next", "Regular", false, false, Visibility.VISIBLE), // duplicate install
			face("Wingdings", "Regular", false, false, Visibility.UNSUPPORTED),
			face("Noto Sans Devanagari", "Regular", false, false, Visibility.NO_LATIN));
		List<FontFamily> families = FontFamily.group(faces, false);
		assertEquals(1, families.size());
		FontFamily avenir = families.getFirst();
		assertEquals("Avenir Next", avenir.name());
		assertEquals(List.of("Regular", "Demi Bold", "Bold", "Italic", "Bold Italic"), avenir.styles().stream().map(FaceInfo::style).toList());
		assertEquals("Regular", avenir.regular().orElseThrow().style());
		assertEquals("Bold", avenir.bold().orElseThrow().style());
		assertEquals("Italic", avenir.italic().orElseThrow().style());
		assertEquals("Bold Italic", avenir.boldItalic().orElseThrow().style());

		List<FontFamily> withHidden = FontFamily.group(faces, true);
		assertEquals(List.of("Avenir Next", "Noto Sans Devanagari"), withHidden.stream().map(FontFamily::name).toList());
	}

	@Test
	void defaultStyleFallsBackWhenThereIsNoRegular() {
		FontFamily light = FontFamily.group(List.of(
			face("Thin Sans", "Light", false, false, Visibility.VISIBLE),
			face("Thin Sans", "Thin", false, false, Visibility.VISIBLE)), false).getFirst();
		assertEquals("Light", light.defaultStyle().style());
		assertTrue(light.bold().isEmpty());
	}

	@Test
	void plainRegularBeatsWidthVariants() {
		FontFamily arial = FontFamily.group(List.of(
			face("Arial", "Narrow", false, false, Visibility.VISIBLE),
			face("Arial", "Narrow Bold", true, false, Visibility.VISIBLE),
			face("Arial", "Regular", false, false, Visibility.VISIBLE),
			face("Arial", "Bold", true, false, Visibility.VISIBLE)), false).getFirst();
		assertEquals("Regular", arial.defaultStyle().style());
		assertEquals("Bold", arial.bold().orElseThrow().style());
		assertEquals(List.of("Regular", "Bold", "Narrow", "Narrow Bold"), arial.styles().stream().map(FaceInfo::style).toList());
	}

	@Test
	void weightsFromStyleNames() {
		assertEquals(600, StyleNames.weight("SemiBold", true));
		assertEquals(600, StyleNames.weight("Demi Bold", false));
		assertEquals(700, StyleNames.weight("Bold", false));
		assertEquals(300, StyleNames.weight("Light Italic", false));
		assertEquals(400, StyleNames.weight("Book", false));
		assertEquals(700, StyleNames.weight("Whatever", true));
	}

	@Test
	void namedInstanceBits() {
		FaceInfo instance = new FaceInfo("/f.ttf", (3 << 16) | 1, "SF Pro", "Semibold", false, false, "TrueType", Visibility.VISIBLE);
		assertTrue(instance.isNamedInstance());
		assertFalse(face("A", "Regular", false, false, Visibility.VISIBLE).isNamedInstance());
	}

	@Test
	void cacheRoundTripAndReuse(@TempDir Path dir) throws IOException {
		Path fonts = Files.createDirectories(dir.resolve("fonts"));
		Path a = Files.writeString(fonts.resolve("a.ttf"), "aaaa");
		Path b = Files.writeString(fonts.resolve("b.ttf"), "bb");
		AtomicInteger reads = new AtomicInteger();
		FontScanner.FaceSource source = file -> {
			reads.incrementAndGet();
			if (file.getFileName().toString().equals("b.ttf")) {
				throw new IOException("corrupt");
			}
			return List.of(new FaceInfo(file.toString(), 0, "A", "Regular", false, false, "TrueType", Visibility.VISIBLE));
		};

		FontScanner scanner = new FontScanner();
		FontIndex first = scanner.scan(List.of(fonts), FontIndex.empty(), source);
		assertEquals(2, reads.get());
		assertEquals(1, scanner.lastStats().failed());
		assertEquals(1, first.allFaces().size());

		Path cache = dir.resolve("cfg/font-index.json");
		first.save(cache);
		FontIndex loaded = FontIndex.load(cache);
		assertEquals(first, loaded);

		// Unchanged files come from the cache, including remembered failures.
		FontIndex second = scanner.scan(List.of(fonts), loaded, source);
		assertEquals(2, reads.get());
		assertEquals(2, scanner.lastStats().reused());
		assertEquals(first, second);

		// A changed file is read again; a deleted file disappears.
		Files.writeString(a, "changed contents");
		Files.delete(b);
		FontIndex third = scanner.scan(List.of(fonts), second, source);
		assertEquals(3, reads.get());
		assertEquals(1, third.files().size());
	}

	@Test
	void corruptCacheLoadsEmpty(@TempDir Path dir) throws IOException {
		Path cache = Files.writeString(dir.resolve("font-index.json"), "{ not json");
		assertTrue(FontIndex.load(cache).files().isEmpty());
		assertTrue(FontIndex.load(dir.resolve("missing.json")).files().isEmpty());
	}
}
