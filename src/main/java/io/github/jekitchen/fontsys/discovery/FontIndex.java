package io.github.jekitchen.fontsys.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scan results, cached to {@code config/fontsys/font-index.json} and keyed by
 * path + modification time + size (PRD FD-6). Holds metadata only, never font bytes (D14).
 */
public record FontIndex(int version, List<FileEntry> files) {
	// 2: macOS system-private fonts and bitmap-only color fonts reclassified (D25).
	public static final int VERSION = 2;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/**
	 * One scanned file. {@code error} is set when FreeType could not read it; the file is then
	 * skipped until it changes.
	 */
	public record FileEntry(String path, long modified, long size, List<FaceInfo> faces, String error) {
		public boolean matches(long modified, long size) {
			return this.modified == modified && this.size == size;
		}
	}

	public static FontIndex empty() {
		return new FontIndex(VERSION, List.of());
	}

	public List<FaceInfo> allFaces() {
		List<FaceInfo> faces = new ArrayList<>();
		for (FileEntry file : files) {
			if (file.faces() != null) {
				faces.addAll(file.faces());
			}
		}
		return faces;
	}

	public Map<String, FileEntry> byPath() {
		Map<String, FileEntry> map = new HashMap<>();
		for (FileEntry file : files) {
			map.put(file.path(), file);
		}
		return map;
	}

	/** Loads a cache file; returns an empty index if it is missing, corrupt or from another version. */
	public static FontIndex load(Path file) {
		if (!Files.isRegularFile(file)) {
			return empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			FontIndex index = GSON.fromJson(reader, FontIndex.class);
			if (index == null || index.version() != VERSION || index.files() == null) {
				return empty();
			}
			return index;
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return empty();
		}
	}

	/** Writes the cache atomically (temp file, then move) so a crash never leaves half a file. */
	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
