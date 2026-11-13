package io.github.jekitchen.fontsys.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The instance's own {@code fonts/} folder, next to {@code mods/} and {@code resourcepacks/}
 * (PRD FD-14, D33). Fonts placed there are listed alongside system fonts, for one instance
 * only, without installing them on the computer.
 *
 * <p>The config stores fonts in this folder as a path relative to the game folder
 * ({@code fonts/MyFont.ttf}), so the choice survives copying or moving the instance; every
 * other path is absolute. In memory, paths are always absolute.
 */
public final class InstanceFonts {
	/** Folder name inside the game folder. */
	public static final String FOLDER = "fonts";

	private static volatile @Nullable Path gameDir;

	private InstanceFonts() {
	}

	/** Set once at start-up from the loader's game folder. */
	public static void setGameDir(@Nullable Path dir) {
		gameDir = dir == null ? null : dir.toAbsolutePath().normalize();
	}

	/** The game folder set at start-up; empty in unit tests that don't set one. */
	public static Optional<Path> gameDir() {
		return Optional.ofNullable(gameDir);
	}

	/** {@code <game folder>/fonts}, whether or not it exists yet. */
	public static Optional<Path> dir() {
		return gameDir().map(InstanceFonts::dirOf);
	}

	public static Path dirOf(Path gameDir) {
		return gameDir.toAbsolutePath().normalize().resolve(FOLDER);
	}

	/**
	 * Creates {@code <game folder>/fonts} if it is missing, so players find it next to
	 * {@code mods/} and {@code resourcepacks/} the way vanilla's own folders appear (D38).
	 * Does nothing if it already exists.
	 *
	 * @throws IOException if it can't be created, for example on a read-only game folder or
	 *     when a file named {@code fonts} is in the way
	 */
	public static Path createDir(Path gameDir) throws IOException {
		return Files.createDirectories(dirOf(gameDir));
	}

	/** Whether the font file is inside this instance's fonts folder. */
	public static boolean contains(@Nullable String path) {
		Path dir = gameDir;
		return dir != null && contains(path, dir);
	}

	public static boolean contains(@Nullable String path, Path gameDir) {
		Path file = parse(path);
		return file != null && file.isAbsolute() && file.normalize().startsWith(dirOf(gameDir));
	}

	/** The form saved in the config: relative with {@code /} separators for instance fonts, otherwise unchanged. */
	public static @Nullable String toStored(@Nullable String path, @Nullable Path gameDir) {
		if (gameDir == null || !contains(path, gameDir)) {
			return path;
		}
		Path relative = gameDir.toAbsolutePath().normalize().relativize(Path.of(path).normalize());
		return relative.toString().replace('\\', '/');
	}

	/** The in-memory form: relative paths from the config resolved against the game folder. */
	public static @Nullable String toAbsolute(@Nullable String stored, @Nullable Path gameDir) {
		Path file = parse(stored);
		if (file == null || file.isAbsolute() || gameDir == null) {
			return stored;
		}
		return gameDir.toAbsolutePath().normalize().resolve(file).normalize().toString();
	}

	private static @Nullable Path parse(@Nullable String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		try {
			return Path.of(path);
		} catch (InvalidPathException e) {
			return null;
		}
	}
}
