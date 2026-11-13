package io.github.jekitchen.fontsys.fabric;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.platform.ClientHooks;
import io.github.jekitchen.fontsys.ui.FontChangeRefresh;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;

/**
 * Fabric client entry point. Keeps Fabric-specific wiring out of the common code (PRD D8):
 * it connects Fabric API's events to {@link ClientHooks}, which NeoForge shares (D51).
 */
public final class FontsysFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Fontsys.init(new FabricPlatform());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> FontsCommand.register(dispatcher));

		KeyMapping.Category category = KeyMapping.Category.register(ClientHooks.KEY_CATEGORY);
		KeyMapping openKey = KeyMappingHelper.registerKeyMapping(ClientHooks.createOpenKey(category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientHooks.endClientTick(mc, openKey));
		// Fabric fires these each time a screen is shown, also when the player returns to it.
		// Labels laid out in the old font are refit when the player returns to a screen (UI-18).
		ScreenEvents.BEFORE_INIT.register((mc, screen, width, height) -> FontChangeRefresh.beforeInit(screen));
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			FontChangeRefresh.afterInit(screen);
			ClientHooks.addToFontOptions(screen);
		});
	}
}
