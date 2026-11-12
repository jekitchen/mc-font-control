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
 * <p>This glyph reports itself as coloured, so it lands in the RGBA atlas and draws with the
 * same {@code text*} pipelines as vanilla's font.
 *
 * <p>Each coverage value {@code c} is uploaded as white with alpha {@code c}, the way vanilla's
 * own font images store their glyphs (D36). The text shader multiplies the texel by the text
 * colour and the pipeline blends by alpha ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}), so an edge
 * pixel gets exactly {@code c} of the text colour. Vanilla's grayscale path samples
 * {@code (c, c, c, c)} ({@code core/text.fsh}, {@code IS_GRAYSCALE}), which applies coverage
 * twice: light text on a dark background lost most of its edge pixels and looked thin.
 *
 * <p>Smooth glyphs also get a thinner drop shadow ({@link TextShadow}, D37).
 */
final class ColorAtlasGlyph implements UnbakedGlyph {
	private final UnbakedGlyph delegate;
	private final FT_Face face;
	private final int glyphIndex;
	private final boolean thinShadow;
	private final GlyphInfo info;

	/**
	 * @param face       the FreeType face the delegate came from
	 * @param glyphIndex the glyph's index in that face
	 */
	ColorAtlasGlyph(UnbakedGlyph delegate, FT_Face face, int glyphIndex) {
		this.delegate = delegate;
		this.face = face;
		this.glyphIndex = glyphIndex;
		this.thinShadow = !SystemFaces.isCrisp(face);
		this.info = drawn(delegate.info());
	}

	@Override
	public GlyphInfo info() {
		return info;
	}

	@Override
	public BakedGlyph bake(UnbakedGlyph.Stitcher stitcher) {
		return delegate.bake(new UnbakedGlyph.Stitcher() {
			@Override
			public BakedGlyph stitch(GlyphInfo bakedInfo, GlyphBitmap bitmap) {
				// the baked glyph's info is the one Font reads the shadow offset from
				return stitcher.stitch(drawn(bakedInfo), bitmap.isColored() ? bitmap : new Rgba(bitmap));
			}

			@Override
			public BakedGlyph getMissing() {
				return stitcher.getMissing();
			}
		});
	}

	private GlyphInfo drawn(GlyphInfo metrics) {
		return thinShadow ? TextShadow.thin(metrics) : metrics;
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
		 * crisp mode with hard edges ({@link CrispGlyphs}), then expands it to RGBA. Locks the
		 * face as vanilla does while loading glyphs, because glyph metrics are loaded off the
		 * render thread.
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
			rgba.setPixelABGR(x, y, texel(c));
		}
	}

	/** The ABGR texel for a coverage value: white, with the coverage as alpha (D36). */
	static int texel(int coverage) {
		return coverage << 24 | 0xFFFFFF;
	}
}
