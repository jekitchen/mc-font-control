package io.github.jekitchen.mcfontcontrol.discovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Where each OS keeps installed fonts (PRD FD-1, FD-2, FD-9, FD-10). */
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
			case LINUX -> {
				// FD-9: standard folders, walked recursively; the walker follows symlinks without looping.
				String dataHome = env.get("XDG_DATA_HOME");
				Path userData = dataHome != null && !dataHome.isBlank() ? Path.of(dataHome) : userHome.resolve(".local").resolve("share");
				dirs.add(userData.resolve("fonts"));
				dirs.add(userHome.resolve(".fonts"));
				dirs.add(Path.of("/usr/local/share/fonts"));
				dirs.add(Path.of("/usr/share/fonts"));
				// FD-10: Flatpak exposes the host's fonts here.
				if (isFlatpak(env)) {
					dirs.add(Path.of("/run/host/fonts"));
					dirs.add(Path.of("/run/host/user-fonts"));
					dirs.add(Path.of("/run/host/local-fonts"));
				}
			}
			case OTHER -> {
				// Unknown OS: nothing to scan; the UI explains (FD-7).
			}
		}
		return List.copyOf(dirs);
	}

	/** Running inside a Flatpak sandbox (FD-10). */
	public static boolean isFlatpak(Map<String, String> env) {
		return env.containsKey("FLATPAK_ID") || java.nio.file.Files.exists(Path.of("/.flatpak-info"));
	}

	/** Inside Flatpak with no host font folder visible: the UI says why the list is short (FD-10). */
	public static boolean flatpakWithoutHostFonts() {
		Map<String, String> env = System.getenv();
		return OsFamily.current() == OsFamily.LINUX && isFlatpak(env)
			&& !java.nio.file.Files.isDirectory(Path.of("/run/host/fonts"))
			&& !java.nio.file.Files.isDirectory(Path.of("/run/host/user-fonts"));
	}

	public static List<Path> forCurrentSystem() {
		return forOs(OsFamily.current(), System.getenv(), Path.of(System.getProperty("user.home")));
	}
}
