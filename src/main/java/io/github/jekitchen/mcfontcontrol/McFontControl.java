package io.github.jekitchen.mcfontcontrol;

import io.github.jekitchen.mcfontcontrol.apply.FontController;
import io.github.jekitchen.mcfontcontrol.config.ConfigStore;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FontDirectories;
import io.github.jekitchen.mcfontcontrol.discovery.OsFamily;
import io.github.jekitchen.mcfontcontrol.discovery.SystemFontIndex;
import io.github.jekitchen.mcfontcontrol.pack.RuntimeFontPackSource;
import io.github.jekitchen.mcfontcontrol.platform.Platform;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. Loader-specific code lives in sub-packages
 * (for example {@code fabric}) so a later NeoForge port stays cheap (PRD D8).
 */
public final class McFontControl {
	public static final String MOD_ID = "mcfontcontrol";
	public static final String DISPLAY_NAME = "MC Font Control";
	public static final Logger LOGGER = LoggerFactory.getLogger(DISPLAY_NAME);

	private static volatile FontController controller;

	/**
	 * Created eagerly because the game builds its pack list in {@code Minecraft.<init>},
	 * possibly before our entry point runs; it reads the controller only at reload time.
	 */
	private static final RuntimeFontPackSource PACK_SOURCE = new RuntimeFontPackSource(
		() -> McFontControl.controller == null ? FontSettings.DEFAULTS : McFontControl.controller.appliedSettings(),
		() -> McFontControl.controller == null || McFontControl.controller.stepAside());

	private static Platform platform;
	private static SystemFontIndex fontIndex;

	private McFontControl() {
	}

	/** Called once by the loader entry point during client start-up. */
	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
		fontIndex = new SystemFontIndex(dataDir().resolve("font-index.json"), OsFamily.current(), FontDirectories::forCurrentSystem);
		controller = new FontController(new ConfigStore(platform.configDir().resolve(MOD_ID + ".json")), platform);
		fontIndex.scan();
	}

	public static Platform platform() {
		return platform;
	}

	public static SystemFontIndex fontIndex() {
		return fontIndex;
	}

	public static FontController controller() {
		return controller;
	}

	public static RuntimeFontPackSource packSource() {
		return PACK_SOURCE;
	}

	/** {@code config/mcfontcontrol/}: font cache. */
	public static Path dataDir() {
		return platform.configDir().resolve(MOD_ID);
	}
}
