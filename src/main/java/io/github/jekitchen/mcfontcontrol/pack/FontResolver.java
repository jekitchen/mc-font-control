package io.github.jekitchen.mcfontcontrol.pack;

import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.discovery.FontFamily;
import io.github.jekitchen.mcfontcontrol.discovery.FontIndex;
import io.github.jekitchen.mcfontcontrol.discovery.FreeTypeFaceReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Turns saved settings into a font file that can actually be loaded. A missing file is
 * re-found by family and style name; anything else that goes wrong means "use vanilla" with
 * a single reason for the caller to log (PRD AP-7, section 5.6).
 */
public final class FontResolver {
	/**
	 * A loadable face.
	 *
	 * @param face the face as FreeType reads it now, with {@code path} as its path
	 */
	public record Resolved(Path path, int faceIndex, String family, String style, boolean moved, FaceInfo face) {
	}

	/** Either a face to load or the reason there is none. */
	public record Result(Optional<Resolved> font, String problem) {
		static Result ok(Resolved r) {
			return new Result(Optional.of(r), null);
		}

		static Result fail(String problem) {
			return new Result(Optional.empty(), problem);
		}
	}

	/** Reads a font's faces; a seam for tests. */
	@FunctionalInterface
	public interface FaceReader {
		List<FaceInfo> read(Path file) throws IOException;
	}

	private FontResolver() {
	}

	public static Result resolve(FontSettings settings, Supplier<FontIndex> index) {
		return resolve(settings, index, FontResolver::readWithFreeType);
	}

	public static Result resolve(FontSettings settings, Supplier<FontIndex> index, FaceReader reader) {
		if (!settings.isActive()) {
			return Result.fail(null);
		}
		String label = describe(settings.familyName(), settings.styleName(), settings.fontPath());
		Path path = Path.of(settings.fontPath());
		boolean moved = false;
		int faceIndex = settings.faceIndex();
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			Optional<FaceInfo> found = refind(settings, index.get());
			if (found.isEmpty()) {
				return Result.fail("The font " + label + " is no longer installed; using the vanilla font.");
			}
			path = Path.of(found.get().path());
			faceIndex = found.get().faceIndex();
			moved = true;
		}
		try {
			int wanted = faceIndex;
			Optional<FaceInfo> face = reader.read(path).stream().filter(f -> f.faceIndex() == wanted).findFirst();
			if (face.isEmpty()) {
				return Result.fail("The font " + label + " no longer has the selected style; using the vanilla font.");
			}
			if (!face.get().visibility().isShown(true)) {
				return Result.fail("The font " + label + " cannot be drawn by Minecraft (" + face.get().visibility().name().toLowerCase() + "); using the vanilla font.");
			}
			return Result.ok(new Resolved(path, faceIndex, face.get().family(), face.get().style(), moved, face.get()));
		} catch (IOException | RuntimeException | LinkageError e) {
			return Result.fail("The font " + label + " could not be read (" + e.getClass().getSimpleName() + "); using the vanilla font.");
		}
	}

	/** Same family and style elsewhere on disk, or the family's default style. */
	static Optional<FaceInfo> refind(FontSettings settings, FontIndex index) {
		if (settings.familyName() == null) {
			return Optional.empty();
		}
		return FontFamily.group(index.allFaces(), true).stream()
			.filter(f -> f.name().equalsIgnoreCase(settings.familyName()))
			.findFirst()
			.map(f -> settings.styleName() == null ? f.defaultStyle() : f.findStyle(settings.styleName()).orElse(f.defaultStyle()))
			.filter(f -> Files.isRegularFile(Path.of(f.path())));
	}

	private static List<FaceInfo> readWithFreeType(Path file) throws IOException {
		try (FreeTypeFaceReader reader = new FreeTypeFaceReader()) {
			return reader.read(file);
		}
	}

	/** "Avenir Next Medium (Avenir Next.ttc)": names and file name only, never a home path. */
	static String describe(String family, String style, String path) {
		String file = path == null ? "?" : Path.of(path).getFileName().toString();
		String name = family == null ? file : style == null ? family : family + " " + style;
		return "'" + name + "' (" + file + ")";
	}
}
