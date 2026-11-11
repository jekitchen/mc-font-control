package io.github.jekitchen.mcfontcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import io.github.jekitchen.mcfontcontrol.pack.SyntheticBold;
import io.github.jekitchen.mcfontcontrol.pack.SystemFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Measures emboldened glyphs for synthetic bold (PRD AP-8, D24).
 *
 * <p>Target: {@code TrueTypeGlyphProvider.loadGlyph(int, FT_Face, int)}, its
 * {@code FT_Load_Glyph(face, index, FT_LOAD_NO_BITMAP | FT_LOAD_BITMAP_METRICS_ONLY)} call.
 * Vanilla reads the glyph's advance and bitmap size from that load. For a face that FreeType
 * emboldens because the family has no heavier face, the outline is loaded, emboldened and
 * rendered instead, so the advance and bitmap size include the extra stroke weight.
 * {@link NativeImageMixin} renders the same way. Every other face loads as in vanilla.
 */
@Mixin(TrueTypeGlyphProvider.class)
abstract class TrueTypeGlyphProviderMixin {
	@WrapOperation(method = "loadGlyph", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
	private int mcfontcontrol$emboldenSyntheticBold(FT_Face face, int glyphIndex, int flags, Operation<Integer> original) {
		if (!SystemFaces.isSyntheticBold(face)) {
			return original.call(face, glyphIndex, flags);
		}
		int error = original.call(face, glyphIndex, FreeType.FT_LOAD_NO_BITMAP);
		return error != 0 ? error : SyntheticBold.emboldenAndRender(face);
	}
}
