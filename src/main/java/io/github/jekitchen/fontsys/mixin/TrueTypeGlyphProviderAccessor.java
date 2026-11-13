package io.github.jekitchen.fontsys.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.gui.font.CodepointMap;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access to {@code TrueTypeGlyphProvider}'s fields:
 *
 * <ul>
 *   <li>{@code face}, to recognize our faces at render time;</li>
 *   <li>{@code glyphs}, the entry for every character the font maps, so the font list can empty
 *       it once a name's glyphs are baked and its face is closed (D49). The baked glyphs keep the
 *       provider reachable, and for a large CJK font that map holds tens of thousands of entries.</li>
 * </ul>
 */
@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeGlyphProviderAccessor {
	@Accessor("face")
	FT_Face fontsys$face();

	@Accessor("glyphs")
	CodepointMap<?> fontsys$glyphs();
}
