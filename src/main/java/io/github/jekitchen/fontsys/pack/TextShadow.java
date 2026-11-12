package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.Minecraft;

/**
 * A thinner drop shadow for smooth system-font glyphs (PRD D37).
 *
 * <p>Vanilla draws the shadow one GUI unit down and right ({@code GlyphInfo.getShadowOffset()},
 * read by {@code Font.PreparedTextBuilder.accept} every time text is laid out). That is one
 * design pixel of the vanilla font, but 3–4 screen pixels at GUI scale 3–4, which makes thin
 * vector glyphs look doubled. Smooth glyphs get half a GUI unit instead, rounded to whole screen
 * pixels so the shadow's edges stay sharp, and never less than one screen pixel. Crisp glyphs
 * (AP-10) keep the classic offset, which matches a pixel font's design pixels.
 *
 * <p>The same offset sets the thickness of the glowing-sign outline
 * ({@code Font.prepare8xTextOutline}), which gets thinner in the same proportion.
 */
final class TextShadow {
	private TextShadow() {
	}

	/** The shadow offset in GUI units at a GUI scale. */
	static float offset(int guiScale) {
		if (guiScale <= 1) {
			return 1.0F;
		}
		int pixels = Math.max(1, Math.round(guiScale / 2.0F));
		return (float) pixels / guiScale;
	}

	/** Wraps a glyph's metrics so it draws with the thinner shadow. */
	static GlyphInfo thin(GlyphInfo info) {
		return new Thin(info);
	}

	private record Thin(GlyphInfo delegate) implements GlyphInfo {
		@Override
		public float getAdvance() {
			return delegate.getAdvance();
		}

		@Override
		public float getBoldOffset() {
			return delegate.getBoldOffset();
		}

		/** Read at layout time, so a GUI-scale change applies without a reload. */
		@Override
		public float getShadowOffset() {
			return offset(Minecraft.getInstance().getWindow().getGuiScale());
		}
	}
}
