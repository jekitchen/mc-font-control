package io.github.jekitchen.fontsys.ui;

import io.github.jekitchen.fontsys.pack.PackContents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text that always renders in the vanilla font, whatever font is applied: it is styled with
 * {@code fontsys:vanilla}, which references vanilla's glyph sets directly (D13).
 */
public final class VanillaText {
	private VanillaText() {
	}

	public static MutableComponent of(Component text) {
		return text.copy().withStyle(style -> style.withFont(PackContents.VANILLA_FONT));
	}

	public static MutableComponent translatable(String key, Object... args) {
		return of(Component.translatable(key, args));
	}
}
