package io.github.jekitchen.mcfontcontrol.discovery;

/** Whether a face is listed, and if hidden, why (PRD FD-13, UI-10). */
public enum Visibility {
	/** Listed normally. */
	VISIBLE(false, true),
	/** No Basic Latin letters or digits. Hidden; revealed by "Show hidden fonts". */
	NO_LATIN(true, true),
	/** Colour font that also has outline glyphs. Hidden; revealed by "Show hidden fonts". */
	COLOR(true, true),
	/**
	 * Never listed: no outline glyphs (bitmap-only, including bitmap colour fonts such as Apple
	 * Color Emoji), no Unicode character map (classic Symbol fonts such as Wingdings), or a
	 * format FreeType can open but vanilla's provider cannot render.
	 */
	UNSUPPORTED(true, false),
	/**
	 * Never listed: a macOS system-private font, marked by a family name starting with a dot
	 * (".SF Arabic", ".LastResort"). macOS keeps these for its own UI and fallback.
	 */
	SYSTEM_PRIVATE(true, false);

	private final boolean hiddenByDefault;
	private final boolean revealable;

	Visibility(boolean hiddenByDefault, boolean revealable) {
		this.hiddenByDefault = hiddenByDefault;
		this.revealable = revealable;
	}

	public boolean isShown(boolean showHidden) {
		return !hiddenByDefault || (showHidden && revealable);
	}

	/**
	 * Classifies a face from facts FreeType reports.
	 *
	 * @param hasOutlines   the font has {@code glyf}, {@code CFF } or {@code CFF2} outlines
	 * @param hasUnicodeMap the font has a Unicode character map
	 * @param hasLatin      'A', 'a' and '0' all map to glyphs
	 * @param color         FreeType reports colour glyphs
	 * @param renderable    the outline format is one vanilla's provider can render (TrueType or CFF)
	 */
	public static Visibility classify(boolean hasOutlines, boolean hasUnicodeMap, boolean hasLatin, boolean color, boolean renderable) {
		if (!hasOutlines || !hasUnicodeMap || !renderable) {
			return UNSUPPORTED;
		}
		if (color) {
			return COLOR;
		}
		if (!hasLatin) {
			return NO_LATIN;
		}
		return VISIBLE;
	}
}
