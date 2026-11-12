package io.github.jekitchen.mcfontcontrol;

import io.github.jekitchen.mcfontcontrol.discovery.FontDirectories;
import io.github.jekitchen.mcfontcontrol.discovery.OsFamily;
import io.github.jekitchen.mcfontcontrol.discovery.SystemFontIndex;
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

	private static Platform platform;
	private static SystemFontIndex fontIndex;

	private McFontControl() {
	}

	/** Called once by the loader entry point during client start-up. */
	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
		Path dataDir = dataDir();
		fontIndex = new SystemFontIndex(dataDir.resolve("font-index.json"), OsFamily.current(), FontDirectories::forCurrentSystem);
		fontIndex.scan();
	}

	public static Platform platform() {
		return platform;
	}

	public static SystemFontIndex fontIndex() {
		return fontIndex;
	}

	/** {@code config/mcfontcontrol/}: cache and backups. */
	public static Path dataDir() {
		return platform.configDir().resolve(MOD_ID);
	}
}
