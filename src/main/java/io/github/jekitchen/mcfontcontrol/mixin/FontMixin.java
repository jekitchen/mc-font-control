package io.github.jekitchen.mcfontcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.jekitchen.mcfontcontrol.pack.StyleRouting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws bold and italic text with the family's real bold and italic faces (PRD AP-8, D24).
 *
 * <p>Targets the two places {@code Font} picks a glyph source for a character, both from
 * {@code style.getFont()}:
 *
 * <ul>
 *   <li>{@code Font.getGlyph(int, Style)}: every glyph that is drawn, including glow-ink
 *       outlines;</li>
 *   <li>{@code Font.lambda$new$0(int, Style)}: the width function of the {@code StringSplitter}
 *       built in {@code Font(Font.Provider)}, used for text width, wrapping and truncation.</li>
 * </ul>
 *
 * <p>Vanilla has one font per {@link FontDescription} and simulates bold (a second copy one
 * pixel to the right) and italic (a shear) on top of it. For bold or italic text in the
 * system font, {@link StyleRouting} returns one of our styled fonts instead, whose glyphs
 * switch the simulation off. All other text, and every font that isn't ours, is unchanged.
 */
@Mixin(Font.class)
abstract class FontMixin {
	@Shadow
	@Final
	private Font.Provider provider;

	@WrapOperation(method = {"getGlyph", "lambda$new$0"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Style;getFont()Lnet/minecraft/network/chat/FontDescription;"))
	private FontDescription mcfontcontrol$styledFace(Style style, Operation<FontDescription> original) {
		return StyleRouting.route(provider, original.call(style), style);
	}
}
