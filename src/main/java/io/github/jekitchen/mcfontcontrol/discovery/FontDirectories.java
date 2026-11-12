package io.github.jekitchen.mcfontcontrol.discovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Where each OS keeps installed fonts (PRD FD-1, FD-2). */
public final class FontDirectories {
	private FontDirectories() {
	}

	/**
	 * Candidate font folders for an OS. Folders may not exist; the scanner skips missing ones.
	 *
	 * @param env       environment variables (for {@code WINDIR}, {@code LOCALAPPDATA})
	 * @param userHome  the user's home folder
	 */
	public static List<Path> forOs(OsFamily os, Map<String, String> env, Path userHome) {
		List<Path> dirs = new ArrayList<>();
		switch (os) {
			case WINDOWS -> {
				String windir = env.getOrDefault("WINDIR", env.getOrDefault("SystemRoot", "C:\\Windows"));
				dirs.add(Path.of(windir, "Fonts"));
				String localAppData = env.get("LOCALAPPDATA");
				Path local = localAppData != null ? Path.of(localAppData) : userHome.resolve("AppData").resolve("Local");
				dirs.add(local.resolve("Microsoft").resolve("Windows").resolve("Fonts"));
			}
			case MACOS -> {
				// /System/Library/Fonts/Supplemental is reached by the recursive walk.
				dirs.add(Path.of("/System/Library/Fonts"));
				dirs.add(Path.of("/Library/Fonts"));
				dirs.add(userHome.resolve("Library").resolve("Fonts"));
			}
			case LINUX, OTHER -> {
				// Linux folders arrive with M5L (FD-9, FD-10).
			}
		}
		return List.copyOf(dirs);
	}

	public static List<Path> forCurrentSystem() {
		return forOs(OsFamily.current(), System.getenv(), Path.of(System.getProperty("user.home")));
	}
}
