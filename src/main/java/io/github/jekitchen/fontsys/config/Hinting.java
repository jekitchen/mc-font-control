package io.github.jekitchen.fontsys.config;

import com.google.gson.annotations.SerializedName;

/**
 * How FreeType fits glyph outlines to the pixel grid before rasterizing them (PRD D53).
 * {@code pack/HintingFlags} turns each mode into {@code FT_Load_Glyph} flags. Saved in lower
 * case ({@code "hinting": "light"}).
 */
public enum Hinting {
	/** The font's own hinting instructions: vanilla's default, the crispest. */
	@SerializedName("full")
	FULL("fontsys.advanced.hinting.full"),
	/** Vertical snapping only (baseline, x-height, cap height); keeps the designer's shapes. */
	@SerializedName("light")
	LIGHT("fontsys.advanced.hinting.light"),
	/** The exact outline, unhinted: the softest and most faithful. */
	@SerializedName("none")
	NONE("fontsys.advanced.hinting.none");

	public static final Hinting DEFAULT = FULL;

	private final String key;

	Hinting(String key) {
		this.key = key;
	}

	/** Lang key of the mode's name. */
	public String key() {
		return key;
	}

	/** A missing or unknown value in the config reads as the default (D16). */
	public static Hinting orDefault(Hinting value) {
		return value == null ? DEFAULT : value;
	}
}
