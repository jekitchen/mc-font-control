package io.github.jekitchen.fontsys.discovery;

import java.nio.file.Path;
import java.util.List;

/**
 * Extra list entries for fonts players know by another name (PRD D25). Each alias lists the
 * same faces as its source family, so the player can preview it and see what they get.
 */
final class FamilyAliases {
	/**
	 * @param family     family name the font file carries
	 * @param folder     folder the file must be in
	 * @param filePrefix file name prefix, so only that file's faces are aliased
	 * @param alias      the extra family name
	 */
	record Alias(String family, String folder, String filePrefix, String alias) {
		boolean matches(FaceInfo face) {
			Path path = Path.of(face.path());
			return face.family().equalsIgnoreCase(family)
				&& path.getParent() != null
				// Compare paths, not strings: the unit tests also run on Windows, where the
				// parent of "/System/Library/Fonts/SFNS.ttf" prints with backslashes.
				&& path.getParent().equals(Path.of(folder))
				&& path.getFileName().toString().startsWith(filePrefix);
		}
	}

	/**
	 * macOS's built-in SF Pro ({@code SFNS.ttf}, {@code SFNSItalic.ttf}) calls itself "System
	 * Font". Apple's downloadable SF Pro installs as "SF Pro"; when that is installed it keeps
	 * the name and no alias is added.
	 */
	static final List<Alias> ALIASES = List.of(new Alias("System Font", "/System/Library/Fonts", "SFNS", "SF Pro"));

	private FamilyAliases() {
	}
}
