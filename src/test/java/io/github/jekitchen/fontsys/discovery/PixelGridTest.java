package io.github.jekitchen.fontsys.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

/**
 * Pixel-font detection for the crisp-mode hint (PRD 5.5). Fonts can't be committed, so the
 * pixel-font checks run only when {@code FONTSYS_PIXEL_FONT} names a pixel-style {@code .ttf}
 * on this machine (for example Press Start 2P or Silkscreen, both SIL OFL).
 */
public class PixelGridTest {
	/** A pixel-style font named by {@code FONTSYS_PIXEL_FONT}, if it exists. */
	public static Optional<Path> pixelFont() {
		String env = System.getenv("FONTSYS_PIXEL_FONT");
		return env == null || env.isBlank() ? Optional.empty() : Optional.of(Path.of(env)).filter(Files::isRegularFile);
	}

	/** Opens face 0 of a font file with LWJGL FreeType; skips the test if the file is missing. */
	public static void withFace(Path file, Consumer<FT_Face> body) {
		assumeTrue(Files.isRegularFile(file), "not installed: " + file);
		ByteBuffer data;
		try {
			byte[] bytes = Files.readAllBytes(file);
			data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		long library = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer ptr = stack.mallocPointer(1);
			assertEquals(0, FreeType.FT_Init_FreeType(ptr));
			library = ptr.get(0);
			assertEquals(0, FreeType.FT_New_Memory_Face(library, data, 0, ptr));
			body.accept(FT_Face.create(ptr.get(0)));
		} finally {
			if (library != 0) {
				FreeType.FT_Done_FreeType(library); // also frees the face
			}
			MemoryUtil.memFree(data);
		}
	}

	@Test
	void cellSizeMustDivideTheEmIntoAPlausibleGrid() {
		assertEquals(OptionalInt.of(8), PixelGrid.cells(1000, 125), "Press Start 2P style: 8 cells of 125 units");
		assertEquals(OptionalInt.of(16), PixelGrid.cells(2048, 128));
		assertEquals(OptionalInt.empty(), PixelGrid.cells(2048, 1), "an ordinary font: no coarse grid");
		assertEquals(OptionalInt.empty(), PixelGrid.cells(1000, 120), "doesn't divide the em");
		assertEquals(OptionalInt.empty(), PixelGrid.cells(1000, 500), "2 cells: too coarse to mean anything");
		assertEquals(OptionalInt.empty(), PixelGrid.cells(1000, 0));
		assertEquals(25, PixelGrid.gcd(-75, 100));
	}

	@Test
	void suggestsTheNearestSizeOnTheGrid() {
		assertEquals(OptionalInt.of(8), PixelGrid.suggestedSize(8, 10, 5, 20));
		assertEquals(OptionalInt.of(16), PixelGrid.suggestedSize(8, 13, 5, 20));
		assertEquals(OptionalInt.of(12), PixelGrid.suggestedSize(12, 10, 5, 20));
		assertEquals(OptionalInt.of(16), PixelGrid.suggestedSize(32, 10, 5, 20), "half of a 32-cell grid");
		assertEquals(OptionalInt.of(6), PixelGrid.suggestedSize(6, 5.5f, 5, 20));
	}

	@Test
	void ordinaryFontsAreNotPixelFonts() {
		Path fonts = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts");
		withFace(fonts.resolve("arial.ttf"), face -> assertEquals(OptionalInt.empty(), PixelGrid.detect(face)));
		withFace(fonts.resolve("consola.ttf"), face -> assertEquals(OptionalInt.empty(), PixelGrid.detect(face)));
	}

	@Test
	void recognizesAPixelFont() {
		Path font = pixelFont().orElse(null);
		assumeTrue(font != null, "set FONTSYS_PIXEL_FONT to a pixel-style .ttf");
		withFace(font, face -> {
			OptionalInt cells = PixelGrid.detect(face);
			assertTrue(cells.isPresent(), font.getFileName() + " not recognized");
			assertTrue(cells.getAsInt() >= 5 && cells.getAsInt() <= 32, "grid " + cells);
		});
	}
}
