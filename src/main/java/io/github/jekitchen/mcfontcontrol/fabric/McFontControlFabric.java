package io.github.jekitchen.mcfontcontrol.fabric;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point. Keeps Fabric-specific wiring out of the common code (PRD D8). */
public final class McFontControlFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		McFontControl.init();
	}
}
