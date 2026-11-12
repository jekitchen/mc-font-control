package io.github.jekitchen.fontsys.compat;

import io.github.jekitchen.fontsys.platform.Platform;
import java.util.List;
import java.util.Optional;

/**
 * Other mods that replace the game font (PRD AP-12). If one is loaded, Fontsys steps
 * aside and leaves the font alone. Keep this list here and nowhere else.
 */
public final class KnownFontMods {
	/** A known font-replacement mod: its mod ID and display name. */
	public record KnownMod(String id, String name) {
	}

	public static final List<KnownMod> MODS = List.of(
		new KnownMod("caxton", "Caxton"),
		new KnownMod("font", "Font"),
		new KnownMod("fontswap", "FontSwap"),
		new KnownMod("modernui", "Modern UI"));

	private KnownFontMods() {
	}

	public static Optional<KnownMod> firstLoaded(Platform platform) {
		return MODS.stream().filter(m -> platform.isModLoaded(m.id())).findFirst();
	}
}
