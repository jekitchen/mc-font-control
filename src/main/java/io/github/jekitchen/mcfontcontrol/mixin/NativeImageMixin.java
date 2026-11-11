package io.github.jekitchen.mcfontcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.mcfontcontrol.pack.SystemFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prevents a crash with fonts that contain embedded bitmap strikes (PRD AP-7).
 *
 * <p>Target: {@code NativeImage.copyFromFont(FT_Face, int)}, its {@code FT_Load_Glyph(face,
 * index, FT_LOAD_RENDER)} call. Vanilla measures glyphs with {@code FT_LOAD_NO_BITMAP}
 * ({@code TrueTypeGlyphProvider.loadGlyph}) but renders them without it. For fonts with
 * embedded bitmaps at the requested size (MS Gothic, SimSun and many CJK fonts), FreeType
 * then returns a 1-bit bitmap and vanilla throws "Rendered glyph was not 8-bit grayscale",
 * crashing the game. Adding {@code FT_LOAD_NO_BITMAP} renders the outline, matching the
 * metrics. Only applied to faces loaded by MC Font Control ({@link SystemFaces}).
 */
@Mixin(NativeImage.class)
abstract class NativeImageMixin {
	@WrapOperation(method = "copyFromFont", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
	private int mcfontcontrol$renderOutlines(FT_Face face, int glyphIndex, int flags, Operation<Integer> original) {
		int effective = SystemFaces.contains(face) ? flags | FreeType.FT_LOAD_NO_BITMAP : flags;
		return original.call(face, glyphIndex, effective);
	}
}
