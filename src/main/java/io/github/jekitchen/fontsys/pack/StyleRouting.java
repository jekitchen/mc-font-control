package io.github.jekitchen.fontsys.pack;

import io.github.jekitchen.fontsys.mixin.FontManagerAccessor;
import io.github.jekitchen.fontsys.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/**
 * Picks the font bold and italic text is drawn with (PRD AP-8, D24). Vanilla has one font per
 * {@link FontDescription} and simulates bold and italic on its glyphs; {@code FontMixin} asks
 * this class instead, so styled text in the system font comes from the family's real bold and
 * italic faces.
 */
public final class StyleRouting {
	/** A {@code Font.Provider} with styled fonts of its own (the live preview). */
	public interface Router {
		FontDescription route(FontDescription font, boolean bold, boolean italic);
	}

	private StyleRouting() {
	}

	/**
	 * @param provider the {@code Font}'s glyph provider
	 * @param font     the font the style names
	 */
	public static FontDescription route(Object provider, FontDescription font, Style style) {
		boolean bold = style.isBold();
		boolean italic = style.isItalic();
		if (!bold && !italic) {
			return font;
		}
		if (provider instanceof Router router) {
			return router.route(font, bold, italic);
		}
		FontDescription routed = PackContents.route(font, bold, italic);
		// Only once the game's font manager has the styled font: mid-reload it may not yet.
		return routed == font || hasFontSet(routed) ? routed : font;
	}

	private static boolean hasFontSet(FontDescription font) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || !(font instanceof FontDescription.Resource resource)) {
			return false;
		}
		return ((FontManagerAccessor) ((MinecraftAccessor) mc).fontsys$fontManager()).fontsys$fontSets().containsKey(resource.id());
	}
}
