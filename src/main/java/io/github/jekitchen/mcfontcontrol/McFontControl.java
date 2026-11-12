package io.github.jekitchen.mcfontcontrol;

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

	private McFontControl() {
	}

	/** Called once by the loader entry point during client start-up. */
	public static void init() {
		LOGGER.info("[{}] Loaded; font replacement is not active yet (M1 skeleton).", DISPLAY_NAME);
	}
}
