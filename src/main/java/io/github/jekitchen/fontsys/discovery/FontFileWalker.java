package io.github.jekitchen.fontsys.discovery;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds font files under a set of folders. Follows symlinks, but never loops and never
 * returns the same file twice (PRD FD-3, FD-9).
 */
public final class FontFileWalker {
	private static final int MAX_DEPTH = 8;

	private FontFileWalker() {
	}

	public static boolean isFontFile(Path path) {
		String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc") || name.endsWith(".otc");
	}

	/** Returns every font file under {@code roots}, as real (symlink-resolved) paths, in a stable order. */
	public static List<Path> find(List<Path> roots) {
		Set<Path> seen = new HashSet<>();
		List<Path> result = new ArrayList<>();
		for (Path root : roots) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			try {
				Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DEPTH, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
						if (attrs.isRegularFile() && isFontFile(file)) {
							Path real = realPath(file);
							if (seen.add(real)) {
								result.add(real);
							}
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) {
						// Loops (FileSystemLoopException), broken links and unreadable folders are skipped.
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException | SecurityException ignored) {
				// An unreadable root just contributes nothing.
			}
		}
		result.sort(null);
		return result;
	}

	private static Path realPath(Path file) {
		try {
			return file.toRealPath();
		} catch (IOException | SecurityException e) {
			return file.toAbsolutePath().normalize();
		}
	}
}
