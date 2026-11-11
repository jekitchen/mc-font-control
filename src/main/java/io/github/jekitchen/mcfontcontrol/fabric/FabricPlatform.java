package io.github.jekitchen.mcfontcontrol.fabric;

import io.github.jekitchen.mcfontcontrol.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}
}
