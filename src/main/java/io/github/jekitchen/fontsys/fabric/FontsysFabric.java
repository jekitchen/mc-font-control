package io.github.jekitchen.fontsys.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Fabric client entry point. Keeps Fabric-specific wiring out of the common code (PRD D8). */
public final class FontsysFabric implements ClientModInitializer {
	private KeyMapping openKey;
	private boolean conflictToastShown;

	@Override
	public void onInitializeClient() {
		Fontsys.init(new FabricPlatform());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> FontsCommand.register(dispatcher));

		// Optional keybind, unbound by default (UI-2).
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fontsys.open", InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(Minecraft mc) {
		Fontsys.controller().tick(mc);
		while (openKey.consumeClick()) {
			if (!(mc.gui.screen() instanceof FontConfigScreen)) {
				mc.gui.setScreen(new FontConfigScreen(mc.gui.screen()));
			}
		}
		// One notice per session when another font mod is loaded (AP-12).
		if (!conflictToastShown && mc.gui.screen() instanceof TitleScreen) {
			conflictToastShown = true;
			Fontsys.controller().conflict().ifPresent(mod -> SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable("fontsys.toast.conflict.title"),
				Component.translatable("fontsys.toast.conflict.message", mod.name())));
		}
	}
}
