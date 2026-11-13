package io.github.jekitchen.fontsys.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The instance's {@code fonts/} folder (PRD FD-14, D33). */
class InstanceFontsTest {
	@TempDir
	Path game;

	@AfterEach
	void clearGameDir() {
		InstanceFonts.setGameDir(null);
	}

	private String inFonts(String... parts) {
		return InstanceFonts.dirOf(game).resolve(Path.of("", parts)).toString();
	}

	private static FaceInfo face(String path, String family, String style, boolean bold) {
		return new FaceInfo(path, 0, family, style, bold, false, "TrueType", Visibility.VISIBLE);
	}

	@Test
	void recognizesFilesInTheFontsFolderAndItsSubfolders() {
		assertTrue(InstanceFonts.contains(inFonts("Comic.ttf"), game));
		assertTrue(InstanceFonts.contains(inFonts("pixel", "Pixel.otf"), game));
		assertFalse(InstanceFonts.contains(game.resolve("mods").resolve("Comic.ttf").toString(), game));
		assertFalse(InstanceFonts.contains(game.resolve("fontsextra").resolve("Comic.ttf").toString(), game), "a sibling folder whose name starts with fonts");
		assertFalse(InstanceFonts.contains("fonts/Comic.ttf", game), "relative paths are config form, not in-memory form");
		assertFalse(InstanceFonts.contains(null, game));
		assertFalse(InstanceFonts.contains("", game));
	}

	@Test
	void storesInstanceFontsRelativeWithForwardSlashes() {
		assertEquals("fonts/Comic.ttf", InstanceFonts.toStored(inFonts("Comic.ttf"), game));
		assertEquals("fonts/pixel/Pixel.otf", InstanceFonts.toStored(inFonts("pixel", "Pixel.otf"), game));
		String system = game.getRoot().resolve("Windows").resolve("Fonts").resolve("arial.ttf").toString();
		assertEquals(system, InstanceFonts.toStored(system, game), "system fonts stay absolute");
		assertNull(InstanceFonts.toStored(null, game));
		assertEquals(inFonts("Comic.ttf"), InstanceFonts.toStored(inFonts("Comic.ttf"), null), "no game folder: unchanged");
	}

	@Test
	void relativePathsResolveAgainstWhereverTheInstanceIsNow(@TempDir Path moved) {
		String stored = InstanceFonts.toStored(inFonts("pixel", "Pixel.otf"), game);
		String resolved = InstanceFonts.toAbsolute(stored, moved);
		assertEquals(InstanceFonts.dirOf(moved).resolve("pixel").resolve("Pixel.otf").toString(), resolved);
		assertTrue(InstanceFonts.contains(resolved, moved));
		String system = game.getRoot().resolve("Library").resolve("Fonts").resolve("Avenir.ttc").toString();
		assertEquals(system, InstanceFonts.toAbsolute(system, moved));
		assertEquals("fonts/Comic.ttf", InstanceFonts.toAbsolute("fonts/Comic.ttf", null));
	}

	@Test
	void createsTheFontsFolderAndLeavesAnExistingOneAlone() throws IOException {
		Path dir = InstanceFonts.createDir(game);
		assertEquals(InstanceFonts.dirOf(game), dir);
		assertTrue(Files.isDirectory(dir));
		Path font = Files.writeString(dir.resolve("Kept.ttf"), "x");
		InstanceFonts.createDir(game);
		assertTrue(Files.exists(font), "an existing folder and its fonts are kept");
	}

	@Test
	void reportsAFileInTheWayInsteadOfReplacingIt(@TempDir Path other) throws IOException {
		Path blocker = Files.writeString(other.resolve(InstanceFonts.FOLDER), "not a folder");
		assertThrows(IOException.class, () -> InstanceFonts.createDir(other));
		assertEquals("not a folder", Files.readString(blocker));
	}

	@Test
	void instanceFamiliesAreListedApartFromSystemFamiliesOfTheSameName() {
		InstanceFonts.setGameDir(game);
		String sys = game.getRoot().resolve("sys").toString();
		List<FaceInfo> faces = List.of(
			face(sys + "/Inter-Regular.ttf", "Inter", "Regular", false),
			face(sys + "/Inter-Bold.ttf", "Inter", "Bold", true),
			face(inFonts("Inter-Regular.ttf"), "Inter", "Regular", false),
			face(inFonts("Pixelated.ttf"), "Pixelated", "Regular", false));
		List<FontFamily> families = FontFamily.group(faces, false);
		assertEquals(List.of("Inter", "Inter", "Pixelated"), families.stream().map(FontFamily::name).toList());
		FontFamily system = families.get(0);
		FontFamily instance = families.get(1);
		assertFalse(system.instance(), "system family first");
		assertEquals(2, system.styles().size());
		assertTrue(instance.instance());
		assertEquals(1, instance.styles().size(), "the instance Inter doesn't borrow the system Inter's Bold");
		assertTrue(families.get(2).instance());

		assertTrue(instance.holds("inter", inFonts("Inter-Regular.ttf")));
		assertFalse(instance.holds("Inter", sys + "/Inter-Regular.ttf"));
		assertTrue(system.holds("Inter", sys + "/Inter-Bold.ttf"));
		assertFalse(system.holds(null, sys + "/Inter-Bold.ttf"));
	}
}
