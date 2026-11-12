package io.github.jekitchen.fontsys.pack;

import java.nio.ByteBuffer;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/**
 * Hard-edged glyphs for crisp mode (PRD AP-10, D34).
 *
 * <p>FreeType rasterizes the outline in its monochrome mode, hinted for monochrome output, and
 * each pixel becomes fully on or fully off. The glyph is measured with the same hinting
 * ({@code TrueTypeGlyphProviderMixin}), so the bitmap is exactly the size vanilla allocated in
 * the atlas. The atlas is sampled nearest-neighbour ({@code FontTexture}), and the oversample is
 * the GUI scale, so one glyph pixel lands on one screen pixel.
 */
public final class CrispGlyphs {
	/**
	 * {@code FT_LOAD_TARGET_MONO}: {@code FT_LOAD_TARGET_(FT_RENDER_MODE_MONO)}. LWJGL 3.4.1
	 * publishes it under a misspelt name ({@code FT_FT_LOAD_TARGET_MONO}), so it is spelt out.
	 */
	public static final int LOAD_TARGET_MONO = (FreeType.FT_RENDER_MODE_MONO & 15) << 16;

	private CrispGlyphs() {
	}

	/** Receives one pixel's coverage, 0 or 255. */
	@FunctionalInterface
	public interface PixelSink {
		void set(int x, int y, int coverage);
	}

	/**
	 * Loads, renders and expands one glyph. The caller holds the face's lock.
	 *
	 * @param syntheticBold embolden the outline first, as for measuring (AP-8)
	 * @return false if FreeType failed; nothing was written
	 */
	public static boolean render(FT_Face face, int glyphIndex, boolean syntheticBold, int width, int height, PixelSink sink) {
		if (FreeType.FT_Load_Glyph(face, glyphIndex, FreeType.FT_LOAD_NO_BITMAP | LOAD_TARGET_MONO) != 0) {
			return false;
		}
		FT_GlyphSlot slot = face.glyph();
		if (slot == null) {
			return false;
		}
		if (syntheticBold) {
			FreeType.FT_GlyphSlot_Embolden(slot);
		}
		if (FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_MONO) != 0) {
			return false;
		}
		FT_Bitmap bitmap = slot.bitmap();
		int rows = bitmap.rows();
		int pitch = bitmap.pitch();
		int stride = Math.abs(pitch);
		ByteBuffer buffer = rows > 0 && stride > 0 ? bitmap.buffer(stride * rows) : null;
		if (buffer == null) {
			return true; // nothing to draw, e.g. a space
		}
		boolean mono = bitmap.pixel_mode() == FreeType.FT_PIXEL_MODE_MONO;
		int w = Math.min(width, bitmap.width());
		int h = Math.min(height, rows);
		for (int y = 0; y < h; y++) {
			// A negative pitch means the rows are stored bottom-up.
			int row = (pitch >= 0 ? y : rows - 1 - y) * stride;
			for (int x = 0; x < w; x++) {
				sink.set(x, y, mono ? monoBit(buffer, row, x) : threshold(buffer.get(row + x)));
			}
		}
		return true;
	}

	/** Bit {@code x} of a 1-bit row, most significant bit first, as 0 or 255. */
	static int monoBit(ByteBuffer buffer, int rowOffset, int x) {
		int bits = buffer.get(rowOffset + (x >> 3));
		return ((bits >> (7 - (x & 7))) & 1) != 0 ? 255 : 0;
	}

	/** An 8-bit coverage value, should FreeType ever return one, cut to fully on or off. */
	static int threshold(byte coverage) {
		return (coverage & 0xFF) >= 128 ? 255 : 0;
	}
}
