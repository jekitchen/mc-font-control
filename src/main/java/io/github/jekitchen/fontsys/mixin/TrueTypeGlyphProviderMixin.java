package io.github.jekitchen.fontsys.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import io.github.jekitchen.fontsys.pack.SyntheticBold;
import io.github.jekitchen.fontsys.pack.SystemFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Measures glyphs the way Fontsys renders them: emboldened for synthetic bold (PRD AP-8, D24),
 * hinted for monochrome output in crisp mode (AP-10, D34), and otherwise with the chosen
 * hinting (D53).
 *
 * <p>Target: {@code TrueTypeGlyphProvider.loadGlyph(int, FT_Face, int)}, its
 * {@code FT_Load_Glyph(face, index, FT_LOAD_NO_BITMAP | FT_LOAD_BITMAP_METRICS_ONLY)} call.
 * Vanilla reads the glyph's advance and bitmap size from that load. For a face that FreeType
 * emboldens because the family has no heavier face, the outline is loaded, emboldened and
 * rendered instead, so the advance and bitmap size include the extra stroke weight.
 * {@link NativeImageMixin} renders the same way. For a crisp face the load also targets
 * monochrome output ({@code FT_LOAD_TARGET_MONO}), whose hinting and bitmap size differ from
 * the antialiased default, so the measured size matches {@code CrispGlyphs.render}. For a smooth
 * face whose hinting is Light or None, the load carries that mode's flags
 * ({@code HintingFlags}), which change the outline, the advance and the bitmap size; the render
 * in {@link NativeImageMixin} uses the same flags, or vanilla's size check throws. All of it
 * comes from {@code SystemFaces.loadFlags}, which is 0 for every face that isn't Fontsys's own,
 * so vanilla's and resource packs' fonts load as in vanilla.
 */
@Mixin(TrueTypeGlyphProvider.class)
abstract class TrueTypeGlyphProviderMixin {
	@WrapOperation(method = "loadGlyph", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
	private int fontsys$measureStyledGlyph(FT_Face face, int glyphIndex, int flags, Operation<Integer> original) {
		boolean crisp = SystemFaces.isCrisp(face);
		int target = SystemFaces.loadFlags(face);
		if (!SystemFaces.isSyntheticBold(face)) {
			return original.call(face, glyphIndex, flags | target);
		}
		int error = original.call(face, glyphIndex, FreeType.FT_LOAD_NO_BITMAP | target);
		return error != 0 ? error : SyntheticBold.emboldenAndRender(face, crisp);
	}
}
