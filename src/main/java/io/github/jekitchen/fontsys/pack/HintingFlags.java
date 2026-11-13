package io.github.jekitchen.fontsys.pack;

import io.github.jekitchen.fontsys.config.Hinting;
import org.lwjgl.util.freetype.FreeType;

/**
 * The {@code FT_Load_Glyph} flags for each hinting mode (PRD D53). {@code TrueTypeGlyphProviderMixin}
 * measures and {@code NativeImageMixin} renders with the same flags, so the bitmap is exactly the
 * size and position vanilla allocated for it.
 *
 * <p>What FreeType 2.14 (bundled with LWJGL 3.4.3, Minecraft 26.3) does with them:
 * <ul>
 *   <li><b>Full</b> adds nothing: vanilla's flags, so FreeType uses the font driver's own hinter.
 *       For TrueType fonts that runs the font's instructions on interpreter v40, which snaps
 *       vertically and rounds advances but ignores most horizontal instructions; a TrueType font
 *       without instructions gets the autohinter. For CFF ({@code .otf}) fonts it is the Adobe
 *       CFF engine, which only hints vertically.</li>
 *   <li><b>Light</b> ({@code FT_LOAD_TARGET_LIGHT}) uses the autohinter in light mode for
 *       TrueType fonts, ignoring the font's instructions: vertical snapping only, advances rounded
 *       to whole pixels. CFF fonts keep the Adobe engine, which already hints lightly, so for them
 *       Light draws exactly like Full.</li>
 *   <li><b>None</b> ({@code FT_LOAD_NO_HINTING}) draws the exact outline, with fractional
 *       advances.</li>
 * </ul>
 * FreeType ignores these flags for "tricky" fonts that can't be drawn unhinted (some old CJK
 * fonts). The rendered bitmap stays 8-bit antialiased in every mode: {@code FT_RENDER_MODE_LIGHT}
 * renders like {@code FT_RENDER_MODE_NORMAL}.
 */
public final class HintingFlags {
	/**
	 * {@code FT_LOAD_TARGET_LIGHT}: {@code FT_LOAD_TARGET_(FT_RENDER_MODE_LIGHT)}. Spelt out like
	 * {@link CrispGlyphs#LOAD_TARGET_MONO}, because LWJGL has misnamed these constants before.
	 */
	public static final int LOAD_TARGET_LIGHT = (FreeType.FT_RENDER_MODE_LIGHT & 15) << 16;

	private HintingFlags() {
	}

	/** Flags to add to vanilla's own for a smooth (not crisp) glyph. */
	public static int loadFlags(Hinting hinting) {
		return switch (Hinting.orDefault(hinting)) {
			case FULL -> 0;
			case LIGHT -> LOAD_TARGET_LIGHT;
			case NONE -> FreeType.FT_LOAD_NO_HINTING;
		};
	}
}
