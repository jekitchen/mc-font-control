package io.github.jekitchen.fontsys.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import io.github.jekitchen.fontsys.pack.CrispGlyphs;
import io.github.jekitchen.fontsys.pack.SyntheticBold;
import io.github.jekitchen.fontsys.pack.SystemFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Measures glyphs the way Fontsys renders them: emboldened for synthetic bold (PRD AP-8, D24)
 * and hinted for monochrome output in crisp mode (AP-10, D34).
 *
 * <p>Target: {@code TrueTypeGlyphProvider.loadGlyph(int, FT_Face, int)}, its
 * {@code FT_Load_Glyph(face, index, FT_LOAD_NO_BITMAP | FT_LOAD_BITMAP_METRICS_ONLY)} call.
 * Vanilla reads the glyph's advance and bitmap size from that load. For a face that FreeType
 * emboldens because the family has no heavier face, the outline is loaded, emboldened and
 * rendered instead, so the advance and bitmap size include the extra stroke weight.
 * {@link NativeImageMixin} renders the same way. For a crisp face the load also targets
 * monochrome output ({@code FT_LOAD_TARGET_MONO}), whose hinting and bitmap size differ from
 * the antialiased default, so the measured size matches {@code CrispGlyphs.render}. Every other
 * face loads as in vanilla.
 */
@Mixin(TrueTypeGlyphProvider.class)
abstract class TrueTypeGlyphProviderMixin {
	@WrapOperation(method = "loadGlyph", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
	private int fontsys$measureStyledGlyph(FT_Face face, int glyphIndex, int flags, Operation<Integer> original) {
		boolean crisp = SystemFaces.isCrisp(face);
		int target = crisp ? CrispGlyphs.LOAD_TARGET_MONO : 0;
		if (!SystemFaces.isSyntheticBold(face)) {
			return original.call(face, glyphIndex, flags | target);
		}
		int error = original.call(face, glyphIndex, FreeType.FT_LOAD_NO_BITMAP | target);
		return error != 0 ? error : SyntheticBold.emboldenAndRender(face, crisp);
	}
}
