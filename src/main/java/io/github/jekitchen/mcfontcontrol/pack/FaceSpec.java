package io.github.jekitchen.mcfontcontrol.pack;

/**
 * How one of our font files is opened and drawn.
 *
 * @param faceIndex     FreeType face index, including named-instance bits (D1)
 * @param syntheticBold FreeType emboldens every outline (no heavier face exists, AP-8)
 * @param providesBold  the glyphs are already bold, so vanilla must not draw them twice
 * @param providesItalic the glyphs are already italic, so vanilla must not slant them
 */
public record FaceSpec(int faceIndex, boolean syntheticBold, boolean providesBold, boolean providesItalic) {
	/** The selected face, used for plain text. */
	public static FaceSpec plain(int faceIndex) {
		return new FaceSpec(faceIndex, false, false, false);
	}

	/** True if glyphs must be wrapped to switch off one of vanilla's simulated styles. */
	boolean replacesVanillaStyle() {
		return providesBold || providesItalic;
	}
}
