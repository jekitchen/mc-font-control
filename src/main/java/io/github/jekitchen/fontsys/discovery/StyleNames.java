package io.github.jekitchen.fontsys.discovery;

import java.util.Locale;

/** Infers weights from style names, since FreeType only reports a bold/not-bold flag. */
final class StyleNames {
	private StyleNames() {
	}

	static int weight(String style, boolean boldFlag) {
		String s = style == null ? "" : style.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
		// Longest keywords first so "semibold" is not read as "bold".
		if (s.contains("hairline") || s.contains("thin")) return 100;
		if (s.contains("extralight") || s.contains("ultralight")) return 200;
		if (s.contains("semilight") || s.contains("demilight")) return 350;
		if (s.contains("light")) return 300;
		if (s.contains("semibold") || s.contains("demibold") || s.contains("demi")) return 600;
		if (s.contains("extrabold") || s.contains("ultrabold")) return 800;
		if (s.contains("black") || s.contains("heavy")) return 900;
		if (s.contains("bold")) return 700;
		if (s.contains("medium")) return 500;
		return boldFlag ? 700 : 400;
	}

	/**
	 * 0 for plain names ("Regular", "Book"), 1 for other upright names, 2 for width variants
	 * ("Narrow", "Condensed"), so a family's default style is its ordinary face.
	 */
	static int plainness(String style) {
		String s = style == null ? "" : style.toLowerCase(Locale.ROOT).strip();
		if (s.equals("regular") || s.equals("normal") || s.equals("book") || s.equals("roman") || s.equals("plain")) return 0;
		if (s.contains("narrow") || s.contains("condensed") || s.contains("compressed") || s.contains("expanded")
			|| s.contains("extended") || s.contains("wide") || s.contains("display") || s.contains("caption")) return 2;
		return 1;
	}

	/** Weight and slope words, longest first so "semibold" is removed before "bold". */
	private static final String[] WEIGHT_AND_SLOPE_WORDS = {
		"extralight", "ultralight", "semilight", "demilight", "extrabold", "ultrabold", "semibold", "demibold",
		"hairline", "regular", "oblique", "italic", "medium", "normal", "black", "heavy", "light", "roman",
		"plain", "bold", "book", "thin", "demi",
	};

	/**
	 * The style name without weight and slope words: the width or optical-size part, such as
	 * {@code "condensed"} for "Condensed Bold" or {@code "display"} for "Display Semibold".
	 * Faces with the same residue belong to the same sub-family ({@code ""} for Regular, Bold,
	 * Italic and Bold Italic).
	 */
	static String residue(String style) {
		String s = style == null ? "" : style.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
		for (String word : WEIGHT_AND_SLOPE_WORDS) {
			s = s.replace(word, "");
		}
		return s;
	}

	static boolean isItalic(String style, boolean italicFlag) {
		String s = style == null ? "" : style.toLowerCase(Locale.ROOT);
		return italicFlag || s.contains("italic") || s.contains("oblique");
	}
}
