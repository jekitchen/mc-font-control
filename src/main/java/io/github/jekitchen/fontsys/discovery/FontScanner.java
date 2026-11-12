package io.github.jekitchen.fontsys.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds a {@link FontIndex}, reusing cached entries for files that have not changed. */
public final class FontScanner {
	/** Reads faces from one file; a seam for tests. */
	@FunctionalInterface
	public interface FaceSource {
		List<FaceInfo> read(Path file) throws IOException;
	}

	/** Counts from the last scan, for logging and the D14 performance check. */
	public record Stats(int files, int reused, int read, int failed, long millis) {
	}

	private Stats lastStats = new Stats(0, 0, 0, 0, 0);

	public Stats lastStats() {
		return lastStats;
	}

	public FontIndex scan(List<Path> roots, FontIndex previous, FaceSource source) {
		long start = System.nanoTime();
		Map<String, FontIndex.FileEntry> cached = previous.byPath();
		List<FontIndex.FileEntry> entries = new ArrayList<>();
		int reused = 0;
		int read = 0;
		int failed = 0;
		for (Path file : FontFileWalker.find(roots)) {
			long modified;
			long size;
			try {
				BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
				modified = attrs.lastModifiedTime().toMillis();
				size = attrs.size();
			} catch (IOException | SecurityException e) {
				continue;
			}
			FontIndex.FileEntry old = cached.get(file.toString());
			if (old != null && old.matches(modified, size)) {
				entries.add(old);
				reused++;
				continue;
			}
			try {
				entries.add(new FontIndex.FileEntry(file.toString(), modified, size, List.copyOf(source.read(file)), null));
				read++;
			} catch (IOException | RuntimeException e) {
				entries.add(new FontIndex.FileEntry(file.toString(), modified, size, List.of(), String.valueOf(e.getMessage())));
				failed++;
			}
		}
		lastStats = new Stats(entries.size(), reused, read, failed, (System.nanoTime() - start) / 1_000_000);
		return new FontIndex(FontIndex.VERSION, List.copyOf(entries));
	}
}
