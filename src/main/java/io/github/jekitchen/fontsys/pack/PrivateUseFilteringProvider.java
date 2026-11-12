package io.github.jekitchen.fontsys.pack;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

/**
 * Wraps the {@code ttf} provider vanilla opens for one of our font files.
 *
 * <ul>
 *   <li>Hides the system font's private-use characters, so server resource packs' custom HUD
 *       glyphs and negative-space characters always come from the server pack or vanilla
 *       (PRD AP-11, D11). Done here rather than with the provider's {@code skip} list: vanilla
 *       keeps {@code skip} in a linear-lookup set and checks it for every character the font
 *       maps, which is far too slow for the ~137,000 private-use code points.</li>
 *   <li>Wraps each glyph in a {@link ColorAtlasGlyph}, so it draws with the same pipelines as
 *       vanilla's own font and works with shader packs (D30).</li>
 *   <li>For bold and italic faces, wraps each glyph in a {@link StyledGlyph} so vanilla doesn't
 *       simulate the style on top (AP-8, D24).</li>
 * </ul>
 */
public final class PrivateUseFilteringProvider implements GlyphProvider {
	private final GlyphProvider delegate;
	private final FT_Face face;
	private final FaceSpec spec;
	private IntSet supported;

	/**
	 * @param face the delegate's FreeType face, registered with {@link SystemFaces} until this
	 *             provider closes; may be null
	 * @param spec how the face is drawn
	 * @param crisp render with hard edges (crisp mode, AP-10)
	 */
	public PrivateUseFilteringProvider(GlyphProvider delegate, FT_Face face, FaceSpec spec, boolean crisp) {
		this.delegate = delegate;
		this.face = face;
		this.spec = spec;
		SystemFaces.add(face, spec.syntheticBold(), crisp);
	}

	/** U+E000–U+F8FF, plus the supplementary private-use planes 15 and 16. */
	public static boolean isPrivateUse(int codepoint) {
		return (codepoint >= 0xE000 && codepoint <= 0xF8FF)
			|| (codepoint >= 0xF0000 && codepoint <= 0xFFFFD)
			|| (codepoint >= 0x100000 && codepoint <= 0x10FFFD);
	}

	@Override
	public @Nullable UnbakedGlyph getGlyph(int codepoint) {
		if (isPrivateUse(codepoint)) {
			return null;
		}
		UnbakedGlyph glyph = delegate.getGlyph(codepoint);
		if (glyph == null) {
			return null;
		}
		if (face != null) {
			glyph = new ColorAtlasGlyph(glyph, face, (int) FreeType.FT_Get_Char_Index(face, codepoint));
		}
		return spec.replacesVanillaStyle() ? new StyledGlyph(glyph, spec.providesBold(), spec.providesItalic()) : glyph;
	}

	@Override
	public synchronized IntSet getSupportedGlyphs() {
		if (supported == null) {
			IntSet filtered = new IntOpenHashSet();
			delegate.getSupportedGlyphs().forEach(cp -> {
				if (!isPrivateUse(cp)) {
					filtered.add(cp);
				}
			});
			supported = filtered;
		}
		return supported;
	}

	@Override
	public void close() {
		if (face != null) {
			SystemFaces.remove(face.address());
		}
		delegate.close();
	}
}
