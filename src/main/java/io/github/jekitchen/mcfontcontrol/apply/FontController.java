package io.github.jekitchen.mcfontcontrol.apply;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.compat.KnownFontMods;
import io.github.jekitchen.mcfontcontrol.config.ConfigStore;
import io.github.jekitchen.mcfontcontrol.config.FontConfig;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.platform.Platform;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Owns the config and decides what the runtime pack should serve. */
public final class FontController {
	private final ConfigStore store;
	private final Optional<KnownFontMods.KnownMod> conflict;
	private FontConfig config;
	private volatile long lastApplyMillis = -1;

	public FontController(ConfigStore store, Platform platform) {
		this.store = store;
		this.conflict = KnownFontMods.firstLoaded(platform);
		ConfigStore.LoadResult result = store.load();
		this.config = result.config();
		result.warnings().forEach(w -> McFontControl.LOGGER.warn("[{}] {}", McFontControl.DISPLAY_NAME, w));
		conflict.ifPresent(m -> McFontControl.LOGGER.warn("[{}] {} is installed and also replaces the game font; MC Font Control is leaving the font alone. Remove one of the two mods to choose.",
			McFontControl.DISPLAY_NAME, m.name()));
	}

	public synchronized FontConfig config() {
		return config;
	}

	/** Settings the runtime pack serves. */
	public synchronized FontSettings appliedSettings() {
		return config.settings();
	}

	/** Another font mod is loaded, so we serve no system font (AP-12). */
	public Optional<KnownFontMods.KnownMod> conflict() {
		return conflict;
	}

	public boolean stepAside() {
		return conflict.isPresent();
	}

	/**
	 * Saves new settings and reloads resources so they take effect (AP-6, D2: full reload).
	 * Logs how long the reload took, for the D14 Apply target.
	 */
	public CompletableFuture<Void> apply(FontSettings settings) {
		synchronized (this) {
			config.setSettings(settings);
		}
		save();
		long start = System.nanoTime();
		return Minecraft.getInstance().reloadResourcePacks().whenComplete((ok, error) -> {
			long ms = (System.nanoTime() - start) / 1_000_000;
			lastApplyMillis = ms;
			McFontControl.LOGGER.info("[{}] Applied {} in {} ms.", McFontControl.DISPLAY_NAME,
				settings.isActive() ? "'" + settings.familyName() + " " + settings.styleName() + "'" : "the vanilla font", ms);
		});
	}

	/** Duration of the last Apply's resource reload, or -1. */
	public long lastApplyMillis() {
		return lastApplyMillis;
	}

	public synchronized void save() {
		try {
			store.save(config);
		} catch (IOException e) {
			McFontControl.LOGGER.warn("[{}] Could not save {}: {}", McFontControl.DISPLAY_NAME, store.file().getFileName(), e.getMessage());
		}
	}
}
