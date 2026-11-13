package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import io.github.jekitchen.fontsys.Fontsys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Clearer distant in-world text (PRD D50): the mipmapped atlas pages of one vanilla
 * {@code GlyphStitcher}, which {@code GlyphStitcherMixin} hands system-font glyphs to.
 *
 * <p>Only smooth glyphs of Fontsys's own fonts come here ({@link #wants}). Vanilla's fonts,
 * server resource-pack glyphs (AP-11) and crisp glyphs (D34) stay on vanilla's pages, exactly
 * as before.
 *
 * <p>If anything goes wrong creating or filling a page, mipmaps are switched off for the rest of
 * the session with one warning, and every glyph goes to vanilla's pages again. They can also be
 * switched off at startup with {@code -Dfontsys.mipmaps=false}, for troubleshooting.
 */
public final class MipmappedGlyphPages {
	private static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("fontsys.mipmaps"));

	private final TextureManager textureManager;
	private final Identifier texturePrefix;
	private final List<MipmappedGlyphPage> pages = new ArrayList<>();

	public MipmappedGlyphPages(TextureManager textureManager, Identifier texturePrefix) {
		this.textureManager = textureManager;
		this.texturePrefix = texturePrefix;
	}

	/** Whether {@code bitmap} belongs on a mipmapped page. */
	public static boolean wants(GlyphBitmap bitmap) {
		return enabled && bitmap instanceof MipmappableBitmap mipmappable && mipmappable.mipmapped();
	}

	/**
	 * Switches mipmapped pages on or off for glyphs baked from now on; already-baked glyphs keep
	 * their page until the next font reload. Used by the game tests to compare before and after.
	 */
	public static void setEnabled(boolean value) {
		enabled = value;
	}

	/**
	 * Stitches a glyph that {@link #wants} accepted.
	 *
	 * @return the baked glyph, or null to let vanilla stitch it (no page could hold it, or
	 *         mipmaps have just been switched off after an error)
	 */
	public @Nullable BakedSheetGlyph stitch(GlyphInfo info, GlyphBitmap bitmap) {
		MipmappableBitmap glyph = (MipmappableBitmap) bitmap;
		try {
			for (MipmappedGlyphPage page : pages) {
				BakedSheetGlyph baked = page.add(info, glyph);
				if (baked != null) {
					return baked;
				}
			}
			Identifier name = pageName(pages.size());
			MipmappedGlyphPage page = new MipmappedGlyphPage(name);
			pages.add(page);
			textureManager.register(name, page);
			return page.add(info, glyph); // null if the glyph is larger than a whole page
		} catch (RuntimeException | LinkageError e) {
			enabled = false;
			Fontsys.LOGGER.warn("[{}] Could not build mipmapped glyph pages; distant text falls back to vanilla's atlas: {}", Fontsys.DISPLAY_NAME, e.toString());
			return null;
		}
	}

	/** Releases every page; called when vanilla resets or closes the stitcher (font reload). */
	public void reset() {
		for (int i = 0; i < pages.size(); i++) {
			textureManager.release(pageName(i));
		}
		pages.clear();
	}

	/** {@code fontsys:font_mip/<namespace>/<path>/<n>}, unique per font set like vanilla's page names. */
	private Identifier pageName(int index) {
		return Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "font_mip/" + texturePrefix.getNamespace() + "/" + texturePrefix.getPath() + "/" + index);
	}
}
