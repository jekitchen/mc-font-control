package io.github.jekitchen.mcfontcontrol.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.font.GlyphProvider;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import io.github.jekitchen.mcfontcontrol.pack.PrivateUseFilteringProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Loads system fonts through vanilla's FreeType {@code ttf} provider (PRD D1).
 *
 * <p>Target: {@code TrueTypeGlyphProviderDefinition.load(ResourceManager)}. Only touches font
 * files served by our runtime pack ({@link PackContents#isSystemFont}); every other
 * {@code ttf} provider loads exactly as in vanilla.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
abstract class TrueTypeGlyphProviderDefinitionMixin {
	@Shadow
	@Final
	private Identifier location;

	/** Hides private-use characters so server-pack glyphs always win (AP-11). */
	@ModifyReturnValue(method = "load", at = @At("RETURN"))
	private GlyphProvider mcfontcontrol$filterPrivateUse(GlyphProvider provider) {
		return PackContents.isSystemFont(location) ? new PrivateUseFilteringProvider(provider) : provider;
	}
}
