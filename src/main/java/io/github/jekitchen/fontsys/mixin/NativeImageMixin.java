package io.github.jekitchen.fontsys.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.pack.SyntheticBold;
import io.github.jekitchen.fontsys.pack.SystemFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Renders glyphs of system fonts safely, and emboldens them when bold is synthesized.
 *
 * <p>Target: {@code NativeImage.copyFromFont(FT_Face, int)}, its {@code FT_Load_Glyph(face,
 * index, FT_LOAD_RENDER)} call. Only faces loaded by Fontsys ({@link SystemFaces}) are
 * touched.
 *
 * <ul>
 *   <li>Embedded bitmaps (PRD AP-7, D20): vanilla measures glyphs with {@code FT_LOAD_NO_BITMAP}
 *       ({@code TrueTypeGlyphProvider.loadGlyph}) but renders them without it. For fonts with
 *       embedded bitmaps at the requested size (MS Gothic, SimSun and many CJK fonts), FreeType
 *       then returns a 1-bit bitmap and vanilla throws "Rendered glyph was not 8-bit grayscale",
 *       crashing the game. Adding {@code FT_LOAD_NO_BITMAP} renders the outline, matching the
 *       metrics.</li>
 *   <li>Synthetic bold (AP-8, D24): for a face that FreeType emboldens, the outline is loaded
 *       without rendering, emboldened, then rendered, exactly as {@code TrueTypeGlyphProviderMixin}
 *       measured it, so the bitmap matches the size vanilla allocated.</li>
 *   <li>Hinting (D53): the face's hinting flags ({@code SystemFaces.loadFlags}), the same ones
 *       {@code TrueTypeGlyphProviderMixin} measured with, so a Light or None glyph is rendered
 *       at the size and position vanilla allocated for it. Crisp faces don't come here: they
 *       render through {@code CrispGlyphs}.</li>
 * </ul>
 */
@Mixin(NativeImage.class)
abstract class NativeImageMixin {
	@WrapOperation(method = "copyFromFont", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
	private int fontsys$renderOutlines(FT_Face face, int glyphIndex, int flags, Operation<Integer> original) {
		if (!SystemFaces.contains(face)) {
			return original.call(face, glyphIndex, flags);
		}
		int hinting = SystemFaces.loadFlags(face);
		if (SystemFaces.isSyntheticBold(face)) {
			int error = original.call(face, glyphIndex, FreeType.FT_LOAD_NO_BITMAP | hinting);
			return error != 0 ? error : SyntheticBold.emboldenAndRender(face);
		}
		return original.call(face, glyphIndex, flags | FreeType.FT_LOAD_NO_BITMAP | hinting);
	}
}
