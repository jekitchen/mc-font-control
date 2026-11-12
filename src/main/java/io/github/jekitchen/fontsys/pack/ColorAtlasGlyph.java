package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.lwjgl.util.freetype.FT_Face;

/**
 * Puts a system-font glyph in vanilla's colour glyph atlas instead of the grayscale one
 * (PRD D30).
 *
 * <p>Vanilla's {@code ttf} provider renders glyphs as 8-bit coverage and reports them as not
 * coloured, so {@code FontTexture} stores them in an {@code R8} atlas and draws them with the
 * {@code text_grayscale*} pipelines. Vanilla's own font never uses those pipelines (its glyphs
 * are PNG bitmaps), and shader mods don't fully support them: Iris 1.11.7 has no shader-pack
 * program for {@code text_grayscale_polygon_offset}, the pipeline for sign text, so with a
 * shader pack on, signs drew as streaks across the screen.
 *
 * <p>This glyph uploads each coverage value {@code c} as the RGBA pixel {@code (c, c, c, c)}
 * and reports itself as coloured, so it lands in the RGBA atlas and draws with the same
 * {@code text*} pipelines as vanilla's font. The grayscale shader samples {@code .rrrr}
 * ({@code core/text.fsh}, {@code IS_GRAYSCALE}), so the drawn pixels are identical.
 */
final class ColorAtlasGlyph implements UnbakedGlyph {
	private final UnbakedGlyph delegate;
	private final FT_Face face;
	private final int glyphIndex;

	/**
	 * @param face       the FreeType face the delegate came from
	 * @param glyphIndex the glyph's index in that face
	 */
	ColorAtlasGlyph(UnbakedGlyph delegate, FT_Face face, int glyphIndex) {
		this.delegate = delegate;
		this.face = face;
		this.glyphIndex = glyphIndex;
	}

	@Override
	public GlyphInfo info() {
		return delegate.info();
	}

	@Override
	public BakedGlyph bake(UnbakedGlyph.Stitcher stitcher) {
		return delegate.bake(new UnbakedGlyph.Stitcher() {
			@Override
			public BakedGlyph stitch(GlyphInfo info, GlyphBitmap bitmap) {
				return stitcher.stitch(info, bitmap.isColored() ? bitmap : new Rgba(bitmap));
			}

			@Override
			public BakedGlyph getMissing() {
				return stitcher.getMissing();
			}
		});
	}

	/** The delegate's bitmap, uploaded as RGBA. Size, bearings and oversample are unchanged. */
	private final class Rgba implements GlyphBitmap {
		private final GlyphBitmap delegate;

		private Rgba(GlyphBitmap delegate) {
			this.delegate = delegate;
		}

		@Override
		public int getPixelWidth() {
			return delegate.getPixelWidth();
		}

		@Override
		public int getPixelHeight() {
			return delegate.getPixelHeight();
		}

		@Override
		public float getOversample() {
			return delegate.getOversample();
		}

		@Override
		public float getBearingLeft() {
			return delegate.getBearingLeft();
		}

		@Override
		public float getBearingTop() {
			return delegate.getBearingTop();
		}

		@Override
		public boolean isColored() {
			return true;
		}

		/**
		 * Renders the glyph the way vanilla's {@code TrueTypeGlyphProvider} does
		 * ({@code NativeImage.copyFromFont}, so {@code NativeImageMixin} still applies), or in
		 * crisp mode with hard edges ({@link CrispGlyphs}), then expands it to RGBA. Locks the face as vanilla does while loading glyphs, because
		 * glyph metrics are loaded off the render thread.
		 */
		@Override
		public void upload(int x, int y, GpuTexture texture) {
			int width = getPixelWidth();
			int height = getPixelHeight();
			synchronized (face) {
				if (!SystemFaces.contains(face)) {
					return; // provider closed by a reload; vanilla would throw here
				}
				try (NativeImage rgba = new NativeImage(NativeImage.Format.RGBA, width, height, true)) {
					boolean rendered = SystemFaces.isCrisp(face)
						? CrispGlyphs.render(face, glyphIndex, SystemFaces.isSyntheticBold(face), width, height, (px, py, c) -> set(rgba, px, py, c))
						: renderSmooth(rgba, width, height);
					if (rendered) {
						RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, rgba, 0, 0, x, y);
					}
				}
			}
		}

		/** Vanilla's antialiased rendering, expanded to RGBA. */
		private boolean renderSmooth(NativeImage rgba, int width, int height) {
			try (NativeImage coverage = new NativeImage(NativeImage.Format.LUMINANCE, width, height, false)) {
				if (!coverage.copyFromFont(face, glyphIndex)) {
					return false;
				}
				for (int py = 0; py < height; py++) {
					for (int px = 0; px < width; px++) {
						set(rgba, px, py, coverage.getLuminanceOrAlpha(px, py) & 0xFF);
					}
				}
				return true;
			}
		}

		private static void set(NativeImage rgba, int x, int y, int c) {
			rgba.setPixelABGR(x, y, c << 24 | c << 16 | c << 8 | c);
		}
	}
}
