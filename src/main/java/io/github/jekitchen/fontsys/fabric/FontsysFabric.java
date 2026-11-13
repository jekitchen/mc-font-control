package io.github.jekitchen.fontsys.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
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

		// Opens the settings from anywhere in game; F7 by default, which vanilla 26.3 leaves free (UI-2, D39).
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Fontsys.MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fontsys.open", InputConstants.KEY_F7, category));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> addToFontOptions(screen));
	}

	/**
	 * A Fontsys button in Options > Language > Font Settings, under Force Unicode Font, so the
	 * settings can be found without Mod Menu (UI-1). It goes into the screen's own option list,
	 * which lays it out and scrolls it with the vanilla options.
	 */
	private static void addToFontOptions(Screen screen) {
		if (!(screen instanceof FontOptionsScreen)) {
			return;
		}
		for (GuiEventListener child : screen.children()) {
			if (child instanceof OptionsList options) {
				options.addBig(Button.builder(Component.translatable("fontsys.options.button"), b -> Minecraft.getInstance().gui.setScreen(new FontConfigScreen(screen)))
					.tooltip(Tooltip.create(Component.translatable("fontsys.options.button.tooltip")))
					.width(Button.BIG_WIDTH)
					.build());
				return;
			}
		}
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
