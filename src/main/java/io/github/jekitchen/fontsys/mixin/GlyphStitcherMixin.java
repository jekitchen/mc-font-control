package io.github.jekitchen.fontsys.mixin;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import io.github.jekitchen.fontsys.pack.MipmappedGlyphPages;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts smooth system-font glyphs on mipmapped atlas pages, so distant in-world text fades evenly
 * instead of breaking up (PRD D50).
 *
 * <p>Targets:
 *
 * <ul>
 *   <li>{@code GlyphStitcher.stitch(GlyphInfo, GlyphBitmap)}: where every glyph of a font set
 *       goes onto an atlas page. Vanilla creates its {@code FontTexture} pages here with one mip
 *       level, a {@code NEAREST} sampler and 1 px between glyphs, and {@code FontTexture} has no
 *       way to ask for more. A glyph that {@link MipmappedGlyphPages#wants} is stitched onto
 *       Fontsys's own pages instead; every other glyph (vanilla's fonts, server-pack glyphs,
 *       crisp mode) takes vanilla's path unchanged, as does a glyph Fontsys's pages refuse.</li>
 *   <li>{@code GlyphStitcher.reset()}: vanilla releases its pages here on every font reload,
 *       and {@code close()} calls it too. Fontsys's pages are released with them.</li>
 * </ul>
 *
 * <p>This replaces the {@code FontTexture} mixin the PRD first sketched: changing vanilla's
 * pages would mipmap vanilla and server-pack glyphs too, and the stitcher is the one place that
 * sees each glyph before a page is chosen.
 */
@Mixin(GlyphStitcher.class)
abstract class GlyphStitcherMixin {
	@Shadow
	@Final
	private TextureManager textureManager;

	@Shadow
	@Final
	private Identifier texturePrefix;

	@Unique
	private @Nullable MipmappedGlyphPages fontsys$mipmappedPages;

	@Inject(method = "stitch", at = @At("HEAD"), cancellable = true)
	private void fontsys$stitchMipmapped(GlyphInfo info, GlyphBitmap bitmap, CallbackInfoReturnable<BakedSheetGlyph> cir) {
		if (!MipmappedGlyphPages.wants(bitmap)) {
			return;
		}
		if (fontsys$mipmappedPages == null) {
			fontsys$mipmappedPages = new MipmappedGlyphPages(textureManager, texturePrefix);
		}
		BakedSheetGlyph glyph = fontsys$mipmappedPages.stitch(info, bitmap);
		if (glyph != null) {
			cir.setReturnValue(glyph);
		}
	}

	@Inject(method = "reset", at = @At("HEAD"))
	private void fontsys$releaseMipmapped(CallbackInfo ci) {
		if (fontsys$mipmappedPages != null) {
			fontsys$mipmappedPages.reset();
		}
	}
}
