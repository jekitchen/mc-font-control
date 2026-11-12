package io.github.jekitchen.fontsys.discovery;

import java.util.regex.Pattern;

/** Reads PostScript glyph names as evidence of what a glyph draws (PRD D27). */
final class GlyphNames {
	/** Names that identify a glyph by number, not by what it draws. */
	private static final Pattern NEUTRAL = Pattern.compile("(?i)(uni[0-9a-f]{4,6}|u[0-9a-f]{4,6}|(glyph|gid|cid|index|char|g)?[0-9]+)");

	private GlyphNames() {
	}

	/**
	 * False only when {@code name} clearly names another glyph than {@code letter}: "a10" or
	 * "Alpha" for "A". A missing or numbered name, the letter itself or a variant of it ("A.alt")
	 * could all be the letter.
	 */
	static boolean couldBe(String name, String letter) {
		if (name == null || name.isEmpty() || name.equals(letter) || name.startsWith(letter + ".") || name.startsWith(letter + "_")) {
			return true;
		}
		return NEUTRAL.matcher(name).matches();
	}
}
