package io.github.jekitchen.mcfontcontrol.pack;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

/**
 * Hides the system font's private-use characters, so server resource packs' custom HUD glyphs
 * and negative-space characters always come from the server pack or vanilla (PRD AP-11, D11).
 *
 * <p>Done as a wrapper rather than with the provider's {@code skip} list: vanilla keeps
 * {@code skip} in a linear-lookup set and checks it for every character the font maps, which
 * is far too slow for the ~137,000 private-use code points.
 */
public final class PrivateUseFilteringProvider implements GlyphProvider {
	private final GlyphProvider delegate;
	private IntSet supported;

	public PrivateUseFilteringProvider(GlyphProvider delegate) {
		this.delegate = delegate;
	}

	/** U+E000–U+F8FF, plus the supplementary private-use planes 15 and 16. */
	public static boolean isPrivateUse(int codepoint) {
		return (codepoint >= 0xE000 && codepoint <= 0xF8FF)
			|| (codepoint >= 0xF0000 && codepoint <= 0xFFFFD)
			|| (codepoint >= 0x100000 && codepoint <= 0x10FFFD);
	}

	@Override
	public @Nullable UnbakedGlyph getGlyph(int codepoint) {
		return isPrivateUse(codepoint) ? null : delegate.getGlyph(codepoint);
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
		delegate.close();
	}
}
