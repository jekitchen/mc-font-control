package io.github.jekitchen.fontsys.platform;

import java.nio.file.Path;

/**
 * The few things common code needs from the mod loader. Fabric implements this in the
 * {@code fabric} package; a NeoForge port would add its own implementation (PRD D8).
 */
public interface Platform {
	/** The game's {@code config/} folder. */
	Path configDir();

	/**
	 * The game (instance) folder that holds {@code mods/}, {@code config/} and, for instance
	 * fonts, {@code fonts/} (FD-14). Defaults to the parent of {@link #configDir()}.
	 */
	default Path gameDir() {
		return configDir().toAbsolutePath().normalize().getParent();
	}

	/** Whether a mod with this ID is loaded. */
	boolean isModLoaded(String modId);
}
