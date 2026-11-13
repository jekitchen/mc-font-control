package io.github.jekitchen.fontsys.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.font.GlyphProvider;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.pack.PrivateUseFilteringProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Loads system fonts through vanilla's FreeType {@code ttf} provider (PRD D1).
 *
 * <p>Target: {@code TrueTypeGlyphProviderDefinition.load(ResourceManager)}. In 26.3 it opens
 * every font with face index {@code 0} and rejects anything whose FreeType format is not
 * {@code "TrueType"}. Without this mixin, faces inside {@code .ttc}/{@code .otc} collections
 * (most macOS system fonts), variable-font named instances and CFF-flavored {@code .otf}
 * files cannot be used.
 *
 * <p>Only font files served by our runtime pack are touched ({@link PackContents#isSystemFont});
 * every other {@code ttf} provider loads exactly as in vanilla.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
abstract class TrueTypeGlyphProviderDefinitionMixin {
	@Shadow
	@Final
	private Identifier location;

	/**
	 * Face index argument of {@code FT_New_Memory_Face}: the face number in the low bits and
	 * the variable-font named instance in the upper bits (FD-12, AP-5).
	 */
	@ModifyArg(method = "load", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_New_Memory_Face(JLjava/nio/ByteBuffer;JLorg/lwjgl/PointerBuffer;)I"), index = 2)
	private long fontsys$faceIndex(long faceIndex) {
		return PackContents.isSystemFont(location) ? PackContents.specFor(location).faceIndex() : faceIndex;
	}

	/**
	 * The {@code "TrueType".equals(FT_Get_Font_Format(face))} check. FreeType renders CFF
	 * outlines through the same API, so CFF-based {@code .otf} files are accepted (AP-9).
	 */
	@ModifyExpressionValue(method = "load", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Get_Font_Format(Lorg/lwjgl/util/freetype/FT_Face;)Ljava/lang/String;"))
	private String fontsys$acceptCff(String format) {
		return PackContents.isSystemFont(location) && "CFF".equals(format) ? "TrueType" : format;
	}

	/**
	 * Hides private-use characters so server-pack glyphs always win (AP-11), registers the face
	 * so {@link NativeImageMixin} can render it safely, and for bold and italic faces switches
	 * off vanilla's simulated styles (AP-8, D24).
	 */
	@ModifyReturnValue(method = "load", at = @At("RETURN"))
	private GlyphProvider fontsys$filterPrivateUse(GlyphProvider provider) {
		if (!PackContents.isSystemFont(location)) {
			return provider;
		}
		PackContents.markProviderLoaded(location);
		FT_Face face = provider instanceof TrueTypeGlyphProviderAccessor accessor ? accessor.fontsys$face() : null;
		return new PrivateUseFilteringProvider(provider, face, PackContents.specFor(location), PackContents.loaded().crisp());
	}
}
