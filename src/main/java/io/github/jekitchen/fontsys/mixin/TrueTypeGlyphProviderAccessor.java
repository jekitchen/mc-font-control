package io.github.jekitchen.fontsys.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to {@code TrueTypeGlyphProvider.face}, to recognise our faces at render time. */
@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeGlyphProviderAccessor {
	@Accessor("face")
	FT_Face fontsys$face();
}
