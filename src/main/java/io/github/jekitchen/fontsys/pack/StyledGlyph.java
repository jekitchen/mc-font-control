package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

/**
 * A glyph from a real (or FreeType-emboldened) bold or italic face. It switches off vanilla's
 * simulated styles, which would otherwise be added on top (PRD AP-8, D24):
 *
 * <ul>
 *   <li>Bold: vanilla draws every bold glyph twice, the second copy one GUI pixel to the right
 *       ({@code GlyphInfo.getBoldOffset()}, drawn in {@code BakedSheetGlyph.renderChar}), and
 *       widens the advance by the same pixel. With a bold face that offset is 0 and the
 *       glyph is drawn once.</li>
 *   <li>Italic: vanilla shears glyphs of italic text. A real italic face is drawn upright.</li>
 * </ul>
 */
final class StyledGlyph implements UnbakedGlyph {
	private final UnbakedGlyph delegate;
	private final GlyphInfo info;
	private final boolean providesBold;
	private final boolean providesItalic;

	StyledGlyph(UnbakedGlyph delegate, boolean providesBold, boolean providesItalic) {
		this.delegate = delegate;
		this.providesBold = providesBold;
		this.providesItalic = providesItalic;
		this.info = providesBold ? new NoSimulatedBold(delegate.info()) : delegate.info();
	}

	@Override
	public GlyphInfo info() {
		return info;
	}

	@Override
	public BakedGlyph bake(UnbakedGlyph.Stitcher stitcher) {
		BakedGlyph baked = delegate.bake(stitcher);
		return new BakedGlyph() {
			@Override
			public GlyphInfo info() {
				return info;
			}

			@Override
			public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
				Style drawn = style;
				if (providesBold && drawn.isBold()) {
					drawn = drawn.withBold(false);
					boldOffset = 0.0F;
				}
				if (providesItalic && drawn.isItalic()) {
					drawn = drawn.withItalic(false);
				}
				return baked.createGlyph(x, y, color, shadowColor, drawn, boldOffset, shadowOffset);
			}
		};
	}

	/** The face's own metrics, with no extra pixel for simulated bold. */
	private record NoSimulatedBold(GlyphInfo delegate) implements GlyphInfo {
		@Override
		public float getAdvance() {
			return delegate.getAdvance();
		}

		@Override
		public float getBoldOffset() {
			return 0.0F;
		}

		@Override
		public float getShadowOffset() {
			return delegate.getShadowOffset();
		}
	}
}
