package io.github.jekitchen.mcfontcontrol.pack;

import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/**
 * Bold for families with no heavier face (PRD AP-8, D24). FreeType's own synthetic bold
 * ({@code FT_GlyphSlot_Embolden}) thickens the glyph outline by about 1/24 em before it is
 * rasterized and widens the advance to match, as word processors and browsers do. Unlike
 * vanilla's double draw, strokes stay solid at any size.
 */
public final class SyntheticBold {
	private SyntheticBold() {
	}

	/**
	 * Called right after the glyph outline was loaded with {@code FT_LOAD_NO_BITMAP} (no
	 * rendering): emboldens and renders it to an 8-bit bitmap.
	 *
	 * @return a FreeType error code, 0 on success
	 */
	public static int emboldenAndRender(FT_Face face) {
		FT_GlyphSlot slot = face.glyph();
		if (slot == null) {
			return 0;
		}
		FreeType.FT_GlyphSlot_Embolden(slot);
		return FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL);
	}
}
