package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.jekitchen.fontsys.Fontsys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Clearer distant in-world text (PRD D50): the mipmapped atlas pages of one vanilla
 * {@code GlyphStitcher}, which {@code GlyphStitcherMixin} hands system-font glyphs to after
 * vanilla has stitched them onto its own pages. In-world text draws from these pages, GUI text
 * from vanilla's ({@link MipmappedSheetGlyph}).
 *
 * <p>Only smooth glyphs of Fontsys's own fonts come here ({@link #wants}). Vanilla's fonts,
 * server resource-pack glyphs (AP-11) and crisp glyphs (D34) are only on vanilla's pages and
 * draw exactly as before, in the world too.
 *
 * <p>If anything goes wrong creating or filling a page, mipmaps are switched off for the rest of
 * the session with one warning, and new glyphs stay on vanilla's pages alone. They can also be
 * switched off at startup with {@code -Dfontsys.mipmaps=false}, for troubleshooting.
 */
public final class MipmappedGlyphPages {
	private static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("fontsys.mipmaps"));
	private static volatile boolean contrastBoost = true;

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
	 * Switches the contrast boost of the smaller levels ({@link GlyphMipmaps#boost}) on or off
	 * for glyphs baked from now on. Used by the game tests to measure the plain mean too.
	 */
	public static void setContrastBoost(boolean value) {
		contrastBoost = value;
	}

	static boolean contrastBoost() {
		return contrastBoost;
	}

	/**
	 * Adds a glyph that {@link #wants} accepted to a mipmapped page, after vanilla has stitched
	 * it onto its own page as usual.
	 *
	 * @param gui vanilla's glyph, which GUI text keeps drawing ({@link MipmappedSheetGlyph})
	 * @return a glyph that draws in-world text from the mipmapped page and GUI text from
	 *         {@code gui}, or null to keep {@code gui} alone (no page could hold it, or mipmaps
	 *         have just been switched off after an error)
	 */
	public @Nullable BakedSheetGlyph stitch(GlyphInfo info, GlyphBitmap bitmap, BakedSheetGlyph gui) {
		MipmappableBitmap glyph = (MipmappableBitmap) bitmap;
		try {
			for (MipmappedGlyphPage page : pages) {
				BakedSheetGlyph baked = page.add(info, glyph, gui);
				if (baked != null) {
					return baked;
				}
			}
			Identifier name = pageName(pages.size());
			MipmappedGlyphPage page = new MipmappedGlyphPage(name);
			pages.add(page);
			textureManager.register(name, page);
			return page.add(info, glyph, gui); // null if the glyph is larger than a whole page
		} catch (RuntimeException | LinkageError e) {
			enabled = false;
			Fontsys.LOGGER.warn("[{}] Could not build mipmapped glyph pages; distant text falls back to vanilla's atlas: {}", Fontsys.DISPLAY_NAME, e.toString());
			return null;
		}
	}

	/**
	 * The texture in-world text draws {@code glyph} from: a mipmapped page for system glyphs on
	 * one, otherwise the same texture as GUI text. For the game tests.
	 */
	public static GpuTextureView worldTextureView(TextRenderable glyph) {
		return glyph instanceof MipmappedSheetGlyph.Instance both ? both.world().textureView() : glyph.textureView();
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
