package io.github.jekitchen.mcfontcontrol.discovery;

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

	static boolean isItalic(String style, boolean italicFlag) {
		String s = style == null ? "" : style.toLowerCase(Locale.ROOT);
		return italicFlag || s.contains("italic") || s.contains("oblique");
	}
}
