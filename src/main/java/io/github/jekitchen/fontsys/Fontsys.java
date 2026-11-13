package io.github.jekitchen.fontsys;

import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.ConfigStore;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FontDirectories;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.discovery.OsFamily;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
import io.github.jekitchen.fontsys.pack.RuntimeFontPackSource;
import io.github.jekitchen.fontsys.platform.Platform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. Loader-specific code lives in sub-packages
 * (for example {@code fabric}) so a later NeoForge port stays cheap (PRD D8).
 */
public final class Fontsys {
	public static final String MOD_ID = "fontsys";
	public static final String DISPLAY_NAME = "Fontsys";
	public static final Logger LOGGER = LoggerFactory.getLogger(DISPLAY_NAME);

	private static volatile FontController controller;

	/**
	 * Created eagerly because the game builds its pack list in {@code Minecraft.<init>},
	 * possibly before our entry point runs; it reads the controller only at reload time.
	 */
	private static final RuntimeFontPackSource PACK_SOURCE = new RuntimeFontPackSource(
		() -> Fontsys.controller == null ? FontSettings.DEFAULTS : Fontsys.controller.appliedSettings(),
		() -> Fontsys.controller == null || Fontsys.controller.stepAside());

	private static Platform platform;
	private static SystemFontIndex fontIndex;

	private Fontsys() {
	}

	/** Called once by the loader entry point during client start-up. */
	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
		InstanceFonts.setGameDir(platform.gameDir());
		createInstanceFontsFolder(platform.gameDir());
		fontIndex = new SystemFontIndex(dataDir().resolve("font-index.json"), OsFamily.current(), Fontsys::fontDirectories);
		controller = new FontController(new ConfigStore(platform.configDir().resolve(MOD_ID + ".json"), platform.gameDir()), platform);
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

	/** Creates the instance's {@code fonts/} folder on start-up (D38); a failure costs only the empty folder. */
	private static void createInstanceFontsFolder(@Nullable Path gameDir) {
		if (gameDir == null) {
			return;
		}
		try {
			InstanceFonts.createDir(gameDir);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[{}] Could not create the instance fonts folder: {}", DISPLAY_NAME, e.toString());
		}
	}

	/** The system font folders, then the instance's {@code fonts/} folder (FD-14). */
	private static List<Path> fontDirectories() {
		List<Path> dirs = new ArrayList<>(FontDirectories.forCurrentSystem());
		InstanceFonts.dir().ifPresent(dirs::add);
		return dirs;
	}

	/** {@code config/fontsys/}: font cache. */
	public static Path dataDir() {
		return platform.configDir().resolve(MOD_ID);
	}
}
